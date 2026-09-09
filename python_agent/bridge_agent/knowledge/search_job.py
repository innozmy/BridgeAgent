"""知识检索作业：混搜 + 自我纠正包装。不写工程记忆、不读磁盘、不生成答案。"""

from __future__ import annotations

from bridge_agent.knowledge.correct import (
    attach_low_confidence,
    evaluate_hits,
    hard_pass,
    pick_better,
    queries_equivalent,
    rewrite_query,
)
from bridge_agent.knowledge.milvus_store import hybrid_search
from bridge_agent.knowledge.search import collect_pending, empty_result, pack_hits, present_hit
from bridge_agent.llm.qwen_embed import embed_hybrid
from bridge_agent.settings import (
    EMBED_DIM,
    SEARCH_ANN_LIMIT,
    SEARCH_CATEGORY,
    SEARCH_CORRECT,
    SEARCH_MAX_FIGURES,
    SEARCH_MAX_TABLES,
    SEARCH_PACK_LIMIT,
    SEARCH_RRF_CANDIDATES,
    SEARCH_RRF_K,
)


def run_search(payload: dict) -> dict:
    query = str(payload.get("query") or "").strip()
    if not query:
        return _fail("问句为空")
    document_ids = _ids(payload.get("documentIds"))
    names = _names(payload.get("documentNames"))
    if not document_ids:
        return empty_result()
    first = retrieve_once(query, document_ids, names)
    if not first.get("ok"):
        return first
    if not SEARCH_CORRECT:
        return first
    if _accepted(query, first):
        return first
    rewritten = rewrite_query(query, first.get("hits") or [])
    if not rewritten or queries_equivalent(query, rewritten):
        return attach_low_confidence(first)
    # 第二次仍按用户原问句评估，避免改写句自己放过自己
    second = retrieve_once(rewritten, document_ids, names)
    if not second.get("ok"):
        return attach_low_confidence(first)
    if _accepted(query, second):
        return second
    return attach_low_confidence(pick_better(first, second))


def retrieve_once(query: str, document_ids: list[int], names: dict[int, str]) -> dict:
    """单次混搜组包。自我纠正循环最多调两次。"""
    try:
        vectors = embed_hybrid([query], text_type="query", sparse=True)
        if not vectors:
            return _fail("问句 embedding 为空")
        vec = vectors[0]
        dense = vec["dense"]
        if len(dense) != EMBED_DIM:
            return _fail("dense 维数不是 %s" % EMBED_DIM)
        rows = hybrid_search(
            dense,
            vec.get("sparse") or {0: 1e-6},
            document_ids,
            category=SEARCH_CATEGORY,
            ann_limit=SEARCH_ANN_LIMIT,
            rrf_k=SEARCH_RRF_K,
            rrf_candidates=SEARCH_RRF_CANDIDATES,
        )
        presented = [present_hit(row, names) for row in rows]
        packed = pack_hits(
            presented,
            limit=SEARCH_PACK_LIMIT,
            max_figures=SEARCH_MAX_FIGURES,
            max_tables=SEARCH_MAX_TABLES,
        )
        pending_fig, pending_tab = collect_pending(packed)
    except Exception as exc:
        return _fail(str(exc) or "检索失败")
    return {
        "ok": True,
        "notice": None,
        "hits": packed,
        "pendingFigures": pending_fig,
        "pendingTables": pending_tab,
    }


def _accepted(query: str, result: dict) -> bool:
    if not hard_pass(result):
        return False
    judged = evaluate_hits(query, result.get("hits") or [])
    if judged is None:
        # 评估口不可用时不空转改写，直接用当前命中
        return True
    return judged


def _ids(raw) -> list[int]:
    out: list[int] = []
    if not isinstance(raw, list):
        return out
    seen: set[int] = set()
    for item in raw:
        try:
            value = int(item)
        except (TypeError, ValueError):
            continue
        if value <= 0 or value in seen:
            continue
        seen.add(value)
        out.append(value)
    return out


def _names(raw) -> dict[int, str]:
    out: dict[int, str] = {}
    if not isinstance(raw, dict):
        return out
    for key, value in raw.items():
        try:
            out[int(key)] = str(value or "")
        except (TypeError, ValueError):
            continue
    return out


def _fail(error: str) -> dict:
    return {"ok": False, "error": error}
