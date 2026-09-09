"""从 Spring 请求里挑出本机可读的 PDF。"""

from __future__ import annotations

from pathlib import Path

from bridge_agent.settings import FILES_ROOT


def file_name(item: dict) -> str:
    return (item.get("originalName") or item.get("original_name") or "") if isinstance(item, dict) else ""


def raw_path(item: dict) -> str:
    if not isinstance(item, dict):
        return ""
    return str(item.get("path") or item.get("absolutePath") or item.get("filePath") or "").strip()


def candidate_paths(raw: str) -> list[Path]:
    """绝对路径直接用；相对路径按 Spring 落盘目录再试。"""
    p = Path(raw)
    out = [p]
    if not p.is_absolute():
        out.append(FILES_ROOT / raw)
        out.append(Path.cwd() / raw)
    return out


def collect_pdfs(payload: dict) -> tuple[list[dict], list[str]]:
    files: list[dict] = []
    rejected: list[str] = []
    items = payload.get("files") or []
    if not items:
        keys = ",".join(payload.keys()) if isinstance(payload, dict) else type(payload).__name__
        return files, ["请求里 files 为空，顶层字段=[%s]" % keys]
    for item in items:
        if not isinstance(item, dict):
            rejected.append("files 项不是对象 keys 无法读取")
            continue
        name = file_name(item).lower()
        raw = raw_path(item)
        looks_pdf = name.endswith(".pdf") or raw.lower().endswith(".pdf")
        if not looks_pdf:
            rejected.append(
                "不是 PDF：originalName=%s path=%s 字段=%s"
                % (file_name(item), raw, ",".join(item.keys()))
            )
            continue
        if not raw:
            rejected.append(
                "没有路径字段：originalName=%s 字段=%s"
                % (file_name(item), ",".join(item.keys()))
            )
            continue
        found = None
        for cand in candidate_paths(raw):
            try:
                if cand.is_file():
                    found = cand
                    break
            except OSError:
                continue
        if found is None:
            rejected.append("磁盘上不存在：%s 字段=%s" % (raw, ",".join(item.keys())))
            continue
        copied = dict(item)
        copied["path"] = str(found)
        files.append(copied)
    return files, rejected
