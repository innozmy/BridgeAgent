"""组包与待补发：不调云、不连 Milvus。"""

from bridge_agent.knowledge.search import collect_pending, empty_result, pack_hits, present_hit
from bridge_agent.tools.knowledge import SEARCH_KNOWLEDGE_DESCRIPTION, SearchKnowledgeBudget


def _row(kind: str, ref: str, doc: int = 13, **extra) -> dict:
    base = {
        "kind": kind,
        "body": kind + "-" + ref,
        "documentId": doc,
        "documentName": "规范",
        "familyCode": "JTG D62",
        "clauseNo": "",
        "figureNo": "",
        "tableNo": "",
        "pageNumbers": [1],
        "refId": ref,
        "mentionedFigureIds": [],
        "mentionedTableIds": [],
    }
    base.update(extra)
    return base


def test_pack_caps_figures_tables_and_fills_text():
    rows = (
        [_row("figure", "f%s" % i) for i in range(3)]
        + [_row("table", "t%s" % i) for i in range(3)]
        + [_row("text", "s%s" % i, clauseNo="6.2.%s" % i) for i in range(8)]
    )
    packed = pack_hits(rows, limit=10, max_figures=2, max_tables=2)
    kinds = [item["kind"] for item in packed]
    assert kinds.count("figure") == 2
    assert kinds.count("table") == 2
    assert kinds.count("text") == 6
    assert len(packed) == 10
    assert packed[0]["refId"] == "f0"
    assert "f2" not in [item["refId"] for item in packed]


def test_pack_empty_figure_slots_go_to_text():
    rows = [_row("text", "s%s" % i) for i in range(12)]
    packed = pack_hits(rows, limit=10, max_figures=2, max_tables=2)
    assert len(packed) == 10
    assert all(item["kind"] == "text" for item in packed)


def test_pending_includes_mentioned_and_direct_hits():
    packed = [
        _row("text", "s-0001", mentionedFigureIds=["p12-i1"], mentionedTableIds=["t1"]),
        _row("figure", "p12-i1"),
        _row("table", "t2"),
    ]
    figures, tables = collect_pending(packed)
    assert figures == [{"documentId": 13, "refId": "p12-i1"}]
    assert tables == [
        {"documentId": 13, "refId": "t1"},
        {"documentId": 13, "refId": "t2"},
    ]


def test_pending_keeps_document_id():
    packed = [
        _row("figure", "p1-i1", doc=13),
        _row("figure", "p1-i1", doc=99),
    ]
    figures, tables = collect_pending(packed)
    assert figures == [
        {"documentId": 13, "refId": "p1-i1"},
        {"documentId": 99, "refId": "p1-i1"},
    ]
    assert tables == []


def test_present_hit_maps_milvus_fields_and_drops_score():
    hit = present_hit(
        {
            "kind": "text",
            "body": "抗震构造。",
            "document_id": 13,
            "family_code": "JTG/T 2231-02",
            "clause_no": "6.2.3",
            "figure_no": "",
            "table_no": "",
            "page_numbers": [12],
            "ref_id": "s-0001",
            "mentioned_figure_ids": ["p12-i1"],
            "mentioned_table_ids": [],
            "distance": 0.01,
        },
        {13: "公路桥梁抗震性能评价细则"},
    )
    assert hit["documentName"] == "公路桥梁抗震性能评价细则"
    assert hit["clauseNo"] == "6.2.3"
    assert "distance" not in hit
    assert hit["mentionedFigureIds"] == ["p12-i1"]


def test_empty_enabled_set_notice():
    result = empty_result()
    assert result["ok"] is True
    assert result["hits"] == []
    assert "未启用" in result["notice"]


def test_run_search_rejects_blank_query():
    from bridge_agent.knowledge.search_job import run_search

    result = run_search({"query": "  ", "documentIds": [1]})
    assert result["ok"] is False
    assert "问句" in result["error"]


def test_run_search_empty_ids_skips_milvus():
    from bridge_agent.knowledge.search_job import run_search

    result = run_search({"query": "板式支座刚度怎么设置？", "documentIds": []})
    assert result["ok"] is True
    assert result["hits"] == []
    assert "未启用" in (result.get("notice") or "")


def test_budget_caps_calls():
    budget = SearchKnowledgeBudget([13], {13: "规范"}, max_calls=2)
    budget.used = 2
    result = budget.search("板式支座刚度怎么设置？")
    assert result["ok"] is False
    assert "次数" in result["error"]


def test_tool_description_mentions_scope():
    assert "已启用且已嵌入" in SEARCH_KNOWLEDGE_DESCRIPTION
    assert "不要编造条号" in SEARCH_KNOWLEDGE_DESCRIPTION


if __name__ == "__main__":
    test_pack_caps_figures_tables_and_fills_text()
    test_pack_empty_figure_slots_go_to_text()
    test_pending_includes_mentioned_and_direct_hits()
    test_pending_keeps_document_id()
    test_present_hit_maps_milvus_fields_and_drops_score()
    test_empty_enabled_set_notice()
    test_run_search_rejects_blank_query()
    test_run_search_empty_ids_skips_milvus()
    test_budget_caps_calls()
    test_tool_description_mentions_scope()
    print("ok")
