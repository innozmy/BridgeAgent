---
name: beam-bridge-modeling
description: >-
  梁式桥抗震分析用 SAP2000 建模规则：平面梁格、板式支座 Link、m 法土弹簧。
  主梁划分 ≤3m，墩与基础 ≤2m。只由 Python 建模图按节点注入。
---

# 梁式桥抗震建模（跑时）

目的：按 **《公路桥梁抗震性能评价细则》JTG/T 2231-02—2021** 建平面梁格 SAP2000 模型（可后续做反应谱/时程）。本任务 **不 RunAnalysis**。知识库未接前不引用其它抗震规范条文号。不是静力脊骨梁。

运行时：

- **识图**把切片注入千问 VL。
- **建模不把切片发给大模型。** `check` / `spec` / `build` 是 Python 代码，必须按本目录切片实现。改 Skill 而不改 `check.py` / `spec.py` / `sap_com.py`，模型不会变。节点里 `modeling_skill()` 只读盘确认切片存在，不是提示词。

尺寸来自账本投影；缺则 `needSupplement` 让 Spring 调识图，**不私调识图图**。不抽钢筋/钢束。只建 **账本指定标号 + 指定单幅**。

SAP2000 版本：请求字段 `sapVersion` / `SAP2000_PROG_ID`。来源是 **项目级默认**，本任务卡可改。建造器打开对应 ProgID，不要混用另一版的文件格式口头承诺。

`.sdb` **先保存在本机** `data/models/{projectId}/`（经 Spring 落盘），不上传云。人可在模型页删除旧版本；不自动删。

## 代码对照切片

| 节点 | 切片 |
|------|------|
| `check` | `required.md` + `seismic.md` |
| `spec` | `geometry.md` `sections.md` `bearings.md` `constraints.md` `soil.md` `loads.md` `naming.md` |
| `build` | `sap_oapi.md` |

构件有无以图纸/账本为准：没有盖梁、系梁、横隔梁、承台、桩，就不要编一套「标准墩」。

## 已锁定：不抄对照模型的简化

K130 手建模型里有、但 **与本 Skill 冲突、禁止采用** 的做法：

| 对照模型 | 本系统 |
|----------|--------|
| 脊骨单梁 | **每片主梁单独杆单元** + 虚横梁 |
| 纵坡抬节点 Z | **全部主梁同一 Z**，纵坡本阶段不做 |
| N·mm | **kN·m**（`InitializeNewModel` = kN_m_C） |
| Wen 塑性 Link | **Linear**，滑动向 1.0 kN/m |
| 多只支座并联成 1 个 Link | **每只支座一个 Link**，刚度不叠加合并 |

对照模型只学：盖梁/系梁、Body、土弹簧、插入点顶缘、台/墩分 GJZ·GJZF、下部与主梁分标号。

## 与旧稿的关系

已作废：单根脊骨代替多片梁、不建桩和支座连接、不加土弹簧、全桥 1m 划分。现行：平面梁格 + 板式支座 Link（刚构处固结）+ 桩/承台/盖梁/系梁（有则建）+ 土弹簧（有土才建）+ 主梁 ≤3m、墩与基础 ≤2m。
