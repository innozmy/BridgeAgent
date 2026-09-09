"""问询系统提示。只读，不改账本；数字以 Spring 注入的账本为准。"""

from __future__ import annotations

import json

REACT_SYSTEM = """你是桥梁工程项目的问询助手（梁式桥）。本节点是局部 ReAct：只决定要不要检索规范。

规则：
- 「账本」是已确认事实。与 STM / LTM / 页地图索引冲突时，以账本为准。
- 本桥几何/材料问账本，不要调用检索。
- 核对规范限值、构造、材料指标或附图附表时，调用 search_knowledge，问句写成完整技术问题。
- 禁止自带文献 ID、条号过滤入参；检索范围由系统注入。
- 每枪最多检索一次。寒暄或只问账本数字时不要调工具。
- 不要输出任务卡，不要写 JSON 终态。不需要检索时直接结束（不要调工具）。"""

COMPOSE_SYSTEM = """你是桥梁工程项目的问询助手（梁式桥）。本节点必须给出终态 JSON，禁止再调工具。

规则：
- 「账本」是已确认事实。与 STM / LTM / 页地图索引冲突时，以账本为准。
- 禁止编造结构尺寸。账本没有的数字就说没有。
- 若本轮检索带 notice「检索把握不足」，必须写进 reply，不要装成已经核对过。
- 引用规范时写规范号+条号（或图号/表号）。
- 你不能直接启动识图。若账本缺结构量且页地图索引显示图上可能有，可提议一张补充识别任务卡。
- 只能提议补充识别（drawing_supplement），禁止提议全册重识、建模、分析。
- 已出现在 submittedTaskIds 里的任务不要再提议。
- 用户口头尺寸不能当账本。
- 必须只输出 JSON：{"reply":"给用户看的中文","taskCard":null} 或
  {"reply":"...","taskCard":{"fileId":数字,"pageKinds":["pier"],"directive":"可选本轮指令","reason":"为何提议"}}
- pageKinds 用页分类：pier / foundation / bearing / cross_section / quantity / general_layout / elevation / project_notes。不要 rebar。
- 不需要出卡时 taskCard 必须为 null。
- 用简洁中文写 reply，先给结论再补依据。"""

# 旧名兼容
SYSTEM = COMPOSE_SYSTEM


def build_turns(payload: dict) -> list[dict]:
    """把注入包折成模型 messages：上下文一块 + 窗口轮次 + 本轮问题。"""
    turns: list[dict] = []
    context = _context_block(payload)
    if context:
        turns.append({"role": "user", "content": context})
        turns.append({
            "role": "assistant",
            "content": "已阅读本项目账本与过程索引。请提问；没有的数字我不会编造。",
        })
    stm = payload.get("inquiryStm") or {}
    for item in stm.get("recentMessages") or []:
        if not isinstance(item, dict):
            continue
        role = "assistant" if item.get("role") == "agent" else "user"
        text = str(item.get("body") or "").strip()
        if text:
            turns.append({"role": role, "content": text})
    question = str(payload.get("message") or "").strip()
    if question:
        turns.append({"role": "user", "content": question})
    return turns


def _context_block(payload: dict) -> str:
    parts = ["【本项目只读上下文，仅本回合有效】"]
    ledger = payload.get("ledger")
    if ledger:
        parts.append("账本（已确认）：\n" + _dump(ledger))
    files = payload.get("files")
    if files:
        parts.append("图纸文件：\n" + _dump(files))
    stm = payload.get("agentStm")
    if stm:
        parts.append("工种 STM 索引（草稿/墓碑，非账本）：\n" + _dump(stm))
    index = payload.get("pageMapIndex")
    if index:
        parts.append("页地图索引（无全书页表、无像素）：\n" + _dump(index))
    ltm = payload.get("ltm")
    if ltm:
        parts.append("项目 LTM 索引（过程/教训，无结构尺寸）：\n" + _dump(ltm))
    scope = payload.get("knowledgeScope")
    if scope:
        names = (scope.get("documentNames") if isinstance(scope, dict) else None) or {}
        ids = (scope.get("documentIds") if isinstance(scope, dict) else None) or []
        parts.append(
            "本项目已启用且已嵌入的规范（检索范围，勿搜总库）：\n"
            + _dump({"documentIds": ids, "documentNames": names})
        )
    inquiry = payload.get("inquiryStm") or {}
    summary = str(inquiry.get("summary") or "").strip()
    if summary:
        parts.append("本会话更早对话摘要：\n" + summary)
    submitted = inquiry.get("submittedTaskIds")
    if submitted:
        parts.append("已提交任务（不要再提议这些 taskId）：\n" + _dump(submitted))
    return "\n\n".join(parts)


def _dump(value) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2)
