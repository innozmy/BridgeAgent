"""规范检索工具。模型只填问句；启用集由调用方从 Spring 注入，禁止模型自带文献 ID。"""

from __future__ import annotations

from bridge_agent.knowledge.search_job import run_search
from bridge_agent.settings import SEARCH_MAX_CALLS

SEARCH_KNOWLEDGE_DESCRIPTION = (
    "在本项目已启用且已嵌入的规范中检索相关条文、图号、表号。"
    "需要核对规范限值、构造、材料指标或附图附表时用。"
    "不要用来查本桥几何/材料等工程账本（那是项目参数）。不要编造条号。"
    "检索范围由系统注入，你不能指定文献 ID 搜整个公司库。"
    "问句写成完整技术问题（构件、工况、规范习语），不要只丢两三个关键词。"
    "返回短摘录和出处（规范号、条号/图号/表号、页码）；"
    "图/表完整内容可能另附，短摘录不等于整图整表。"
    "同一轮不要用几乎相同的问句反复空转。"
)

SEARCH_KNOWLEDGE_TOOL = {
    "type": "function",
    "function": {
        "name": "search_knowledge",
        "description": SEARCH_KNOWLEDGE_DESCRIPTION,
        "parameters": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "完整技术问句，不要只丢关键词，不要带文献 ID。",
                }
            },
            "required": ["query"],
        },
    },
}


def search_knowledge(
    query: str,
    *,
    document_ids: list[int],
    document_names: dict[int, str] | dict[str, str] | None = None,
) -> dict:
    """
    入参只有问句对模型可见。document_ids / document_names 由 Agent 节点从注入包传入。
    当次不带 JPEG / 表 HTML，只回短文、出处和待补发 id。
    """
    names: dict = {}
    if isinstance(document_names, dict):
        names = {str(key): value for key, value in document_names.items()}
    return run_search(
        {
            "query": query,
            "documentIds": list(document_ids or []),
            "documentNames": names,
        }
    )


class SearchKnowledgeBudget:
    """同一 HTTP 内最多调工具 SEARCH_MAX_CALLS 次。一次调用内部最多再混搜 2 次（含改写）。"""

    def __init__(self, document_ids: list[int], document_names: dict | None = None, max_calls: int | None = None):
        self.document_ids = list(document_ids or [])
        self.document_names = document_names or {}
        self.max_calls = int(max_calls if max_calls is not None else SEARCH_MAX_CALLS)
        self.used = 0

    def search(self, query: str) -> dict:
        if self.used >= self.max_calls:
            return {"ok": False, "error": "本轮检索次数已用尽", "hits": []}
        self.used += 1
        return search_knowledge(
            query,
            document_ids=self.document_ids,
            document_names=self.document_names,
        )
