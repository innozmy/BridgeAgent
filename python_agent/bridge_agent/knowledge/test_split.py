"""过长切分：gradient 刀口与 1200 字上限，不调云。"""

from bridge_agent.knowledge.split import split_long_text, split_merged_chunks
from bridge_agent.settings import SPLIT_MAX_CHARS as MAX


def _fake_embed(texts: list[str]) -> list[list[float]]:
    out = []
    for text in texts:
        if "甲主题" in text:
            out.append([1.0, 0.0, 0.0])
        elif "乙主题" in text:
            out.append([0.0, 1.0, 0.0])
        else:
            out.append([0.5, 0.5, 0.0])
    return out


def test_short_chunk_copied():
    src = [{"id": "c-0001", "text": "短句。", "clauseNo": "1.0.1", "elementIds": ["a"], "pageNumbers": [1]}]
    chunks, warnings = split_merged_chunks(src, [], [], _fake_embed)
    assert len(chunks) == 1
    assert chunks[0]["text"] == "短句。"
    assert chunks[0]["splitFromLong"] is False
    assert chunks[0]["parentChunkId"] == "c-0001"
    assert not warnings


def test_long_text_splits_on_topic_change():
    pad_a = "甲主题。" + ("规定甲。" * 200)
    pad_b = "乙主题。" + ("规定乙。" * 200)
    text = pad_a + pad_b
    assert len(text) > MAX
    pieces, _warnings = split_long_text(text, _fake_embed)
    assert len(pieces) >= 2
    assert "甲主题" in pieces[0]
    assert any("乙主题" in p for p in pieces)
    if len(pieces) >= 2:
        prefix = pieces[0][-80:] if len(pieces[0]) > 80 else pieces[0]
        assert pieces[1].startswith(prefix)


if __name__ == "__main__":
    test_short_chunk_copied()
    test_long_text_splits_on_topic_change()
    print("ok")
