"""规范图 JPEG / 表 HTML 补发。只打开 Spring 注入的文献根下已知文件，不扫总库。"""

from __future__ import annotations

import json
from pathlib import Path

from bridge_agent.settings import RESUPPLY_MAX_JPEG, RESUPPLY_TABLE_CHARS


def scope_ids(knowledge_scope: dict | None) -> list[int]:
    """启用 ∩ 已嵌入 ∩ 规范 的 documentId 列表。"""
    if not isinstance(knowledge_scope, dict):
        return []
    raw = knowledge_scope.get("documentIds")
    if isinstance(raw, list) and raw:
        return _ints(raw)
    docs = knowledge_scope.get("documents")
    if isinstance(docs, list):
        return [int(item.get("documentId")) for item in docs if isinstance(item, dict) and _safe_int(item.get("documentId"))]
    return []


def scope_names(knowledge_scope: dict | None) -> dict[int, str]:
    if not isinstance(knowledge_scope, dict):
        return {}
    names: dict[int, str] = {}
    raw = knowledge_scope.get("documentNames")
    if isinstance(raw, dict):
        for key, value in raw.items():
            doc = _safe_int(key)
            if doc:
                names[doc] = str(value or "")
    for item in knowledge_scope.get("documents") or []:
        if not isinstance(item, dict):
            continue
        doc = _safe_int(item.get("documentId"))
        if doc and doc not in names:
            names[doc] = str(item.get("name") or "")
    return names


def attach_resupply(
    result: dict,
    knowledge_scope: dict | None,
    *,
    max_jpeg: int | None = None,
    table_chars: int | None = None,
) -> dict:
    """
    按 pendingFigures / pendingTables 从注入的 rootPath 读 JPEG 与表 HTML。
    写回 result['figures']（含 jpegBase64 标记与 bytes 另放 images）与 result['tables']。
    JPEG 字节只放 result['_jpegBytes'] 给本进程 VL，不把大 base64 塞进工具回传文本。
    """
    jpeg_cap = int(max_jpeg if max_jpeg is not None else RESUPPLY_MAX_JPEG)
    tab_budget = int(table_chars if table_chars is not None else RESUPPLY_TABLE_CHARS)
    roots = _roots(knowledge_scope)
    figures: list[dict] = []
    jpeg_bytes: list[bytes] = []
    for pending in result.get("pendingFigures") or []:
        if len(jpeg_bytes) >= jpeg_cap:
            break
        if not isinstance(pending, dict):
            continue
        doc = _safe_int(pending.get("documentId"))
        ref = str(pending.get("refId") or "").strip()
        root = roots.get(doc)
        if not doc or not ref or root is None:
            continue
        loaded = _load_figure(root, ref)
        if loaded is None:
            continue
        meta, blob = loaded
        meta["documentId"] = doc
        figures.append(meta)
        jpeg_bytes.append(blob)
    tables: list[dict] = []
    used_chars = 0
    for pending in result.get("pendingTables") or []:
        if not isinstance(pending, dict):
            continue
        doc = _safe_int(pending.get("documentId"))
        ref = str(pending.get("refId") or "").strip()
        root = roots.get(doc)
        if not doc or not ref or root is None:
            continue
        loaded = _load_table(root, ref)
        if loaded is None:
            continue
        loaded["documentId"] = doc
        html = str(loaded.get("html") or "")
        if used_chars + len(html) > tab_budget and tables:
            break
        if len(html) > tab_budget - used_chars:
            html = html[: max(0, tab_budget - used_chars)]
        loaded["html"] = html
        used_chars += len(html)
        tables.append(loaded)
        if used_chars >= tab_budget:
            break
    out = dict(result)
    out["figures"] = figures
    out["tables"] = tables
    out["_jpegBytes"] = jpeg_bytes
    return out


def tool_result_for_model(result: dict) -> dict:
    """交给模型的工具回传：短文 + 表 HTML + 图题注，不含 JPEG 字节。"""
    hits = result.get("hits") or []
    notice = result.get("notice")
    tables = []
    for item in result.get("tables") or []:
        tables.append({
            "documentId": item.get("documentId"),
            "refId": item.get("refId"),
            "tableNo": item.get("tableNo"),
            "caption": item.get("caption"),
            "html": item.get("html") or "",
        })
    figures = []
    for item in result.get("figures") or []:
        figures.append({
            "documentId": item.get("documentId"),
            "refId": item.get("refId"),
            "figureNo": item.get("figureNo"),
            "caption": item.get("caption"),
            "attached": True,
        })
    body = {
        "ok": bool(result.get("ok")),
        "notice": notice,
        "hits": hits,
        "tables": tables,
        "figures": figures,
    }
    if result.get("error"):
        body["error"] = result.get("error")
    return body


def _roots(knowledge_scope: dict | None) -> dict[int, Path]:
    out: dict[int, Path] = {}
    if not isinstance(knowledge_scope, dict):
        return out
    for item in knowledge_scope.get("documents") or []:
        if not isinstance(item, dict):
            continue
        doc = _safe_int(item.get("documentId"))
        raw = str(item.get("rootPath") or "").strip()
        if not doc or not raw:
            continue
        path = Path(raw)
        if not path.is_dir():
            continue
        out[doc] = path
    return out


def _load_figure(root: Path, ref_id: str) -> tuple[dict, bytes] | None:
    index = _read_json(root / "parse" / "figures.json")
    figures = index.get("figures") if isinstance(index, dict) else None
    if not isinstance(figures, list):
        return None
    fig = None
    for item in figures:
        if isinstance(item, dict) and str(item.get("id") or "") == ref_id:
            fig = item
            break
    if fig is None:
        return None
    rel = str(fig.get("path") or "")
    name = Path(rel.replace("\\", "/")).name
    if not name:
        return None
    jpeg_path = (root / "parse" / "figures" / name).resolve()
    figures_dir = (root / "parse" / "figures").resolve()
    try:
        jpeg_path.relative_to(figures_dir)
    except ValueError:
        return None
    if not jpeg_path.is_file():
        return None
    blob = jpeg_path.read_bytes()
    if not blob:
        return None
    meta = {
        "documentId": None,
        "refId": ref_id,
        "figureNo": fig.get("figureNo") or "",
        "caption": fig.get("caption") or "",
        "pageNumber": fig.get("pageNumber"),
    }
    return meta, blob


def _load_table(root: Path, ref_id: str) -> dict | None:
    index = _read_json(root / "parse" / "tables.json")
    tables = index.get("tables") if isinstance(index, dict) else None
    if not isinstance(tables, list):
        return None
    for item in tables:
        if not isinstance(item, dict):
            continue
        if str(item.get("id") or "") != ref_id:
            continue
        html = str(item.get("textAsHtml") or item.get("html") or "")
        return {
            "documentId": None,
            "refId": ref_id,
            "tableNo": item.get("tableNo") or "",
            "caption": item.get("caption") or "",
            "html": html,
        }
    return None


def _read_json(path: Path) -> dict:
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _ints(raw) -> list[int]:
    out: list[int] = []
    seen: set[int] = set()
    for item in raw:
        value = _safe_int(item)
        if not value or value in seen:
            continue
        seen.add(value)
        out.append(value)
    return out


def _safe_int(value) -> int | None:
    try:
        number = int(value)
    except (TypeError, ValueError):
        return None
    return number if number > 0 else None
