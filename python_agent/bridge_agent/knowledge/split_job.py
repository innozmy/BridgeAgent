"""知识分割作业：只读 merge/chunks.json，写出 split/chunks.json。不改 parse/merge。"""

from __future__ import annotations

from pathlib import Path

from bridge_agent.knowledge.catalog import load_indexes
from bridge_agent.knowledge.sidecar import load_sidecar, write_sidecar
from bridge_agent.knowledge.split import split_merged_chunks
from bridge_agent.llm.qwen_embed import embed_texts
from bridge_agent.settings import EMBED_MODEL, SPLIT_MAX_CHARS, SPLIT_OVERLAP_CHARS


def run_split(payload: dict) -> dict:
    merge_path = _abs(payload.get("mergeChunksPath"))
    split_path = _abs(payload.get("splitChunksPath"))
    if merge_path is None or split_path is None:
        return _fail("缺少 mergeChunksPath / splitChunksPath")
    if split_path.parent.name != "split":
        return _fail("splitChunksPath 必须落在 split/ 目录")
    if merge_path.resolve() == split_path.resolve():
        return _fail("禁止把分割结果写回 merge")

    merged = load_sidecar(merge_path)
    if not merged or not merged.get("chunks"):
        return _fail("第二步合并结果不在磁盘上，请先合并")

    elements = load_sidecar(_abs(payload.get("elementsPath"))) or {}
    figures, tables = load_indexes(
        _abs(payload.get("figureIndexPath")),
        _abs(payload.get("tableIndexPath")),
        elements,
    )
    chunks, warnings = split_merged_chunks(merged.get("chunks") or [], figures, tables, embed_texts)
    split_count = sum(1 for item in chunks if item.get("splitFromLong"))
    body = {
        "schemaVersion": 1,
        "readOnlyMerge": True,
        "embedModel": EMBED_MODEL,
        "maxChars": SPLIT_MAX_CHARS,
        "overlapChars": SPLIT_OVERLAP_CHARS,
        "breakpoint": "gradient",
        "chunkCount": len(chunks),
        "splitSourceCount": split_count,
        "chunks": chunks,
        "documentId": payload.get("documentId"),
        "sha256": payload.get("sha256"),
        "source": {
            "mergeChunksPath": str(merge_path),
            "figureIndexPath": str(payload.get("figureIndexPath") or ""),
            "tableIndexPath": str(payload.get("tableIndexPath") or ""),
        },
        "warnings": warnings,
    }
    if split_path.is_file():
        split_path.unlink()
    write_sidecar(split_path, body)
    return {
        "ok": True,
        "chunkCount": len(chunks),
        "splitSourceCount": split_count,
        "warnings": warnings,
    }


def _abs(value) -> Path | None:
    text = str(value or "").strip()
    if not text:
        return None
    return Path(text)


def _fail(error: str) -> dict:
    return {"ok": False, "error": error}
