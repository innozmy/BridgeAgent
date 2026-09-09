# 二期恒载（线质量）

口径与识图锁定一致：**本幅全宽每延米 kN/m**（`sdlKNPerM`），护栏为本幅两侧合计。数量表须分清单幅/双幅。

抗震：该线荷载同时作为 **质量**（线质量 = sdl / g，SAP 用 Mass Source 或 Frame 附加质量），均分到本幅各片主梁（按片数平均，note `sdlSplitByGirderCount`）。不要整段只加在边梁。

无 `sdlKNPerM`：不加二期，**仍保存模型**，gaps / note 标明二期缺失。禁止编标准沥青+防撞墙。

自重：材料重度，不要把梁自重复加进 SDL。

汽车、人群、温度、预应力：本任务不建（抗震工况属分析步）。

## 案例（K130+260，非通用模板）

该模型主梁加了线质量、跨中点加了节点重量，Mass Source = 单元质量 + 附加质量、**不含荷载模式**。第一版：有 `sdlKNPerM` 则均分到各片主梁 `Frame.SetMass`（t/m）；Mass Source 同样 Elements+Masses、Loads=No。不要抄它的 0.00797（那是 N·mm 制）。
