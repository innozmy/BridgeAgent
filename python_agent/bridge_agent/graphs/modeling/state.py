from __future__ import annotations

from typing import Any, TypedDict


class ModelingState(TypedDict, total=False):
    payload: dict
    error: str
    response: dict[str, Any]
    missing: list
    code_overlay: dict
    spec: dict
