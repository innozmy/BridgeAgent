"""把 split / 图目录 / 表目录拼成待嵌入行。不调云、不写 Milvus。"""

from __future__ import annotations

# Milvus VARCHAR max_length 按字节。写入仍按最多 2048 字截断，字段开 8192 才能装下中文。
BODY_CHAR_MAX = 2048
BODY_BYTE_MAX = 8192
MEANING_MAX = 400


def build_embed_rows(
    split_chunks: list[dict],
    figures: list[dict],
    tables: list[dict],
    meta: dict,
) -> list[dict]:
    """
    meta 含 documentId / sha256 / category / familyCode / region / specialty。
    返回未带向量的行，字段名与 Milvus 对齐。
    """
    document_id = int(meta.get("documentId") or 0)
    sha256 = _s(meta.get("sha256"), 64)
    category = _s(meta.get("category"), 16)
    family = _s(meta.get("familyCode"), 64)
    region = _s(meta.get("region"), 64)
    specialty = _s(meta.get("specialty"), 64)
    rows: list[dict] = []
    for chunk in split_chunks:
        ref = _s(chunk.get("id"), 64)
        body = _clip(str(chunk.get("text") or ""))
        if not ref or not body:
            continue
        rows.append(
            _base(document_id, sha256, category, family, region, specialty)
            | {
                "id": _pk(document_id, "text", ref),
                "kind": "text",
                "ref_id": ref,
                "parent_chunk_id": _s(chunk.get("parentChunkId"), 32),
                "clause_no": _s(chunk.get("clauseNo"), 32),
                "figure_no": "",
                "table_no": "",
                "body": body,
                "page_numbers": _pages(chunk.get("pageNumbers")),
                "mentioned_figure_ids": _ids(chunk.get("mentionedFigureIds")),
                "mentioned_table_ids": _ids(chunk.get("mentionedTableIds")),
            }
        )
    for fig in figures:
        ref = _s(fig.get("id"), 64)
        no = _s(fig.get("figureNo"), 32)
        vl = fig.get("vl") if isinstance(fig.get("vl"), dict) else {}
        # 只要短 meaning；inFigureText / 路径 / JPEG 不进向量正文
        meaning = _clip(str(vl.get("meaning") or ""), MEANING_MAX)
        caption = str(fig.get("caption") or "")
        body = _clip(" ".join(p for p in (f"图{no}" if no else "图", caption, meaning) if p).strip())
        if not ref or not body:
            continue
        page = fig.get("pageNumber")
        rows.append(
            _base(document_id, sha256, category, family, region, specialty)
            | {
                "id": _pk(document_id, "figure", ref),
                "kind": "figure",
                "ref_id": ref,
                "parent_chunk_id": "",
                "clause_no": _s(fig.get("nearbyClause"), 32),
                "figure_no": no,
                "table_no": "",
                "body": body,
                "page_numbers": _pages([page] if page not in (None, "") else []),
                "mentioned_figure_ids": [],
                "mentioned_table_ids": [],
            }
        )
    for tab in tables:
        ref = _s(tab.get("id"), 64)
        no = _s(tab.get("tableNo"), 32)
        caption = str(tab.get("caption") or "")
        nearby = _s(tab.get("nearbyClause"), 32)
        body = _clip(" ".join(p for p in (f"表{no}" if no else "表", caption, nearby) if p).strip())
        if not ref or not body:
            continue
        page = tab.get("pageNumber")
        rows.append(
            _base(document_id, sha256, category, family, region, specialty)
            | {
                "id": _pk(document_id, "table", ref),
                "kind": "table",
                "ref_id": ref,
                "parent_chunk_id": "",
                "clause_no": nearby,
                "figure_no": "",
                "table_no": no,
                "body": body,
                "page_numbers": _pages([page] if page not in (None, "") else []),
                "mentioned_figure_ids": [],
                "mentioned_table_ids": [],
            }
        )
    return rows


def _base(document_id: int, sha256: str, category: str, family: str, region: str, specialty: str) -> dict:
    return {
        "document_id": document_id,
        "sha256": sha256,
        "category": category,
        "family_code": family,
        "region": region,
        "specialty": specialty,
    }


def _pk(document_id: int, kind: str, ref: str) -> str:
    return f"{document_id}:{kind}:{ref}"[:128]


def _s(value, limit: int) -> str:
    return str(value or "")[:limit]


def _clip(text: str, limit: int = BODY_CHAR_MAX, byte_limit: int = BODY_BYTE_MAX) -> str:
    """先按字截，再按 UTF-8 字节截，避免 Milvus VARCHAR 拒收中文。"""
    text = (text or "").strip()
    if len(text) > limit:
        text = text[:limit]
    encoded = text.encode("utf-8")
    if len(encoded) <= byte_limit:
        return text
    return encoded[:byte_limit].decode("utf-8", errors="ignore")


def _pages(raw) -> list[int]:
    out: list[int] = []
    if not isinstance(raw, list):
        return out
    for item in raw:
        try:
            num = int(item)
        except (TypeError, ValueError):
            continue
        if num > 0 and num not in out:
            out.append(num)
        if len(out) >= 64:
            break
    return out


def _ids(raw) -> list[str]:
    out: list[str] = []
    if not isinstance(raw, list):
        return out
    for item in raw:
        text = str(item or "").strip()
        if text and text not in out:
            out.append(text[:64])
        if len(out) >= 32:
            break
    return out
