"""千问文本向量：切分只用 dense；入库用同一次 dense&sparse。向量不落 split JSON。"""

from __future__ import annotations

import httpx

from bridge_agent.settings import API_KEY, EMBED_DIM, EMBED_MODEL

_URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding"
_BATCH = 10


def embed_texts(texts: list[str]) -> list[list[float]]:
    """
    切分用：只取 dense。text_type=document，句与句对称相似度。
    一次最多 10 条（v4 限额）。
    """
    return [item["dense"] for item in embed_hybrid(texts, text_type="document", sparse=False)]


def embed_hybrid(texts: list[str], text_type: str = "document", sparse: bool = True) -> list[dict]:
    """
    入库/检索用。sparse=True 时 output_type=dense&sparse。
    每条返回 {dense: list[float], sparse: dict[int, float]}。
    """
    if not API_KEY:
        raise RuntimeError("未配置 DASHSCOPE_API_KEY")
    if not texts:
        return []
    out: list[dict] = []
    headers = {
        "Authorization": "Bearer " + API_KEY,
        "Content-Type": "application/json",
    }
    output_type = "dense&sparse" if sparse else "dense"
    with httpx.Client(timeout=60.0) as client:
        for start in range(0, len(texts), _BATCH):
            batch = texts[start : start + _BATCH]
            payload = {
                "model": EMBED_MODEL,
                "input": {"texts": batch},
                "parameters": {
                    "text_type": text_type,
                    "dimension": EMBED_DIM,
                    "output_type": output_type,
                },
            }
            response = client.post(_URL, headers=headers, json=payload)
            response.raise_for_status()
            body = response.json()
            err = body.get("code") or body.get("message")
            embeddings = ((body.get("output") or {}).get("embeddings")) or []
            if not embeddings:
                raise RuntimeError(str(err or "embedding 为空"))
            by_index = {int(item.get("text_index") or i): item for i, item in enumerate(embeddings)}
            for i in range(len(batch)):
                item = by_index.get(i)
                if not item or not item.get("embedding"):
                    raise RuntimeError("embedding 返回条数不足")
                dense = [float(x) for x in item["embedding"]]
                if len(dense) != EMBED_DIM:
                    raise RuntimeError("embedding 维数不是 %s" % EMBED_DIM)
                sparse_map = _sparse_map(item.get("sparse_embedding")) if sparse else {}
                out.append({"dense": dense, "sparse": sparse_map})
    return out


def _sparse_map(raw) -> dict[int, float]:
    """v4 文档是 [{index,value,token}]；兼容 {index: weight} 字典。"""
    out: dict[int, float] = {}
    if isinstance(raw, dict):
        items = raw.items()
    elif isinstance(raw, list):
        items = []
        for piece in raw:
            if not isinstance(piece, dict):
                continue
            items.append((piece.get("index"), piece.get("value")))
    else:
        return out
    for key, value in items:
        try:
            idx = int(key)
            val = float(value)
        except (TypeError, ValueError):
            continue
        if val != 0:
            out[idx] = val
    return out
