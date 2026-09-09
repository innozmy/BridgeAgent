"""检索自我纠正：评估命中、改写问句、挑选较好一组。不写工程记忆、不生成答案。"""

from __future__ import annotations

import re

from bridge_agent.knowledge.search import EMPTY_NOTICE
from bridge_agent.llm.qwen_vl import chat_text_json

LOW_CONFIDENCE_NOTICE = "检索把握不足"

_EVAL_SYSTEM = (
    "你只评估规范检索命中是否够用，不要写答案，不要编造条号。"
    "命中是短摘录：条文、图题、表题，不是整图整表；缺 JPEG/表 HTML 不能因此判失败。"
    "只判断：这组短摘录合在一起，是否回答得了用户问句里的规范问题。"
    "只返回 JSON：{\"pass\": true} 或 {\"pass\": false}。"
)

_REWRITE_SYSTEM = (
    "你只改写规范检索问句，不要写答案，不要编造条号或图号表号。"
    "不要把本桥几何、跨径、墩高、材料用量等工程账本数字写进问句。"
    "不要指定文献 ID。问句仍应是完整技术问题（构件、工况、规范习语）。"
    "只返回 JSON：{\"query\": \"改写后的问句\"}。"
)

_PUNCT = re.compile(r"[\s　，。、；：！？,.!?;:\"'“”‘’（）()\[\]【】《》…—\-]+")


def hard_pass(result: dict) -> bool:
    """空命中或未启用提示视为失败。"""
    hits = result.get("hits") or []
    if not hits:
        return False
    notice = str(result.get("notice") or "")
    if EMPTY_NOTICE in notice:
        return False
    return True


def queries_equivalent(left: str, right: str) -> bool:
    """改写与原句实质相同则不必再搜。"""
    return _fold(left) == _fold(right)


def pick_better(first: dict, second: dict) -> dict:
    """非空优先；条数多优先；并列留第一次。"""
    a = first.get("hits") or []
    b = second.get("hits") or []
    if a and not b:
        return first
    if b and not a:
        return second
    if len(b) > len(a):
        return second
    return first


def attach_low_confidence(result: dict) -> dict:
    out = dict(result)
    out["ok"] = True
    out["notice"] = LOW_CONFIDENCE_NOTICE
    return out


def evaluate_hits(query: str, hits: list[dict]) -> bool | None:
    """
    集合级通过/不通过。True/False 为判定；None 表示模型不可用，调用方应降级。
    对照的是用户原问句，不是改写句。
    """
    if not hits:
        return False
    try:
        data = chat_text_json(
            _EVAL_SYSTEM,
            [{"role": "user", "content": _eval_user(query, hits)}],
        )
    except Exception:
        return None
    return _as_bool(data.get("pass"))


def rewrite_query(query: str, hits: list[dict]) -> str | None:
    """只返回新问句。失败或空则 None。"""
    try:
        data = chat_text_json(
            _REWRITE_SYSTEM,
            [{"role": "user", "content": _rewrite_user(query, hits)}],
        )
    except Exception:
        return None
    text = str(data.get("query") or "").strip()
    return text or None


def _eval_user(query: str, hits: list[dict]) -> str:
    lines = ["用户问句：", query.strip(), "", "命中短摘录："]
    for i, hit in enumerate(hits, 1):
        lines.append("%s. %s" % (i, _hit_line(hit)))
    return "\n".join(lines)


def _rewrite_user(query: str, hits: list[dict]) -> str:
    lines = ["原问句：", query.strip(), "", "上次命中（可能不够）："]
    if not hits:
        lines.append("（无命中）")
    else:
        for i, hit in enumerate(hits, 1):
            lines.append("%s. %s" % (i, _hit_line(hit)))
    lines.append("")
    lines.append("请改写问句，便于再检索同一批已启用规范。")
    return "\n".join(lines)


def _hit_line(hit: dict) -> str:
    kind = str(hit.get("kind") or "text")
    loc = str(hit.get("clauseNo") or hit.get("figureNo") or hit.get("tableNo") or "")
    family = str(hit.get("familyCode") or hit.get("documentName") or "")
    body = str(hit.get("body") or "").replace("\n", " ")
    if len(body) > 280:
        body = body[:280] + "…"
    return "[%s] %s %s | %s" % (kind, family, loc, body)


def _fold(text: str) -> str:
    return _PUNCT.sub("", str(text or "")).casefold()


def _as_bool(raw) -> bool | None:
    if isinstance(raw, bool):
        return raw
    if raw is None:
        return None
    text = str(raw).strip().lower()
    if text in ("1", "true", "yes", "pass", "通过"):
        return True
    if text in ("0", "false", "no", "fail", "不通过"):
        return False
    return None
