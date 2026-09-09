"""问询图节点：gate → 局部 ReAct → compose 终态 JSON。不写库。"""

from __future__ import annotations

import json

from openai import APIStatusError, AuthenticationError, RateLimitError

from bridge_agent.graphs.inquiry.prompts import COMPOSE_SYSTEM, REACT_SYSTEM, build_turns
from bridge_agent.graphs.inquiry.state import InquiryState
from bridge_agent.knowledge.resupply import attach_resupply, scope_ids, scope_names, tool_result_for_model
from bridge_agent.llm.qwen_vl import chat_messages, parse_json_object
from bridge_agent.runtime import classify_api_error
from bridge_agent.settings import API_KEY, INQUIRY_LLM_MAX, INQUIRY_TOOL_MAX
from bridge_agent.tools.knowledge import SEARCH_KNOWLEDGE_TOOL, SearchKnowledgeBudget


def ok_or_end(state: InquiryState) -> str:
    if state.get("error") or (state.get("response") and not state.get("response", {}).get("ok")):
        return "end"
    return "ok"


def gate(state: InquiryState) -> dict:
    if not API_KEY:
        return {"error": "no_key", "response": _fail("【百炼密钥】未配置 DASHSCOPE_API_KEY。")}
    payload = state.get("payload") or {}
    if not str(payload.get("message") or "").strip():
        return {"error": "empty", "response": _fail("问题为空。")}
    return {}


def react(state: InquiryState) -> dict:
    """局部 ReAct：最多 INQUIRY_TOOL_MAX 次 search_knowledge；为 compose 预留一轮 LLM。"""
    payload = state.get("payload") or {}
    turns = build_turns(payload)
    if not turns:
        return {"error": "empty", "response": _fail("问题为空。")}
    scope = payload.get("knowledgeScope") if isinstance(payload.get("knowledgeScope"), dict) else {}
    budget = SearchKnowledgeBudget(scope_ids(scope), scope_names(scope), max_calls=INQUIRY_TOOL_MAX)
    jpeg_bytes: list[bytes] = []
    notice = ""
    used_llm = 0
    react_budget = max(1, INQUIRY_LLM_MAX - 1)
    try:
        while used_llm < react_budget:
            used_llm += 1
            allow_tools = budget.used < budget.max_calls
            result = chat_messages(
                REACT_SYSTEM,
                turns,
                tools=[SEARCH_KNOWLEDGE_TOOL] if allow_tools else None,
                force_json=False,
            )
            calls = [c for c in result.get("tool_calls") or [] if c.get("name") == "search_knowledge"]
            if not calls:
                break
            call = calls[0]
            query = str((call.get("arguments") or {}).get("query") or "").strip()
            packed = budget.search(query)
            packed = attach_resupply(packed, scope)
            jpeg_bytes.extend(packed.get("_jpegBytes") or [])
            if packed.get("notice"):
                notice = str(packed.get("notice") or "")
            shown = tool_result_for_model(packed)
            turns.append({
                "role": "assistant",
                "content": result.get("content") or None,
                "tool_calls": [{
                    "id": call.get("id") or "search_knowledge",
                    "type": "function",
                    "function": {
                        "name": "search_knowledge",
                        "arguments": json.dumps({"query": query}, ensure_ascii=False),
                    },
                }],
            })
            turns.append({
                "role": "tool",
                "tool_call_id": call.get("id") or "search_knowledge",
                "content": json.dumps(shown, ensure_ascii=False),
            })
    except json.JSONDecodeError:
        return {"error": "json", "response": _fail("模型没有返回合法 JSON。")}
    except (AuthenticationError, RateLimitError, APIStatusError, Exception) as exc:
        return {"error": "llm", "response": _fail(classify_api_error(exc))}
    return {"react_turns": turns, "jpeg_bytes": jpeg_bytes, "search_notice": notice}


def compose(state: InquiryState) -> dict:
    """禁工具收口：必须 reply；可选 1 张补充识别卡。"""
    payload = state.get("payload") or {}
    turns = list(state.get("react_turns") or build_turns(payload))
    notice = str(state.get("search_notice") or "").strip()
    if notice:
        turns.append({
            "role": "user",
            "content": "系统提示：检索 notice 为「%s」。若属实必须写进 reply。" % notice,
        })
    images = list(state.get("jpeg_bytes") or [])
    try:
        result = chat_messages(
            COMPOSE_SYSTEM,
            turns,
            force_json=True,
            images=images or None,
        )
        data = parse_json_object(result.get("content"))
        if not isinstance(data, dict):
            return {"error": "json", "response": _fail("模型没有返回合法 JSON。"), "jpeg_bytes": []}
    except json.JSONDecodeError:
        return {"error": "json", "response": _fail("模型没有返回合法 JSON。"), "jpeg_bytes": []}
    except (AuthenticationError, RateLimitError, APIStatusError, Exception) as exc:
        return {"error": "llm", "response": _fail(classify_api_error(exc)), "jpeg_bytes": []}
    reply = str(data.get("reply") or "").strip()
    if not reply:
        return {"error": "empty_reply", "response": _fail("模型没有返回文字。"), "jpeg_bytes": []}
    out: dict = {"ok": True, "reply": reply}
    card = _normalize_card(data.get("taskCard"))
    if card:
        out["taskCard"] = card
    # 清掉 JPEG，避免进 checkpoint
    return {"response": out, "jpeg_bytes": []}


def finish(state: InquiryState) -> dict:
    if state.get("response"):
        return {}
    return {"response": _fail(state.get("error") or "图执行未产生响应")}


def _fail(error: str) -> dict:
    return {"ok": False, "error": error}


def _normalize_card(raw) -> dict | None:
    """只把带合法 fileId 的卡交给 Spring；其余当没出卡。"""
    if not isinstance(raw, dict):
        return None
    try:
        file_id = int(raw.get("fileId"))
    except (TypeError, ValueError):
        return None
    if file_id <= 0:
        return None
    kinds = raw.get("pageKinds")
    page_kinds = [str(k) for k in kinds if k] if isinstance(kinds, list) else []
    card = {"fileId": file_id, "pageKinds": page_kinds}
    directive = str(raw.get("directive") or "").strip()
    reason = str(raw.get("reason") or "").strip()
    if directive:
        card["directive"] = directive[:500]
    if reason:
        card["reason"] = reason[:512]
    return card
