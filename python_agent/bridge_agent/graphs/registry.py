"""按 kind 找到编译好的图。Spring 仍按任务类型发 HTTP，不在 Python 里私聊跳转。"""

from __future__ import annotations

from bridge_agent.graphs.drawing_parse import graph as drawing_parse
from bridge_agent.graphs.inquiry import graph as inquiry
from bridge_agent.graphs.modeling import graph as modeling
from bridge_agent.runtime import fail

# 已登记的 Agent。新增种类：实现 graphs/<kind>/ 后在此挂上。
AGENT_KINDS = ("drawing_parse", "inquiry", "modeling")

_RUNNERS = {
    "drawing_parse": drawing_parse.run,
    "inquiry": inquiry.run,
    "modeling": modeling.run,
}


def run_agent(kind: str, payload: dict) -> dict:
    runner = _RUNNERS.get(kind)
    if runner is None:
        return fail("未知 Agent 种类：%s。已登记：%s" % (kind, "、".join(AGENT_KINDS)))
    return runner(payload)
