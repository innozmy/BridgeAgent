# BridgeAgent Python 大脑（LangGraph 1.2）

需要 **Python 3.12**（LangGraph 1.2 要求 ≥3.10）。本目录 `.python-version` 已标明。

建议用虚拟环境（与系统里的 3.9 隔离）：

```
py -3.12 -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python app.py
```

Spring 只调本进程的 HTTP。Key 在 `.env`（`DASHSCOPE_API_KEY`、`UNSTRUCTURED_API_KEY`），不进 Java。只开一份 8001。

- 健康检查：`GET http://127.0.0.1:8001/health`（含 `langgraphVersion` / `pythonVersion`）
- 识图（兼容 Spring）：`POST /v1/parse-drawings`
- 知识 PDF 解析：`POST /v1/knowledge/parse`（Unstructured Transform + 附图千问，不是 LangGraph）
- 知识合并：`POST /v1/knowledge/merge`（只读 parse/，写 merge/chunks.json）
- 知识过长分割：`POST /v1/knowledge/split`（只读 merge/，写 split/chunks.json；千问 embedding，不是 LangGraph）
- 知识嵌入：`POST /v1/knowledge/embed`（只读 split/ 与 parse 图/表目录，写入本机 Milvus `knowledge_chunk`）
- 知识向量删除：`POST /v1/knowledge/vectors/delete`（按 documentId 清 Milvus 行）
- 知识检索：`POST /v1/knowledge/search`（混搜 RRF + 自我纠正：评估/改写最多再搜一次；调用方注入启用集，不挂 Agent）
- 按 Agent 种类：`POST /v1/agents/{kind}`，`kind` 现为 `drawing_parse` / `inquiry` / `modeling`

## 目录

```
python_agent/
  app.py                 入口，只启动 HTTP
  bridge_agent/
    settings.py          环境与识图超参
    runtime.py           失败 JSON、额度归类
    llm/qwen_vl.py       千问 VL（OpenAI 兼容）
    tools/               无状态工具（拆 PDF、筛路径、search_knowledge 未挂图）
    memory/
      page_map.py        按文件的页分类 JSON
      checkpointer.py    thread_id → 图 State（MemorySaver）
      store.py           跨 thread 的 Store（InMemoryStore）
      runtime.py         compile(checkpointer, store)
    graphs/
      registry.py        kind → 图
      drawing_parse/     识图主图 + 跑时 Skill（skills/drawing_parse/）
      inquiry/           问询图（文本已接；单页 VL 未做）
      modeling/          建模图（骨架；Skill 已写在 skills/modeling/）
    skills/              跑时 Skill；识图/建模按节点注入（不是 Cursor Skill）
```

- 问询：`POST /v1/agents/inquiry`（Spring 注入账本投影 + 本会话 STM 窗口 + 工种/LTM 索引；只读文本作答，不写库）

1.2 已接上的能力：StateGraph **State**、**checkpoint** + **thread_id**、**Store**、**Memory** 接口。问询已接文本模型；单页 VL 细看尚未挂。

图与图不私聊。下一步由 Spring 发新 HTTP。写库仍只在 Spring。
