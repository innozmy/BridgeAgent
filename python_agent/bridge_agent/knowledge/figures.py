"""从 Unstructured 元素生成 sidecar，裁图落盘，千问读附图。"""

from __future__ import annotations

import base64
import re
import shutil
from pathlib import Path

from bridge_agent.knowledge.sidecar import build_hierarchy, persist_unstructured_element, write_sidecar
from bridge_agent.llm.qwen_vl import chat_json
from bridge_agent.settings import API_KEY, ROOT

CLAUSE_RE = re.compile(
    r"(?:第\s*)?(\d+(?:\.\d+)+|[A-Z]\.\d+(?:\.\d+)*)\s*条?"
)
CAPTION_RE = re.compile(r"图\s*[A-Z]?\s*\d+(?:\.\d+)*")
HEADER_TYPES = {"Header", "Footer"}
IMAGE_TYPES = {"Image", "Figure", "Picture"}
# 页顶这一带且面积很小的图当 Logo，不送千问
HEADER_BAND = 0.08
MIN_AREA_RATIO = 0.012
MIN_JPEG_BYTES = 2500

_SKILL = (ROOT / "bridge_agent" / "skills" / "knowledge_parse" / "figure.md").read_text(
    encoding="utf-8"
)


def wipe_outputs(elements_path: Path, figures_dir: Path) -> None:
    """只清解析产物。目录名必须是 parse/ 才整夹删，避免误删 source.pdf 或 merge/。"""
    parse_dir = elements_path.parent
    if parse_dir.is_dir() and parse_dir.name == "parse":
        shutil.rmtree(parse_dir, ignore_errors=True)
        return
    if elements_path.is_file():
        elements_path.unlink()
    if figures_dir.is_dir() and figures_dir.name == "figures":
        shutil.rmtree(figures_dir, ignore_errors=True)


def materialize(
    raw_elements: list[dict],
    *,
    sidecar: dict,
    elements_path: Path,
    figures_dir: Path,
    sha256: str,
) -> dict:
    """Unstructured 元素尽量原样落盘；图的像素写成 JPEG。先写 sidecar 再给千问。"""
    figures_dir.mkdir(parents=True, exist_ok=True)
    clean_elements: list[dict] = []
    figures: list[dict] = []
    last_clause = ""
    page_index: dict[int, int] = {}

    for index, raw in enumerate(raw_elements):
        kind = _element_type(raw)
        page = _page_number(raw)
        text = str(raw.get("text") or "")
        if CLAUSE_RE.search(text):
            match = CLAUSE_RE.search(text)
            if match:
                last_clause = match.group(1)

        item = persist_unstructured_element(raw, index)

        if kind in IMAGE_TYPES:
            skip_reason = _skip_reason(raw)
            jpeg = None if skip_reason else _decode_image(raw)
            if jpeg is None and not skip_reason:
                skip_reason = "no_image_bytes"
            if skip_reason:
                item["imageSkipped"] = skip_reason
            elif jpeg:
                n = page_index.get(page, 0) + 1
                page_index[page] = n
                rel = f"parse/figures/p{page}-i{n}.jpg"
                dest = figures_dir / f"p{page}-i{n}.jpg"
                dest.write_bytes(jpeg)
                caption, nearby = _caption_context(raw_elements, index, page)
                fig = {
                    "id": f"p{page}-i{n}",
                    "pageNumber": page,
                    "path": rel,
                    "caption": caption,
                    "nearbyClause": nearby or last_clause,
                    "nearbyText": _nearby_text(raw_elements, index, page),
                    "vlStatus": "pending",
                }
                figures.append(fig)
                item["figureId"] = fig["id"]
                item["imagePath"] = rel
        clean_elements.append(item)

    sidecar["elements"] = clean_elements
    sidecar["hierarchy"] = build_hierarchy(clean_elements)
    sidecar["figures"] = figures
    write_sidecar(elements_path, sidecar)
    return sidecar


def recognize_pending_figures(
    sidecar: dict,
    *,
    elements_path: Path,
    figures_dir: Path,
    name: str,
    family_code: str,
) -> None:
    """对 vlStatus=pending 的图调千问；每张回写 sidecar。"""
    if not API_KEY:
        sidecar.setdefault("warnings", []).append("未配置 DASHSCOPE_API_KEY，附图未识别")
        for fig in sidecar.get("figures") or []:
            if fig.get("vlStatus") == "pending":
                fig["vlStatus"] = "failed"
                fig["vlError"] = "no_key"
        write_sidecar(elements_path, sidecar)
        return

    for fig in sidecar.get("figures") or []:
        if fig.get("vlStatus") != "pending":
            continue
        jpeg_path = _figure_abs(fig.get("path") or "", figures_dir)
        if jpeg_path is None or not jpeg_path.is_file():
            fig["vlStatus"] = "failed"
            fig["vlError"] = "missing_file"
            write_sidecar(elements_path, sidecar)
            continue
        prompt = _figure_prompt(name, family_code, fig)
        try:
            result = chat_json([jpeg_path.read_bytes()], prompt)
            fig["vlStatus"] = "ok"
            fig["vl"] = {
                "figureNo": str(result.get("figureNo") or ""),
                "inFigureText": str(result.get("inFigureText") or ""),
                "meaning": str(result.get("meaning") or ""),
                "unclear": bool(result.get("unclear")),
            }
        except Exception as exc:
            fig["vlStatus"] = "failed"
            fig["vlError"] = str(exc)[:400]
        write_sidecar(elements_path, sidecar)


def count_types(sidecar: dict) -> dict:
    elements = sidecar.get("elements") or []
    figures = sidecar.get("figures") or []
    tables = sum(1 for item in elements if item.get("type") == "Table")
    vl_done = sum(1 for item in figures if item.get("vlStatus") == "ok")
    vl_failed = sum(1 for item in figures if item.get("vlStatus") == "failed")
    vl_skipped = sum(1 for item in elements if item.get("imageSkipped"))
    return {
        "elementCount": len(elements),
        "tableCount": tables,
        "figureCount": len(figures),
        "vlDone": vl_done,
        "vlFailed": vl_failed,
        "vlSkipped": vl_skipped,
    }


def _figure_abs(rel: str, figures_dir: Path) -> Path | None:
    name = Path(rel.replace("\\", "/")).name
    if not name:
        return None
    return figures_dir / name


def _figure_prompt(name: str, family_code: str, fig: dict) -> str:
    return (
        _SKILL
        + "\n\n文献："
        + (name or "")
        + "\n规范号："
        + (family_code or "")
        + "\n页码："
        + str(fig.get("pageNumber") or "")
        + "\n图题："
        + (fig.get("caption") or "")
        + "\n邻近条号："
        + (fig.get("nearbyClause") or "")
        + "\n同页前后文字："
        + (fig.get("nearbyText") or "")
        + "\n请只输出 JSON。"
    )


def _element_type(raw: dict) -> str:
    return str(raw.get("type") or raw.get("category") or "UncategorizedText")


def _page_number(raw: dict) -> int:
    meta = raw.get("metadata") if isinstance(raw.get("metadata"), dict) else {}
    page = raw.get("page_number") or raw.get("pageNumber") or meta.get("page_number")
    try:
        return int(page or 0)
    except (TypeError, ValueError):
        return 0


def _text_as_html(raw: dict) -> str:
    meta = raw.get("metadata") if isinstance(raw.get("metadata"), dict) else {}
    html = meta.get("text_as_html") or raw.get("text_as_html") or ""
    return str(html) if html else ""


def _decode_image(raw: dict) -> bytes | None:
    meta = raw.get("metadata") if isinstance(raw.get("metadata"), dict) else {}
    b64 = meta.get("image_base64") or raw.get("image_base64")
    if not b64 or not isinstance(b64, str):
        return None
    try:
        data = base64.b64decode(b64)
    except Exception:
        return None
    if len(data) < MIN_JPEG_BYTES:
        return None
    return data


def _skip_reason(raw: dict) -> str | None:
    kind = _element_type(raw)
    if kind in HEADER_TYPES:
        return "header"
    meta = raw.get("metadata") if isinstance(raw.get("metadata"), dict) else {}
    coords = meta.get("coordinates") if isinstance(meta.get("coordinates"), dict) else {}
    points = coords.get("points") or []
    width = float(coords.get("layout_width") or 0)
    height = float(coords.get("layout_height") or 0)
    if not points or width <= 0 or height <= 0:
        return None
    xs = [float(p[0]) for p in points if isinstance(p, (list, tuple)) and p]
    ys = [float(p[1]) for p in points if isinstance(p, (list, tuple)) and len(p) > 1]
    if not xs or not ys:
        return None
    box_w = max(xs) - min(xs)
    box_h = max(ys) - min(ys)
    area_ratio = (box_w * box_h) / (width * height) if width * height else 0
    top = min(ys) / height
    if top <= HEADER_BAND and area_ratio < MIN_AREA_RATIO:
        return "logo"
    if area_ratio < MIN_AREA_RATIO * 0.4:
        return "too_small"
    return None


def _caption_context(elements: list[dict], index: int, page: int) -> tuple[str, str]:
    window = []
    for offset in range(-3, 4):
        pos = index + offset
        if pos < 0 or pos >= len(elements):
            continue
        item = elements[pos]
        if _page_number(item) not in (0, page):
            continue
        kind = _element_type(item)
        text = str(item.get("text") or "").strip()
        if not text:
            continue
        window.append((kind, text))
    caption = ""
    clause = ""
    for kind, text in window:
        if "Caption" in kind or CAPTION_RE.search(text):
            caption = text
        match = CLAUSE_RE.search(text)
        if match:
            clause = match.group(1)
    return caption, clause


def _nearby_text(elements: list[dict], index: int, page: int) -> str:
    bits = []
    for offset in (-2, -1, 1, 2):
        pos = index + offset
        if pos < 0 or pos >= len(elements):
            continue
        item = elements[pos]
        if _page_number(item) not in (0, page):
            continue
        kind = _element_type(item)
        if kind in IMAGE_TYPES:
            continue
        text = str(item.get("text") or "").strip()
        if text:
            bits.append(text[:200])
    return " / ".join(bits)[:600]
