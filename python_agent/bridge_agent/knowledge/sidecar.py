"""知识解析 sidecar：尽量原样保留 Unstructured 元素，只去掉像素类大字段。"""

from __future__ import annotations

import json
from pathlib import Path

SCHEMA_VERSION = 2
# 像素与向量本身不进 JSON：图改落 JPEG；embedding 留给以后向量库
_DROP_TOP = frozenset({"embeddings", "image_base64"})
_DROP_META = frozenset({"image_base64"})


def persist_unstructured_element(raw: dict, index: int) -> dict:
    """
    落盘一条 Unstructured 元素：保留 type / element_id / text / 全部 metadata
    （含 parent_id、坐标、表 HTML 与单元格），去掉 Base64 与 embeddings。
    另写 id / pageNumber / parentId / textAsHtml 方便后续切块合并。
    """
    element_id = str(raw.get("element_id") or raw.get("id") or f"el-{index}")
    meta_in = raw.get("metadata") if isinstance(raw.get("metadata"), dict) else {}
    metadata = {key: value for key, value in meta_in.items() if key not in _DROP_META}
    item: dict = {
        "type": raw.get("type") or raw.get("category") or "UncategorizedText",
        "element_id": element_id,
        "text": raw.get("text") or "",
        "metadata": metadata,
    }
    for key, value in raw.items():
        if key in ("type", "category", "element_id", "id", "text", "metadata") or key in _DROP_TOP:
            continue
        item[key] = value
    page = metadata.get("page_number") or raw.get("page_number") or raw.get("pageNumber") or 0
    try:
        page = int(page)
    except (TypeError, ValueError):
        page = 0
    item["id"] = element_id
    item["pageNumber"] = page
    if metadata.get("parent_id"):
        item["parentId"] = metadata["parent_id"]
    if metadata.get("category_depth") is not None:
        item["categoryDepth"] = metadata["category_depth"]
    if metadata.get("text_as_html"):
        item["textAsHtml"] = metadata["text_as_html"]
    if metadata.get("table_as_cells") is not None:
        item["tableAsCells"] = metadata["table_as_cells"]
    return item


def build_hierarchy(elements: list[dict]) -> list[dict]:
    """元素父子索引，切块/合并不必扫完全文 metadata。"""
    rows = []
    for item in elements:
        meta = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
        rows.append(
            {
                "element_id": item.get("element_id") or item.get("id"),
                "parent_id": meta.get("parent_id") or item.get("parentId"),
                "type": item.get("type"),
                "page_number": item.get("pageNumber") or meta.get("page_number"),
            }
        )
    return rows


def load_sidecar(path: Path | None) -> dict | None:
    if path is None or not path.is_file():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return data if isinstance(data, dict) else None


def write_sidecar(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


def empty_sidecar(meta: dict) -> dict:
    body = {
        "schemaVersion": SCHEMA_VERSION,
        "persist": {
            "keepUnstructuredMetadata": True,
            "dropFromJson": ["image_base64", "embeddings"],
            "figuresOnDisk": True,
        },
        "elements": [],
        "hierarchy": [],
        "figures": [],
        "warnings": [],
    }
    body.update(meta)
    return body


def can_resume(data: dict | None) -> bool:
    if not data or not data.get("elements"):
        return False
    # 旧瘦 sidecar 没有 parent_id / metadata，只补 VL 不够，必须重跑 Unstructured
    try:
        version = int(data.get("schemaVersion") or 1)
    except (TypeError, ValueError):
        version = 1
    return version >= SCHEMA_VERSION
