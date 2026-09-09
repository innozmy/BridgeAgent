"""问询 StateGraph。每次 HTTP 覆盖 payload，不靠磁带里的旧回复。"""

from __future__ import annotations

from langgraph.graph import END, StateGraph

from bridge_agent.graphs.inquiry import nodes
from bridge_agent.graphs.inquiry.state import InquiryState
from bridge_agent.memory.runtime import compile_graph
from bridge_agent.memory.thread_id import invoke_config

_COMPILED = None


def build_graph():
    g = StateGraph(InquiryState)
    g.add_node("gate", nodes.gate)
    g.add_node("react", nodes.react)
    g.add_node("compose", nodes.compose)
    g.add_node("finish", nodes.finish)
    g.set_entry_point("gate")
    g.add_conditional_edges("gate", nodes.ok_or_end, {"ok": "react", "end": "finish"})
    g.add_conditional_edges("react", nodes.ok_or_end, {"ok": "compose", "end": "finish"})
    g.add_conditional_edges("compose", nodes.ok_or_end, {"ok": "finish", "end": "finish"})
    g.add_edge("finish", END)
    return compile_graph(g)


def get_graph():
    global _COMPILED
    if _COMPILED is None:
        _COMPILED = build_graph()
    return _COMPILED


def run(payload: dict) -> dict:
    final = get_graph().invoke(
        {
            "payload": payload,
            "error": "",
            "response": {},
            "react_turns": [],
            "jpeg_bytes": [],
            "search_notice": "",
        },
        config=invoke_config("inquiry", payload),
    )
    return final.get("response") or {"ok": False, "error": "图执行未产生响应"}
