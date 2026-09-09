"""规范覆盖层白名单。"""

from bridge_agent.graphs.modeling.overlay import empty_overlay, merge_overlay, sanitize_overlay
from bridge_agent.graphs.modeling.consult import apply_topic_result, list_topics


def test_sanitize_drops_topology_and_bad_numbers():
    raw = {
        "overrides": {"soilM": "5000", "spanM": 30},
        "flags": {"abutmentGjzf": True, "inventLink": True},
        "notices": [{"text": "见附录 L", "citation": "JTG 3363", "part": "soil"}],
        "joints": [{"id": 1}],
    }
    out = sanitize_overlay(raw)
    assert out["overrides"] == {"soilM": 5000.0}
    assert out["flags"] == {"abutmentGjzf": True}
    assert "inventLink" not in out["flags"]
    assert out["notices"][0]["citation"] == "JTG 3363"
    assert "joints" not in out


def test_merge_overlay_last_wins_flags():
    a = sanitize_overlay({"flags": {"abutmentGjzf": True}})
    b = sanitize_overlay({"overrides": {"soilM": 8000}, "flags": {"abutmentGjzf": False}})
    merged = merge_overlay(a, b)
    assert merged["flags"]["abutmentGjzf"] is False
    assert merged["overrides"]["soilM"] == 8000.0


def test_list_topics_pile_without_m():
    payload = {
        "ledger": {
            "params": [{"key": "foundationType", "label": "桩基", "value": "钻孔灌注桩"}],
            "units": [{"supports": [{"kind": "pier", "columns": [{"heightM": 8}]}]}],
        }
    }
    ids = [item["id"] for item in list_topics(payload)]
    assert "soil_m" in ids
    assert "pier_connection" in ids
    assert len(ids) <= 3


def test_list_topics_skips_soil_when_ledger_has_m():
    payload = {
        "ledger": {
            "params": [
                {"key": "foundationType", "label": "桩基", "value": "钻孔灌注桩"},
                {"key": "soilM", "label": "m值", "value": "12000"},
            ],
            "units": [{"supports": [{"kind": "pier", "columns": [{"heightM": 8}]}]}],
        }
    }
    ids = [item["id"] for item in list_topics(payload)]
    assert "soil_m" not in ids


def test_low_confidence_does_not_apply_flags():
    overlay = apply_topic_result(
        empty_overlay(),
        "pier_connection",
        {"flags": {"skipBearingWhereRigid": True}, "overrides": {"soilM": 5000}},
        "检索把握不足",
    )
    assert overlay["flags"] == {}
    assert overlay["overrides"] == {}
    assert overlay["notices"][0]["text"] == "检索把握不足"


def test_confident_extract_applies_flag():
    overlay = apply_topic_result(
        empty_overlay(),
        "bearing_slide",
        {"flags": {"abutmentGjzf": True}},
        "",
    )
    assert overlay["flags"]["abutmentGjzf"] is True
