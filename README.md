# BridgeAgent

本机桥梁工程助手：识图写入账本、按需检索规范、再编排建模（本机 SAP2000）。  
浏览器只调 Spring；Python 只跑 Agent；记忆与账本在 MySQL。

规划底稿（架构与已实现功能）：[`docs/roadmap.md`](docs/roadmap.md)。许可证：[MIT](LICENSE)。密钥约定：[SECURITY.md](SECURITY.md)。

## 架构（本机三进程）

| 进程 | 地址 | 说明 |
| --- | --- | --- |
| Spring `bridge_agent_demo` | http://127.0.0.1:8080 | 账本与全部 `/api` |
| Vite `web` | http://127.0.0.1:5173 | 前端；`/api` 代理到 8080 |
| Python `python_agent` | http://127.0.0.1:8001 | 只给本机 Spring 调，不要对局域网开放 |

另需本机 **MySQL 8**（库名 `bridge_agent`）、知识嵌入用 **Milvus**（默认 `127.0.0.1:19530`）。识图/问询/嵌入需要阿里云百炼 Key。知识 PDF 解析需要 Unstructured Key。建模需要本机已授权的 **SAP2000**（仓库不包含安装包）。

规范 PDF、竣工图册有版权，**不要**提交 `data/` 下的文件。

## 克隆后的本地配置（必做）

1. **Python 密钥**（勿提交 `.env`）

```text
cd python_agent
copy .env.example .env
```

用编辑器写入 `DASHSCOPE_API_KEY=`（以及可选的 `UNSTRUCTURED_API_KEY`）。不要把真实 Key 贴进 GitHub Issue。

2. **Spring 密钥**（勿提交 `application-local.properties`）

把 `bridge_agent_demo/src/main/resources/application-local.properties.example` 复制为同目录的 `application-local.properties`，填写 MySQL 密码和至少 32 位的 JWT 密钥。缺 JWT 时 Spring 会拒绝启动。

3. **数据库**

对本机 MySQL 执行 `bridge_agent_demo/src/main/resources/db/schema.sql`（新库）。已有库按 `docs/roadmap.md`「怎么跑」里的补丁顺序执行。

## 启动

三个终端，端口约定：Spring **8080**、Python **8001**、Vite **5173**。

```text
cd bridge_agent_demo
mvn -DskipTests spring-boot:run
```

```text
cd python_agent
py -3.12 -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python app.py
```

```text
cd web
npm install
npm run dev
```

浏览器打开 http://127.0.0.1:5173 。

**局域网（可选）：** 默认 Vite 只绑本机。若要让同一 WiFi 的同事打开前端：把 `web/vite.config.ts` 里 `server.host` 改成 `true`，防火墙放行 5173，用**这台电脑的 IPv4:5173**，不要暴露 8080/8001。

## 演示账号

种子密码均为 **`1234`**，仅供本机。用户名不区分大小写。不要用于公网。

| 登录名 | 昵称 | 角色 | 能做什么 |
| --- | --- | --- | --- |
| `root` | zmy | 超级管理员 `admin` | 系统管理、知识总库写、全部项目操作 |
| `kb` | 知识库管理员 | `knowledge` | 知识总库上传/四步；无项目管理员权 |
| `pm` | 项目管理员 | `project` | 全部项目操作与建删项；知识库只读 |
| `user` | 操作员 | `user` | 仅成员表里的项目；种子为项目 `id=11` 的 **operate** |
| `reader` | 只读 | `user` | 种子为项目 `id=11` 的 **read**（可问询/看任务，不能同意开跑） |

超管 / 项目管理员不写 `sys_project_member`。普通用户的项目层权限在该表：`read` 或 `operate`。一人一角色；同一账号同时只允许一处登录。

## 仓库里有什么、不要提交什么

- 提交：源码、`docs/`、`.cursor/rules`、空的 `.env.example` 与 `application-local.properties.example`。
- 不要提交：`.env`、`application-local.properties`、`data/`、`node_modules`、`.venv`、`target/`。

百炼 Key **不会**也不应出现在本仓库任何已跟踪文件中。若误提交，立刻轮换 Key，并视为已泄露。
