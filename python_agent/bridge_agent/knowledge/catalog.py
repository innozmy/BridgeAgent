"""第一步解析写出的图号/表号目录。合并只读，不回写本文件。"""

from __future__ import annotations

import re
from pathlib import Path

from bridge_agent.knowledge.sidecar import write_sidecar

# 图 6.2.3 / 图A.0.1 / 图 6.2.3-1
_FIG_NO = re.compile(r"图\s*([A-Z]?\s*\d+(?:\.\d+)*(?:\s*-\s*\d+)?)")
_TAB_NO = re.compile(r"表\s*([A-Z]?\s*\d+(?:\.\d+)*(?:\s*-\s*\d+)?)")
TABLE_TYPES = {"Table"}
IMAGE_TYPES = {"Image", "Figure", "Picture"}


def normalize_ref(value: str) -> str:
    """去掉空格，便于「图 6.2.3」和「图6.2.3」对上。"""
    return re.sub(r"\s+", "", (value or "").strip())


def figure_no_from_text(*parts: str) -> str:
    for part in parts:
        match = _FIG_NO.search(part or "")
        if match:
            return normalize_ref(match.group(1))
    return ""


def table_no_from_text(*parts: str) -> str:
    for part in parts:
        match = _TAB_NO.search(part or "")
        if match:
            return normalize_ref(match.group(1))
    return ""


def write_parse_indexes(sidecar: dict, parse_dir: Path) -> dict:
    """
    把 sidecar 里的图、表抽成 parse/figures.json 与 parse/tables.json。
    认不出号时 figureNo/tableNo 为空，id 仍用页码+序号或 element_id。
    """
    parse_dir.mkdir(parents=True, exist_ok=True)
    figures = []
    for fig in sidecar.get("figures") or []:
        vl = fig.get("vl") if isinstance(fig.get("vl"), dict) else {}
        number = figure_no_from_text(
            str(vl.get("figureNo") or ""),
            str(fig.get("caption") or ""),
            str(fig.get("nearbyText") or ""),
        )
        figures.append(
            {
                "id": fig.get("id"),
                "figureNo": number,
                "pageNumber": fig.get("pageNumber"),
                "path": fig.get("path"),
                "caption": fig.get("caption") or "",
                "nearbyClause": fig.get("nearbyClause") or "",
                "vlStatus": fig.get("vlStatus"),
                "vl": vl or None,
            }
        )

    tables = []
    last_clause = ""
    elements = sidecar.get("elements") or []
    for index, item in enumerate(elements):
        text = str(item.get("text") or "")
        clause = _clause_in(text)
        if clause:
            last_clause = clause
        if item.get("type") not in TABLE_TYPES:
            continue
        caption = _neighbor_caption(elements, index, _TAB_NO)
        number = table_no_from_text(caption, text, str(item.get("textAsHtml") or ""))
        element_id = str(item.get("element_id") or item.get("id") or f"table-{index}")
        tables.append(
            {
                "id": element_id,
                "tableNo": number,
                "pageNumber": item.get("pageNumber"),
                "caption": caption,
                "nearbyClause": last_clause,
                "elementId": element_id,
                "textAsHtml": item.get("textAsHtml") or "",
                "text": text[:2000],
            }
        )

    figure_doc = {"schemaVersion": 1, "figures": figures}
    table_doc = {"schemaVersion": 1, "tables": tables}
    write_sidecar(parse_dir / "figures.json", figure_doc)
    write_sidecar(parse_dir / "tables.json", table_doc)
    return {"figureIndexCount": len(figures), "tableIndexCount": len(tables)}


def load_indexes(figure_path: Path | None, table_path: Path | None, sidecar: dict) -> tuple[list[dict], list[dict]]:
    """优先读 parse 目录里的索引；没有则从 sidecar 现拼一份（只读，不落盘）。"""
    figures = _load_list(figure_path, "figures")
    tables = _load_list(table_path, "tables")
    if not figures:
        figures = list(sidecar.get("figures") or [])
        for fig in figures:
            vl = fig.get("vl") if isinstance(fig.get("vl"), dict) else {}
            fig.setdefault(
                "figureNo",
                figure_no_from_text(str(vl.get("figureNo") or ""), str(fig.get("caption") or "")),
            )
    if not tables:
        stub = {"elements": sidecar.get("elements") or [], "figures": sidecar.get("figures") or []}
        # 不写盘，只得到列表
        tables = []
        last_clause = ""
        elements = stub["elements"]
        for index, item in enumerate(elements):
            text = str(item.get("text") or "")
            clause = _clause_in(text)
            if clause:
                last_clause = clause
            if item.get("type") not in TABLE_TYPES:
                continue
            caption = _neighbor_caption(elements, index, _TAB_NO)
            element_id = str(item.get("element_id") or item.get("id") or f"table-{index}")
            tables.append(
                {
                    "id": element_id,
                    "tableNo": table_no_from_text(caption, text),
                    "caption": caption,
                    "nearbyClause": last_clause,
                }
            )
    return figures, tables


def _load_list(path: Path | None, key: str) -> list[dict]:
    if path is None or not path.is_file():
        return []
    from bridge_agent.knowledge.sidecar import load_sidecar

    data = load_sidecar(path)
    if not data:
        return []
    rows = data.get(key) or []
    return [row for row in rows if isinstance(row, dict)]


def _clause_in(text: str) -> str:
    match = re.search(r"(?:第\s*)?(\d+(?:\.\d+)+|[A-Z]\.\d+(?:\.\d+)*)\s*条?", text or "")
    return match.group(1) if match else ""


def _neighbor_caption(elements: list[dict], index: int, pattern: re.Pattern) -> str:
    page = elements[index].get("pageNumber")
    for offset in (-2, -1, 1, 2):
        pos = index + offset
        if pos < 0 or pos >= len(elements):
            continue
        item = elements[pos]
        if page not in (None, 0) and item.get("pageNumber") not in (0, page, None):
            continue
        text = str(item.get("text") or "").strip()
        if text and (item.get("type") == "Caption" or pattern.search(text)):
            return text
    return ""
