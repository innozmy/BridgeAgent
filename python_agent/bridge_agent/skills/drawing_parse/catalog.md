# 梁式桥结构参数目录（建模用）

给抗震平面梁格准备的量。**钢筋/钢束不在本目录。**  
`round=1`：本轮说明+总布置能看见就进 extracts。`round=2`：要对应专页（墩柱图、桩位、支座垫石、横断面、地质、变截面构造）再抽。

`value` 约定：长度默认米；重度 kN/m³；线荷载 kN/m（单幅全宽，除非 label 写明「每片梁」）；角度用度。  
按墩/按桩号的量：`label` 必须带墩号或桩号（如 `2#墩桩径`），同一 key 可出现多条 extracts。

看不清就空着。**禁止**用地区经验、标准图集或邻桥数字冒充本图。土层不全时识图只抽看见的；**不要在识图阶段把邻墩土层抄进本墩**——建模才允许借用并标注。

---

## 0. 已有账本（禁止再进参数袋）

| key | label | 说明 |
|-----|--------|------|
| code | 工程标号 | 桩号/桥名 |
| carriageway | 幅面 | 只采账本指定幅 |
| girderType | 主梁形式 | |
| layoutType | 结构体系 | 简支/连续/桥面连续 |
| material | 主梁材料 | 主梁砼标号 |
| region | 地区 | |
| spansM | 跨径组合 | 一联一个数组；墩柱高不在此列 |

---

## 1. 联、跨、斜、弯、缝（round=1 为主）

| key | label | unit | round | 常见来源 |
|-----|--------|------|-------|----------|
| nSpans | 孔数 | — | 1 | 说明「5×30」、立面分跨 |
| nUnits | 联数 | — | 1 | 说明或总布置分联 |
| unitBreakStation | 分联桩号 | 文本 | 1 | 如 K130+320 分联；label 写第几联端 |
| expansionJointM | 伸缩缝宽 / 梁端间隙 | m | 1 | 总布置、伸缩缝构造；没有则建模用 0.08 m 并标 estimated，识图仍空着 |
| expansionJointType | 伸缩缝类型 | 文本 | 2 | 模数式/异型钢等 |
| skewDeg | 斜交角 | ° | 1 | 有则填，正交填 0 或省略；按墩不同则 label 带墩号 |
| curveRadiusM | 平曲线半径 | m | 1 | 总布置、路线；直桥省略 |
| curveDeflectionDeg | 曲线转角 / 圆心角 | ° | 1 | 有则填 |
| alignmentNote | 平面线形说明 | 文本 | 1 | 如「左偏圆曲线 R=250m」 |

跨径只写 `units[].spansM`，不要用斜长、桥面连续长度冒充跨径。

---

## 2. 上部结构几何与截面

梁格建模需要 **片数、梁距、各片截面、桥面板厚、变截面分段、横隔梁**。横断面上看见的尺寸尽量一次抽全。

| key | label | unit | round | 常见来源 |
|-----|--------|------|-------|----------|
| girderHeightM | 主梁梁高 | m | 1 | 说明、横断面、T 梁构造；变截面则按跨中/支点分条 |
| girderHeightAtMidspanM | 跨中梁高 | m | 2 | 变截面箱梁 |
| girderHeightAtSupportM | 支点梁高 | m | 2 | 变截面箱梁 |
| variableSectionStartM | 变截面起点距支点 | m | 2 | 构造图；label 写联/跨 |
| variableSectionLengthM | 变截面段长 | m | 2 | |
| girderStationOfSection | 截面所在桩号 | 文本 | 2 | 同一片梁多种截面时必填 |
| girderSpacingM | 主梁中心距 | m | 1 | 说明「梁距 2.19m」、横断面 |
| girderCount | 单幅片数 | — | 1 | 横断面数梁 |
| innerGirderWidthM | 中梁顶宽 | m | 1 | 说明、横断面 |
| edgeGirderWidthM | 边梁顶宽 | m | 2 | 横断面 |
| girderBottomWidthM | 梁底宽 | m | 2 | T 梁/箱梁底板 |
| webThickM | 腹板厚 | m | 2 | 多腹板则 label 写左/中/右 |
| flangeThickM | 翼缘/顶板厚 | m | 2 | |
| deckSlabThickM | 桥面板厚（含现浇调平层以外的板） | m | 2 | 横断面；虚横梁刚度用。与铺装厚分开 |
| wetJointWidthM | 湿接缝宽 | m | 2 | 预制梁 |
| deckClearWidthM | 桥面净宽 | m | 1 | 说明「净 11.75+2×0.5」拆开写 |
| totalWidthM | 桥梁全宽 | m | 1 | 净宽+护栏 |
| barrierWidthM | 单侧护栏宽 | m | 1 | |
| crossSlopePct | 桥面横坡 | % | 1 | 总体布置 |
| diaphragmCountPerSpan | 每孔横隔板道数 | — | 1 | 说明 |
| diaphragmLocationM | 横隔梁位置 | m 或文本 | 2 | 距支点或桩号；label 写第几跨第几道 |
| diaphragmSectionB | 横隔梁宽 | m | 2 | |
| diaphragmSectionH | 横隔梁高 | m | 2 | |
| continuityJoint | 连续段做法 | 文本 | 1 | 湿接缝/现浇连续段 |

边梁与中梁尺寸不同必须分开写，不要合成一个「典型截面」。

---

## 3. 下部结构（墩台）

「每个标号桥墩上每根墩柱长度」（已锁定）：**盖梁底（或墩顶）至承台顶/桩顶** 的柱长。来源不限于标高差：总体布置尺寸、墩高表、构造图柱长均可（见 `piers.md`）。不要用地面线以上的外露高度冒充。

**落点（已锁定）：** 逐墩/逐柱进账本联表子表 `units[].supports[].columns[]`，**禁止**再进 `project_param`。同一 `code`（如 P1）上两根柱高度不同必须两行 column。

| key | label | unit | round | 说明 |
|-----|--------|------|-------|------|
| abutmentType | 桥台类型 | 文本 | 1 | 重力式、肋板式、桩柱式、扶壁式等 |
| pierType | 桥墩类型 | 文本 | 1 | **双柱墩、三柱墩、独柱墩、刚构墩（墩梁固结）、排架墩、薄壁墩** |
| pierMaterial | 墩身砼标号 | 文本 | 2 | 与主梁不同则必抽；没有则建模可暂用主梁标号并标注 |
| pierSectionType | 墩柱截面 | 文本 | 1 | 圆形、矩形、圆端形、空心薄壁 |
| pierDiameterM | 圆形墩直径 | m | 1 | 仅圆截面；变径则按段分条 |
| pierSectionB | 矩形墩横桥向边长 | m | 2 | |
| pierSectionH | 矩形墩顺桥向边长 | m | 2 | |
| pierWallThickM | 空心墩壁厚 | m | 2 | |
| pierColumnCount | 该墩柱数 | — | 1 | 与 columns 条数核对 |
| capBeam | 盖梁 | 文本/尺寸 | 2 | 有盖梁才填，独柱无盖梁则注明无 |
| capBeamLengthM | 盖梁长 | m | 2 | 横桥向 |
| capBeamWidthM | 盖梁宽 | m | 2 | 顺桥向 |
| capBeamHeightM | 盖梁高 | m | 2 | |
| tieBeam | 系梁 | 文本 | 2 | 几道、在柱顶/柱中/桩顶；没有填「无」 |
| tieBeamWidthM | 系梁宽 | m | 2 | |
| tieBeamHeightM | 系梁高 | m | 2 | |
| groundElevM | 地面线高程 | m | 2 | 用于埋深；label 写墩号 |
| capBottomElevM | 盖梁底高程 | m | 2 | 有标高可与承台顶相减得柱长 |
| pileCapTopElevM | 承台顶/桩顶高程 | m | 2 | |

**已废止：** 用多次 `pierColumnHeightM` extracts 靠 label 区分各柱。高度只写 `supports`。桩号等非高度仍可用 extracts：

| key | label 示例 | unit | round |
|-----|------------|------|-------|
| pierStation | 2#墩桩号 | 文本 | 1 | 如 K130+245 |

---

## 4. 基础与桩位

一柱一桩、柱底直接接桩、无承台：行业称 **桩柱式**（柱下单桩）。  
多桩用承台连成整体：**承台 + 群桩**。  
无桩的扩大基础：**刚性扩大基础 / 明挖基础**。

| key | label | unit | round | 说明 |
|-----|--------|------|-------|------|
| foundationType | 基础类型 | 文本 | 1 | 桩柱式（一柱一桩）、承台群桩、扩大基础 |
| pileType | 成桩工艺 | 文本 | 1 | 钻孔灌注桩、挖孔灌注桩、打入桩 |
| pileDiameterM | 桩径 | m | 2 | 变径则分段 |
| pileLengthM | 桩长 | m | 2 | 逐桩或典型值，label 写墩号；可用桩顶/桩底标高差 |
| pileTopElevM | 桩顶高程 | m | 2 | |
| pileBottomElevM | 桩底高程 | m | 2 | |
| pileCountPerPier | 该墩桩数 | — | 2 | |
| pileLayout | 桩位布置 | 文本 | 2 | 行列或相对墩中心坐标；建模按此布点 |
| pileGroupAlong | 群桩顺桥向排数 | — | 2 | 仅群桩 |
| pileGroupTrans | 群桩横桥向排数 | — | 2 | |
| pileSpacingAlongM | 顺桥向桩距 | m | 2 | |
| pileSpacingTransM | 群桩横桥向桩距 | m | 2 | |
| pileCap | 承台 | 文本/尺寸 | 2 | 桩柱式无承台要写「无承台」 |
| pileCapLengthM | 承台长 | m | 2 | |
| pileCapWidthM | 承台宽 | m | 2 | |
| pileCapThickM | 承台厚 | m | 2 | |
| spreadFoundation | 扩大基础尺寸 | 文本 | 2 | 无桩时 |

---

## 5. 支座与垫石（按墩号 / 台号）

布置以 **本图垫石平面 + 垫石/支座大样** 为准，各桥不同。禁止默认每片几块或一石几座。数量表只作核对，且必须分清单幅还是左右幅合计。`GQF`/`MZL` 是伸缩缝，不要进 `bearingType`（见 `bearings.md`）。

| key | label 示例 | unit | round | 说明 |
|-----|------------|------|-------|------|
| bearingType | 2#墩支座类型 | 文本 | 2 | 本图所写 GJZ / GJZF / 盆式 / 球钢；墩台不同则分条或写清部位 |
| bearingTypePier | 桥墩支座类型 | 文本 | 2 | 仅当本图墩、台型号不同 |
| bearingTypeAbutment | 桥台支座类型 | 文本 | 2 | 仅当本图墩、台型号不同 |
| bearingSize | 2#墩支座平面尺寸 | mm 或 m | 2 | 如 400×500；**建模不作为硬缺口**（Link 不建截面），读到仍写入袋 |
| bearingFixed | 2#墩固定/滑动 | 文本 | 2 | 本图标明才填 |
| bearingLayout | 2#墩支座布置 | 文本 | 2 | 本图：每片几块、几排、部位 |
| bearingCountPerGirder | 2#墩每片梁支座数 | — | 2 | 由本图垫石数与大样推，不要默认 1 或 2 |
| bearingRows | 2#墩支座排数 | — | 2 | 顺桥向，按本图 |
| bearingG | 橡胶剪切模量 | MPa | 2 | 型号表或说明 |
| bearingRubberThickM | 橡胶层总厚 | m | 2 | \(\sum t\)，厘米要换成米 |
| bearingHeightM | 支座总高 | m | 2 | |
| padStoneLayout | 2#墩垫石布置 | 文本 | 2 | 横桥几块、顺桥几排；每石几座按大样 |
| padStoneSize | 垫石平面/高度 | 文本 | 2 | |

桥台支座同样按台号抽；不建台身但台处支座必须能布置。墩台布置不同不要合成一句「全桥同」。

---

## 6. 二期恒载（铺装 + 护栏）

口径（已锁定）：`sdlKNPerM` = **本幅（左或右或整幅不分幅）全宽** 的每延米 kN/m，不要按每片 T 梁分摊。

无铺装、无护栏数量也 **不要编标准值**。识图留空；建模把二期当软缺口，只标注缺失，仍可保存模型。

算法（已锁定）：

1. 铺装：优先用图上的铺装厚度 + 桥面宽度：每延米体积 = 厚(m) × 宽(m)，再乘一般重度得到该层 kN/m。沥青可取 **23～24 kN/m³**，水泥砼铺装 **25 kN/m³**。
2. **护栏每延米重度：** 优先用施工统计图 / 数量表中护栏材料用量反算（体积×重度或表上已列重量 ÷ 对应长度）。**必须看清表是单幅还是左右幅（两幅）合计**：合计则除以 2 再给本幅；`barrierKNPerM` 口径仍是 **本幅两侧护栏合计**。没有数量也没有截面时不要用一个假的「标准护栏重」硬凑，该 key 留空。
3. 也可用数量表里的铺装材料用量反算，同样必须分清单幅/双幅。
4. extracts 的 `note` 写明 `estimated`、用的重度、宽度口径，以及护栏用量取自单幅还是双幅表。

| key | label | unit | round |
|-----|--------|------|-------|
| asphaltThickM | 沥青铺装厚 | m | 1 |
| concreteOverlayThickM | 砼铺装/现浇层厚 | m | 1 |
| deckWidthForSdlM | 二期恒载所用桥宽 | m | 1 | 与 sdl 同口径，通常为全宽 |
| asphaltKNPerM | 沥青层每延米重 | kN/m | 1 | 可估算 |
| overlayKNPerM | 砼铺装每延米重 | kN/m | 1 | 可估算 |
| barrierKNPerM | 护栏每延米重（本幅两侧合计） | kN/m | 1 若本轮见到数量表或截面；否则 2 | 须注明单幅/双幅表 |
| sdlKNPerM | 二期恒载每延米（单幅全宽） | kN/m | 1 | 各层相加，能加的先加 |

---

## 7. 地质弹簧（m 法）——尽量按墩抽

结构竣工图常常 **没有** 完整土层与 m/Cz。有地质页再抽；没有就 gap。

**禁止**用地区经验 m 值冒充识图。  
**禁止**在识图阶段把 A 墩土层默默写成 B 墩的值。

分层尽量写成「深度区间 + 土名 + 状态（密实/可塑等）+ 该层厚度」。同一钻孔适用于一段桥时，用 `soilAppliesTo` 列出墩号/桩号范围，不要复制多份假的「每墩一份」。

某墩地质页缺分层、缺 m、或缺地面线：能看见的仍抽，缺的留空。建模阶段允许该墩 **借用相邻墩或相近区域** 已入账土层，并在模型 note 标注 `soilBorrowedFrom=…`。图上写明「与××墩相同」时，识图可以抽并在 `note` 写来源墩号。

| key | label | unit | round | 说明 |
|-----|--------|------|-------|------|
| soilLayer | 土层描述 | 文本 | 2 | 分层：深度+土名+状态；label 带墩号或钻孔号 |
| soilThicknessM | 该层厚度 | m | 2 | 与 soilLayer 对应 |
| soilM | 土的比例系数 m | kN/m⁴ | 2 | 地勘有则抽；没有则建模按 JTG 3363 表 L.0.2-1 查并标 estimated |
| rockCz | 岩石地基系数 Cz | kN/m³ | 2 | 岩层 |
| soilAppliesTo | 本钻孔适用墩号 | 文本 | 2 | 如 P1–P3 或 K130+200～+280 |
| groundwaterDepthM | 地下水位埋深 | m | 2 | |
| boreholeStation | 钻孔桩号 | 文本 | 2 | |

---

## 8. 明确不抽

钢筋明细、箍筋间距、钢束坐标、卷内备考表页数。数量表 **不用于跨径**；铺装与护栏材料用量可用于二期恒载，且须分清单幅/双幅。  
本任务不做抗震分析，不必抽反应谱、场地类别（除非说明页顺带出现且不增加幻觉）。

---

## 已锁定的口径

- 墩柱高：盖梁底～承台顶/桩顶；**每根柱单独存**（双柱不同高不要合成一个数）；进联表 `supports` 不进参数袋。（2026-08-27）
- 二期恒载：单幅全宽 kN/m；铺装可用厚度×宽度×一般重度估算；护栏优先数量表用量并分清单幅/双幅，note 标 `estimated`。无二期则留空，不编标准值。（2026-08-27，2026-08-30 补：建模软缺口）
- 地质 m/Cz：无地勘页则空，不用地区经验冒充。某墩缺土由 **建模** 借用邻墩/相近区域并标注，识图不擅自抄邻墩。有土名无 m：建模按 JTG 3363—2019 表 L.0.2-1 查中值并标 `estimated`。（2026-08-30）
- 伸缩缝宽：图上优先；没有则建模 0.08 m + `estimated`。（2026-08-30）
- 支座布置：以本图垫石平面与大样为准，不默认每片几块或一石几座；GQF/MZL 不进 `bearingType`。（2026-08-30）
- 虚横梁：板条 \(I=bh^3/12\)、\(J=bh^3/6\)，故必须尽量抽出 `deckSlabThickM`（或砼铺装厚）。（2026-08-30）
- 单元划分：主梁/虚横梁/横隔梁 ≤3 m；墩柱/盖梁/系梁/承台/桩 ≤2 m。（2026-08-30）
