"""自我纠正：挑选、等价问句、包装循环。不调云。"""

from bridge_agent.knowledge.correct import (
    LOW_CONFIDENCE_NOTICE,
    attach_low_confidence,
    hard_pass,
    pick_better,
    queries_equivalent,
)
from bridge_agent.knowledge.search import empty_result
from bridge_agent.knowledge import search_job


def _ok(hits):
    return {
        "ok": True,
        "notice": None,
        "hits": hits,
        "pendingFigures": [],
        "pendingTables": [],
    }


def _hit(ref: str):
    return {
        "kind": "text",
        "body": "摘录-" + ref,
        "documentId": 13,
        "documentName": "规范",
        "familyCode": "JTG D62",
        "clauseNo": "6.2.1",
        "figureNo": "",
        "tableNo": "",
        "pageNumbers": [1],
        "refId": ref,
        "mentionedFigureIds": [],
        "mentionedTableIds": [],
    }


def test_hard_pass():
    assert hard_pass(_ok([_hit("a")])) is True
    assert hard_pass(_ok([])) is False
    assert hard_pass(empty_result()) is False


def test_queries_equivalent():
    assert queries_equivalent("板式支座刚度怎么设置？", "板式支座刚度怎么设置")
    assert not queries_equivalent("板式支座刚度", "盆式支座转角")


def test_pick_better_rules():
    empty = _ok([])
    one = _ok([_hit("a")])
    two = _ok([_hit("a"), _hit("b")])
    assert pick_better(one, empty) is one
    assert pick_better(empty, two) is two
    assert pick_better(one, two) is two
    assert pick_better(two, _ok([_hit("x"), _hit("y")])) is two


def test_attach_notice():
    out = attach_low_confidence(_ok([_hit("a")]))
    assert out["notice"] == LOW_CONFIDENCE_NOTICE
    assert out["hits"][0]["refId"] == "a"


def test_run_search_empty_ids_skips_correct():
    result = search_job.run_search({"query": "支座刚度", "documentIds": []})
    assert "未启用" in (result.get("notice") or "")
    assert result["hits"] == []


def test_second_pass_returns_second_without_notice():
    packs = [_ok([_hit("a")]), _ok([_hit("b"), _hit("c")])]
    orig_retrieve = search_job.retrieve_once
    orig_eval = search_job.evaluate_hits
    orig_rewrite = search_job.rewrite_query
    search_job.retrieve_once = lambda *args, **kwargs: packs.pop(0)
    search_job.evaluate_hits = lambda query, hits: len(hits) >= 2
    search_job.rewrite_query = lambda query, hits: "板式支座竖向刚度如何按规范取值"
    try:
        out = search_job.run_search({"query": "支座刚度怎么设置？", "documentIds": [13]})
    finally:
        search_job.retrieve_once = orig_retrieve
        search_job.evaluate_hits = orig_eval
        search_job.rewrite_query = orig_rewrite
    assert [item["refId"] for item in out["hits"]] == ["b", "c"]
    assert out.get("notice") is None


def test_both_fail_keeps_more_hits_and_notice():
    packs = [_ok([_hit("a")]), _ok([_hit("b"), _hit("c"), _hit("d")])]
    orig_retrieve = search_job.retrieve_once
    orig_eval = search_job.evaluate_hits
    orig_rewrite = search_job.rewrite_query
    search_job.retrieve_once = lambda *args, **kwargs: packs.pop(0)
    search_job.evaluate_hits = lambda query, hits: False
    search_job.rewrite_query = lambda query, hits: "板式支座刚度规范条文"
    try:
        out = search_job.run_search({"query": "支座刚度怎么设置？", "documentIds": [13]})
    finally:
        search_job.retrieve_once = orig_retrieve
        search_job.evaluate_hits = orig_eval
        search_job.rewrite_query = orig_rewrite
    assert len(out["hits"]) == 3
    assert out["notice"] == LOW_CONFIDENCE_NOTICE


def test_same_rewrite_skips_second_search():
    calls = []

    def once(query, document_ids, names):
        calls.append(query)
        return _ok([_hit("a")])

    orig_retrieve = search_job.retrieve_once
    orig_eval = search_job.evaluate_hits
    orig_rewrite = search_job.rewrite_query
    search_job.retrieve_once = once
    search_job.evaluate_hits = lambda query, hits: False
    search_job.rewrite_query = lambda query, hits: query
    try:
        out = search_job.run_search({"query": "支座刚度怎么设置？", "documentIds": [13]})
    finally:
        search_job.retrieve_once = orig_retrieve
        search_job.evaluate_hits = orig_eval
        search_job.rewrite_query = orig_rewrite
    assert calls == ["支座刚度怎么设置？"]
    assert out["notice"] == LOW_CONFIDENCE_NOTICE


if __name__ == "__main__":
    test_hard_pass()
    test_queries_equivalent()
    test_pick_better_rules()
    test_attach_notice()
    test_run_search_empty_ids_skips_correct()
    test_second_pass_returns_second_without_notice()
    test_both_fail_keeps_more_hits_and_notice()
    test_same_rewrite_skips_second_search()
    print("ok")
