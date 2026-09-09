"""问询出卡校验，不调千问。"""

from bridge_agent.graphs.inquiry.nodes import _normalize_card
from bridge_agent.graphs.inquiry.prompts import build_turns


def test_normalize_card_requires_file_id():
    assert _normalize_card({"pageKinds": ["pier"]}) is None
    assert _normalize_card({"fileId": 0}) is None
    card = _normalize_card({"fileId": 12, "pageKinds": ["pier", ""], "reason": "缺柱高"})
    assert card["fileId"] == 12
    assert card["pageKinds"] == ["pier"]
    assert card["reason"] == "缺柱高"


def test_build_turns_includes_scope_and_question():
    turns = build_turns({
        "message": "跨径多少",
        "ledger": {"code": "K1"},
        "knowledgeScope": {"documentIds": [13], "documentNames": {"13": "JTG 3363"}},
        "inquiryStm": {"recentMessages": [], "submittedTaskIds": []},
    })
    joined = "\n".join(str(item.get("content") or "") for item in turns)
    assert "K1" in joined
    assert "JTG 3363" in joined
    assert turns[-1]["content"] == "跨径多少"
