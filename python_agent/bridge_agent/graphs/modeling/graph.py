from __future__ import annotations

from langgraph.graph import END, StateGraph

from bridge_agent.graphs.modeling import nodes
from bridge_agent.graphs.modeling.state import ModelingState
from bridge_agent.memory.runtime import compile_graph
from bridge_agent.memory.thread_id import invoke_config
from bridge_agent.runtime import fail

_COMPILED = None


def build_graph():
    g = StateGraph(ModelingState)
    g.add_node("gate", nodes.gate)
    g.add_node("check", nodes.check_node)
    g.add_node("consult", nodes.consult_node)
    g.add_node("spec", nodes.spec_node)
    g.add_node("build", nodes.build_node)
    g.add_node("finish", nodes.finish)
    g.set_entry_point("gate")
    g.add_conditional_edges("gate", nodes.ok_or_end, {"ok": "check", "end": "finish"})
    g.add_conditional_edges("check", nodes.after_check, {"ok": "consult", "end": "finish"})
    g.add_conditional_edges("consult", nodes.ok_or_end, {"ok": "spec", "end": "finish"})
    g.add_conditional_edges("spec", nodes.ok_or_end, {"ok": "build", "end": "finish"})
    g.add_conditional_edges("build", nodes.ok_or_end, {"ok": "finish", "end": "finish"})
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
            "missing": [],
            "code_overlay": {},
            "spec": {},
        },
        config=invoke_config("modeling", payload),
    )
    return final.get("response") or fail("图执行未产生响应")
