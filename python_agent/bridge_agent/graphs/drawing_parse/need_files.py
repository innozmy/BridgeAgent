"""从图纸目录解析 needFiles。fileId 必须来自 catalog，禁止编造路径。"""

from __future__ import annotations


def _as_int(value) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def catalog_index(payload: dict, current_file_id=None) -> tuple[dict[int, dict], set[int]]:
    """fileId → 目录项；already 含本轮正在打开的文件。"""
    by_id: dict[int, dict] = {}
    for item in payload.get("drawingCatalog") or []:
        if not isinstance(item, dict):
            continue
        fid = _as_int(item.get("fileId"))
        if fid is None:
            continue
        by_id[fid] = item
    already: set[int] = set()
    for raw in payload.get("alreadyFetchedFileIds") or []:
        fid = _as_int(raw)
        if fid is not None:
            already.add(fid)
    current = _as_int(current_file_id)
    if current is not None:
        already.add(current)
    return by_id, already


def _is_cad(item: dict) -> bool:
    kind = str(item.get("kind") or "").strip().lower()
    name = str(item.get("originalName") or "").strip().lower()
    return kind == "cad" or name.endswith(".dwg") or name.endswith(".dxf")


def _strip_rebar(kinds) -> list[str]:
    if not isinstance(kinds, list):
        return []
    out: list[str] = []
    for kind in kinds:
        text = str(kind or "").strip()
        if not text or text.lower() == "rebar":
            continue
        out.append(text)
    return out


def normalize_need_files(raw, catalog_by_id: dict[int, dict], already: set[int]) -> list[dict]:
    """只保留目录里未打开过的 PDF；只抽钢筋的条目丢掉。"""
    if not isinstance(raw, list):
        return []
    out: list[dict] = []
    seen: set[int] = set()
    for item in raw:
        if not isinstance(item, dict):
            continue
        fid = _as_int(item.get("fileId"))
        if fid is None or fid in already or fid in seen:
            continue
        meta = catalog_by_id.get(fid)
        if meta is None or _is_cad(meta):
            continue
        name = str(meta.get("originalName") or "").lower()
        if name and not name.endswith(".pdf") and meta.get("kind") not in (None, "", "drawing", "pdf"):
            continue
        kinds = _strip_rebar(item.get("focusKinds"))
        raw_kinds = item.get("focusKinds")
        if isinstance(raw_kinds, list) and raw_kinds and not kinds:
            continue
        row = {"fileId": fid, "reason": str(item.get("reason") or "本轮图纸不够")[:200]}
        if kinds:
            row["focusKinds"] = kinds
        out.append(row)
        seen.add(fid)
    return out


def heuristic_need_file(gaps: list[str], catalog_by_id: dict[int, dict], already: set[int]) -> list[dict]:
    """模型没填 needFiles 时：缺口仍在且目录里有未读、kindCounts 对得上的 PDF，补一条。"""
    if not gaps:
        return []
    need_layout = "spansM" in gaps or "missing_layout" in gaps
    need_notes = any(g in gaps for g in ("girderType", "layoutType", "material"))
    unread_unscanned: int | None = None
    for fid, item in catalog_by_id.items():
        if fid in already or _is_cad(item):
            continue
        name = str(item.get("originalName") or "").lower()
        if name.endswith(".dwg") or name.endswith(".dxf"):
            continue
        counts = item.get("kindCounts") if isinstance(item.get("kindCounts"), dict) else {}
        if need_layout and (
            counts.get("general_layout") or counts.get("elevation") or counts.get("project_notes")
        ):
            return [{
                "fileId": fid,
                "focusKinds": ["general_layout", "elevation", "project_notes"],
                "reason": "本册未读出跨径，目录中另一份有总布置/立面或竣工说明",
            }]
        if need_notes and (counts.get("project_notes") or counts.get("general_layout")):
            return [{
                "fileId": fid,
                "focusKinds": ["project_notes", "general_layout"],
                "reason": "本册未读出主梁/结构/材料，目录中另一份有说明或总布置",
            }]
        if not counts and unread_unscanned is None:
            unread_unscanned = fid
    if unread_unscanned is not None and (need_layout or need_notes):
        return [{
            "fileId": unread_unscanned,
            "reason": "目录中尚未打开的 PDF，可能含本轮缺口所需图纸",
        }]
    return []


def resolve_need_files(payload: dict, result: dict, gaps: list[str], primary: dict | None) -> list[dict]:
    current_id = None
    if isinstance(primary, dict):
        current_id = primary.get("fileId")
    catalog_by_id, already = catalog_index(payload, current_id)
    model = normalize_need_files(result.get("needFiles"), catalog_by_id, already)
    if model:
        return model
    return heuristic_need_file(list(gaps or []), catalog_by_id, already)
