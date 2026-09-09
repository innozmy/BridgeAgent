"""第二步：对着 parse 产物黏正文碎片。只读 parse，只写 merge/chunks.json。"""

from __future__ import annotations

import re

from bridge_agent.knowledge.catalog import normalize_ref

CLAUSE_RE = re.compile(
    r"^(?:第\s*)?(\d+(?:\.\d+)+|[A-Z]\.\d+(?:\.\d+)*)(?:\s*条)?"
)
# 「见图 6.2.3」「图6.2.3-1」；不要把「见图集」这种无数字的算进去
REF_FIG = re.compile(r"(?:见)?图\s*([A-Z]?\s*\d+(?:\.\d+)*(?:\s*-\s*\d+)?)")
REF_TAB = re.compile(r"(?:见)?表\s*([A-Z]?\s*\d+(?:\.\d+)*(?:\s*-\s*\d+)?)")

SKIP_TYPES = {
    "Header",
    "Footer",
    "PageBreak",
    "Image",
    "Figure",
    "Picture",
    "Table",
}


def merge_elements(elements: list[dict], figures: list[dict], tables: list[dict]) -> dict:
    """
    双钥匙：同一条号或同一 parent_id 可并；条号不同禁止合并。
    图/表不进正文，只给 chunk 挂 mentioned*Ids。
    """
    fig_by_no = _index_by_no(figures, "figureNo", "id")
    tab_by_no = _index_by_no(tables, "tableNo", "id")

    chunks: list[dict] = []
    current: dict | None = None
    for item in elements:
        kind = str(item.get("type") or "")
        if kind in SKIP_TYPES:
            continue
        text = str(item.get("text") or "").strip()
        if not text:
            continue
        clause = _clause_of(item)
        parent = _parent_of(item)
        if current is None:
            current = _new_chunk(item, clause, parent, text)
            continue
        if _can_merge(current, clause, parent):
            _append(current, item, clause, parent, text)
        else:
            chunks.append(_finish(current, fig_by_no, tab_by_no))
            current = _new_chunk(item, clause, parent, text)
    if current is not None:
        chunks.append(_finish(current, fig_by_no, tab_by_no))

    for index, chunk in enumerate(chunks, start=1):
        chunk["id"] = f"c-{index:04d}"

    mentioned_fig = sum(1 for chunk in chunks if chunk.get("mentionedFigureIds"))
    mentioned_tab = sum(1 for chunk in chunks if chunk.get("mentionedTableIds"))
    return {
        "schemaVersion": 1,
        "readOnlyParse": True,
        "chunkCount": len(chunks),
        "mentionedFigureChunkCount": mentioned_fig,
        "mentionedTableChunkCount": mentioned_tab,
        "chunks": chunks,
    }


def _can_merge(current: dict, clause: str, parent: str) -> bool:
    cur_clause = current.get("clauseNo") or ""
    cur_parent = current.get("parentId") or ""
    if cur_clause and clause and cur_clause != clause:
        return False
    if cur_clause and clause and cur_clause == clause:
        return True
    if cur_parent and parent and cur_parent == parent:
        return True
    # 没写出条号的紧随碎片并进当前条，直到出现新条号
    if cur_clause and not clause:
        return True
    return False


def _new_chunk(item: dict, clause: str, parent: str, text: str) -> dict:
    page = item.get("pageNumber")
    return {
        "clauseNo": clause,
        "parentId": parent,
        "elementIds": [_eid(item)],
        "pageNumbers": [page] if page not in (None, 0) else [],
        "text": text,
    }


def _append(current: dict, item: dict, clause: str, parent: str, text: str) -> None:
    if not current.get("clauseNo") and clause:
        current["clauseNo"] = clause
    if not current.get("parentId") and parent:
        current["parentId"] = parent
    eid = _eid(item)
    if eid and eid not in current["elementIds"]:
        current["elementIds"].append(eid)
    page = item.get("pageNumber")
    if page not in (None, 0) and page not in current["pageNumbers"]:
        current["pageNumbers"].append(page)
    current["text"] = (current["text"] + "\n" + text).strip()


def _finish(current: dict, fig_by_no: dict[str, str], tab_by_no: dict[str, str]) -> dict:
    text = current.get("text") or ""
    current["mentionedFigureIds"] = _collect_refs(text, REF_FIG, fig_by_no)
    current["mentionedTableIds"] = _collect_refs(text, REF_TAB, tab_by_no)
    return current


def _collect_refs(text: str, pattern: re.Pattern, by_no: dict[str, str]) -> list[str]:
    ids: list[str] = []
    seen: set[str] = set()
    for match in pattern.finditer(text):
        number = normalize_ref(match.group(1))
        fid = by_no.get(number)
        if fid and fid not in seen:
            seen.add(fid)
            ids.append(fid)
    return ids


def _index_by_no(rows: list[dict], number_key: str, id_key: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for row in rows:
        number = normalize_ref(str(row.get(number_key) or ""))
        ident = str(row.get(id_key) or "")
        if number and ident and number not in out:
            out[number] = ident
    return out


def _clause_of(item: dict) -> str:
    match = CLAUSE_RE.search(str(item.get("text") or ""))
    return match.group(1) if match else ""


def _parent_of(item: dict) -> str:
    meta = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
    return str(item.get("parentId") or meta.get("parent_id") or "")


def _eid(item: dict) -> str:
    return str(item.get("element_id") or item.get("id") or "")
