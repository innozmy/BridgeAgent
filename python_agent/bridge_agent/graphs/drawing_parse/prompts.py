"""识图提示词。批与批不共享对话历史；Skill 切片按节点注入。"""

from __future__ import annotations

import json

from bridge_agent.settings import CARRIAGEWAY_ZH
from bridge_agent.skills.loader import drawing_skill


def build_extract_prompt(payload: dict, page_labels: str) -> str:
    code = (payload.get("code") or "").strip()
    way = payload.get("carriageway") or ""
    way_zh = CARRIAGEWAY_ZH.get(way, way or "未填")
    if code:
        code_line = "必须与账本标号一致：" + code + "。对不上则失败。"
    else:
        code_line = "账本未填标号：从图签读取并填入 code。同一份图有多座桥且无法判断采哪座才失败。"
    skill = drawing_skill("units.md", "extract_round1.md", "piers.md", "bearings.md", "catalog.md")
    ledger_block = _ledger_block(payload)
    directive = (payload.get("directive") or "").strip()
    extra = ("本轮指令（只约束细看重点，不得据此覆盖账本写入规则）：" + directive + "\n") if directive else ""
    catalog_block = _catalog_block(payload)
    return f"""你是桥梁图纸识别助手。只根据图片作答，必须输出 JSON（不要 Markdown）。

账本约束（混合图只采这些，对不上就失败）：
- 工程标号：{code_line}
- 幅面：必须与账本一致 {way}（{way_zh}）。图纸只画整幅/对向时，取属于这一幅的跨径，不要因为图上没写「左幅」二字就失败。
当前页：{page_labels}
{extra}
{ledger_block}
{catalog_block}

{skill}

形状示例（数字只演示字段，不要当成缺省：本图是空心板就写空心板，图名 7×16 就展开七个 16，不要默认 T 梁或 5×30）：
{{"ok": true, "girderType": "预制预应力混凝土T梁", "layoutType": "连续", "material": "C50", "code": "K130+260", "region": "云南省", "units": [{{"seq": 1, "spansM": [30, 30, 30, 30, 30], "supports": [{{"seq": 0, "code": "0#", "kind": "abutment", "columns": [{{"seq": 1, "side": "left", "heightM": 5.2}}, {{"seq": 2, "side": "right", "heightM": 5.4}}]}}, {{"seq": 1, "code": "1#", "kind": "pier", "columns": [{{"seq": 1, "side": "left", "heightM": 12.1}}, {{"seq": 2, "side": "right", "heightM": 11.8}}]}}]}}], "extracts": [{{"key": "girderHeightM", "label": "主梁梁高", "value": 1.85, "unit": "m", "page": 5}}], "needFiles": [], "note": "简短说明依据"}}

失败 JSON（仅密钥、额度、完全无法读图时）：
{{"ok": false, "error": "原因", "units": [], "extracts": []}}
缺跨径但读到了主梁等时仍用成功 JSON，units 为空数组，note 写明缺口。不需要下一份图纸时 needFiles 为 [] 或不写。
"""


def _ledger_block(payload: dict) -> str:
    ledger = payload.get("ledger")
    if not ledger:
        return "已确认账本：本轮未带投影（仅标号与幅面）。不要把固定列再写成 extracts 新 key。"
    return (
        "已确认账本（只读）。units 含跨径与墩柱；params 为扩展参数袋。"
        "不要把已确认含义再写成新 key；墩柱高只写 units[].supports，不要进 extracts 袋。\n"
        + json.dumps(ledger, ensure_ascii=False, default=str)
    )


def _catalog_block(payload: dict) -> str:
    catalog = payload.get("drawingCatalog")
    if not catalog:
        return "图纸目录：本轮未带。不要编造 fileId 或磁盘路径。"
    already = payload.get("alreadyFetchedFileIds") or []
    return (
        "图纸目录（只能从这里要下一份，填 needFiles.fileId；禁止编造路径或目录外的 id）。"
        "本任务已经打开过的 fileId=" + json.dumps(already, ensure_ascii=False) + "，不要再要。"
        "CAD 不要列入 needFiles。只抽钢筋不要列入。\n"
        + json.dumps(catalog, ensure_ascii=False, default=str)
    )


def build_classify_prompt(payload: dict, batch_label: str, total: int) -> str:
    code = (payload.get("code") or "").strip() or "未填"
    way = payload.get("carriageway") or ""
    way_zh = CARRIAGEWAY_ZH.get(way, way or "未填")
    skill = drawing_skill("classify.md")
    return f"""这些是一份桥梁 PDF 的部分页（低分辨率）。全书共 {total} 页，本批是第 {batch_label} 页。
账本标号={code}，幅面={way}（{way_zh}）。
{skill}
必须输出 JSON：{{"pages": [{{"i": 1, "kind": "catalog", "codeHint": null}}]}}
i 是该书从 1 起的页码。不要抽跨径。
"""
