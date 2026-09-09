"""嵌入行拼装：不调云、不连 Milvus。"""

from bridge_agent.knowledge.embed import BODY_BYTE_MAX, BODY_CHAR_MAX, build_embed_rows, _clip, _pk


def test_three_kinds():
    chunks = [
        {
            "id": "s-0001",
            "text": "6.2.3 抗震构造。见图 6.2.3。",
            "parentChunkId": "c-0001",
            "clauseNo": "6.2.3",
            "pageNumbers": [12],
            "mentionedFigureIds": ["p12-i1"],
            "mentionedTableIds": [],
        }
    ]
    figures = [
        {
            "id": "p12-i1",
            "figureNo": "6.2.3",
            "caption": "构造示意",
            "pageNumber": 12,
            "nearbyClause": "6.2.3",
            "path": "parse/figures/p12-i1.jpg",
            "vl": {
                "meaning": "墩柱箍筋加密。",
                "inFigureText": "['完整 VL 长文不该进 body']",
            },
        }
    ]
    tables = [
        {
            "id": "t1",
            "tableNo": "4.1.1",
            "caption": "材料表",
            "nearbyClause": "4.1.1",
            "pageNumber": 8,
            "textAsHtml": "<table><tr><td>HTML</td></tr></table>",
        }
    ]
    rows = build_embed_rows(
        chunks,
        figures,
        tables,
        {"documentId": 13, "sha256": "ab", "category": "code", "familyCode": "JTG 2231"},
    )
    kinds = {row["kind"]: row for row in rows}
    assert set(kinds) == {"text", "figure", "table"}
    assert kinds["text"]["id"] == _pk(13, "text", "s-0001")
    assert "抗震构造" in kinds["text"]["body"]
    assert kinds["text"]["mentioned_figure_ids"] == ["p12-i1"]
    assert kinds["figure"]["figure_no"] == "6.2.3"
    assert "墩柱箍筋" in kinds["figure"]["body"]
    assert "完整 VL" not in kinds["figure"]["body"]
    assert "JPEG" not in kinds["figure"]["body"]
    assert ".jpg" not in kinds["figure"]["body"]
    assert kinds["table"]["table_no"] == "4.1.1"
    assert "<table" not in kinds["table"]["body"]


def test_body_fits_milvus_bytes():
    text = "抗震构造。" * 400
    clipped = _clip(text)
    assert len(clipped) <= BODY_CHAR_MAX
    assert len(clipped.encode("utf-8")) <= BODY_BYTE_MAX
    rows = build_embed_rows(
        [{"id": "s-0001", "text": text, "parentChunkId": "c-0001"}],
        [],
        [],
        {"documentId": 1, "sha256": "ab"},
    )
    assert len(rows) == 1
    assert len(rows[0]["body"].encode("utf-8")) <= BODY_BYTE_MAX


if __name__ == "__main__":
    test_three_kinds()
    test_body_fits_milvus_bytes()
    print("ok")
