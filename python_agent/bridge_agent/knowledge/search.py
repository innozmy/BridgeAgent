"""混搜召回后的 kind 封顶组包。不调云、不连 Milvus。"""

from __future__ import annotations

from bridge_agent.settings import SEARCH_MAX_FIGURES, SEARCH_MAX_TABLES, SEARCH_PACK_LIMIT

EMPTY_NOTICE = "本项目未启用已嵌入的规范"


def pack_hits(
    rows: list[dict],
    *,
    limit: int | None = None,
    max_figures: int | None = None,
    max_tables: int | None = None,
) -> list[dict]:
    """
    按 RRF 顺序截取。图/表各有上限，空位补给条文。
    rows 已是融合后的顺序，不要再按分数排。
    """
    cap = int(limit if limit is not None else SEARCH_PACK_LIMIT)
    fig_cap = int(max_figures if max_figures is not None else SEARCH_MAX_FIGURES)
    tab_cap = int(max_tables if max_tables is not None else SEARCH_MAX_TABLES)
    packed: list[dict] = []
    n_fig = 0
    n_tab = 0
    for row in rows:
        if len(packed) >= cap:
            break
        kind = str(row.get("kind") or "text")
        if kind == "figure":
            if n_fig >= fig_cap:
                continue
            n_fig += 1
        elif kind == "table":
            if n_tab >= tab_cap:
                continue
            n_tab += 1
        packed.append(row)
    return packed


def collect_pending(packed: list[dict]) -> tuple[list[dict], list[dict]]:
    """待补发必须带 documentId，避免不同文献的 ref_id 撞车。"""
    figures: list[dict] = []
    tables: list[dict] = []
    seen_f: set[tuple[int, str]] = set()
    seen_t: set[tuple[int, str]] = set()
    for row in packed:
        doc = int(row.get("documentId") or 0)
        if doc <= 0:
            continue
        kind = str(row.get("kind") or "")
        if kind == "figure":
            _add_pending(figures, seen_f, doc, row.get("refId"))
        elif kind == "table":
            _add_pending(tables, seen_t, doc, row.get("refId"))
        else:
            for ref in row.get("mentionedFigureIds") or []:
                _add_pending(figures, seen_f, doc, ref)
            for ref in row.get("mentionedTableIds") or []:
                _add_pending(tables, seen_t, doc, ref)
    return figures, tables


def present_hit(row: dict, names: dict[int, str]) -> dict:
    """Milvus 行 → 交给模型的短出处。不含分数、不含向量。"""
    doc = int(row.get("document_id") or row.get("documentId") or 0)
    kind = str(row.get("kind") or "text")
    return {
        "kind": kind,
        "body": str(row.get("body") or ""),
        "documentId": doc,
        "documentName": names.get(doc) or "",
        "familyCode": str(row.get("family_code") or row.get("familyCode") or ""),
        "clauseNo": str(row.get("clause_no") or row.get("clauseNo") or ""),
        "figureNo": str(row.get("figure_no") or row.get("figureNo") or ""),
        "tableNo": str(row.get("table_no") or row.get("tableNo") or ""),
        "pageNumbers": _ints(row.get("page_numbers") or row.get("pageNumbers")),
        "refId": str(row.get("ref_id") or row.get("refId") or ""),
        "mentionedFigureIds": _strs(row.get("mentioned_figure_ids") or row.get("mentionedFigureIds")),
        "mentionedTableIds": _strs(row.get("mentioned_table_ids") or row.get("mentionedTableIds")),
    }


def empty_result(notice: str = EMPTY_NOTICE) -> dict:
    return {
        "ok": True,
        "notice": notice,
        "hits": [],
        "pendingFigures": [],
        "pendingTables": [],
    }


def _add_pending(bucket: list[dict], seen: set[tuple[int, str]], document_id: int, ref) -> None:
    text = str(ref or "").strip()
    if not text:
        return
    key = (document_id, text)
    if key in seen:
        return
    seen.add(key)
    bucket.append({"documentId": document_id, "refId": text})


def _ints(raw) -> list[int]:
    out: list[int] = []
    if not isinstance(raw, list):
        return out
    for item in raw:
        try:
            out.append(int(item))
        except (TypeError, ValueError):
            continue
    return out


def _strs(raw) -> list[str]:
    if not isinstance(raw, list):
        return []
    return [str(item) for item in raw if str(item or "").strip()]
