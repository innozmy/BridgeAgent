"""双钥匙合并的最小用例，不调 Unstructured。"""

from bridge_agent.knowledge.merge import merge_elements


def test_merge_same_clause_and_block_cross_clause():
    elements = [
        {"type": "NarrativeText", "element_id": "a", "parentId": "p1", "pageNumber": 1, "text": "6.2.3 第一条碎片"},
        {"type": "NarrativeText", "element_id": "b", "parentId": "p-other", "pageNumber": 1, "text": "同一条后半，见图 6.2.3。"},
        {"type": "NarrativeText", "element_id": "c", "parentId": "p1", "pageNumber": 2, "text": "6.2.4 另一条，见表 4.1.1。"},
        {"type": "Table", "element_id": "t1", "pageNumber": 2, "text": "表 4.1.1"},
        {"type": "Image", "element_id": "img", "pageNumber": 1, "text": ""},
    ]
    figures = [{"id": "p1-i1", "figureNo": "6.2.3"}]
    tables = [{"id": "t1", "tableNo": "4.1.1"}]
    out = merge_elements(elements, figures, tables)
    assert out["chunkCount"] == 2, out
    first, second = out["chunks"]
    assert first["clauseNo"] == "6.2.3"
    assert "a" in first["elementIds"] and "b" in first["elementIds"]
    assert first["mentionedFigureIds"] == ["p1-i1"]
    assert second["clauseNo"] == "6.2.4"
    assert second["mentionedTableIds"] == ["t1"]
    assert "t1" not in first["elementIds"]


def test_merge_same_parent_without_clause():
    elements = [
        {"type": "ListItem", "element_id": "x", "parentId": "title", "pageNumber": 3, "text": "甲款"},
        {"type": "ListItem", "element_id": "y", "parentId": "title", "pageNumber": 3, "text": "乙款"},
        {"type": "ListItem", "element_id": "z", "parentId": "other", "pageNumber": 3, "text": "另一父"},
    ]
    out = merge_elements(elements, [], [])
    assert out["chunkCount"] == 2, out["chunks"]
    assert out["chunks"][0]["elementIds"] == ["x", "y"]
    assert out["chunks"][1]["elementIds"] == ["z"]


if __name__ == "__main__":
    test_merge_same_clause_and_block_cross_clause()
    test_merge_same_parent_without_clause()
    print("ok")
