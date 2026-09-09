# 安全说明

本仓库是 **源码公开** 的本机演示系统，不是已经加固的公网 SaaS。

## 密钥

- **不要提交** `python_agent/.env`、`application-local.properties`。
- 百炼 `DASHSCOPE_API_KEY`、Unstructured Key **只**写在本机 `.env`。仓库里的 `.env.example` 必须保持空值。
- JWT 密钥只写本机 `application-local.properties`。换密钥会使已登录票全部失效。
- 若 Key 曾出现在聊天、截图或误提交的 Git 历史中：立刻在云控制台作废并换新；Git 历史里的旧值视为已泄露。

公开仓库请打开 GitHub **Secret scanning**。不要在 Issue / PR 里粘贴 Key、JWT、密码。

## 演示账号

种子用户密码均为 `1234`，**仅本机**。不要把同一套账密放到可被外网访问的部署上。
