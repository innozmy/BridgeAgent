"""知识合并作业：只读 parse，写出 merge/chunks.json。不调 Unstructured、不改第一步文件。"""

from __future__ import annotations

from pathlib import Path

from bridge_agent.knowledge.catalog import load_indexes
from bridge_agent.knowledge.merge import merge_elements
from bridge_agent.knowledge.sidecar import load_sidecar, write_sidecar


def run_merge(payload: dict) -> dict:
    elements_path = _abs(payload.get("elementsPath"))
    chunks_path = _abs(payload.get("chunksPath"))
    if elements_path is None or chunks_path is None:
        return _fail("缺少 elementsPath / chunksPath")
    sidecar = load_sidecar(elements_path)
    if not sidecar or not sidecar.get("elements"):
        return _fail("第一步解析结果不在磁盘上，请先解析")

    # 合并目录必须是 merge/，避免误写到 parse/
    if chunks_path.parent.name != "merge":
        return _fail("chunksPath 必须落在 merge/ 目录")
    if elements_path.resolve() == chunks_path.resolve():
        return _fail("禁止把合并结果写回解析文件")

    figures, tables = load_indexes(
        _abs(payload.get("figureIndexPath")),
        _abs(payload.get("tableIndexPath")),
        sidecar,
    )
    body = merge_elements(sidecar.get("elements") or [], figures, tables)
    body["documentId"] = payload.get("documentId")
    body["sha256"] = payload.get("sha256")
    body["source"] = {
        "elementsPath": str(elements_path),
        "figureIndexPath": str(payload.get("figureIndexPath") or ""),
        "tableIndexPath": str(payload.get("tableIndexPath") or ""),
    }
    if chunks_path.is_file():
        chunks_path.unlink()
    write_sidecar(chunks_path, body)
    return {
        "ok": True,
        "chunkCount": body["chunkCount"],
        "mentionedFigureChunkCount": body["mentionedFigureChunkCount"],
        "mentionedTableChunkCount": body["mentionedTableChunkCount"],
        "warnings": [],
    }


def _abs(value) -> Path | None:
    text = str(value or "").strip()
    if not text:
        return None
    return Path(text)


def _fail(error: str) -> dict:
    return {"ok": False, "error": error}
