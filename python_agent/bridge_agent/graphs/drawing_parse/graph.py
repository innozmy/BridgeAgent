"""识图 StateGraph。编译一次，HTTP 每次 invoke。"""

from __future__ import annotations

from langgraph.graph import END, StateGraph

from bridge_agent.graphs.drawing_parse import nodes
from bridge_agent.graphs.drawing_parse.state import DrawingParseState
from bridge_agent.memory.runtime import compile_graph
from bridge_agent.memory.thread_id import invoke_config
from bridge_agent.runtime import fail

_COMPILED = None


def build_graph():
    g = StateGraph(DrawingParseState)
    g.add_node("gate", nodes.gate)
    g.add_node("collect", nodes.collect)
    g.add_node("open_pdf", nodes.open_pdf)
    g.add_node("render_short", nodes.render_short)
    g.add_node("classify", nodes.classify)
    g.add_node("pick_render", nodes.pick_render)
    g.add_node("extract", nodes.extract)
    g.add_node("persist", nodes.persist)
    g.add_node("finish", nodes.finish)
    g.add_node("load_map", nodes.load_map)
    g.set_entry_point("gate")
    g.add_conditional_edges("gate", nodes.ok_or_end, {"ok": "collect", "end": "finish"})
    g.add_conditional_edges("collect", nodes.ok_or_end, {"ok": "open_pdf", "end": "finish"})
    g.add_conditional_edges(
        "open_pdf",
        nodes.after_open,
        {"short": "render_short", "long": "classify", "mapped": "load_map", "end": "finish"},
    )
    g.add_edge("render_short", "extract")
    g.add_conditional_edges("classify", nodes.ok_or_end, {"ok": "pick_render", "end": "finish"})
    g.add_conditional_edges("load_map", nodes.ok_or_end, {"ok": "pick_render", "end": "finish"})
    g.add_edge("pick_render", "extract")
    g.add_conditional_edges("extract", nodes.ok_or_end, {"ok": "persist", "end": "finish"})
    g.add_edge("persist", "finish")
    g.add_edge("finish", END)
    return compile_graph(g)


def get_graph():
    global _COMPILED
    if _COMPILED is None:
        _COMPILED = build_graph()
    return _COMPILED


def run(payload: dict) -> dict:
    """稳定 thread_id（项目×识图 Agent）恢复 STM；本轮临时字段必须覆盖掉，避免旧页图留在磁带里。"""
    final = get_graph().invoke(
        {
            "payload": payload,
            "rendered": [],
            "error": "",
            "response": {},
            "extract_result": {},
            "labels": "",
        },
        config=invoke_config("drawing_parse", payload),
    )
    return final.get("response") or fail("图执行未产生响应")
