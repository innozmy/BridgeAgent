"""识图图状态。节点只返回要改的字段，LangGraph 做浅合并。

进 Checkpoint 的是整份 State。只把短期记忆需要的瘦字段留下来：
page_map / gaps / has_layout。rendered 是页图字节，用完必须清空，否则逐步存档会胀库。
"""

from __future__ import annotations

from typing import Any, TypedDict


class DrawingParseState(TypedDict, total=False):
    payload: dict
    pdfs: list
    rejected: list
    primary: dict
    pdf_path: str
    total: int
    # STM：页分类地图，补充抽取要能从同 thread 读回来
    page_map: dict
    picked: list
    # 临时：细看 JPEG；extract 之后必须写成 []，禁止作为记忆长期留在 checkpoint
    rendered: list
    labels: str
    extract_result: dict
    gaps: list
    has_layout: bool
    error: str
    response: dict[str, Any]
