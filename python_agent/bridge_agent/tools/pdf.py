"""本机把 PDF 渲成 JPEG 页图。云端 VL 不收磁盘路径。"""

from __future__ import annotations

import pymupdf as fitz

from bridge_agent.settings import MAX_EDGE


def page_count(pdf_path: str) -> int:
    doc = fitz.open(pdf_path)
    try:
        return len(doc)
    finally:
        doc.close()


def render_pages(pdf_path: str, dpi: int, page_indices: list[int] | None = None) -> list[tuple[int, bytes]]:
    """返回 (0-based 页码, JPEG 字节)。"""
    doc = fitz.open(pdf_path)
    try:
        indices = page_indices if page_indices is not None else list(range(len(doc)))
        out: list[tuple[int, bytes]] = []
        scale = dpi / 72.0
        for i in indices:
            if i < 0 or i >= len(doc):
                continue
            pix = doc[i].get_pixmap(matrix=fitz.Matrix(scale, scale), alpha=False)
            if max(pix.width, pix.height) > MAX_EDGE:
                ratio = MAX_EDGE / float(max(pix.width, pix.height))
                pix = doc[i].get_pixmap(
                    matrix=fitz.Matrix(scale * ratio, scale * ratio),
                    alpha=False,
                )
            out.append((i, pix.tobytes("jpeg")))
        return out
    finally:
        doc.close()
