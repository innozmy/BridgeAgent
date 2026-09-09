"""建模 consult：代码按账本组题，最多 3 次检索，产出 codeOverlay。不是 ReAct。"""

from __future__ import annotations

import json

from bridge_agent.graphs.modeling.check import _num, _params
from bridge_agent.graphs.modeling.overlay import empty_overlay, merge_overlay, sanitize_overlay
from bridge_agent.knowledge.correct import LOW_CONFIDENCE_NOTICE
from bridge_agent.knowledge.resupply import attach_resupply, scope_ids, scope_names, tool_result_for_model
from bridge_agent.llm.qwen_vl import chat_messages, parse_json_object
from bridge_agent.settings import MODELING_CONSULT_MAX_SEARCHES
from bridge_agent.tools.knowledge import SearchKnowledgeBudget

EXTRACT_SYSTEM = """你从规范检索结果中抽取建模覆盖层 JSON，不要生成 SAP 节点或杆件。
只输出 JSON：{"overrides":{"soilM":数字或null},"flags":{"abutmentGjzf":true/false/null,"skipBearingWhereRigid":true/false/null,"requireCapBeam":true/false/null},"notices":[{"part":"soil|bearing|pier","citation":"规范号+条号","text":"给工程师看的一句","severity":"info"}]}
本题没有的字段用 null。禁止编造条号。禁止把本桥账本尺寸或工程过程记录写进规范结论。"""


def list_topics(payload: dict) -> list[dict]:
    """按这座桥有哪些部分组题，最多 MODELING_CONSULT_MAX_SEARCHES。"""
    ledger = payload.get("ledger") if isinstance(payload.get("ledger"), dict) else {}
    params = _params(ledger)
    topics: list[dict] = []
    ftype = params.get("foundationType") or ""
    if ("桩" in ftype or "桩柱" in ftype) and _num(params.get("soilM")) is None:
        topics.append({
            "id": "soil_m",
            "query": "公路桥梁桩基础土弹簧比例系数m值，黏性土或一般土的取值区间，JTG 3363 附录",
        })
    if not _looks_rigid(params):
        topics.append({
            "id": "bearing_slide",
            "query": "公路桥梁板式橡胶支座 GJZ 与 GJZF 选用，桥台是否采用四氟滑板，固定向与滑动向",
        })
    if _has_pier(ledger):
        topics.append({
            "id": "pier_connection",
            "query": "公路桥梁多柱墩盖梁与墩柱刚接构造，墩梁固结时是否取消支座",
        })
    return topics[: max(1, MODELING_CONSULT_MAX_SEARCHES)]


def run_consult(payload: dict) -> dict:
    """返回 sanitize 后的 codeOverlay。无题或检索失败则空覆盖层 + 可选 notices。"""
    overlay = empty_overlay()
    topics = list_topics(payload)
    if not topics:
        return overlay
    scope = payload.get("knowledgeScope") if isinstance(payload.get("knowledgeScope"), dict) else {}
    ids = scope_ids(scope)
    if not ids:
        overlay["notices"].append({
            "part": "scope",
            "citation": "",
            "text": "本项目未启用已嵌入的规范，软参数走代码缺省。",
            "severity": "info",
        })
        return overlay
    budget = SearchKnowledgeBudget(ids, scope_names(scope), max_calls=len(topics))
    for topic in topics:
        packed = budget.search(topic["query"])
        packed = attach_resupply(packed, scope)
        shown = tool_result_for_model(packed)
        prompt = (
            "本题：" + topic["id"] + "\n问句：" + topic["query"] + "\n检索结果：\n"
            + json.dumps(shown, ensure_ascii=False)
        )
        images = list(packed.get("_jpegBytes") or [])
        try:
            result = chat_messages(
                EXTRACT_SYSTEM,
                [{"role": "user", "content": prompt}],
                force_json=True,
                images=images or None,
            )
            extracted = parse_json_object(result.get("content") or "{}")
        except Exception:
            overlay = apply_topic_result(overlay, topic["id"], None, packed.get("notice"))
            continue
        overlay = apply_topic_result(overlay, topic["id"], extracted, packed.get("notice"))
    return sanitize_overlay(overlay)


def apply_topic_result(overlay: dict, topic_id: str, extracted, notice) -> dict:
    """把握不足只记 warn，不合并 overrides/flags。"""
    text = str(notice or "").strip()
    if text:
        overlay["notices"].append({
            "part": str(topic_id or "")[:32],
            "citation": "",
            "text": text[:400],
            "severity": "warn",
        })
        if LOW_CONFIDENCE_NOTICE in text:
            return overlay
    if isinstance(extracted, dict):
        overlay = merge_overlay(overlay, extracted)
    return overlay
    return sanitize_overlay(overlay)


def _looks_rigid(params: dict[str, str]) -> bool:
    text = (params.get("pierType") or "") + (params.get("layoutType") or "")
    return "刚构" in text or "固结" in text


def _has_pier(ledger: dict) -> bool:
    for unit in ledger.get("units") or []:
        if not isinstance(unit, dict):
            continue
        for support in unit.get("supports") or []:
            if not isinstance(support, dict):
                continue
            kind = str(support.get("kind") or "").lower()
            if kind in ("pier", "墩") or (kind and "台" not in kind and "abut" not in kind):
                return True
    return False
