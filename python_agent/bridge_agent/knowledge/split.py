"""第三步：对超长 merge chunk 做 embedding + gradient 语义切。不改 merge。"""

from __future__ import annotations

import re
from collections.abc import Callable

from bridge_agent.knowledge.catalog import normalize_ref
from bridge_agent.knowledge.merge import REF_FIG, REF_TAB
from bridge_agent.settings import SPLIT_GRADIENT_PERCENTILE, SPLIT_MAX_CHARS, SPLIT_OVERLAP_CHARS

SENTENCE_RE = re.compile(r".+?(?:[。！？；\n]|$)")
EmbedFn = Callable[[list[str]], list[list[float]]]


def split_merged_chunks(
    merge_chunks: list[dict],
    figures: list[dict],
    tables: list[dict],
    embed: EmbedFn,
) -> tuple[list[dict], list[str]]:
    """
    返回 split 后的 chunks 与警告。
    不超过 SPLIT_MAX_CHARS 的原样抄出；超过的才 embedding。
    """
    fig_by_no = _index_by_no(figures, "figureNo")
    tab_by_no = _index_by_no(tables, "tableNo")
    warnings: list[str] = []
    out: list[dict] = []
    for src in merge_chunks:
        text = str(src.get("text") or "")
        parent_id = str(src.get("id") or "")
        if len(text) <= SPLIT_MAX_CHARS:
            out.append(_child(src, text, 0, False, fig_by_no, tab_by_no))
            continue
        pieces, local_warn = split_long_text(text, embed)
        warnings.extend(parent_id + ": " + w for w in local_warn)
        for index, piece in enumerate(pieces):
            out.append(_child(src, piece, index, True, fig_by_no, tab_by_no))
    for index, chunk in enumerate(out, start=1):
        chunk["id"] = f"s-{index:04d}"
        chunk["parentChunkId"] = chunk.get("parentChunkId") or ""
    return out, warnings


def split_long_text(text: str, embed: EmbedFn) -> tuple[list[str], list[str]]:
    """只处理已确认超长的一段。刀口在句界；gradient 找转折；硬上限 1200 字。"""
    warnings: list[str] = []
    sentences = _sentences(text)
    if not sentences:
        return [text], warnings
    if len(sentences) == 1:
        warnings.append("单句超过 %s 字，未从句中切开" % SPLIT_MAX_CHARS)
        return [sentences[0]], warnings

    vectors = embed(sentences)
    distances = [_distance(vectors[i], vectors[i + 1]) for i in range(len(vectors) - 1)]
    cuts = _gradient_cuts(distances)
    groups = _groups_by_cuts(sentences, cuts)
    packed: list[str] = []
    for group in groups:
        packed.extend(_pack_max_chars(group, warnings))
    return _with_overlap(packed), warnings


def _gradient_cuts(distances: list[float]) -> set[int]:
    """
    对距离曲线求离散 gradient，超过本段 95 分位的位置作为句间切点。
    切点 i 表示在第 i 句与第 i+1 句之间切开（i 从 0 计）。
    """
    if len(distances) < 2:
        return set()
    grads = _gradient(distances)
    # 句间切点对应 distances[i] 的 gradient（与 numpy.gradient 等长）
    threshold = _percentile(grads, SPLIT_GRADIENT_PERCENTILE)
    cuts = set()
    for i, g in enumerate(grads):
        if g > threshold:
            cuts.add(i)
    return cuts


def _groups_by_cuts(sentences: list[str], cuts: set[int]) -> list[list[str]]:
    groups: list[list[str]] = []
    current: list[str] = []
    for i, sent in enumerate(sentences):
        current.append(sent)
        if i in cuts:
            groups.append(current)
            current = []
    if current:
        groups.append(current)
    return groups or [sentences]


def _pack_max_chars(sentences: list[str], warnings: list[str]) -> list[str]:
    """gradient 组内若仍超 1200，按句贪心再切，不从句中切开。"""
    pieces: list[str] = []
    buf: list[str] = []
    size = 0
    for sent in sentences:
        extra = len(sent)
        if buf and size + extra > SPLIT_MAX_CHARS:
            pieces.append("".join(buf))
            buf = [sent]
            size = extra
            if extra > SPLIT_MAX_CHARS:
                warnings.append("单句超过 %s 字，未从句中切开" % SPLIT_MAX_CHARS)
                pieces.append(sent)
                buf = []
                size = 0
            continue
        if not buf and extra > SPLIT_MAX_CHARS:
            warnings.append("单句超过 %s 字，未从句中切开" % SPLIT_MAX_CHARS)
            pieces.append(sent)
            continue
        buf.append(sent)
        size += extra
    if buf:
        pieces.append("".join(buf))
    return pieces


def _with_overlap(pieces: list[str]) -> list[str]:
    if len(pieces) <= 1 or SPLIT_OVERLAP_CHARS <= 0:
        return pieces
    out = [pieces[0]]
    for i in range(1, len(pieces)):
        prev = out[-1]
        prefix = prev[-SPLIT_OVERLAP_CHARS:] if len(prev) > SPLIT_OVERLAP_CHARS else prev
        nxt = pieces[i]
        if nxt.startswith(prefix):
            out.append(nxt)
        else:
            out.append(prefix + nxt)
    return out


def _sentences(text: str) -> list[str]:
    found = [m.group(0) for m in SENTENCE_RE.finditer(text) if m.group(0).strip()]
    return found if found else [text]


def _child(
    src: dict,
    text: str,
    split_index: int,
    split_from_long: bool,
    fig_by_no: dict[str, str],
    tab_by_no: dict[str, str],
) -> dict:
    return {
        "parentChunkId": src.get("id") or "",
        "splitIndex": split_index,
        "splitFromLong": split_from_long,
        "clauseNo": src.get("clauseNo") or "",
        "parentId": src.get("parentId") or "",
        "elementIds": list(src.get("elementIds") or []),
        "pageNumbers": list(src.get("pageNumbers") or []),
        "text": text,
        "mentionedFigureIds": _collect_refs(text, REF_FIG, fig_by_no),
        "mentionedTableIds": _collect_refs(text, REF_TAB, tab_by_no),
    }


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


def _index_by_no(rows: list[dict], number_key: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for row in rows:
        number = normalize_ref(str(row.get(number_key) or ""))
        ident = str(row.get("id") or "")
        if number and ident and number not in out:
            out[number] = ident
    return out


def _distance(a: list[float], b: list[float]) -> float:
    return 1.0 - _cosine(a, b)


def _cosine(a: list[float], b: list[float]) -> float:
    dot = 0.0
    na = 0.0
    nb = 0.0
    for x, y in zip(a, b):
        dot += x * y
        na += x * x
        nb += y * y
    if na <= 0 or nb <= 0:
        return 0.0
    return dot / ((na ** 0.5) * (nb ** 0.5))


def _gradient(ys: list[float]) -> list[float]:
    n = len(ys)
    if n == 0:
        return []
    if n == 1:
        return [0.0]
    out = [0.0] * n
    out[0] = ys[1] - ys[0]
    out[-1] = ys[-1] - ys[-2]
    for i in range(1, n - 1):
        out[i] = (ys[i + 1] - ys[i - 1]) / 2.0
    return out


def _percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    rank = (len(ordered) - 1) * (p / 100.0)
    low = int(rank)
    high = min(low + 1, len(ordered) - 1)
    if low == high:
        return ordered[low]
    frac = rank - low
    return ordered[low] + (ordered[high] - ordered[low]) * frac
