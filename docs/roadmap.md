# BridgeAgent 规划底稿

> 活文档。架构一旦拍板、功能一旦落地、规划一旦增减，**同步改这一份**（见 `.cursor/rules/roadmap-sync.mdc`）。  
> 最近整理：2026-09-09（公开 GitHub MIT / BridgeAgent；Vite 默认本机；启动回收孤儿 running；一人一设备登录；并发控制已接线）。

产品目标：企业级桥梁有限元闭环——**生成 → 验证 → 校准**。Agent 第一版只做 **梁式桥**。

---

## 怎么跑


| 进程                                     | 地址                      | 说明                                                                     |
| -------------------------------------- | ----------------------- | ---------------------------------------------------------------------- |
| Spring `bridge_agent_demo`             | `http://127.0.0.1:8080` | 账本与全部 `/api`。本机绑定即可，同事不要直连 8080/8001                                   |
| Vite `web`                             | `http://127.0.0.1:5173` | 默认只本机。`/api` 代理到 8080。同事访问见下「局域网」                              |
| Python `python_agent/.venv` + `app.py` | `http://127.0.0.1:8001` | 只给 Spring 本机调；不要对局域网开放                                                 |


**局域网（可选，非仓库默认）：** 把 `web/vite.config.ts` 的 `server.host` 改成 `true` 后重启 Vite；Windows 防火墙放行 **5173**。同事浏览器打开 `http://<这台电脑的IPv4>:5173`，不要打开 8080。Python / MySQL / Milvus 仍只在这台电脑上。一人一账号同时只允许一处登录（新登录废旧票）。


MySQL 库 `bridge_agent`（本机 8.0）。图纸磁盘：`bridge_agent_demo/data/files/`。知识 PDF：`data/knowledge/`。SAP 模型：`data/models/{projectId}/`。表结构：`bridge_agent_demo/src/main/resources/db/schema.sql`。已有库补识图列：`db/drawing_parse.sql`；补字段来源：`db/field_meta.sql`；补记忆表：`db/memory.sql`；补联下墩柱：`db/unit_supports.sql`；补任务卡：`db/task_cards.sql`；补 SAP 模型版本：`db/sap_models.sql`；补知识合并状态：`db/knowledge_merge.sql`；补知识分割状态：`db/knowledge_split.sql`；补知识嵌入状态：`db/knowledge_embed.sql`；补登录账号：`db/sys_user.sql`；补角色与项目成员：`db/rbac.sql`；补权限审计：`db/audit.sql`；行级乐观锁：`db/optimistic_lock.sql`。页地图/STM/LTM 均在 MySQL；Python 不写 `python_agent/memory/` 文件。

---



## 已锁定（架构与规则）



### 隔离与角色

- 隔离单位是 **项目**（一座桥 × 一幅）。**禁止一条无限聊天当工作台**；不为每个 Agent 单独装对话框。干活入口是项目内 **任务页（干活页）** 上的任务卡。
- **问询不写账本 / 页地图 / LTM / 工种 STM。** 它可以经 Spring **提议任务卡**（`proposed`），人同意后才变成可执行任务。问询图不调用、不转接识图/建模图。
- **Spring 管账本**（MySQL + 本地文件）。**Python 是唯一 Agent 大脑**（LangGraph 多图），禁止直连业务库。浏览器调 `/api` 须登录（见「登录认证」）；Spring→Python 仍是本机内部调用，**不**套用户 JWT。不上 Spring AI / LangChain4j。
- 前端 **只调 Spring**，从不直连 Python。
- 知识库公司一份；**项目启用集**才是以后 RAG 正文范围。总库上传/清单/启用/解析/合并/分割/**嵌入（写 Milvus）**已落地；检索内核与自我纠正包装已接。**RAG 白名单：问询已接线（局部 ReAct）；识图已锁（不加 RAG）；建模已接线（consult 节点，category=code）。**
- **控制平面（2026-09-03 已锁）：外部 WorkFlow + 局部 ReAct。** 工种顺序、硬缺口补识图、过 check 才启 SAP、图与图不私聊，全部是 **Spring 编排 + LangGraph 节点** 写死的工作流，禁止把整条干活链做成通用 ReAct。千问只在 **某一节点内部** 有限自主。高风险动作（COM/`build`、写账本、开另一张图）不进模型可选清单。问询外部四节点与局部 ReAct 数字已锁，见「问询图」。



### 登录认证（2026-09-05 已锁并接线）

**范围：** 身份认证已接线。权限见下节「权限 RBAC」，不再全员当超管。

**流程：**

1. `POST /api/auth/login`（放行）：用户不存在 →「用户名错误」；密码不对 →「密码错误」。密码 **BCrypt** 单向哈希+盐，cost **10**，只存密文。对则签发 JWT。
2. 其它 `/api` 须 `Authorization: Bearer`。拦截器：HS256 重算签名比对 + 看 `exp` + 读用户表 `token_version` 与载荷 `ver` 比对。`OPTIONS` 预检放行。
3. 无令牌 / 过期 / 签名不对 → HTTP **401** +「未登录或登录已失效」。载荷 `ver` 与表 `token_version` 不一致（含他处新登录）→ 401「账号已在其他设备登录」。前端清票并跳 `/login`。`exp` 为签发后 **8 小时**。
4. **登录失败不是 401：** 用户名/密码错误走现有业务 `Result`（HTTP 200 + `code=0` + 上列文案），与其它业务错一致。401 只表示「没票 / 票废」。
5. 载荷：`userId`、`username`、`ver`、`exp`。**不要**放密码、密钥、昵称（昵称可改，登录响应 / `GET /api/auth/me` 给前端）。

**实现约定（2026-09-05）：**

- JWT **手写 HS256**，不上 jjwt，也不上 Spring Security 过滤器链（只要 `spring-security-crypto` 做 BCrypt）。
- 密钥只在 Spring `app.jwt.secret`（至少 32 字），不进前端、不进 Python。换密钥等于废掉所有未过期票。
- 浏览器：`localStorage` 键 `ba_token` / `ba_profile` + Bearer。退出=前端删票。已登录再进 `/login` 跳工作台。
- 首个账号：用户名 `root`，昵称 `zmy`，初始密码 `1234`（演示；SQL 只写 BCrypt 密文）。用户名按库校对，当前 `utf8mb4_0900_ai_ci` **不区分大小写**。
- 已有库补丁 `db/sys_user.sql`。问询按 **项目 + 用户** 私有，互不可见。
- Spring→Python **不**验用户 JWT。不上 Redis 黑名单。不做注册页。
- 改密 / 停用账号 / **新登录**：`token_version +1`，旧设备上的票立刻 401（「账号已在其他设备登录」）。一人同时只一张有效票。本人可改昵称、头像、自己的密码。

### 权限 RBAC（2026-09-05 已锁）

**模型：** 一人一角色；高层三个开关 `super` / `knowledge` / `projectAdmin`（`super` 视为后两开关都开）。项目层 `sys_project_member.perm` = `read` | `operate`，同一用户同一项目一行。操作包含只读。

**内置角色不可删、开关不可改：** `admin`（三角开）、`knowledge`（仅知识库）、`project`（仅项目管理）、`user`（全关）。可新建自定义角色组合开关。

**覆盖：** `super` 或 `projectAdmin` **不写成员表**，默认全部项目 = 操作。`knowledge` 只管公司总库写，**不**因此看见项目。

**动作：**

- 建项/删项：仅 `super` 或 `projectAdmin`。
- 项目内改账本、图纸增删、同意/拒绝任务卡、开跑识图/建模、删 SAP 版本、改项目启用集：项目 **operate**。
- **删自己的问询会话：** 会话主人 + 项目 **read** 即可，不要求 operate（问询私有推出）。不能删别人的。
- **read：** 可见项目；**仅自己的**问询（含自己会话里的提议卡）；干活页**起草**任务卡；下载；看任务/模型/范围（作业链对人可见）。不能改账本、不能改已起草卡、不能同意/开跑。无权限入口不渲染按钮；直打 API **HTTP 403**。
- 知识总库写（上传/删/四步）：仅 `knowledge`（含超管）。其他登录者知识库 **只读**。
- 试检索：有该项目 read 即可。

**菜单：** **权限驱动路由**，路由写在前端代码。不做菜单管理表。侧栏「系统管理」：`super` 见用户管理 + 角色管理 + **操作审计**；`projectAdmin`（无 super）只见「项目层权限」子页。项目内成员 Tab **只读展示**同一张成员表。

**列表：** 超管/项目管理员看全部项目；其他人只看成员表里的项目。无项目权限直打 URL → 403。

**用户：** 停用（`enabled`/`disabled`）不做物理删；不能停用最后一个 `super`。停用且密码正确 →「账号已停用」。头像 `data/avatars/`，不进 JWT。

**鉴权：** 权限不放 JWT；拦截器读角色写入 `UserContext`。`@Async` 作业不读用户权限（开跑前 HTTP 已判 operate）。前端藏按钮 + 后端 403。

**演示账号（密码 1234）：** `root`（admin，昵称 zmy）、`kb`、`pm`、`user`（8031test 操作）、`reader`（8031test 只读）。

**请求级用户上下文（与 version 不是二选一）：**

- `token_version`：管**这张票还作不作数**。改密、停用、**再次登录**都 `+1`，旧票立刻废。一人一设备。
- **ThreadLocal `UserContext`**：管**这一枪 HTTP 是谁**。验票通过后写入 `userId/username/nickname`，业务用 `UserContext.get()`，`afterCompletion` 必须 `remove`，避免线程池脏数据。
- 每次请求仍验签+解析 JWT（便宜）。跨请求不缓存已解析用户。
- `@Async` 后台作业不带这个 ThreadLocal（Spring→Python 本就不是用户 JWT）。

### 权限审计（2026-09-06 已锁）

**和另外三条链分开：**

- **本链（权限审计）：** 账号进得去系统吗、权限是谁改的。表 `sys_audit`，Spring 写，Python 不读不写。
- **作业链（2026-09-06 已锁、已落地）：** 见下节。不进 `sys_audit`。
- **项目链（后议，未做）：** 项目的新建、删除。
- **知识系统链（后议，未做）：** 知识库文献的创建/删除（及四步作业）。本轮不记审计。

**只记三类：**

1. 登录相关：登录成功；登录失败（用户名错误 / 密码错误 / 账号已停用，分原因）；本人改密成功或原密码错误；超管重置他人密码；本人改昵称；本人换头像（只记「换了头像」，不存图片）。
2. 账号与角色：新建用户；停用/启用；超管改他人昵称或角色；新建/删除自定义角色；改自定义角色开关。
3. 项目授权：分配或变更 `read`/`operate`；移除成员。记改前→改后。

**不记：** 验票每一次、退出（本轮无退出接口）、识图/建模/问询/同意任务卡/改账本/图纸、建删项目、知识上传与四步、密码明文、JWT、规范正文、账本数字。

**一条字段：** 时间、操作者 userId（登录失败可空）+ 当时用户名、动作枚举、对象（用户/角色/项目+被授权人）、成功/失败+短原因、改前/改后、IP、User-Agent（截断）。不存请求体全文。

**写失败不挡主业务**（吞掉后打日志）。只在有人点的 HTTP 上写，`@Async` 不写。演示阶段不自动清理。

**前端：** 系统管理「操作审计」只读页，仅超管。按动作、操作者用户名筛选。不能改、不能删。项目管理员不看。不做导出、不做「我的登录记录」。

### 作业链（2026-09-06 已锁）

**落点：** 加字段到现有 `model_task` / `model_task_event`，**不**新建第二张审计总表。补丁 `db/job_chain.sql`。

**范围：** 只含识图/建模相关任务（含分析卡骨架与「未执行分析」时间线，仍不跑求解器）。问询纯聊天不进作业链。手改概览、上传/删图纸本轮不做（属项目资料，以后可归项目链）。

**来源 `origin`（避免和账本 `source` 撞名）：** `draft` / `inquiry` / `drawing_button` / `auto_supplement`。

**自动补充识图：** `origin=auto_supplement`；时间线操作者为「系统」；`agreed_by_*` 抄父建模的同意人。

**展示：** 只加强任务页 + 时间线（来源、创建人、开跑人、时间线前缀操作者）。不做新 Tab、不做导出、不做全公司作业总表。有项目 **read** 即可看。

**识别了什么：** 仍看账本 + 该任务时间线 + `proposal_json`，作业链不抄一份尺寸。

**操作者：** HTTP 上的人点记登录名；`@Async` 无 `UserContext` → 时间线 `actor_username=系统`。

### 问询私有（2026-09-06 已锁）

问询按 **项目 + 用户**。每用户每项目会话独立；**只有主人能看/发/删**。超管也不能看别人的问询。

问询出的任务卡仍是**项目级** `model_task`，任务页有 read 的人都能看见，同意仍要 operate。

旧会话 `user_id IS NULL`：不对任何人展示（不认领）。

`inquiry_stm` 仍跟 thread，因此自然跟主人。

### 工作队列 / 资源队列（2026-09-07 已锁）

**页名：** 侧栏 **「资源队列」**（原「计算队列」）。路由仍 `/queue`。展示本机三条作业车道的占用与排队，不是只展示 SAP。

**可见范围：** 识图 / 建模按项目权限过滤（有 read 才看见该项目的行）；**超管 / 项目管理员看全部项目**。知识车道是公司总库占用本机 Python，**凡登录可见**（与知识库只读清单一致）。不做导出、不能在此页取消进行中的 Python。

**产品队列 ≠ 中间件 MQ ≠ 线程池 BlockingQueue。** 不上 Redis / Rabbit。排队真相在 MySQL：`model_task.status=queued`（人已同意、还没进线程池）；知识四步对应列也可为 `queued`。`proposed` 仍是等人同意。进程内每条车道再囤 **5** 个已派发任务（`app.queue.pool-queue-capacity`）；这 5 个进池后库状态为 `running` / 知识四步进行中，重启可能丢掉池内缓冲（行会卡在进行中，与原先 running 崩溃同类）。

**三套独立线程池（救急=0，即 max=core；池内队列=5）：**

| 车道 | Bean | 干什么 | 核心（可配） |
|------|------|--------|-------------|
| 识图 | `drawingJobExecutor` | 全册 / 补充识别（含建模自动子识图） | **2**（`app.queue.drawing-core`） |
| 知识 | `knowledgeJobExecutor` | 解析 / 合并 / 分割 / 嵌入（四步共用这一条车道） | **2**（`app.queue.knowledge-core`） |
| 建模 | `sapJobExecutor` | 整张建模卡的 Python（check/consult/spec/build）。父任务 `running` 期间占此车道，即使正在等子识图 | **1**（`app.queue.sap-core`） |

拒绝：线程池队列满则 Abort，行改回 `queued` 再派。禁止 Discard、禁止 CallerRuns。问询 / 短 API 仍走 Tomcat，不进这三池。分析卡仍不占车道（本阶段不跑求解器）。

**同一项目干活链互斥** 仍有效：`running` 与 **`queued`** 的识图/建模都占项目链（已同意就会跑）。同意卡与图纸一键识图先 `SELECT project FOR UPDATE`，再查链，识图/建模卡再用 `proposed→queued` CAS。派发 `queued→running` 也是 CAS，并跳过本项目链已被别人占的卡（只放行父建模 running + 其子识图）。与跨项目车道容量是两把锁。单机 Spring，派发用进程内锁，不做分布式锁。

**接线：** `queued`、三车道派发、`GET /api/resource-queue`、侧栏「资源队列」已落地。分析仍不跑求解器。

### 并发控制（2026-09-07 已接线）

**前提：** 同时操作人数不多。不上 Redis 锁、不铺悲观锁、不加 WebSocket 专为同步。

**三层，不是一把全局锁，也不是只锁项目：**

1. **本机车道：** 识图 / 知识 / 建模资源队列 + 项目干活链。长作业不靠乐观锁限流。
2. **公司知识：** 锁到**文献行**。同一文献同时只一个四步作业（忙检 + `queued` 列 CAS）。四步回写只改状态列，禁止整行 `updateById` 覆盖元数据。
3. **项目账本：** 锁到**行**。`project` / `project_param` 带 `version` 乐观锁。人手保存必须带回当前 version，冲突提示刷新、不覆盖。问询会话私有，不锁别人的聊天。

**写数据：** 行级乐观锁（`version` 自增）或状态 CAS（`WHERE status≠queued/running`）。任务卡同意只靠 `proposed`→`queued` CAS，不加第二套 version。补丁 `db/optimistic_lock.sql`。MyBatis-Plus `OptimisticLockerInnerInterceptor`。

**看见别人的改动（界面）：** 不推送。GET 读 MySQL。知识范围与概览建模提示约 4 秒短轮询；切回页面重拉。保存冲突后刷新即看到对方的值。

**长作业快照（方案 A）：** 以 **一次 Spring→Python HTTP** 为快照边界（「一枪」）。本枪用开头注入的账本/启用集跑完，中途改库不打进已经在跑的 Python。同一张建模卡若因硬缺口自动补识图，Spring **再发一枪**（人不必再点同意），这一枪重新读库。若本卡一轮 HTTP 就建成 `.sdb`，这份模型按开跑时快照；要对齐新账本需再同意一张新建模卡（v2）。概览在建模 `queued`/`running` 时提示上述边界。不采用 B（version 变了中止本卡）、C（建模中禁止改账本）。识图写入用落库当下 version，冲突重试一次仍失败则任务失败并写时间线。

**问询：不要作业级并发控制。** 不进三车道、不占干活链、不写账本。下一句重注账本/`knowledgeScope`。出的任务卡同意/开跑仍走干活链 + 资源队列。前端 `sending` 防连点。问询 STM 不加 `version`。

**重启注意：** 线程池队列在 JVM 内存；池内最多约 5 个已派未跑的作业，进程退出可能停在 `running`。**启动回收已接线（2026-09-07）：** `ApplicationReady` 把识图/建模 `running` CAS→`failed`，时间线写「服务重启，本枪中断」；图纸 `parsing`→`uploaded`；知识四步进行中同样标 `failed`。父+子都 running 则都失败。不自动重跑。`queued` / `waiting` / `proposed` 不动，回收后再 `pumpAll()`。不要定时杀所有 running。产品排队在 MySQL `queued`，重启后可再派。

### GitHub 公开仓库（2026-09-09 已锁）

- 远程名 **BridgeAgent**，**public**，MIT。单仓（`web` + `bridge_agent_demo` + `python_agent` + `docs`）。
- **不提交：** `.env`、`application-local.properties`、本机 `data/`（图纸 / 知识 PDF 与解析产物 / `.sdb` / 头像）、`node_modules`、`.venv`/`venv`、`target/`、IDE、本机对话记录。规范整本 PDF 与竣工图册有版权，不进 Git。百炼 / Unstructured Key **只**在本机 `python_agent/.env`。
- Spring 提交无密钥的 `application.properties`；本机复制 `application-local.properties.example` → `application-local.properties`（已 ignore）。Python 只提交 `.env.example`。
- Vite **默认** `127.0.0.1:5173`；局域网需改 `host: true`（见「怎么跑」）。
- 根 README / LICENSE / SECURITY；演示账号与权限表写在 README。昵称 `zmy` 保持。包名不改。不做 CI。
- `docs/roadmap.md` 与 `.cursor/rules` 进仓。知识种子 SQL 只留规范名称。
- 上架前轮换曾出现在本机/对话里的百炼 Key；第一次 commit 不得含真实 Key / JWT。

**尚未实现：** 本机安装 Git 后 `git init` 与推送到 GitHub（密钥拆分与说明文件已按上列落地）。

---



### 通讯

```
浏览器 ──Spring Web /api──► Spring
                              │ 仅 Agent 步骤
                              └──HTTP JSON──► Python :8001
                                              │ 识图等
                                              └──API Key──► 云端 VL（千问）
```

- 识图：`POST http://127.0.0.1:8001/v1/parse-drawings`（内部 `drawing_parse` 图）。也可 `POST /v1/agents/{kind}`，`kind`：`drawing_parse` / `inquiry` / `modeling`。Spring 配置 `app.python-agent-url`。
- 请求带 `projectId`、`carriageway`、`code`、`files[].{fileId,originalName,path,sha256}`（**本轮要打开的 PDF**，一般一份）。另带 **图纸目录** `drawingCatalog`（本项目全部图纸的 fileId / 文件名 / 页地图索引，无像素、无磁盘路径）和 `alreadyFetchedFileIds`。`path` 是 **本机绝对路径**（与 Spring 同机）。Python 不按文件名扫盘、不查库。若本轮不够，响应可带 `needFiles[{fileId,focusKinds,reason}]`；**Spring 校验后在同一 running 任务里再发 HTTP**（最多额外 2 轮）。不是 Python 回调业务库。
- 每次 Spring→Python 另带 **账本只读投影**（固定列 + 联跨径与墩柱 + `field_meta` + 已有 `project_param`）以及本任务所需的 STM/LTM 注入，不靠 Python 查库。识图 HTTP 已带该投影。
- 响应必须是结构化 JSON（`ok`、`girderType`、`layoutType`、`units`（含 `supports`）、`extracts`、`gaps`、`hasLayoutPages`、可选 `needFiles` 等）。**写库、改任务状态、冲突确认只发生在 Spring。** 中间轮 `needFiles` 非空时先落该 fileId 页地图，全部轮次结束后再空写或冲突确认。
- Spring 调 Python 仍是 HTTP JSON，读超时 `app.python-agent-read-timeout-seconds=600`。**对人：** 同意任务卡与图纸「识别本项目」立刻返回 `running`，后台再调 Python；任务页刷时间线。不是卡住浏览器直到识图结束。
- 补充识图/继续建模：由 **Spring 发起新请求**，不是 Python 里两个 Agent 私聊。细则见下「建模与识图协同」。



### 知识 RAG（解析 / 合并 / 分割 / 嵌入 / 检索已接；问询与建模已挂）

**已锁定**

- 知识 PDF（中国规范等）**解析放 Python**，调用 **Unstructured 云 API**（本机直装 / Docker 以后再定，不挡本阶段设计）。与识图同一模式：Spring 编排（上传、清单、启用集、`parse_status`、落库），Python 只干活、**不连业务库**。
- Unstructured API Key **只放 Python 环境变量**（`UNSTRUCTURED_API_KEY`），不进前端、不进 Spring、不进 git（与千问 Key 同一纪律）。API URL：`https://platform-api.transform.unstructured.io/api/v1`（`UNSTRUCTURED_API_URL`）。走 Transform **jobs**（`hi_res` Partitioner 节点），不是 Spring 调云、也不是开源库本机 partition。
- **规范 PDF 出网：接受** 发到 Unstructured 云解析（2026-09-02）。本机/Docker 不是本阶段前提。
- 触发：**人在知识库点「解析」**，不是上传后自动。Spring 立刻把 `parse_status` 置为 `parsing`，后台调 Python；失败为 `failed`，可再点重试。
- **禁止**在 Spring 里跑 Unstructured / 解析 PDF 正文。
- **禁止**把知识规范 **整本 PDF** 走识图管道（PyMuPDF 渲页 + 千问 VL）。竣工图识图与规范入库是两条管线。规范附图由知识作业裁切后调千问，**不经过** `drawing_parse` 图。
- 解析作业 **不是** LangGraph：`python_agent` 上单独作业接口即可，不新开一种 Agent 图。
- **知识磁盘：一文献一文件夹，按步骤分子目录（2026-09-02 已落地）：** 不用中文规范名当目录。根仍是 `data/knowledge/`，每本用内容指纹：
  ```
  data/knowledge/{sha256}/
    source.pdf
    parse/          # 第一步：elements.json、figures.json、tables.json、figures/*.jpg
    merge/          # 第二步：chunks.json（只读 parse/，禁止回写 parse/）
    split/          # 第三步（已落地）：过长 chunk 语义再切，只读 merge/
  ```
  旧平铺 `{sha256}.pdf` / `{sha256}.elements.json` **双读、不搬家**。新上传写 `source.pdf`。删文献删整个 `{sha256}/` 及旧平铺文件。强制重解析清 `parse/` 与过期 `merge/`、`split/`，不删 PDF。强制重合并清 `merge/` 与过期 `split/`，不改 `parse/`。
- **产物交回用 sidecar：** 解析写入 `parse/elements.json`。元素尽量原样保留 Unstructured 字段（含 `parent_id`、坐标、`text_as_html`、`table_as_cells` 等）+ `hierarchy[]`。**JSON 仍禁止** `image_base64` 与 `embeddings`。Spring 注入各步绝对路径；Python 不扫目录；HTTP 只回摘要。后续工序读对应子目录，不必再调 Unstructured。
- **第一版规范附图必须进入 RAG（2026-09-02）：** Unstructured 固定 **`hi_res`**，OCR 语言 **`chi_sim` + `eng`**，抽出表 HTML。裁图落 `parse/figures/`（约定生效后），elements JSON **不写 Base64**。单张 VL 失败仍可留下图题+页码，不把整本打成 `failed`。
- 表不送千问。装饰性页眉 Logo 不进 RAG。
- **规范附图不进工程记忆：** 不写 `agent_stm` / `drawing_page_map` / `project_ltm` / 账本 / 问询 STM。识别结果只落知识 sidecar（及以后的知识 chunk）。删项目不清公司知识库。
- **千问读附图时要带图侧上下文（不是识图 Skill、不是项目账本）：** 文献名与规范号、页码、图题、同页邻近条号与前后短文本。禁止注入 `drawing_parse` 切片，禁止注入该项目账本投影。
- **切块 / 合并不由 Unstructured 做（2026-09-02）：** Unstructured 只出 layout 元素（文/表/图框）。按条切分、过短合并、条文说明挂接由 **应用层**（Python 作业 + Spring 编排）对着 sidecar 做。不用 Unstructured 自带 chunk_by_title 等策略当中国规范条号规则。
- **入库分阶段、分按钮：** **「解析」** / **「合并」** / **「分割」** / **「嵌入」** 四个独立按钮。嵌入须已 split，人点才跑，分割成功后 **不**自动写 Milvus。无 PDF 的种子行不能点解析。
- **解析按钮作业（已落地）**
  - 入口：知识库行内「解析」。`POST /api/knowledge/documents/{id}/parse` 立刻 `parse_status=parsing`；后台 Spring→Python `POST /v1/knowledge/parse`。
  - Spring 注入 PDF、`parse/` 绝对路径、文献名与规范号。新作业写 `parse/elements.json`、`parse/figures/`、`parse/figures.json`、`parse/tables.json`。
  - 写盘顺序：先落 elements 和 JPEG，再分批千问回写 `figures[].vl`，最后写图/表目录。
  - 状态：`unparsed` / `parsing` / `parsed` / `failed`。Unstructured 失败才 `failed`。同一文献同时只跑一个解析作业。
  - 再点「解析」：无 sidecar → 全量；有 elements 但附图 VL 未完 → 只补 VL；已 `parsed` → 须确认后清 `parse/`（及过期 `merge/`、`split/`）重跑。
  - 删文献：整夹 `{sha256}/` + 旧平铺 PDF/sidecar。
- **合并按钮作业（2026-09-02 已落地）：** `POST /api/knowledge/documents/{id}/merge` → Python `POST /v1/knowledge/merge`。须已 `parsed`。`merge_status`：`unmerged` / `merging` / `merged` / `failed`。已有库补丁 `db/knowledge_merge.sql`。已合并再点须确认，只重写 `merge/chunks.json`（并清过期 `split/`）。双钥匙：同一 `parent_id` 或同一条号；条号不同禁止合并；**没写出条号的紧随碎片并进当前条**。图/表不进正文，扫「见图/见表」挂 `mentionedFigureIds` / `mentionedTableIds`。
- **图/表晚绑定（2026-09-02 已锁；三步均已落地）：** 不把 JPEG、VL 长文或表 HTML 拼进条文 chunk。
  1. **解析写目录（已做）：** `parse/figures.json`、`parse/tables.json`。认不出号时 figureNo/tableNo 为空，id 用页码+序号或 element_id。
  2. **合并挂引用（已做）：** 只读 parse，写 merge。
  3. **补发给 Agent（已落地）：** 问询 + 建模。同一次 Python HTTP 内，工具回待补发后按 Spring 注入的 `knowledgeScope.documents[].rootPath` 读 `parse/` 组 JPEG/表 HTML；不写工程记忆。识图不补发规范图/表。未进白名单的图不要接规范。实现默认：JPEG≤4、表 HTML 字符预算 8000（`KNOWLEDGE_RESUPPLY_TABLE_CHARS`）。JPEG 以 VL 附图进问询 `compose` / 建模 `consult`；工具回传文本不含字节。
- **补发触发（2026-09-02 已锁，2026-09-02 补：向量命中图/表行也补）：** 下列任一即进入补发候选：条文命中上的 `mentionedFigureIds` / `mentionedTableIds`；ANN 直接命中的 `kind=figure|table` 行；用户点名「图/表 x」。去重后按配额从磁盘取 JPEG/表 HTML。不要做成「只有点名才补」。没命中的不传。命中几乎只有「按表采用」的条时必须带上表。向量命中图/表时，Agent 先看到行上的短 `body`（题注等），**完整图/表仍只走补发，不把 JPEG/HTML 写进向量命中正文。**
- **补发配额（2026-09-02 已锁）：** 单次请求最多 **4 张 JPEG**。表 HTML 走字符预算；超预算先减旧对话，**不减**账本数字。
- **组包方已锁：** 与识图点名某页同一模式——Spring 查目录、注入当次请求；不是 Python 私自读盘，也不是把图/表抄进记忆。
- **改路径（2026-09-02 已锁并落地）：** 双读旧 `{sha256}.pdf` / `{sha256}.elements.json`，新作业写 `{sha256}/source.pdf` + `parse/`；**不自动搬**已解析 sidecar。
- **过长再切（第三步，2026-09-02 已锁并落地）：** 独立按钮/作业，只读 `merge/chunks.json`，只写 `split/`。不改 parse、不改 merge。
  - **只切超长：** 正文 **>1200 字** 才切；未超的 **原样抄入** `split/`。以后 RAG 只读 `split/`。
  - **切法：** 千问 embedding **语义切**，不按「条文说明 / 附录 / 款号」硬切，不上生成式大模型。刀口落在句界（。！？；换行）。相邻句余弦改成距离后，对距离曲线求 **gradient**（离散斜率），本块内超过 gradient 的 95 分位即为候选切点；再保证每块正文 ≤1200 字（一句仍超长则整句留下并记警告，不从句中切开）。
  - **重叠：** 下一块开头重复上一块末尾 **80 字**（另加，不计入「是否超 1200 才切」的判定）。
  - **页码垃圾（如 —39—）先不管**，原文保留。
  - **模型：** DashScope `text-embedding-v4`（1024 维，`text_type=document`）。Key 只放 Python `DASHSCOPE_API_KEY`。`QWEN_EMBED_MODEL` 默认 `text-embedding-v4`。切分向量不落 `split/` JSON。
  - 入口：知识库独立「分割」按钮。`POST /api/knowledge/documents/{id}/split` → Python `POST /v1/knowledge/split`。`split_status`：`unsplit` / `splitting` / `split` / `failed`。须已合并。已有库补丁 `db/knowledge_split.sql`。
- **嵌入按钮作业（2026-09-02 已落地）：** 知识库行内独立「嵌入」（不是「索引」）。`POST /api/knowledge/documents/{id}/embed` → Python `POST /v1/knowledge/embed`。须已 `split`。`embed_status`：`unembedded` / `embedding` / `embedded` / `failed`。已有库补丁 `db/knowledge_embed.sql`。已嵌入再点须确认，只重写该文献在 Milvus 的行。重跑解析/合并/分割作废嵌入（Python `POST /v1/knowledge/vectors/delete` 按 `document_id` 删行）。分割成功后 **不**自动写。不改 parse/merge/split 磁盘。

**已知问题（不挡本阶段，改代码时注意）**

- 表 HTML 的 OCR 常把汉字拆空格、表头切坏；晚绑定不修复 OCR，只保证组装时仍交 HTML。
- 图号/表号依赖题注与正文正则；对不上就占位，合并再靠「见图/见表」对齐。
- 合并「无条号碎片并进当前条」会把 **条文说明 / 用词说明 / 页码「—39—」** 粘进上一条文（现网最长块 `c-0121` 约 2100 字）。第三步按语义切、不硬切结构标题；是否回头收紧合并规则另议。
- 公式编号可能被当成条号（如 `0.025`）。分割不应再把它当新条。

**尚未拍板 / 刻意延后**

- **补给哪些 Agent：** **问询已接线（2026-09-04）。** 局部 ReAct：1 次工具 / 含 compose 最多 3 轮 LLM / 出卡仅终态。**识图不加 RAG。** **建模已接线（2026-09-04）：** 规范覆盖层 `codeOverlay`；问句代码按部分组，对外最多 3 次检索；拓扑仍写死；不写账本。分析仍待定。补发对象 = 问询 + 建模。
- **「企业要求」分类：本阶段不定、不加（2026-09-02）。** 当前只定规范（`code`）。总库现有 `manual` / `case` 维持，不为此扩第四类。
- chunk **不建议**再落一份 MySQL 正文表（磁盘 `split/` 为源、Milvus 为派生索引）；若要坚持双写另议。

### 向量库（Milvus，2026-09-02；写入、检索与 Agent 接线已落地）

约束（已锁，设计必须遵守）：

- RAG 正文只读 **`split/`**，不读 merge/parse 正文。图/表目录只读 **`parse/figures.json`、`parse/tables.json`**。
- 图/表 **晚绑定**：JPEG、VL 长文、表 HTML **不进** 条文 chunk 正文，也 **不进** Milvus 字段。完整内容只由 Spring 当次补发。
- 切分用的向量 **不落** `split/` JSON；索引时再 embed，只写入 Milvus。
- Python **不连业务 MySQL**、不扫 `data/knowledge/`；路径与启用集由 Spring 注入。Milvus 不是业务库，允许 Python 读写（URI/Token 只放 Python `.env`）。
- 检索范围 = **项目启用集**，不是整份公司总库。启用关系在 MySQL `project_knowledge`，会变、不必为此重建向量。
- 现有分类只有 `code` / `manual` / `case`（界面：标准规范 / 建模指导手册 / 工程案例）。**不新增「企业要求」**（2026-09-02：本阶段不定）；当前拍板范围是规范。以后要加须改 MySQL CHECK 与前端分组。
- 没进白名单的图调不到 `search_knowledge`。问询、建模已接线；识图不加。
- **公司知识共用一个 collection**（如 `knowledge_chunk`）。不要一本文献一个 collection，也不按类型拆成互不检索的多库。分类用标量 `category`（以后若要物理隔离，用 Partition=category，仍是同一 collection）。
- **删除/重建按行过滤同步：** `document_id == ?` 或 `sha256 == ?`。删总库文献 = MySQL + 磁盘 `{sha256}/` + 该文献全部向量行。不必 drop collection。
- **出处跟行走，不靠 collection 名。** 每行带 `document_id` / `family_code` / 条号或图号表号；交给 Agent 的召回必须带规范号（或文献名）+ 定位（条/图/表）+ 页码。检索先 `document_id in (启用集)`。
- **三种 kind 同行一个 collection：** `text` | `figure` | `table`。主键 `{documentId}:{kind}:{refId}`。
- **embed 文本（不是完整内容）：** text 用 split chunk 正文；figure 用 `图{号}` + 题注 + VL 短 `meaning`（可截断），不要 JPEG、不要 VL 全文；table 用 `表{号}` + 题注 + 邻近条号，整表 HTML 不进向量。
- **检索包含三种 kind**（不默认只搜条文）。ANN 命中后条文/图/表都给短 `body` 作出处。**JPEG / 表 HTML 仍只走补发**（触发 = 条文 `mentioned*` ∪ 图/表向量命中的 `ref_id` ∪ 用户点名；去重后套已锁配额）。
- **混搜封顶（组包，不是 ANN 过滤 kind）：** 召回后按 kind 截取再交给 Agent：图最多 2 条、表最多 2 条，**其余名额给条文**（条文占多数）。图/表未满 2 的空位补给条文。补发配额（最多 4 张 JPEG、表走字符预算）仍独立计算；组包里的图/表 id 进入补发候选时同样去重。
- **Embedding（2026-09-02 已锁）：** 索引行两个向量字段。密集与稀疏 **同一次** DashScope `text-embedding-v4`（`output_type=dense&sparse`），禁止 BGE-M3 / 本机 SPLADE / v1 再叠 Milvus BM25。密集 **1024** 维，写入 `text_type=document`、查询 `query`，COSINE + HNSW。稀疏为词表维（建字段不写 dim），只存非零 `{index:weight}`，`SPARSE_FLOAT_VECTOR`，IP + `SPARSE_INVERTED_INDEX`。两路 `hybrid_search` **RRF**。必须走 DashScope 原生口（OpenAI 兼容口无 sparse）。切分仍只用 dense、不落盘；换模须重建 Milvus。
- **字段与落盘（2026-09-02 已锁）：** collection `knowledge_chunk`，固定 schema，不开 dynamic field，不按文献/类型分区。磁盘为源、Milvus 为索引；文献名与启用集不进 Milvus。主键 `{documentId}:{kind}:{refId}`。字段见下表。条号/图号/表号有 INVERTED（以后精确通道可用）；**v1 不对模型开放这些入参**，只靠问句 embedding。
- **Milvus 部署（2026-09-02 已锁）：** 使用本机已安装的 Milvus（默认 `http://127.0.0.1:19530`）。URI/Token 只放 Python `.env`。助手调试结束不要去停用户的 Milvus。不上云 Zilliz。
- **第四步「嵌入」按钮（2026-09-02 已落地）：** 知识库行内独立按钮，文案是 **「嵌入」不是「索引」**。`POST /api/knowledge/documents/{id}/embed` → Python `POST /v1/knowledge/embed`。须已 `split`。`embed_status`：`unembedded` / `embedding` / `embedded` / `failed`。已嵌入再点须确认，只重写该文献在 Milvus 的行。重跑解析/合并/分割作废嵌入（按 `document_id` 删向量，`embed_status=unembedded`）。不在分割成功后自动写。
- **RAG 职责（2026-09-02 已锁）：** **嵌入（写入与查询）和 Milvus 读写只在 Python。** Spring **不**调 DashScope、**不**连 Milvus。Spring 编排按钮/状态、注入 `knowledgeScope`（id/名/sha/文献根路径）与启用集、删文献时通知 Python 清向量、检索后补文献名。补发由 Python 按注入路径读盘。前端只调 Spring。工具白名单：问询已接线；建模 consult 已接线；识图不加 RAG。补发对象：问询 + 建模。
- **检索策略（2026-09-03 已锁）：** 混合搜索。问句一次 DashScope `text-embedding-v4` `dense&sparse`、`text_type=query`；Milvus `hybrid_search` 同时走 dense（COSINE）与 sparse（IP），融合 **RRF**。不单路 dense、不 BM25、不加权求和当 v1。硬过滤：`document_id in (项目启用且已嵌入的文献)` **且** `category == "code"`（手册/案例不进 v1 检索）。三种 kind 一起搜，组包封顶规则仍有效。启用集为空时工具返回明确提示，**禁止**降级搜公司总库。RRF 分数 **不**回给模型。
- **检索数字（2026-09-03 已锁；实现时做成配置，默认按下表）：**
  1. **RRF 平滑 k = 60**（越大越平滑；Milvus `RRFRanker` 默认即 60）。
  2. **每路 ANN 召回 = 20**（dense / sparse 各 20，融合前候选，不是交给模型的条数）。
  3. **工具返回（组包总条数）= 10**（图≤2、表≤2，其余给条文；空位补给条文）。与补发 JPEG 最多 4 张分开。例：图、表都满 2 → 6 条条文 + 2 图 + 2 表。
  4. **同一 HTTP 最多混搜 2 次**（自我纠正：至多 1 次改写 + 第 2 次检索）。
  5. **RRF 取出候选 = 40**（两路 ANN 并集上限），再按 kind 截成组包 10。实现配置 `KNOWLEDGE_SEARCH_RRF_CANDIDATES`。
- **RAG 按需工具（2026-09-03 已锁；已挂问询 `react` / 建模 `consult`）：** 不做成「每次 HTTP 自动塞规范」。Python 工具 `bridge_agent.tools.knowledge.search_knowledge`。问询模型只填问句 `query`；建模问句由代码按账本组。**禁止**传入 `documentId[]`、`clauseNo` / `figureNo` / `tableNo`。启用集只来自 Spring 注入。工具结果不写工程记忆。对外一次工具调用内部最多 2 次混搜（含改写）。
- **工具与补发（2026-09-04 已落地）：** 图内调工具时 Spring 来不及再组 JPEG。工具先回短 `body` + 出处 + 待补发 `{documentId, refId}`；**同一 Python HTTP 内**立刻按 `knowledgeScope.documents[].rootPath` 读 `parse/figures.json` / `parse/tables.json` 与 JPEG（basename 限制在 `parse/figures/`）。Python **不扫** `data/knowledge/`。JPEG≤4；表 HTML 实现默认 8000 字。问询 JPEG 进 `compose` VL；建模 JPEG 只进 `consult`。工具回传给模型的文本不含 JPEG 字节。
- **试检索（2026-09-03 已落地）：** `POST /api/projects/{id}/knowledge/search` → Python `POST /v1/knowledge/search`。Spring 注入启用∩已嵌入∩`code` 的 id 与文献名。知识范围页可试问句。不写记忆、不挂 Agent。
- **自我纠正（2026-09-03 已锁并落地包装，已挂检索工具）：** 纠正**检索质量**，不替工种写最终答案。实现在 `knowledge/correct.py` + `search_job.run_search`。问询/建模调 `search_knowledge` 即走此包装。不要把纠正环做成问询图节点。
  1. **要不要检索** = 调用方职责（白名单 Agent 选调工具，或某节点写死要调）。不每枪先跑分类器。
  2. **搜 → 评 → 改写再搜** = 检索包装内部写死步序。最多 **1 次改写、合计 2 次混搜**。改写问句仍落在同一启用集、`category=code`，禁止扩大文献范围或打开条号过滤入参。改写与原问句实质相同则跳过第二次检索。启用集为空只提示「未启用已嵌入的规范」，不改写空转。
  3. **评估双层：** 代码硬规则（空命中 / 仅有未启用提示）必失败；再一次千问**文本**（与问询同一口，不用 VL）做集合级通过/不通过：「这组短摘录是否回答得了问句」。不逐条打分。不得因看不到 JPEG/表 HTML 判失败。
  4. **改写约束：** 禁止编造条号；禁止把本桥账本尺寸写进规范问句。
  5. **作答**仍由调用方 Agent 用账本 + 最终命中组包产出。RAG 不生成答案。
  6. **第二次仍不通过（或两次都失败）：** 交回当次**较好**的一组命中，并带 notice **「检索把握不足」**。较好：非空优先；条数多者优先；并列留第一次（更贴近原问句）。不交空组（除非两次都空）。

**待你拍板**

- 分析 Agent 要不要进 RAG 白名单（问询/建模已接线；识图不加）。
- 表 HTML 字符预算 8000 是实现默认；若问询长对话要把表预算和旧对话裁切绑在一起，另议。
- `requireCapBeam`：多柱缺盖梁目前是 **check 硬缺口**，consult 跑不到；flag 会抽但仍几乎用不上。若要把「规范说必须设盖梁」改成软提示而不挡开建，需改口 check。

**工具描述（已进 `tools/knowledge.py`；问询 `react` / 建模 `consult` 已挂）：**

- 名称：`search_knowledge`
- 给模型看的说明：在本项目**已启用且已嵌入**的规范中检索相关条文、图号、表号。需要核对规范限值、构造、材料指标或附图附表时用。不要用来查本桥几何/材料等工程账本（那是项目参数）。不要编造条号。检索范围由系统注入，你不能指定文献 ID 搜整个公司库。问句写成完整技术问题（构件、工况、规范习语），不要只丢两三个关键词。返回短摘录和出处（规范号、条号/图号/表号、页码）；图/表完整内容可能另附，短摘录不等于整图整表。同一轮不要用几乎相同的问句反复空转。
- 入参：仅必填 `query`（自然语言）。
- 出参：最多 10 条的有序列表；每条含 `kind`、短 `body`、`documentId`、文献名（Spring 注入的映射）、`familyCode`、`clauseNo`/`figureNo`/`tableNo`、页码、`refId`。待补发为 `{documentId, refId}` 列表。不含 RRF 分数。

**行模型（文本 / 图 / 表，已锁）**

三种可检索单元都进 **同一个 collection**，用 `kind` 区分：`text` | `figure` | `table`。不要为图、表另开 collection（维度都是 1024，拆库没有好处）。主键建议 `{documentId}:{kind}:{refId}`，例如 `13:text:s-0001`、`13:figure:p1-i1`。

| kind | 源（只读磁盘） | 拿去 embed 的字符串 | 不进 Milvus、补发时读盘 |
| --- | --- | --- | --- |
| text | `split/chunks.json` | 该 chunk 的 `text` | — |
| figure | `parse/figures.json` | `图{figureNo}` + 题注 + VL 的短摘要（`meaning`，可截断） | JPEG、完整 VL JSON |
| table | `parse/tables.json` | `表{tableNo}` + 题注 + 邻近条号 | 表 HTML（OCR 常脏，**不要**拿整表 HTML 做向量） |

互指（已有合并产物，不另造一套 ID）：

- 条文行带 `mentioned_figure_ids` / `mentioned_table_ids`（目录里的图/表 `id`）。
- 图/表行带同一 `ref_id`，以及 `figure_no` / `table_no`、页码、邻近条号。
- 两条通道都用：搜到条文后按 mentioned* 补发；ANN 也可直接命中图/表行（短 body 给模型，完整内容仍补发）。同一文献重索引：先删该 `document_id` 全部 kind 再写入。

**字段与落盘（已锁）**

Collection 名 **`knowledge_chunk`**。固定 schema，**不开** dynamic field。不按文献分区；也不按 category 分区。

磁盘 `split/` + `parse/*index` 是源；Milvus 是派生索引。文献中文名、启用集 **不抄进** Milvus（检索命中后 Spring 用 `document_id` 补全名称）。

已锁字段（三种 kind 同行，用不到的留空字符串 / 空数组）：

| 字段 | Milvus 类型 | 索引 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(128) 主键 | PK | `{documentId}:{kind}:{refId}`，便于按行 upsert |
| `kind` | VARCHAR(16) | INVERTED | `text` / `figure` / `table` |
| `dense` | FLOAT_VECTOR 1024 | HNSW COSINE | v4 dense |
| `sparse` | SPARSE_FLOAT_VECTOR | SPARSE_INVERTED_INDEX IP | v4 sparse；无 dim |
| `document_id` | INT64 | INVERTED | 启用集过滤、按文献删除 |
| `sha256` | VARCHAR(64) | — | 对齐磁盘目录 |
| `ref_id` | VARCHAR(64) | — | 条文 `s-0001` / 图目录 id / 表目录 id |
| `parent_chunk_id` | VARCHAR(32) | — | 仅 text |
| `category` | VARCHAR(16) | INVERTED | code / manual / case |
| `family_code` | VARCHAR(64) | INVERTED | 如 JTG D62 |
| `region` | VARCHAR(64) | — | |
| `specialty` | VARCHAR(64) | — | |
| `clause_no` | VARCHAR(32) | INVERTED | 精确通道；图/表可填邻近条号 |
| `figure_no` | VARCHAR(32) | INVERTED | 仅 figure 有值 |
| `table_no` | VARCHAR(32) | INVERTED | 仅 table 有值 |
| `body` | VARCHAR(8192) | — | 写入截断 2048 **字**；Milvus `max_length` **按字节**，2048 字节装不下 1200 字中文 split chunk，故字段开 8192。**不要**开 analyzer |
| `page_numbers` | ARRAY\<INT32\> | — | |
| `mentioned_figure_ids` | ARRAY\<VARCHAR(64)\> | — | 主要 text |
| `mentioned_table_ids` | ARRAY\<VARCHAR(64)\> | — | 主要 text |
| `embed_model` | VARCHAR(64) | — | 如 `text-embedding-v4`，换模对照 |

**禁止写入：** JPEG、表 HTML、VL 全文、`project_id`、启用标记、文献中文全称、embedding 副本进 `split/` JSON。

**怎么存（写路径，已落地）**

1. 人点「嵌入」（须已 split）→ Spring 置 `embed_status=embedding`，注入 sidecar 路径与文献元数据。
2. Python 读 `split/chunks.json`、`parse/figures.json`、`parse/tables.json`，拼三种 `body`；`document_id == ?` 删旧行；分批 10 条调 v4 `dense&sparse`，upsert。
3. Spring 只回写 `embed_status`，不落 chunk 正文。

**怎么读（检索内核已落地；未进白名单的 Agent 调不到）**

1. Spring 注入启用且已嵌入的 `documentId[]`、文献名映射（试检索接口已如此；Agent HTTP 尚未注入）。不自己 embed、不查 Milvus。
2. `search_knowledge(query)`：混搜组包后自我纠正（搜 → 双层评估 → 不通过则改写再搜，最多 2 次混搜）→ 仍不通过则较好一组 +「检索把握不足」。出参：短 `body` + 出处 + 待补发 `{documentId, refId}` + 可选 notice。改写句不回传。
3. v1 当次不带 JPEG/表 HTML。文献名用 Spring 注入的映射补，不进 Milvus。不生成答案。

删文献 / 重跑 parse·merge·split：Milvus 按 `document_id` 同步删。


### 建模与识图协同（2026-08-30，方案 A 已锁定）

**不要**两张 LangGraph 共享一份 State，也 **不要** Python 里建模图与识图图互发消息。协作是两层：

- **共享的是账本**（MySQL：固定列、联/墩柱、参数袋、页地图）。各 Agent 每次 HTTP 只看到 Spring 注入的**只读投影**。支座布置、胶层厚、片数等「细节」入账之后，下一枪建模自然看见，不必把识图 STM 抄给建模。平面尺寸不是建模硬条件。
- **传递的是短消息**（建模 → Spring：`needSupplement`；识图 → Spring：`extracts` / `needFiles`）。消息只给 Spring，由 Spring 校验、落时间线、开下一枪 HTTP。这不是聊天，不是共享内存。

工种是 **各司其职、串行接力**，不是多 Agent 围桌共议，也不是同一项目里识图/建模/分析并发抢活：建模硬缺口才自动开识图，识图入账（或人确认冲突）后才续跑建模；有模型之后才分析；分析要改结构再开新的建模卡。同一时刻一条干活链上只推进一个工种；消息经 Spring **一对一编排**（当前干活的 Agent ↔ 下一个工种），降低耦合。问询可并行问答，但 **不写账本、不私调其它图**，要干活仍出卡由人同意。分析本阶段不做。

**方案 A（已锁定）：只在写出第一份 `.sdb` 之前，为硬缺口自动补识别。** 软缺口（二期、全桥无土、缝 estimated）只记 note/gaps，不为此自动识图。本轮同意 = 出一份能开建的模型。建完后要加细：人再同意一张建模卡出 v2。

「建模过程中突然发现缺支座布置」仍落在 A 里，因为发现点必须在 **落盘之前**：

1. `check` **先走规则再走模型**（不启 SAP）：Spring/Python 用代码对照账本投影与 `required.md`（有没有跨径、片数、支座布置与胶层厚等；**不含**支座平面尺寸）。缺硬条件才进入「从图纸清单挑 `fileId`、写 `reason`」（这里才可用模型）。缺则立刻 `needSupplement`，**不进入** `spec`/`build`。
2. `check` 漏检、`spec` 组几何时才发现（例如刚构与板式混用、某墩无数）→ **仍不启 SAP**，同样返回 `needSupplement`。
3. 若已进 `build` 调 COM 才发现硬缺口：中止、**不写残缺 `.sdb`**，仍走 `needSupplement`（仍算开建前）。
4. `.sdb` 已经登记进 `project_sap_model` 之后再发现缺细节：本轮结束；人另开建模卡。禁止同一任务为软缺口或「再精细一点」自动续跑识图。

页地图 **没有** 精确专页 kind（例如无 `bearing`）：**不要立刻失败**。对同一份 PDF 扩扫相关页（支座：`detail`/`other`/数量表/墩柱；桩径：`foundation`+`pier`+大样/其它），补充识别一轮最多细看 `MAX_SUPPLEMENT_FINE_PAGES`（12）页、按 4 页一批。仅当该 `fileId`+缺口 key **已经扩扫过**（墓碑 `sweep=true`）仍缺，才提示：**上传该专页 PDF**，或在概览 **参数袋手填**（来源 `manual`）。旧墓碑无 `sweep` 视为未遍历，下一张建模卡可再识一轮。问询里口述尺寸仍不算入账。时间线「图纸清单 N 份」指本项目 PDF 份数，不是只看了图纸目录页。

SAP 版本第一版：本机 ProgID 写进 Spring 配置，作项目默认；任务卡改版本接口后做。  
本机探测（2026-08-30）：已装 **SAP2000 22 Chinese**（22.0.0），路径 `D:\SAP2000_v22_benti\`。COM ProgID `CSI.SAP2000.API.SapObject`，Helper `SAP2000v1.Helper`。`ApplicationStart(kN_m_C, visible, "")` 返回 0，`GetDatabaseUnits` 为 `kN_m_C`。建模图接入时按此 ProgID 起软件。

`needSupplement` 形态（`fileId` 必须来自本项目 `drawingCatalog`，与识图 `needFiles` 同一套校验；非法丢弃并记时间线）：

```json
{
  "needSupplement": {
    "fileId": 12,
    "focusKinds": ["bearing", "detail", "other", "quantity", "pier"],
    "missingKeys": ["bearingLayout", "bearingRubberThickM"],
    "supportCode": "P2",
    "sweep": true,
    "reason": "非刚构墩缺板式支座顺/横桥布置与胶层厚"
  }
}
```

循环：人同意建模 → Spring HTTP 建模 → 硬缺口 **汇总后**自动插 **一张** `drawing_supplement` 子任务（`parent_task_id`，已是 `running`，不再点同意）→ 识图子任务内仍可 `needFiles`（额外最多 2 枪）→ 空写账本或冲突 `waiting`（人确认前不续跑建模；冲突须写明覆盖前/后）→ Spring **再发**建模 HTTP（全新投影，无 Python 续跑）→ `check` 通过才 `spec`/`build` → 本机 `.sdb`。自动子识图 **每张建模卡最多 4 次**（`app.modeling.max-auto-supplements`，不跨卡累加）；次数用尽仍缺硬条件则建模失败，不写残缺 `.sdb`。**编排已接（2026-08-30）。**

补充识别冲突：任务页表格展示 **字段 / 覆盖前（账本）/ 覆盖后（识别）**，父建模时间线同样写清。自动补识别时，账本已有的跨径、墩高、主梁、铺装/二期等若被模型顺带再读，**不**因此整份卡住，也 **不得**用缺柱高的提案覆盖联表；联在但柱高全空时允许空写墩柱。手填袋「桩径」等中文键归一为 `pileDiameterM`，建模 check 同时认 label。每次建模 HTTP 都重新注入最新账本（`getById`），建模 Agent 不识图。

父建模任务在子识图期间保持 `running`（不要占用识图冲突用的 `waiting`）。子识图是同一条干活链上的自动步骤，允许 **父建模 running + 子识图 running**；其它情况 **项目干活链互斥**：已有 running 的识图或建模时，不得再同意另一张识图/建模卡，图纸页「识别本项目」同样拦住（问询仍可聊、不写账本）。现网只拦两个识图重叠，编排接入时改成这条链锁。

自动子识图与账本冲突、人点 **放弃写入**：账本不动；子任务失败；**父建模 `failed`**，时间线写清「放弃写入，硬缺口未补」。人再同意一张建模卡才重开。不在放弃后自动换一份 PDF 再识，也不把父任务留在 running 空等。

识图不读建模 STM、不读 `.sdb`。建模下一枪不读识图 STM 全文，只读 **已写入账本** 的值；未确认 `proposal_json` 不进建模投影。

**补识别的结果必须落盘，禁止「看过又当没看过」。** 尺寸进账本（空写或人确认冲突）；没读出或人放弃则仍写 **页地图 `gaps` / 墓碑**。下一枪识图带已有页地图，禁止当没扫过。

**建模要有自己的工种 STM**（`agent_stm`，`agent_kind=modeling`），但是 **过程记忆，不是第二份账本**：

- 记：已要过的 `fileId` + `missingKeys` + `supportCode`、自动补识次数、该缺口在某文件上「已看过仍缺」（墓碑）。
- 不记：支座尺寸、跨径、规格草稿、`.sdb` 路径、线框预览。历史模型在 `project_sap_model` + 磁盘，**与 STM 分表**。
- **不要**做成问询那种「最近 N 轮过程原文 + 更早散文摘要」。给人看的过程在时间线；跨任务教训在 LTM。STM 过长由 Spring **裁剪墓碑条数**（删图已清对应项；可再设上限），不用大模型摘要。
- 规则 `check` 先看账本；账本仍缺时再看建模 STM：同一 `fileId`+key 且墓碑 **`sweep=true`** → **不再对同一份图发同样的 `needSupplement`**（可换清单里另一份未扩扫文件；都已扩扫仍缺 → 失败并提示上传或手填袋）。无 `sweep` 的旧墓碑不挡再识。
- 人新传了 PDF 或手改参数袋后，账本或目录变了，下一张建模卡自然能过或换一份图，不必清空整份建模 STM。删该图纸则清对应墓碑（与删 `drawing_page_map` 一致）。

**下一版模型从账本重建，不打开上一份 `.sdb` 改。** 补数据、改参数袋、要出 v2：人再同意一张建模卡；Spring 注入**最新账本**，Python `check`→`spec`→`build` 写一份新文件并登记 `project_sap_model`（seq+1）。注入里最多带上一版的 **元数据指针**（id / seq / sapVersion / storagePath / note），供命名与说明对齐，**不把二进制或 previewJson 塞进 STM，也不在旧文件上增量 COM**。账本是真相，避免 `.sdb` 与账本漂移。在旧模型上改（分析/校准）本阶段不做。

**建模范围（已锁定）：** 第一版始终建 **本项目指定幅面的全部联**。任务卡上的联/墩只写入时间线作说明，不缩小几何。建模卡 **不强制选 PDF/页类**（目录由注入提供）。本轮指令可空，**不得覆盖账本数字**（禁止「按 30m 编」这类口头改尺寸）。

**其余按推荐一并锁定（实现默认，改口再改文档）：**

- `check` 在 Python 建模图内用代码跑（对照 `required.md`）；Spring 只校验 `needSupplement`（图纸清单、未扩扫墓碑、本项目 PDF）。挑 `fileId` **优先代码**：页地图 `kindCounts` 对得上、未扩扫墓碑的第一份；对不上精确 kind 仍打开该 PDF 并带扩扫 `focusKinds`。模型只补 `reason`。非法/空 `fileId` **不扣**自动补识次数，由 Spring 改派或失败。
- 子识图进入冲突 `waiting`：**不设超时**。任务页须标明父建模在等确认写入；人确认 → 自动续跑建模；人放弃 → 父 `failed`。进行中仍不可取消 Python。
- 建模 STM 墓碑条数上限做成 Spring 配置（默认 80）；超出丢最旧条，数字仍以账本为准。
- 本机无 SAP / COM 失败：建模 `failed`，不写残缺 `.sdb`，时间线写清。

**已拍板对照（2026-08-30）**

| 项 | 口径 |
|----|------|
| 协作 | 只共享已确认账本；消息经 Spring 一对一；不共享图 State、不私聊 |
| 循环 | 方案 A：仅第一份 `.sdb` 前自动补硬缺口；软缺口不自动识图 |
| 次数 | 自动子识图每张建模卡最多 4 次（不跨卡累加）；子任务内 `needFiles` 仍最多额外 2 枪 |
| 互斥 | 干活链互斥；允许父建模 running + 子识图 running；问询可聊 |
| 放弃冲突 | 父建模 `failed`，人再同意建模卡 |
| check | 先规则后挑文件；无精确 kind 则扩扫相关页；扩扫后仍缺才提示上传或手填 |
| 记忆 | 建模 STM=过程墓碑，不摘要、不存 `.sdb`；版本在 `project_sap_model` |
| 下一版 | 从最新账本重建，不打开旧 `.sdb` |
| 范围 | 本幅全部联；卡上联/墩不作几何裁剪 |
| 分析 | 本阶段不做 |



### 数据规则

- 主键自增 `id`；工程标号 `code` 可重复、可空。
- 幅面建项必填：`left` / `right` / `undivided`。跨径识图（或人工）后再写。主梁形式项目级。
- 多联挂同一项目，`project_unit.spans_m` 为 MySQL JSON 数组。来源：`drawing` / `cad` / `agent` / `manual`。
- **联—墩—柱（2026-08-27）：** 跨径只在 `project_unit.spans_m`。墩台在 `project_unit_support`（沿联向 seq 从 0 起，n 跨通常 n+1 个墩台；`kind`=`pier`/`abutment`；`code` 如 P1/1#）。**同一墩上每根柱**在 `project_unit_column`（seq 从 1；`side` 可空；`height_m` 为盖梁底至承台顶/桩顶）。P1 双柱且左右高度不同必须两行，禁止把两根柱合成一个数，也禁止塞进 `spans_m` 或 `project_param`。删联级联清墩柱。概览跨径仍是「40+60+40」一行文本；墩柱用结构化表编辑。改跨径孔数不自动删已有墩柱行。
- **账本可扩展（2026-08-27）：** 固定概览字段（标号、主梁、结构形式、材料、地区、跨径联等）只走 **现有列 +** `field_meta`**（来源/上次识图值/缺口）**，**禁止**再在 `project_param` 建同义 key。`project_param` 一行一 key，只放编程时未做成固定列的结构关键量（梁高、护栏每延米重等）。墩柱高不进袋。清单由识图跑时 Skill 的参数目录约束。写入规则与固定列相同。问询不得写袋。概览「其他参数」可展示、手改、整袋保存（`PUT /projects/{id}/params`）。
- **参数袋 key 去重与命名（2026-08-27）：** 识图提议新袋项前必须对照账本投影里的 **（1）固定列 /** `field_meta` **已管字段的含义（2）已有** `project_param` **的 key 与专业名词 label**。含义相同：固定列已有则只更新该列（走现有空写/冲突确认），不得新建袋 key；袋里已有则复用该 key。命名用桥梁与土木工程专业名词做 `label`，`key` 稳定一一对应；禁止口语同义各建一条。Spring 落袋时应再拒一层同义/与固定列冲突的写入，不单靠模型。新量由识图进 `extracts` 再经 Spring 入账。建模缺项由 Spring 发补充识别，不私调识图、不把假定写入袋。拓扑优先进 `project_unit` / `project_unit_support` / `project_unit_column` 等结构化表。
- **extracts 路由（2026-08-27）：** Python 只返回摘录（工程含义/`label`、可选建议 `key`、数值、单位、fileId、页号），**不声明**进列还是进袋。Spring 按序：与固定列/`field_meta` 同义 → 只更新该列；墩柱高以 `units[].supports` 为准不落袋；与已有袋 key/label **精确相同** → 更新该行；都对不上 → 才 insert `project_param`。空写/冲突确认与现识图规则相同。识图落袋去重第一版仍是 key/label 精确匹配。**建模读袋（2026-08-31）：** check 遍历每一行的 key 与 label（含中文键），按专业同义表 + 最长子串认到目录 key（`2#墩桩径`→`pileDiameterM`），不调大模型近义。未确认只留 `drawing_page_map.extracts`；入列/入袋发生在提交点或人确认之后。幅面、项目名、通车日、规范策略仍拒绝。
- **删除项目：** 确认后异步清本项目 MySQL 中的账本（含参数袋）、LTM、各 STM、问询、时间线、页地图、SAP 模型行，以及图纸磁盘与 `data/models/{projectId}/`。公司知识总库 **不删**。迁库完成后不必再清 Python 记忆目录。
- 文件判重看 **SHA-256 内容**，不看文件名。图纸白名单：pdf / dwg / dxf，单文件 200MB。JPG 不入库。
- 混合图只采 **本项目标号 + 建项幅面**。标号以账本为准，VL 在图上核对。对不上且账本已有值：走冲突确认，不编造、不默默覆盖。图上读不清的字段保持空，**不再因为缺跨径而整单失败**。



### 建模图（2026-09-04：工作流 + 规范覆盖层已接线）

**现状代码：** `gate → check → consult → spec → build → finish`。COM 不进模型可选清单。`consult` 代码组题 + ≤3 次 `search_knowledge` + 补发，产出当次 `codeOverlay`；`spec` 只消费白名单。

**已锁并落地（2026-09-03/04）：**

- **不加通用 ReAct**（SAP COM 是高风险动作，不进模型可选清单）。
- **建模进入 `search_knowledge` 白名单**（`category=code`，仅规范；不搜手册/案例。手册与切片容易打架，不拿手册教 COM）。
- **`consult` 在 `check` 通过后、`spec` 之前**，写出当次 **`codeOverlay`**（不写账本）。不是模型自选工具的 ReAct。
- **规范覆盖层（2026-09-04 拍板）：** RAG 不只查数字。规范管「该不该、限值、构造判别」；「SAP 用 Link / Body / Restraint、插入点」只认 `spec.py` + `skills/modeling`。`spec` 只消费白名单字段：

| 通道 | 作用 | 例 |
| --- | --- | --- |
| `overrides` | 软数字；三级链：图→RAG→代码兜底 | `soilM` |
| `flags` | 代码里已有的有限开关，禁止自造拓扑 | 台用 GJZF、刚构处不建支座、多柱必须有盖梁否则走已有补识/缺口逻辑 |
| `notices` | 带出处的提示进时间线/`gaps`，可不改几何 | 「规范要求盖梁与柱刚接；本桥按 Body 实现」 |

- **问句由代码按账本现有部分组。** 有上部才问支座固定/滑动；有桩才问 m 值；多柱且盖梁空才问是否必须设盖梁。对外 `search_knowledge` **最多 3 次**（每题 1 次，工具内纠正仍最多 2 次混搜）。不把多题融成一句。
- **连接：** 只映射到已有分支（刚构⇒该处不建 Link；有盖梁⇒柱顶连盖梁；无盖梁⇒连柱顶）。禁止输出节点/杆/Link 列表，禁止改插入点，禁止改约束种类。
- **软数字三级链**仍有效：先图、再 RAG、再代码兜底；软缺口不挡开建。`overrides`/`flags` 只影响当次 `spec`，打「规范估算/判别，需核对」。
- **`soilM` 走 RAG；`bearingG` 与板厚/缝宽等代码兜底暂不走 RAG。** 覆盖账本已有尺寸：禁止。
- **不做：** `spec` 后再审一遍（与 SAP 同一 HTTP 太重）；按构件开放 ReAct。

#### 建模图外部工作流（Spring 编排，图内写死）

```
人同意建模卡
  → Spring 组注入包（账本投影 / drawingCatalog / pageMapIndex
       / modelingStm 墓碑 / latestSapModel 指针 / directive / sap / knowledgeScope / ltm）
  → POST /v1/agents/modeling
  → 建模图（节点顺序写死，无模型工具自选环节）
  → 返回 JSON { ok, ready, missingHard?, needSupplement?, gaps?, notices?, sdb... }
  → Spring 落：ready=true → 写 project_sap_model + 时间线
                ready=false + needSupplement（硬缺口）→ 自动插子识图任务（最多 4 次）
                failed（COM/密钥/quota）→ 时间线写清原因，不写残缺 .sdb
```

#### 建模图节点（已落地）

| 节点 | 谁做主 | 做什么 | 出口 |
|---|---|---|---|
| `gate` | 代码 | 密钥、注入包完整性检查 | 不通过 → `finish` |
| `check` | 代码（`check.py` 规则） | 按 `required.md` / `seismic.md` 验**硬缺口**；挑 `needSupplement` 文件；识别软参数缺失列表 | 硬缺口 → `finish`（返回 `needSupplement`）；无硬缺口 → `consult` |
| **`consult`** | **代码组题 + ≤3 次 RAG + LLM 填 `codeOverlay`** | 按部分组问句；产出 overrides / flags / notices；补发只供本节点抽数/判别 | 无论结果 → `spec` |
| `spec` | 代码（`spec.py`） | 按账本 + `codeOverlay` 白名单生成梁格规格（确定性）；账本已有 `soilM` 不被覆盖 | 失败 → `finish` |
| `build` | 代码（`sap_com.py`） | 调本机 SAP2000 COM，逐杆建模写 `.sdb` | COM 失败 → `finish`（不写残缺） |
| `finish` | 代码 | 回 JSON，不写库 | 本次 HTTP 结束 |

#### consult 节点内部（写死步序，不是 ReAct）

```
topics = 代码按账本现有部分生成（最多 3 题：如支座固定/滑动、桩 m 值、多柱盖梁）
if not topics:
    跳过，直接进 spec

for topic in topics:
    软数字类：墓碑已扫过图仍缺 → 才搜规范；未扫过的补识已在 check/needSupplement
    开关/提示类：组该题规范问句
    search_knowledge 1 次（内部纠正 ≤2 次混搜）+ 按指针补发 JPEG/表 HTML
    LLM 把短文/表填进 codeOverlay 白名单字段；无命中则该题走代码缺省 + notices

spec 只读白名单；非法字段丢弃
codeOverlay 不写账本、不写 LTM；notices 随响应给 Spring 进时间线/gaps
```

**不允许：** 改变 `check` 硬缺口结论；出任务卡；第 4 次对外检索；把估算写进账本；搜 `manual`/`case`；输出节点/杆/Link；覆盖账本已有尺寸。

#### 已落地（2026-09-04）：建模 LTM 注入 + RAG 补发

- **建模 LTM：** Spring 建模 HTTP 加与问询同一套 `ltm` 只读索引（最近 20 条，`ltmOf`）。`check` / `spec` / `build` **不读** LTM（几何只认账本）。`consult` 不得把 LTM 当规范依据，也不得把过程教训写进规范问句。咨询估算仍不写 LTM。
- **补发补给谁：** **问询 + 建模**。识图不加 RAG、不补发规范 JPEG/表 HTML。配额：JPEG≤4、表 HTML 实现默认 8000 字（先减旧对话，不减账本）。
- **补发时机：** 同一次 Python HTTP 内，`search_knowledge` 回待补发 `{documentId, refId}` 后立刻组 JPEG/表 HTML。路径只来自 Spring 注入的文献磁盘指针（`knowledgeScope` 带 sha/路径），Python **不扫** `data/knowledge/`。问询：补发进 `compose` 前当次 VL。建模：补发只进 `consult` 填 `codeOverlay`，不进 `spec`/`build`、不写账本。
- **Spring 超时：** 问询/识图读超时 600 秒；建模单独 `app.python-agent-modeling-timeout-seconds=900`（最多 3 次检索 + SAP COM）。
- **notices：** 随建模响应给 Spring，写入模型版本 note 与任务时间线；不写账本。
- **consult 把握不足（2026-09-04 联调）：** 某题 `search_knowledge` 带 notice「检索把握不足」时，只把 notice 写入覆盖层，**不合并**该题抽出的 `overrides`/`flags`，避免错书或低把握命中误改支座/刚构。

### 问询图（2026-09-04：外部工作流 + 局部 ReAct 已接线）

**现状代码：** `gate → react → compose → finish`。`react` 白名单仅 `search_knowledge`（每枪最多 1 次）；含 `compose` 在内 LLM 最多 3 轮；出卡只允许 `compose` 终态。Spring 注入账本、图纸文件清单、本会话 STM（近 8 轮 + 摘要）、工种 STM 全文、页地图索引、LTM 索引、已提交 taskId、**`knowledgeScope`**。不写账本 / 页地图 / LTM / 工种 STM；不调其它图。检索命中不写问询 STM。

**已锁并落地（2026-09-03/04，数字同日拍板）：**

- 问询 **进入** `search_knowledge` 白名单（识图不加；建模见「建模图」规范覆盖层）。
- 规范按需检索，不每次 HTTP 自动塞条文。
- 出卡规则不变：最多 1 张 `drawing_supplement`；Spring 校验后 `proposed`；人同意才跑。问询不能出建模/全册识图卡。
- 只读通道不变：账本为准；STM / LTM / 页地图索引当草稿或索引。账本、各 STM、LTM **只读注入，不做「读记忆」工具**。
- 检索命中与改写句 **不写** 问询 STM / LTM / 账本。补发 JPEG/表 HTML 已接线：同 HTTP 内组包，进 `compose` VL。
- 高风险不进白名单：写账本、调识图/建模图、点名某页 VL（通路 A 仍不做）、自带 `documentId[]`。

Spring 每次问询 HTTP 已加 `knowledgeScope`（启用∩已嵌入∩`code` 的 id、文献名、文献磁盘根）。启用集为空时工具只提示、不降级搜总库。

#### 一层：项目外到图内（Spring 写死，不是 ReAct）

```
浏览器发消息
  → Spring 组只读注入包（账本 / files / 工种 STM 全文（轻量墓碑）/ 页地图索引 / LTM 索引
       / 本会话近 8 轮 + 摘要 / submittedTaskIds / knowledgeScope / 本轮 message）
  → HTTP POST Python /v1/agents/inquiry
  → 问询图（下表四节点）
  → 只回 JSON { ok, reply, taskCard? }
  → Spring 落 inquiry_message；合法卡落 proposed；STM 只滚窗口，不写检索命中
  → 人同意后 Spring 才开识图 HTTP（问询图已结束，不转接）
```

#### 二层：问询图外部工作流（LangGraph 节点写死）

| 节点 | 谁做主 | 做什么 | 失败/出口 |
| --- | --- | --- | --- |
| `gate` | 代码 | 密钥、空消息 | 不通过 → `finish` |
| `react` | **局部 ReAct**（见下） | 模型在白名单里短循环：要不要搜规范 | 轮次/工具用尽或已有可交卷内容 → `compose` |
| `compose` | 代码逼终态 | 必须产出 `reply`；可选 1 张 `taskCard`。若 `react` 没交卷，再逼一枪且 **禁工具** | 仍无 `reply` → 失败 JSON |
| `finish` | 代码 | 只回 JSON，不写库 | 结束本次 HTTP |

`compose` **不是** ReAct 的又一轮自由行动：它只收口。出卡只允许出现在这一枪的 JSON，中途节点不落 `proposed`。

#### 三层：`react` 内的局部 ReAct（千问有限自主；数字已锁）

这里才是「自主」：模型看见注入包 + 本轮问题，自己决定调不调工具。不是整条问询链通用 ReAct。

| 约束 | 已锁值 | 说明 |
| --- | --- | --- |
| 白名单 | **仅** `search_knowledge` | 模型只填 `query`；文献范围来自注入的 `knowledgeScope`，禁止自带 `documentId[]` / 条号过滤入参 |
| 对外工具次数 | 每枪最多 **1** | 寒暄 / 只问本桥账本数字：**0 次**，禁止空转检索 |
| LLM 轮次 | 最多 **3** | 典型：判断 →（可选）检索 → 作答。含工具回传后的续轮 |
| 出卡 | **禁止**出现在 ReAct 中途 | 不是工具；只允许 `compose` 终态 JSON |
| 命中寿命 | 当次 State | 不写问询 STM / LTM / 账本；`reply` 可引用规范号+条号 |
| notice | 「检索把握不足」须写进 `reply` | 不要装成已经核对过 |

局部 ReAct 内部步序（代码封顶，模型选岔）：

1. 第 1 轮 LLM：看注入包。本桥数字已在账本则直接准备作答；问限值/构造/条文才调 `search_knowledge`。
2. 若调工具：进入 **工具内部固定小循环**（不是问询 ReAct，见「自我纠正」）：搜 → 双层评估 → 不通过则改写再搜；对外仍算 1 次工具、内部最多 2 次混搜。改写句不回给问询图。
3. 第 2～3 轮 LLM：带着短摘录组织结论。仍不得再调第二次检索，也不得出卡。
4. 交出可给 `compose` 用的内容（或轮次用尽，由 `compose` 禁工具收口）。

#### 四层：一次 `search_knowledge` 内部（已落地包装；问询图看不见改写）

与问询局部 ReAct **分开计层**：问询只看到一次工具结果。

1. 硬过滤：`document_id ∈ knowledgeScope` 且 `category=code`。启用集空 → 提示，不改写。
2. 问句 `text-embedding-v4` `dense&sparse` `text_type=query` → Milvus `hybrid_search` → dense COSINE + sparse IP → RRF。
3. 组包 10 条（图≤2、表≤2，空位补给条文）。工具先回短 `body` + 出处 + 待补发 `{documentId, refId}`；图内立刻按注入路径补发 JPEG/表 HTML。
4. 自我纠正：最多 1 次改写、共 2 次混搜。两次都不过 → 较好一组 + notice「检索把握不足」。

#### 典型路径（便于对照）

- **寒暄 / 只问本桥跨径：** `gate → react（0 次工具、1 轮 LLM）→ compose（reply，taskCard=null）→ finish`。
- **问规范限值：** `gate → react（1 次 search_knowledge + 2～3 轮 LLM）→ compose（reply 引用条号；若 notice 则写进回复）→ finish`。
- **账本缺结构量且页地图显示图上可能有：** `gate → react（通常不搜规范）→ compose（reply + 1 张 drawing_supplement）→ finish` → Spring `proposed`，人同意后才识图。

问询 **不能** 在 ReAct 里「先搜规范再偷偷出建模卡」或「调识图图」。要干活只出补充识别卡。

### 任务卡与干活页（2026-08-28，拍板补于 08-29）

项目内「看 / 问 / 干」分开：概览与图纸负责看和手改；问询负责问答与 **提议卡**；导航 **「任务」**（原「建模任务」）是干活页，负责起草、列出待同意卡、人同意后执行、时间线、识图写入确认。

- **任务卡形态：** 上半是 **结构化范围**（必填：工种、文件、页 kind / Skill 分组、可选联或墩），下半是 **本轮指令文本框**（选填，建议上限约 500 字）。问询带出的卡另有只读「提议原因」。同意前允许人改范围和指令。不为每个 Agent 建会话 STM。
- **问询默认只能出「补充识别」卡**（继承页地图）。全册重新识别不由问询提议，只走图纸页一键或任务页显式选择全册。
- **图纸页：** 「识别本项目」仍 **一键直接开跑** 全册识图。页上须提示：细抽 / 按指令重识请到 **任务** 页起草，或在问询里让助手出卡后同意。
- **两种确认不得混用：**
  - **同意任务卡**：是否开跑这一轮。未同意不调 Python。
  - **确认写入账本**：识图与已有值冲突时（现有 `waiting` + `proposal_json`）。未确认不覆盖账本。
- **问询出卡：** Python 只返回结构化 `taskCard`（最多 1 张）；**Spring 校验后**插入 `proposed`（非法 fileId、目录外字段、抽钢筋、改幅面/项目名、全册重识等直接丢弃）。问询 STM 不存页地图、不把指令全文当工程记忆。
- **人同意：** 立刻将卡改为 `running` 并 **跳到任务页**；Spring **后台** 调对应工种 HTTP（补充识别继承页地图）。本轮指令可空；若有则进注入包，**不得**覆盖空写/冲突/拒绝幅面等写入规则。问询会话追加一条 `role=event` 展示记录（含 taskId 链接），**不进**问询窗口喂模型；STM 只记已提交 `taskId`。未同意的卡 STM 不记。
- **人也可在任务页手写卡**（不经过问询），同一套校验；手写工种枚举：`drawing_full` / `drawing_supplement` / `modeling` / `analysis`。问询 **只能** 出 `drawing_supplement`，不能出建模卡。建模只在任务页起草。同意建模后后台跑 Python `check`/`spec`/`build`：硬缺口自动子识图，齐了则本机 SAP 存 `.sdb` 并登记模型页。分析卡第一版只记时间线，**本阶段不考虑分析**（不 RunAnalysis、模型页不展示周期/质量参与）。
- **待同意分组：** 任务页顶部单独列出 `proposed`，超过 5 张折起。问询里未点同意的卡留在该条回复下。
- **拒绝任务卡：** 状态 `rejected`，不跑 Agent、不改账本；问询对应 event 标已拒绝。
- **进行中不可取消**（第一版）：不能从卡片停 Python。
- **第一版不做问询点名某页 VL**（通路 A）。要结构数字一律走补充识别任务卡。
- 同一项目干活链：`running` 与 `queued` 的识图/建模都占链（父建模 + 自动子识图除外）。跨项目容量见「资源队列」。
- `kind` 收成上述枚举。旧数据「图纸识别」视同 `drawing_full`。

### 识图闭环

- 图纸页「识别本项目」→ Spring 直接建可执行任务（类型 **全册识图** `drawing_full`），过程进 `model_task_event`。细抽与按指令重识不在此按钮，须提示去 **任务** 页或问询出卡。
- Python 识图 Agent（`python_agent`，`qwen3-vl-plus`）经 HTTP 接入；**能确定读到的字段**由 Spring 写入 MySQL。写库、冲突确认仍只发生在 Spring。
- 第一版只把 **PDF** 送给 Python；DWG/DXF 只在时间线记「本步不识 CAD」。每轮 Python 细看一份主 PDF；其它项目图纸只出现在目录里。不够则 `needFiles`，Spring 同任务续调（额外最多 2 轮）。被索取的文件若还没有页地图，允许本轮对该份粗看+细看。
- **图纸回调（2026-08-29）：** Python 不得直连图纸库。目录由 Spring 注入；非法 fileId / 非本项目 / CAD / 已打开过 / 只抽钢筋 → 丢弃并记时间线。仍缺则用已有摘录走空写或冲突确认，时间线说明已达回调上限或目录没有更多 PDF。
- **已有字段且与识别结果不一致：不得默默覆盖。** 冲突则整份提案待确认。空字段才直接补：联跨径、空着的墩柱高、`girder_type`、`layout_type`、`material`、空着的 `code`、空着的 `region`、空着的参数袋项。不改幅面、项目名、通车日、规范策略。已有柱高且与提案不同 → 整份待确认。
- **信息不完整 = 完成但有缺口，不是失败。** 已看清的写入账本；空的保持空。概览提示使用者补什么：缺图纸（例如没有总布置）→ 去图纸页再传；模型没读出 → 问询说明或直接改概览。后续建模缺信息同样可让人补，或再调识图（继承页地图，不从头盲扫）。禁止为凑齐跨径而编造。仍算失败的只有：无 PDF、Python 挂掉、密钥/额度、模型调用失败。
- 概览每个可识图字段标明 **识图 / 手填**；识图结论与手填不一致时在页面上方提示。账本当前值给人改的那份为准，上次识图值存在 `project.field_meta`。
- 人确认后覆盖提案中给出的字段（联跨径来源 `drawing`）；放弃则任务失败、账本不动。
- 后续建模/分析若还缺几何：人已同意建模则 Spring 按 `needSupplement` 自动补充识别（见「建模与识图协同」）；人也可在任务页起草或问询出卡。Python 根据页地图挑页并扩扫相关类，禁止当没扫过，也禁止因无精确 kind 就放弃整份 PDF。



### 读图方式

- **本机把 PDF 渲染成页图，再送给视觉模型。** 不是把 `C:\...pdf` 当 URL 丢给云端。
- 目录、说明、图例仍是图纸的一部分，**初步扫描必须覆盖整份 PDF**，不能只看前若干页。页多时按约 12 页一批、低 DPI 粗看分类，再细看总布置/立面。
- 粗看目的：给每一页（或每一批页）分类——如目录/说明、总布置、立面、数量表、钢筋、大样、其它——得到「有用页地图」，再挑总布置/立面等做本轮细抽。数量表不是废页，只是本轮不拿来填跨径。
- **实现约束（已定）：** 全册分批粗看，不要一次把 50+ 页塞进一个 VL 请求。例如每批约 12 页、更低 DPI，同一次 Spring HTTP 内合并成完整页地图。图签「第 x 页共 y 页」与 PDF 页序不是一回事。
- 页少（整本很短）可直接细看；页多：全册分批粗看分类 → 细看说明 + 总布置/立面 + **横断、墩柱、基础、垫石**（最多 `MAX_FULL_FINE_PAGES`，每批 4 页）。只看说明+总布置会漏柱高和桩径。补充识别是另一次 Spring→Python HTTP，带上 MySQL 里的页地图。
- 不把整份 200MB PDF 原件当默认输入。



### 模型与框架（已定）

- 识图主力：**阿里云百炼通义千问 VL**（API Key，OpenAI 兼容）。Key 只放 Python 环境变量，不进前端、不进 Spring、不进 git。
- 无 Key：任务失败并写时间线，**禁止退回占位假 JSON**。
- 缺跨径或某字段看不清：**部分写入 + 记记忆**，不因缺项整单失败（2026-08-26 改口；旧规则「整单失败不写半份账本」作废）。
- 多份 PDF 结论不一致：与账本冲突同一套——停下整份确认，不默默拼接。
- **Python 运行时：LangGraph 1.2.x**（一种 Agent 一张 StateGraph；工具放 `bridge_agent/tools`）。Python **3.12**。**不落记忆盘、不连业务库**：STM/LTM/页地图/参数袋由 Spring 写入 MySQL，请求时注入、响应里交回增量。Spring **不上** LangChain4j / Spring AI / MCP。图与图不私聊；下一步由 Spring 发新 HTTP。
- 备选对照：智谱视觉；有海外条件可用 GPT-4o 对照。DeepSeek 视觉会压到约 800px，不适合当布置图主力。



### 抗震建模口径（2026-08-30）

规范：**《公路桥梁抗震性能评价细则》JTG/T 2231-02—2021**。知识库未接前不换其它抗震规范、不引用其它条文号。2231-02 **没有**「斜交角大于 xx° 才按斜桥建」的单一限值：图上有斜交则按斜桥布置；有平面曲线则按弯桥布点。图上未标斜交/平曲线 → **正交直桥**，不为斜弯补图；图上明显斜/弯但读不出角度或半径 → 硬缺口要总布置。细则 5.2.2 的扭转/反应谱属 **分析步**，本任务不做。

几何与构件（Skill：`python_agent/bridge_agent/skills/modeling/`）：

- 平面梁格（每片主梁一根杆 + 虚横梁），不是脊骨梁。纵坡本阶段不做（主梁同一 Z）。单位 kN·m。支座每只一个 Linear Link，禁止并联合成、禁止 Wen。
- 划分：主梁 / 虚横梁 / 横隔梁 **≤3 m**；墩柱 / 盖梁 / 系梁 / 承台 / 桩 **≤2 m**。土弹簧与桩划分点对齐。
- 联间缝宽：图上优先；没有则 **0.08 m**，note 标 `estimated`。
- 虚横梁按梁格板条（Hambly 常用）：代表宽 \(b=\) 虚横梁间距（与主梁划分一致，约 3 m），板厚 \(h=\) `deckSlabThickM` 否则砼铺装厚；\(I=bh^3/12\)，\(J=bh^3/6\)；不计自重。缺板厚要横断面，不编厚。
- 无二期恒载：**软缺口**，只标注缺失，仍可保存模型；禁止当硬接口、禁止编标准沥青+防撞墙。
- 土层不全：该墩可 **借用相邻墩或相近区域** 已入账分层，note 写 `soilBorrowedFrom=…`。有土名无 m：按 **JTG 3363—2019 表 L.0.2-1** 取区间中值并标 `estimated`。禁止地区经验口头 m。全桥无土则不加弹簧，不卡住开建。识图阶段如实抽、不擅自抄邻墩。
- 墩身无砼标号：允许暂用主梁标号，note `pierMaterialFromGirder`。
- 支座第一版 **只建板式**（GJZ / GJZF），按 SAP **Link（连接单元）** 建，**不建几何截面**。因此 `bearingSize` **不是**开建硬条件。竖向 \(K_z=1.0\times10^8\,\mathrm{kN/m}\)；固定向 \(k=GA/\sum t\)（无平面尺寸则用约定剪切面积并标 `estimated`）；缺 G 用 **1.0 MPa** 并标 `estimated`；滑动向 **1.0 kN/m**（`slideApproxZero`）。硬条件仍要：板式类型、布置（每片几块/排数）、橡胶层总厚。盆式/球钢硬缺口。
- **刚构**（墩梁固结）：不建该处支座，柱顶/盖梁与主梁 Body 固结。
- SAP 版本：**项目级默认**，任务卡可改（接口待接）；建造器用请求里的 ProgID。
- `.sdb` **先保存在本机** `data/models/{projectId}/`，模型页可删旧版；不自动只留 N 份。COM：`InitializeNewModel` 后必须 `File.NewBlank()`；对象名走 **UserName**；支座 `SetLinear`；Body / 桩底约束 / 土弹簧 / 主梁插入点顶缘 / 二期线质量按切片落地。
- 本任务 **不 RunAnalysis**。保存后导出 `previewJson` 给模型页线框。

识图目录（`drawing_parse/catalog.md`）已加细。细抽注入 `piers.md`：柱高/柱数可读总体布置尺寸、表格、构造图或标高差，**不是**只能做盖梁底减承台顶。支座注入 `bearings.md`：禁止把伸缩缝当支座，禁止用梁距×桥宽反推支座个数。全册细抽覆盖横断/墩柱/基础/垫石，不把 T 梁、5×30 当缺省。

模型版本：表 `project_sap_model`，磁盘 `data/models/{projectId}/`。模型页列出对应 SAP 版本、线框预览、下载、删除。删项目清模型盘。建模图未接时列表可空。



### Agent 记忆（规划已定；识图终态与问询组包已落地）

见下文「已实现 / 记忆」与「下一步 / Agent 记忆」。问询表不是记忆；账本是已确认事实。新跑次页地图在 `drawing_page_map`，不进 Python 磁盘。

**记忆边界（2026-08-27）**


| 是什么                      | 隔离                         | 谁用                            | 落哪（规划）   |
| ------------------------ | -------------------------- | ----------------------------- | -------- |
| 账本固定列 + `field_meta`     | 每项目                        | 概览已有字段的值与来源；各 Agent 只读投影      | MySQL    |
| `project_param`          | 每项目多行，一行一 key              | 仅扩展结构量，与 `field_meta` 所管含义不重复 | MySQL    |
| 工种 STM `agent_stm`       | 项目 × agent_kind 一行，只放索引/墓碑 | 该工种读写提交；问询只读                  | MySQL    |
| 识图页地图 `drawing_page_map` | 项目 × fileId                | 识图提交；问询/补充识别只读                | MySQL    |
| 问询 STM `inquiry_stm`     | 每条对话一行                     | 仅该会话问询                        | MySQL    |
| 问询消息                     | 每条对话多行                     | 给人看，不整段喂模型                    | MySQL 已有 |
| LTM `project_ltm`        | 每项目一行                      | 问询只读；Spring 任务终态写             | MySQL    |
| 时间线                      | 每任务多行                      | 给人看                           | MySQL 已有 |
| 请求内 Checkpoint           | 一次 HTTP                    | 仅本趟图                          | 进程内存，不落盘 |


删项目：上表凡挂项目的记忆 + 图纸磁盘 + SAP 模型盘一并清；公司知识总库不删。删图纸：只删该 fileId 的页地图与索引项，不清账本。

**落盘一律 Spring / MySQL（2026-08-27 改口）**

- Python **只跑 Agent**（调千问、拆 PDF），**不存储记忆**（不写页地图文件、不写 Sqlite/业务库、不维护 Store 当真相）。便于以后 Spring 侧迁 MongoDB：换 Spring 仓储即可，Python 契约仍是「注入上下文 + 返回增量」。
- STM（含识图页地图与 `extracts`）、问询 STM、LTM、参数袋、问询消息、时间线、任务草稿若需跨进程保留，均由 **Spring 写入 MySQL**。删项目/删图纸只删库（及图纸磁盘），不必再调 Python 清记忆文件。
- 每次 HTTP：Spring 注入账本投影 + 本任务所需 STM/LTM（见下「注入包」）；Python 返回要提交的增量；Spring 按提交规则落库。
- **同一次请求内** LangGraph 可用进程内 Checkpointer（跑完即弃），这不算落盘。进程崩溃则任务失败，由 Spring 重开任务（注入仍在 MySQL 的已提交 STM），不在 Python 里做跨重启磁带。若以后要「图跑到一半续跑」，由 Spring 提供存取 State 的接口，数据仍在 MySQL，不在 Python 本地。

**MySQL 表怎么拆（2026-08-27）**

- `project_param`：一行一 key（项目内唯一），已确认结构量。
- `project_ltm`：每项目一行，过程索引 + 教训（JSON）。
- `agent_stm`：`(project_id, agent_kind)` 一行，**只放索引/墓碑/短摘要**（哪些 fileId、有无总布置、拒绝的提案 id），**禁止**把全书页地图或 JPEG 塞进这一行。
- `drawing_page_map`：`(project_id, file_id)` 一行，`pages` / `extracts` / `gaps`。删图纸删此行。这才是识图体积所在，仍然很小（分类+摘录，无像素）。
- `inquiry_stm`：每个 `inquiry_thread` 一行（摘要 + 最近句的消息 id）。
- 已有 `inquiry_message`、`model_task_event` 不变。不为 Python Checkpoint 建表。

**账本投影进各 Agent 上下文（2026-08-27）**

- 各工种 Agent（识图 / 建模 / 问询 / 以后的分析）**都要**看到账本里的模型信息，通道是 Spring **每一次 HTTP** 带上的只读快照，不是各 STM 里再存一份跨径/主梁。
- 投影标明「账本已确认」。未确认的识图提案（`proposal_json`）不混进这份投影。
- 寿命仅本趟；用完不写回 STM。下次概览改了，下一枪自动是新值。
- 第一版可带整份项目概览（名称、幅面、标号、跨径联、主梁、材料、地区、规范策略、`field_meta` 所管字段）+ `project_param` **的 key 与 label**，供识图对固定列和袋做同义去重。
- 与 STM/LTM 冲突时仍以账本为准。

**Spring→Python 注入包（2026-08-27）**

- 公共：`projectId`、`taskId` 或 `inquiryThreadId`、`agentKind`、账本只读投影（固定列 + `field_meta` 含义 + `project_param` 的 key/label）。不带未确认的 `proposal_json`。
- **识图 / 补充识别：** 本轮 `files[]`（绝对路径，一般一份）、`drawingCatalog`（本项目图纸元数据 + 页地图索引，无像素无路径）、`alreadyFetchedFileIds`、相关 `drawing_page_map`（补充识别或回调已有地图的文件带该份；全册首轮仍可重扫）。不够则响应 `needFiles`，Spring 同任务最多再发 2 枪。不带问询 STM、消息全文、时间线全文。
- **建模：** 账本投影、建模 `agent_stm`（补识次数与 `fileId`+`missingKeys` 墓碑）、图纸目录与页地图**指针**（供规则 `check` 判断有无专页）。可选带上一份 `project_sap_model` 的元数据指针（id/seq/路径/note），不带 `.sdb` 二进制、不带全书 `previewJson`。不带问询会话、竣工图 JPEG、识图 STM 全文。规格草稿不跨请求保存。每次出新版本从账本重建，不打开旧 `.sdb`。已加 `knowledgeScope` 与 `ltm` 只读索引（最近 20 条）。`check`/`spec`/`build` 不读 LTM。
- **问询：** 本会话 `inquiry_stm`（摘要 + 最近 8 轮）、LTM 索引、工种 STM 全文（内容本身是轻量墓碑/索引，不含结构尺寸；`drawing` 含 fileIds / hasLayoutPages / rejectedTaskIds，`modeling` 含 supplementCount / tombstones）。已加 `knowledgeScope`（启用∩已嵌入∩`code`，含文献根路径）。默认不带各 PDF 完整 `pages[]`；点名某页再带该份地图，像素由 Python 当次现渲。不带其它会话。
- 返回由 Spring 写库：识图给页地图增量/`extracts`/gaps/账本提案；问询给回复 + 可选 STM 更新（无页图）；建模给缺口（供 Spring 开补充识别）+ 建模 STM 增量。
- 组包后仍按约 70% 裁切：先减问询旧轮次和整本页表，**不减账本数字**。

**结构参数放哪（2026-08-27）**

- 未确认：发现它的工种侧草稿。识图为页地图里的结构化 `extracts`（值 + fileId + 页号），不把尺寸塞进 `agent_stm` 的 kind 行。建模 STM **不存**结构尺寸，只存过程墓碑；规格草稿仅当次 HTTP。
- 已确认：账本固定列或动态参数袋。
- LTM：只存过程索引 + 跨任务教训，**不存**跨径/墩高等结构尺寸（最多一句指针：摘录在哪个 fileId/页）。
- **LTM 写入（2026-08-27）：** 只由 **Spring** 在任务到达终态后写，Python 不直接改 LTM。问询回合 **不写** LTM。
  - 完成（含有缺口）：索引（Agent 种类、`taskId`、相关 `fileId`、完成/有缺口）；识图可加「已有页地图 / 有无总布置」指针，不抄页表。
  - 失败（无 PDF / 进程挂 / 密钥额度 / 调用失败）：只写教训（类型 + 短因），不写「已完成」。若半截地图已提交进识图 STM，可补「地图已部分提交」。
  - 人放弃识图提案：教训/墓碑指针；账本不动。
  - 人确认提案：参数进账本；LTM 最多「识图结论已入账」一句。
- **时间线**（`model_task_event`）是给人看的原纪录，按任务/项目分页读，不压缩、不整段喂模型，也不当 LTM 正文。

**展示记录 vs 给模型的记忆（2026-08-27）**

- 问询展示：`inquiry_message` 全文；问询 STM：摘要 + 最近窗口（正文可用消息 id 回表取，不复制全文进 STM）。
- **压缩（2026-08-27）：** 组装后的整包（系统提示 + 账本投影 + LTM 索引 + 工种 STM 索引/页地图指针 + 问询 STM）预估 token 超过当前模型输入上限的 **约 70%** 再压。N 与 70% 做成 **Spring 配置**，默认 **问询窗口 8 轮**（一轮 = 一问一答）。只把窗口之前的对话折进 `summary`；账本、参数袋、页地图、`extracts` 数字 **不压成散文**。工种 STM 仅过长时压过程叙述。识图无聊天轮次，不适用 N。问询看某一页的 JPEG 只在当次 VL 请求里，不写问询 STM。Spring 在发问询 HTTP 前组包裁切；写回 STM 仍只 Spring。不要每句都摘要。Checkpoint 不自动摘要。

**Checkpoint 与 State（2026-08-27）**

- Checkpoint 是 LangGraph 的线程落盘：同一 `thread_id` 再跑，恢复当时整份 **图 State**。它不会自动按 token 摘要。
- State 是图的工作记忆，**不是**千问自动看到的上下文。模型只看到节点这一次 `messages` 里放的内容。千问默认无会话，不会记住上一枪的图，除非本轮再把页图附上。
- 页图 JPEG、给人看的完整聊天 **不进 State / Checkpoint**。识图要记的是页号、`kind`、fileId/sha256、`fineRead`/`gaps` 等索引；像素用时从本机 PDF 重渲染。
- 膨胀来自胖 State + 逐步存档 + 不修剪，不是 MySQL 或 Checkpoint 机制本身。

**任务草稿 vs 工种短期记忆（方案 C，2026-08-27）**

- 产品上：一个项目、一种任务 Agent（识图 / 建模 / 以后的分析）**一份**跨任务短期记忆，存在 MySQL（`agent_stm` + 识图另有 `drawing_page_map`）。
- **请求内** LangGraph 可用临时 `thread_id`（建议带 `taskId`），只服务这一次 HTTP，跑完即弃。进程挂了 **不** 从 Python 磁带续跑，由 Spring 用已提交 STM 重开任务。
- 每次 HTTP 开头 Spring 注入已提交 STM；Python 返回增量；**有可复用过程结果才写入** MySQL（完成但有缺口要写页地图；额度中断前已粗看完的页也可以写）。真正失败不把残渣提交进工种 STM。
- **问询例外：** 一份对话一份记忆（`inquiry_stm` 跟 `inquiry_thread`）。会话按 **项目 + 用户** 私有；不读同项目其他用户的会话；超管也不能看别人的问询。
- **问询是独立 Agent，不调用、不转接其它图。** 读：账本投影、本项目其它工种已提交 STM、项目 LTM、本会话问询 STM；规范经 `search_knowledge` 按需检索（局部 ReAct 已接线）。不读其它问询会话。不写账本 / 页地图 / LTM / 工种 STM。需要干活时返回任务卡，由 Spring 落 `proposed`，**人同意后** Spring 再发识图/建模 HTTP。问询自己调 VL 看某一页（通路 A 不做），结果只进本会话 STM / 回复，不写页地图。用户口头尺寸要改模型，须经概览确认或走任务卡入账。未确认 STM 当草稿说明，冲突以账本为准。检索命中不写任何工程记忆。
- 同一项目干活链：识图/建模 `running` 或 `queued` 时不得再同意另一张（父建模 + 子识图除外）。开跑串行化：锁项目行 + 状态 CAS；派发再验链。

识图工种 STM（2026-08-27）：**主体是按文件的页地图 + 未确认的结构化摘录**，不另写识图聊天摘要。项目级只做索引（有哪些 `fileId`、哪份有总布置）。正文按文件拆，键 `projectId + fileId + sha256`。新文件或哈希变了才全册粗看。

**识图跑时 Skill（2026-08-27）**

- 给 **Python 识图 VL** 用，文件在 `python_agent/bridge_agent/skills/drawing_parse/`。按节点注入对应切片（粗看只带分类规则，细看带单位 + 本轮字段），**不是** Cursor Skill、不上 MCP。
- 第一版梁式桥。竣工图册常见：扫描件几乎无文字层、图签「第 x 页共 y 页」≠ PDF 页序、前部说明/数量表、总体布置靠后、左右幅/上下行并存。
- 本轮细看：`project_notes`（竣工说明/工程概况）+ `general_layout` / `elevation`，有位再带 **一页数量表**（只为护栏/铺装用量，不填跨径）。说明页常有最干净的跨径与材料，总布置对几何。
- 账本 6 项仍走现有 JSON 字段；联上墩柱走 `units[].supports`；其余结构量进 `extracts`（label/key/value/unit/页），**不抽钢筋明细**。地质 m/Cz 以图上或勘察页为准，禁止用经验值冒充读图。
- **墩柱高（已锁定）：** 盖梁底（或墩顶）至承台顶/桩顶；每根柱单独存进联表子表，不进参数袋。
- **二期恒载（已锁定）：** 本幅全宽每延米 kN/m；铺装可用厚×桥宽×一般重度估算（沥青 23～24、砼铺装 25 kN/m³）；**护栏优先用施工统计/数量表材料用量反算**，必须分清单幅还是左右幅合计（合计则 ÷2 给本幅）；`barrierKNPerM` 仍是本幅两侧合计；无数量也无截面时不要用「标准防撞墙」硬凑。`note` 标明 `estimated` 与单幅/双幅。
- 补充识别按页地图挑相关页再细看墩柱/基础/支座/横断面；精确 kind 没有或不够则扩扫大样/`other`/数量表等（跳过图纸目录页、钢筋、封面），最多 12 页分批细看。人同意 `drawing_supplement` 任务卡后执行，继承页地图。
- 不把已确认跨径等账本字段抄进 STM；每次由 Spring 投影只读带给 Python。
- 人放弃的识图提案在 STM 打墓碑（提案 id/哈希已拒绝），补充识别不得再当新发现。
- 问询 STM 禁止写入页地图。用户在问询里说的尺寸要改模型，必须经概览确认进账本。
- **删除项目图纸 → 同时删该** `fileId` **的识图记忆**（MySQL 页地图/`extracts`/索引/墓碑）。只删这一份。删文件 **不自动清空账本**。正在识别则取消任务。落地前现网若仍有 Python 页地图文件，迁库时一并废弃。
- **粗看页分类地图**：按文件存 **MySQL**（`drawing_page_map`），不进问询表。键带 `fileId` + 内容 `sha256`。旧的 `python_agent/memory/` 落盘文件已废弃删除。
- 建议结构（实现时可微调，字段含义已定）：

```json
{
  "projectId": 7,
  "fileId": 11,
  "sha256": "…",
  "totalPages": 57,
  "pages": [
    { "i": 0, "kind": "catalog" },
    { "i": 1, "kind": "notes" },
    { "i": 27, "kind": "general_layout", "codeHint": "K130+260" }
  ],
  "fineRead": [27],
  "gaps": ["spansM"],
  "extracts": [],
  "updatedAt": "…"
}
```

- `kind`：`catalog` 目录、`notes` 图例/附注汇编、`project_notes` 竣工说明/工程概况、`general_layout` 总体布置、`elevation` 独立立面、`site_plan` 桥位平面、`cross_section` 上部横断面、`quantity` 数量表、`rebar` 钢筋、`pier` 墩柱/盖梁构造、`foundation` 桩基/承台/扩大基础、`bearing` 支座垫石、`geology` 地质、`archive` 备考表/封面、`detail` 大样、`other`。本轮细看：`project_notes` + 总布置/立面。数量表、钢筋、桥位平面不拿来填跨径。建模 **不抽钢筋**。
- 分批粗看时每批只分类本批页号，合并进同一份 JSON，不要覆盖已分类的页。
- **本轮细抽过哪些页、抽出了什么、还缺什么** 写在 `fineRead` / `gaps`；未确认尺寸在 `extracts`（与 `pages` 并列）。
- 建模缺信息或使用者点「补充识别」：Spring 发补充抽取（`projectId`、`fileId`、需要的页 kind、可选指令）。Python **读这份地图挑页** 再细看，禁止当没扫过；精确 kind 没有则扩扫大样/`other` 等，不要 0 页收工。新 PDF（新 `fileId` 或哈希变了）才重新全册粗看。
- 缺口给人看的文案进任务时间线和概览提示；地图本身只在 `drawing_page_map`（规划），不进问询。



### 账本来源、缺口提示与冲突条（Spring / 前端）

与 Python 页地图分开：概览固定项的「识图还是手填」在 `project.field_meta`；扩展结构量在 `project_param`，两套不记同一含义。

- `project_unit.source` 已有 `drawing` / `cad` / `agent` / `manual`。项目级固定字段（标号、主梁、结构形式、材料、地区、跨径对照）只记在列 + `field_meta`：当前值来源、上次识图读到的值、本轮 `gaps` / `hasLayoutPages`。这些含义 **不得** 再出现在 `project_param`。
- 概览每项标注识图 / 手填，字段可直接改并保存。空着的项按缺口类型提示：
  - **缺图纸**：页地图里没有总布置/立面一类 → 请到图纸页补充上传后再识别。
  - **没读出**：地图里有相应页但字段仍空 → 请手改概览，或在问询说明；也可再点补充识别（继承页地图，走任务卡）。
- 上次识图值与当前手填值不一致：概览**上方条**提示（例如「跨径：识图 5×30m，当前手填 …」），不默默覆盖。当前账本值以使用者最后确认/手填为准，建模读账本。
- 建模步骤发现还缺字段：同一套提示（补图 / 手填或问询 / 带记忆的补充识别）。
- 项目可删：`DELETE /api/projects/{id}`。规划上记忆均在 MySQL，删除走库级联 + 图纸磁盘；公司总库不删。

---



## 已实现



### 技术栈

- 后端：Java 21、Spring Boot 4、MyBatis-Plus、Spring Web。Java 注释约定见 `.cursor/rules/java-comments.mdc`。
- 前端：Vue 3、Vite、TypeScript、Ant Design Vue。
- Python：3.12 + `python_agent` LangGraph **1.2.x** 多图（`drawing_parse` / `inquiry` 四节点 / `modeling` 六节点）。Checkpointer / Store / thread_id 已接到 compile。PyMuPDF 拆页 + 千问 VL / 文本。目录见 `python_agent/README.md`。启动用 `python_agent/.venv`，不要用系统 3.9。
- 向量写入与检索内核已接本机 Milvus；问询 / 建模已挂工具，识图不加。

### GitHub 公开准备

根目录 README / MIT LICENSE / SECURITY。Spring 密钥在本机 `application-local.properties`（示例文件可提交）。Python Key 只在 `.env`。Vite 默认 `127.0.0.1:5173`。远程名 BridgeAgent、public。推送需本机已装 Git。

### 库表

`sys_user`（含 `role_id` / `status` / `avatar_path`）、`sys_role`、`sys_project_member`、`sys_audit`、`project`（含 `field_meta`）、`project_unit`、`project_unit_support`、`project_unit_column`、`project_file`、`knowledge_document`、`project_knowledge`、`inquiry_thread`、`inquiry_message`（含 `event`）、`model_task`（含任务卡字段与 `proposal_json`）、`model_task_event`、`project_sap_model`。  
记忆表 DDL 已写入 `schema.sql` / `db/memory.sql`，实体与识图/问询写入已挂：`project_param`、`project_ltm`、`agent_stm`、`drawing_page_map`、`inquiry_stm`（含 `submitted_json`）。已有库执行过 `db/memory.sql`；联下墩柱补丁 `db/unit_supports.sql`；任务卡补丁 `db/task_cards.sql`；SAP 模型版本补丁 `db/sap_models.sql`；建模协同补丁 `db/modeling_collab.sql`（`parent_task_id`）；知识合并状态 `db/knowledge_merge.sql`；知识分割状态 `db/knowledge_split.sql`；登录账号补丁 `db/sys_user.sql`；角色与项目成员补丁 `db/rbac.sql`；权限审计补丁 `db/audit.sql`；作业链与问询主人补丁 `db/job_chain.sql`；资源队列 `queued` 补丁 `db/resource_queue.sql`；行级乐观锁补丁 `db/optimistic_lock.sql`（`project` / `project_param` / `knowledge_document.version`）。

### 登录

- `POST /api/auth/login`（放行）：BCrypt 核密，签发 HS256 JWT（8 小时，`ver` = 登录时新的 `token_version`）。同一账号再登录会 `token_version+1`，旧设备 401。停用账号提示「账号已停用」。
- `GET /api/auth/me`：拦截器验票后从用户+角色写入 `UserContext`，回昵称、三角色开关、`projectPerms` / `allProjectsOperate`、头像 URL。
- `PUT /api/auth/profile`、`PUT /api/auth/password`、`POST /api/auth/avatar`、`GET /api/auth/avatars/{userId}`。改密 / 停用 / 再登录 `token_version+1`。
- 其它 `/api` 须 Bearer；无票/废票 HTTP 401；无权限 HTTP 403。前端登录页、路由守卫、`localStorage` + 401 跳转、403 提示不清票。侧栏按权限显示系统管理与新建项目。

### 权限 RBAC

- 一人一角色；内置 `admin` / `knowledge` / `project` / `user`；自定义角色可组合 `super` / `knowledge` / `projectAdmin`。
- 项目层 `read` | `operate`（`sys_project_member`）。超管/项目管理员不写成员表、默认全部项目操作。
- 前端：系统管理（用户/角色/项目层权限/**操作审计**）、项目内藏写按钮、知识库写按钮按 `canWriteKnowledge`。只读可问询与起草任务卡，不能同意/开跑。
- 演示账号密码 `1234`：见根目录 README 权限表；`user` / `reader` 种子绑项目 `id=11`。

### 权限审计

- 表 `sys_audit`。登录成功/失败、改密、重置密码、改昵称/头像、用户与角色变更、项目成员分配/移除。
- `GET /api/admin/audits` 仅超管。系统管理「操作审计」只读页。写失败不挡主业务。
- 不记验票、干活、建删项目、知识文件。

### 作业链

- `model_task.origin` / `created_by_*` / `agreed_by_*`；`model_task_event.actor_*`。图纸一键、任务页起草、问询出卡、建模自动补识分别打标。
- 任务页展示来源与开跑人；时间线前缀操作者。有项目 read 即可看。

### 问询私有

- `inquiry_thread.user_id`。列表/详情/发消息/删除均校验主人。删除只需项目 read。旧 `user_id` 空行不展示。

### 资源队列

- 三车道独立线程池（识图 2 / 知识 2 / 建模 1，救急 0，池内队列 5）。同意或点四步先 `queued`，有空位再派进池。
- 启动回收孤儿 `running`：不自动重跑；人再同意或再点四步。后台识图/建模若读到仍是 `queued` 会自行 CAS 成 `running`，避免静默 return 后永远卡在进行中。
- `GET /api/resource-queue`。侧栏「资源队列」。知识车道全登录可见；识图/建模按项目权限。

### 并发控制

- `project` / `project_param` / `knowledge_document` 有 `version`。概览 `PUT` 与联/参数袋必须带当前 `project.version`；冲突返回业务错误「请刷新后再保存」，不覆盖。
- 知识四步开始仍是状态列 CAS；解析/合并/分割回写只改状态列。
- 识图写入认落库当下 version，冲突重试一次。
- 问询不加作业锁。建模 `queued`/`running` 时概览提示方案 A 快照。启用集约 4 秒轮询。
- 干活链：同意 / 图纸一键先锁 `project` 行再占链；`proposed→queued`、`queued→running` 状态 CAS。同一项目两人同时开两张卡，后提交的会收到「已有识图或建模进行中」。派发跳过占链冲突的卡，避免识图双开。

### Spring 接口（均经 `/api`）

- 登录：`POST /auth/login`、`GET /auth/me`、改资料/密码/头像。
- 系统管理：`/api/admin/users`、`/api/admin/roles`、`/api/admin/projects/{id}/members`、`GET /api/admin/audits`。项目内只读 `GET /api/projects/{id}/members`。
- 项目：CRUD（含删除）、`PUT /projects/{id}/units`（跨径+墩柱，带 `version`）、`PUT /projects/{id}/params`（扩展参数袋，带 `project.version`）。`GET /projects/{id}` 带联下 supports、params 与 `version`。概览手改走 `PUT /projects/{id}`（来源 `manual`，必带 `version`）；识图写入走 `updateFromDrawing`（来源 `drawing`）。列表按可见项目过滤；写接口在 Controller 鉴权。
- 图纸：上传/列表（详情里）/下载/删除。
- 知识总库：`/api/knowledge/documents` 上传/列表/下载/删除/解析（`POST /{id}/parse`）/合并（`POST /{id}/merge`）/分割（`POST /{id}/split`）/嵌入（`POST /{id}/embed`）。解析写 `{sha256}/parse/`，合并写 `{sha256}/merge/`，分割写 `{sha256}/split/`，嵌入写本机 Milvus，互不覆盖。旧平铺 PDF/sidecar 双读不搬家。
- 项目启用：`GET/PUT/DELETE /projects/{id}/knowledge/{docId}`。试检索 `POST /projects/{id}/knowledge/search`（启用∩已嵌入∩规范）。规范号 `familyCode` 分组，主标签最新年版。
- 问询：`/projects/{projectId}/inquiries` 新建/列表/详情/发消息/删除（**仅当前用户自己的会话**；删会话只需 read）。发消息时 Spring 组注入包（账本投影 + 本会话 STM 窗口 + 工种 STM / 页地图索引 + LTM 索引 + 已提交 taskId + **knowledgeScope**），`POST /v1/agents/inquiry`（`gate/react/compose/finish`，可调 1 次 `search_knowledge` 并补发图/表），回复写入 `inquiry_message`，窗口写入 `inquiry_stm`。只读，不改账本 / 页地图 / LTM。可返回最多 1 张补充识别卡，Spring 校验后落 **项目级** `proposed`。检索命中不写 STM。
- 任务：列表、起草卡 `POST /tasks/cards`、改卡、同意/拒绝、时间线。`kind` 枚举 `drawing_full` / `drawing_supplement` / `modeling` / `analysis`。同意后先 `queued` 再进对应车道；满员则等。建模硬缺口自动插 `drawing_supplement`（`parent_task_id`）。
- 识图：`POST /projects/{id}/tasks/parse` 进入识图车道（可能先排队）；`.../tasks/{taskId}/parse/confirm` | `reject`。
- 资源队列：`GET /api/resource-queue`。
- SAP 模型版本：`GET /projects/{id}/models`、详情、下载、删除。建模成功后 Spring `register` 本机 `.sdb`。删项目清 `data/models/{projectId}/`。



### 记忆（Spring / MySQL）

- 识图成功或冲突待确认：页地图写入 `drawing_page_map`，识图 `agent_stm` 更新 fileId / 有无总布置，`project_ltm` 追加完成索引。失败只写 LTM 教训；放弃提案打 `rejectedTaskIds` 墓碑。
- 问询组包：最近 **8 轮**（`app.memory.inquiry-window-turns`）原文 + 更早轮次折进 `inquiry_stm.summary`；整包按约 **70%** 字符估算裁切（先减页索引/LTM/旧对话，不减账本数字）。`app.memory.context-budget-ratio`。默认不塞全书 `pages[]`。
- Python 不再写 `python_agent/memory/` 页地图文件。



### 前端页面（真接口已接的）

登录页（用户名/密码，错因分开提示）、工作台（含删除项目，仅项目管理员/超管）、新建项目（同上）、概览（只读账号不可保存；保存带 version；建模进行中提示快照边界）、图纸（只读无上传/识别/删除）、知识库（仅 knowledge/超管可上传与四步）、知识范围（只读可试检索，开关需 operate；启用集约 4 秒轮询）、问询（**仅自己的会话**；可展示待同意卡；只读不能同意；主人可删自己的会话）、任务（作业链：来源/开跑人/时间线操作者；只读可起草，不能同意/开跑/确认写入）、模型版本（只读无删除）、资源队列（三车道占用）、系统管理（含操作审计）、项目成员只读表。侧栏显示登录昵称，可改资料/头像/自己的密码并退出。

验证记录仍为壳子或 mock，**未接真账本**。

### 识图

- 无 PDF / Python 挂掉：任务 `failed` + 时间线说明。找不到文件时时间线会带上 Spring 解析出的绝对路径；Python 若仍打不开会写明收到的字段和路径。
- 真识别：页少直接细看；页多则同请求内 **全册分批粗看**（约每批 12 页）再细看说明 + 总布置/立面 + 横断/墩柱/基础/垫石（最多 12 页分批）。分类 kind 见规划。页地图由 Python 随响应返回（含 `extracts`），Spring 写入 `drawing_page_map`。识图 HTTP 带账本投影（含联/墩柱/参数袋）。
- 缺跨径或其它字段：任务 `done`，能读到的写入账本，缺口进时间线与 `field_meta`。真正失败只保留无 PDF / Python 挂掉 / 密钥额度 / 调用失败。
- 可写入的项目字段：跨径联、墩柱高、主梁、结构形式、材料、标号（仅账本为空时）、地区（仅空时）、参数袋空项（extracts 按 key/label 路由）。
- 额度或密钥失败：时间线与图纸页弹窗出现 `【百炼额度】` 或 `【百炼密钥】`。
- 读超时 600 秒。每轮注入 `drawingCatalog`（无路径）；Agent 可返回 `needFiles`，Spring 校验后同任务续调其它 PDF（额外最多 2 轮）。中间轮先写该 fileId 页地图，全部轮次结束后再空写或冲突确认。每一枪仍只细看一份主 PDF。



### 问询 Agent

- 页面发消息 → Spring 注入（含 `knowledgeScope`）→ Python `inquiry` 图 `gate → react → compose → finish` → 助手回复进会话。
- 规则：以账本为准；STM/LTM 当草稿或过程索引；不编造尺寸；不能替使用者直接启动识图；最多提议 1 张补充识别卡，人同意后 Spring 后台执行。
- 同意后会话追加 `role=event` 展示记录；STM 只记已提交 `taskId`。未同意的卡 STM 不记。
- **已接线：** 局部 ReAct 仅 `search_knowledge`（每枪 1 次）/ 含 compose 最多 3 轮 LLM / 出卡仅终态；检索命中不写 STM；补发 JPEG/表 HTML 进 compose VL。见「问询图」。
- 未做：点名某页再 VL（通路 A）；精确 tokenizer。

### 建模 Agent

- 人同意建模卡 → Spring 注入（账本 / 目录指针 / 建模 STM / sap 路径 / **knowledgeScope** / **ltm**）→ Python `gate → check → consult → spec → build → finish`。
- 硬缺口返回 `needSupplement`，Spring 自动子识图最多 4 次。软缺口与 consult `notices` 进模型版本 note 与时间线，不挡开建、不写账本。
- `consult` 代码组题最多 3 次检索 + 补发，产出 `codeOverlay`；COM/`build` 不进模型可选清单。
- 读超时单独 900 秒。

---



## 下一步



### 1. 真识图（主路径已按新规则落地）

- 已接 `qwen3-vl-plus`、全册分批粗看、页地图入 MySQL、600 秒超时、部分写入、概览来源/冲突/缺口、项目删除。
- 识图跑时 Skill 已接入粗看分类与「说明+总布置」细抽（有位带数量表）；**参数目录已加细**（变截面、桥面板厚、垫石/桩位、按墩土层、缝/斜/弯），细抽与补充识别注入 `catalog.md`；墩柱进 `units.supports`，其余进 `extracts` 由 Spring 落袋。
- **建模跑时 Skill（2026-08-31）：** `python_agent/bridge_agent/skills/modeling/`。建模 **不把切片注入大模型**（与识图不同）；`check.py` / `spec.py` / `sap_com.py` 按切片写死。K130+260 正确模型只作案例：学盖梁/系梁/Body/土弹簧/插入点/分标号，不抄脊骨、纵坡、Wen。方案 A 编排已接。盖梁/斜弯精细几何仍按账本有则建。
- **待做：** 用 K130 这类竣工图册验收补充识别扩扫（桩径/支座不在精确 kind 页时）与多册回调。



### 2. 问询与任务卡（出卡、ReAct/RAG、补发已落地）

- 已做：问询返回最多 1 张 `taskCard`，Spring 校验后落 `proposed`；任务页起草/同意/拒绝/时间线；人同意后后台跑对应 Agent；图纸一键全册仍直接跑。问询四节点 + `search_knowledge` + `knowledgeScope` + 图/表补发已接线。
- **待做：** 点名某页 VL（通路 A）仍不做；精确 tokenizer 仍后置。用真项目问一句规范限值，核对 reply 是否带条号、notice 是否进回复。



### 3. Agent 记忆（识图终态与问询组包已落地）

记忆由 **Spring 写入 MySQL**，按 `projectId` 组织。Python 不落盘。不是 `inquiry_message` 当工程记忆。

与账本冲突时，**以人确认过的账本为准**。

**短期记忆（STM）**  
方案 **C**：工种 STM / 页地图 / 问询 STM 在 Spring MySQL。Checkpoint 仅请求内进程内存（MemorySaver），跨重启靠已提交 STM。

**长期记忆（LTM）**  
每项目一份；问询只读不写。识图、建模任务终态已写 `project_ltm`。建模 HTTP 已注入最近 20 条只读索引。时间线只做展示。

**待做：** 识图 **写入**袋时的口语近义去重（现仍 key/label 精确匹配）。建模 **读取**袋已按专业同义表遍历 key+label。

### 4. 知识 RAG（解析 / 合并 / 分割 / 嵌入 / 检索 / 问询与建模接线已接）

- 解析 / 合并 / 分割 / 嵌入 / 试检索见「已锁定 / 知识 RAG」。
- **向量库：** 写入、混搜 RRF、自我纠正包装已落地。问询 `react`、建模 `consult` 已挂工具；识图不加 RAG。分类不新增「企业要求」。
- **已做：** 问询 ReAct 接线；建模 `consult` + LTM/`knowledgeScope` 注入；补发 JPEG/表 HTML（实现默认 4 张 / 8000 字）。不建议再做 MySQL chunk 正文表。
- **建模 RAG：** 规范覆盖层 `codeOverlay`（overrides + flags + notices），不是按构件 ReAct。见「建模图」。

### 5. 更后能力

建模图已接 `gate/check/consult/spec/build/finish` 与 Spring 编排（含 `knowledgeScope`、LTM、notices）。项目级 `sapVersion` 任务卡改版本接口仍后做；验证、校准；DWG 识图（或 CAD 先导出 PDF）。盖梁/斜弯/群桩精细几何随账本加细。

### 6. 登录与权限（已接线）

见「已实现 / 登录」「已实现 / 权限 RBAC」「已实现 / 权限审计」「已实现 / 作业链」「已实现 / 问询私有」。菜单管理表不做。问询按用户私有。

作业链已落地。资源队列（三车道 + `queued` + 资源队列页）已接线。项目链（建删项）、知识系统链（文献创建删除）已命名、未做。

并发控制已接线。见「已锁定 / 并发控制」与「已实现 / 并发控制」（快照方案 A；问询不做作业级互斥）。

---



## 暂缓（未做、且近期不排）

Redis、MQ、MinIO、筛选知识范围的智能推荐、验证记录真表、在线图纸预览、DWG 识图、工作台个人问询、MCP、Cursor Skill 当运行时依赖、Spring AI / LangChain4j。抗震分析（反应谱/时程）本阶段不做。知识 PDF **解析 / 合并 / 分割 / 嵌入 / 检索 + 自我纠正包装**已落地。RAG 白名单：问询已接线；识图不加 RAG；建模 consult 已接线（`category=code`）。不新增「企业要求」分类。点名某页 VL（通路 A）不做。不做库内菜单树 / 一人多角色 / 按钮码权限表。项目链 / 知识系统链未做。审计不做导出、不做退出接口、不做当事人自助登录记录。作业链不做全公司总表、不做导出。

---



## 文档维护


| 发生了什么          | 改哪里               |
| -------------- | ----------------- |
| 新确认/改口一条规则     | 「已锁定」             |
| 功能已能在页面或接口用    | 从「下一步」挪到「已实现」     |
| 新的延后项或记忆/RAG 等 | 「下一步」或「暂缓」，并写清未实现 |
| 日期             | 文首「最近整理」          |


