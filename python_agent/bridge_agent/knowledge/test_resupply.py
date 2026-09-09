"""补发只读 parse 索引与 JPEG，不扫总库。"""

from pathlib import Path

from bridge_agent.knowledge.resupply import attach_resupply, scope_ids, tool_result_for_model


def test_resupply_reads_injected_root_only(tmp_path: Path):
    parse = tmp_path / "parse"
    fig_dir = parse / "figures"
    fig_dir.mkdir(parents=True)
    jpeg = fig_dir / "p12.jpg"
    jpeg.write_bytes(b"\xff\xd8fakejpeg")
    (parse / "figures.json").write_text(
        '{"figures":[{"id":"p12-i1","figureNo":"6.2.1","caption":"截面","path":"figures/p12.jpg"}]}',
        encoding="utf-8",
    )
    (parse / "tables.json").write_text(
        '{"tables":[{"id":"t1","tableNo":"6.2.2","caption":"m值","textAsHtml":"<table><tr><td>5000</td></tr></table>"}]}',
        encoding="utf-8",
    )
    result = {
        "ok": True,
        "hits": [],
        "pendingFigures": [{"documentId": 13, "refId": "p12-i1"}],
        "pendingTables": [{"documentId": 13, "refId": "t1"}],
    }
    scope = {"documents": [{"documentId": 13, "rootPath": str(tmp_path)}]}
    packed = attach_resupply(result, scope, max_jpeg=4, table_chars=8000)
    assert len(packed["_jpegBytes"]) == 1
    assert packed["figures"][0]["figureNo"] == "6.2.1"
    assert packed["figures"][0]["documentId"] == 13
    assert "5000" in packed["tables"][0]["html"]
    shown = tool_result_for_model(packed)
    assert shown["figures"][0]["attached"] is True
    assert "_jpegBytes" not in shown


def test_resupply_skips_unknown_ref(tmp_path: Path):
    (tmp_path / "parse").mkdir()
    (tmp_path / "parse" / "figures.json").write_text('{"figures":[]}', encoding="utf-8")
    (tmp_path / "parse" / "tables.json").write_text('{"tables":[]}', encoding="utf-8")
    result = {
        "ok": True,
        "pendingFigures": [{"documentId": 13, "refId": "missing"}],
        "pendingTables": [],
    }
    packed = attach_resupply(result, {"documents": [{"documentId": 13, "rootPath": str(tmp_path)}]})
    assert packed["figures"] == []
    assert packed["_jpegBytes"] == []


def test_table_budget_stops_after_first(tmp_path: Path):
    parse = tmp_path / "parse"
    parse.mkdir()
    (parse / "tables.json").write_text(
        '{"tables":[{"id":"a","textAsHtml":"AAAA"},{"id":"b","textAsHtml":"BBBB"}]}',
        encoding="utf-8",
    )
    result = {
        "ok": True,
        "pendingFigures": [],
        "pendingTables": [
            {"documentId": 1, "refId": "a"},
            {"documentId": 1, "refId": "b"},
        ],
    }
    packed = attach_resupply(
        result,
        {"documents": [{"documentId": 1, "rootPath": str(tmp_path)}]},
        table_chars=4,
    )
    assert len(packed["tables"]) == 1
    assert packed["tables"][0]["refId"] == "a"


def test_scope_ids():
    assert scope_ids({"documentIds": [3, 3, 0, "x", 5]}) == [3, 5]
