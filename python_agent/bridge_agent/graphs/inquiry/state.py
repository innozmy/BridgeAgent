from __future__ import annotations

from typing import Any, TypedDict


class InquiryState(TypedDict, total=False):
    payload: dict
    error: str
    response: dict[str, Any]
    # react → compose：工具后的对话与补发 JPEG（不进 Checkpoint 长期记忆）
    react_turns: list
    jpeg_bytes: list
    search_notice: str
