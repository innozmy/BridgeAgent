"""知识嵌入作业：只读 split/ 与 parse 图/表目录，写入本机 Milvus。不改磁盘 sidecar。"""

from __future__ import annotations

from pathlib import Path

from bridge_agent.knowledge.catalog import load_indexes
from bridge_agent.knowledge.embed import build_embed_rows
from bridge_agent.knowledge.milvus_store import delete_document, upsert_rows
from bridge_agent.knowledge.sidecar import load_sidecar
from bridge_agent.llm.qwen_embed import embed_hybrid
from bridge_agent.settings import EMBED_DIM, EMBED_MODEL


def run_embed(payload: dict) -> dict:
    split_path = _abs(payload.get("splitChunksPath"))
    if split_path is None:
        return _fail("缺少 splitChunksPath")
    if split_path.parent.name != "split":
        return _fail("splitChunksPath 必须落在 split/ 目录")
    split_doc = load_sidecar(split_path)
    if not split_doc or not split_doc.get("chunks"):
        return _fail("第三步分割结果不在磁盘上，请先分割")

    try:
        document_id = int(payload.get("documentId") or 0)
    except (TypeError, ValueError):
        return _fail("documentId 无效")
    if document_id <= 0:
        return _fail("documentId 无效")

    try:
        elements = load_sidecar(_abs(payload.get("elementsPath"))) or {}
        figures, tables = load_indexes(
            _abs(payload.get("figureIndexPath")),
            _abs(payload.get("tableIndexPath")),
            elements,
        )
        rows = build_embed_rows(split_doc.get("chunks") or [], figures, tables, payload)
        if not rows:
            return _fail("没有可嵌入的条文/图/表")

        vectors = embed_hybrid([row["body"] for row in rows], text_type="document", sparse=True)
        if len(vectors) != len(rows):
            return _fail("embedding 条数与行数不一致")
        for row, vec in zip(rows, vectors):
            dense = vec["dense"]
            if len(dense) != EMBED_DIM:
                return _fail("dense 维数不是 %s" % EMBED_DIM)
            row["dense"] = dense
            # 空稀疏不能写 {}；占位必须非零，否则 Milvus 可能丢掉该维
            row["sparse"] = vec["sparse"] or {0: 1e-6}

        delete_document(document_id)
        written = upsert_rows(rows)
    except Exception as exc:
        return _fail(str(exc) or "嵌入失败")
    text_n = sum(1 for row in rows if row["kind"] == "text")
    fig_n = sum(1 for row in rows if row["kind"] == "figure")
    tab_n = sum(1 for row in rows if row["kind"] == "table")
    return {
        "ok": True,
        "rowCount": written,
        "textCount": text_n,
        "figureCount": fig_n,
        "tableCount": tab_n,
        "embedModel": EMBED_MODEL,
    }


def run_drop_vectors(payload: dict) -> dict:
    try:
        document_id = int(payload.get("documentId") or 0)
    except (TypeError, ValueError):
        return _fail("documentId 无效")
    if document_id <= 0:
        return _fail("documentId 无效")
    try:
        deleted = delete_document(document_id)
    except Exception as exc:
        return _fail(str(exc) or "删除向量失败")
    return {"ok": True, "deleted": deleted}


def _abs(value) -> Path | None:
    text = str(value or "").strip()
    if not text:
        return None
    return Path(text)


def _fail(error: str) -> dict:
    return {"ok": False, "error": error}
