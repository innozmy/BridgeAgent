# SAP2000 OAPI 步骤

建造器按序调用。ProgID 用请求里的项目默认版本（任务卡可改），不要写死一个小版本号却打开另一套。单位 `InitializeNewModel` = kN_m_C。

保存：**先写本机**。建造器保存到 Spring 给定的 `sapOutputDir`（即 `data/models/{projectId}/` 下本次文件），再由 Spring 登记 `project_sap_model`。不要传到网盘或改用户桌面模型。

失败：`ok=false`，`error` 以 `【SAP2000】` 开头。禁止无文件还返回 path。

## 允许的顺序

1. 启动对应版本 SapObject，`ApplicationStart(kN_m_C, Visible=True, "")`；建库后再 `Hide`。
2. `InitializeNewModel(kN_m_C)` **之后必须** `File.NewBlank()`（CSI 官方顺序）。缺这一步 `Save` 只写出约 6KB 空壳，SAP 打不开。
3. 材料、主梁/墩/桩/盖梁/系梁/虚横梁截面（`sections.md`）。单位始终 kN_m_C，禁止改成对照模型的 N·mm。
4. 按规格加点、加框架。**UserName** 才是对象名（`naming.md`）；不要把名字传给 OUT 的 `Name`，否则全是 `1` `2` `3`。点 `MergeOff=True`，避免支座顶与梁节点被合并。主梁插入点 **8（顶缘）**。
5. 板式支座 `PropLink.SetLinear(Name, DOF, Fixed, Ke, Ce, DJ2, DJ3)` 后 `LinkObj.AddByPoint(..., False, PropName, UserName)`。不要调用不存在的 `SetLinearSprings`。刚构墩跳过 Link。
6. Body 约束：`ConstraintDef.SetBody` + `PointObj.SetConstraint`（梁形心–支座顶、墩顶–盖梁）。
7. 桩底、台底 `SetRestraint` 六自由度。
8. 土弹簧：埋深划分点 `PointObj.SetSpring` 仅水平（`soil.md`）。
9. DEAD 用材料重度；二期用主梁 `Frame.SetMass`。Mass Source = 单元质量 + 附加质量、**不含荷载模式**。虚横梁截面质量/重量修正为 0。
10. `SetModelIsLocked(False)` 后 `File.Save`。本任务 **不** `RunAnalysis`。**等 .sdb 体积稳定后再** `ApplicationExit`；马上退出会截断复合文档。约 6KB 且体积已稳定 = 未 NewBlank 的空壳，不是「没写完」。过小（约 &lt;32KB）不要登记。
11. 导出节点坐标与框架连接为 `previewJson`（`joints[{id,x,y,z}]`、`frames[{i,j,name,kind}]`），供模型页展示该版本几何。
12. `ApplicationExit`。

## 禁止

- 禁止读用户随便一个桌面模型当底图。
- 禁止建钢筋、钢束。
- 禁止用全约束代替上部板式支座 Link（刚构固结除外）。
- 禁止规格外加点加杆。
- 禁止建盆式/球钢「简化 Link」。
- 禁止 `SetPlasticWen`；禁止把多只支座并成一个 Link。
