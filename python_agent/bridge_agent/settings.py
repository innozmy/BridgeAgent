"""环境与识图超参。Key 只从 python_agent/.env 读，禁止写进 Spring。"""

from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
load_dotenv(ROOT / ".env")

# 与 Spring app.storage-root 默认值对齐，相对路径再试一次
FILES_ROOT = ROOT.parent / "bridge_agent_demo" / "data" / "files"

HOST = "127.0.0.1"
PORT = 8001

MODEL = os.getenv("QWEN_VL_MODEL", "qwen3-vl-plus").strip() or "qwen3-vl-plus"
EMBED_MODEL = os.getenv("QWEN_EMBED_MODEL", "text-embedding-v4").strip() or "text-embedding-v4"
EMBED_DIM = 1024
# 知识第三步：过长 chunk 语义切（字，不是 token）
SPLIT_MAX_CHARS = int(os.getenv("KNOWLEDGE_SPLIT_MAX_CHARS") or "1200")
SPLIT_OVERLAP_CHARS = int(os.getenv("KNOWLEDGE_SPLIT_OVERLAP_CHARS") or "80")
SPLIT_GRADIENT_PERCENTILE = float(os.getenv("KNOWLEDGE_SPLIT_GRADIENT_PERCENTILE") or "95")
API_KEY = (os.getenv("DASHSCOPE_API_KEY") or "").strip()
BASE_URL = os.getenv(
    "DASHSCOPE_BASE_URL",
    "https://dashscope.aliyuncs.com/compatible-mode/v1",
).strip()

# 知识 PDF：Unstructured Transform 云 API（Key 只在 .env）
UNSTRUCTURED_API_KEY = (os.getenv("UNSTRUCTURED_API_KEY") or "").strip()
UNSTRUCTURED_API_URL = (
    os.getenv("UNSTRUCTURED_API_URL") or "https://platform-api.transform.unstructured.io/api/v1"
).strip().rstrip("/")
# 等 Unstructured job 完成的上限（不含随后千问）
UNSTRUCTURED_JOB_TIMEOUT_SECONDS = int(os.getenv("UNSTRUCTURED_JOB_TIMEOUT_SECONDS") or "1500")

# 本机 Milvus；Token 可空（单机常无鉴权）
MILVUS_URI = (os.getenv("MILVUS_URI") or "http://127.0.0.1:19530").strip()
MILVUS_TOKEN = (os.getenv("MILVUS_TOKEN") or "").strip()
MILVUS_COLLECTION = (os.getenv("MILVUS_COLLECTION") or "knowledge_chunk").strip() or "knowledge_chunk"
# 知识检索（已锁默认；实现成配置便于微调）
SEARCH_RRF_K = int(os.getenv("KNOWLEDGE_SEARCH_RRF_K") or "60")
SEARCH_ANN_LIMIT = int(os.getenv("KNOWLEDGE_SEARCH_ANN_LIMIT") or "20")
SEARCH_PACK_LIMIT = int(os.getenv("KNOWLEDGE_SEARCH_PACK_LIMIT") or "10")
SEARCH_MAX_FIGURES = int(os.getenv("KNOWLEDGE_SEARCH_MAX_FIGURES") or "2")
SEARCH_MAX_TABLES = int(os.getenv("KNOWLEDGE_SEARCH_MAX_TABLES") or "2")
# RRF 先取两路并集再按 kind 截成 PACK_LIMIT，避免只看前 10 名漏掉图/表
SEARCH_RRF_CANDIDATES = int(os.getenv("KNOWLEDGE_SEARCH_RRF_CANDIDATES") or "40")
# 工具对外调用次数；自我纠正的第 2 次混搜在包装内，不另占这一次
SEARCH_MAX_CALLS = int(os.getenv("KNOWLEDGE_SEARCH_MAX_CALLS") or "1")
SEARCH_CATEGORY = (os.getenv("KNOWLEDGE_SEARCH_CATEGORY") or "code").strip() or "code"
SEARCH_CORRECT = (os.getenv("KNOWLEDGE_SEARCH_CORRECT") or "1").strip() not in ("0", "false", "False")
# 问询局部 ReAct：对外工具 1 次、含 compose 在内最多 3 轮 LLM
INQUIRY_TOOL_MAX = int(os.getenv("INQUIRY_TOOL_MAX") or "1")
INQUIRY_LLM_MAX = int(os.getenv("INQUIRY_LLM_MAX") or "3")
# 建模 consult：代码组题，对外最多 3 次 search_knowledge
MODELING_CONSULT_MAX_SEARCHES = int(os.getenv("MODELING_CONSULT_MAX_SEARCHES") or "3")
# 补发：同 HTTP 内按注入路径读 parse/；不扫总库
RESUPPLY_MAX_JPEG = int(os.getenv("KNOWLEDGE_RESUPPLY_MAX_JPEG") or "4")
RESUPPLY_TABLE_CHARS = int(os.getenv("KNOWLEDGE_RESUPPLY_TABLE_CHARS") or "8000")

FINE_DPI = 150
COARSE_DPI = 72
MAX_EDGE = 1200
# 全册细抽上限（分批，每批仍 MAX_FINE_PAGES）。只看说明+总布置会漏墩高、桩径、横断。
MAX_FULL_FINE_PAGES = 12
MAX_FINE_PAGES = 4
# 补充识别：精确 kind 不够时扩扫相关页，分批细看，避免只盯目录/基础两页就收工
MAX_SUPPLEMENT_FINE_PAGES = 12
COARSE_PAGE_LIMIT = 12

LAYOUT_KINDS = {"general_layout", "elevation"}
# 扩扫时跳过：图纸目录页、钢筋、图例汇编、封面备考（不含结构尺寸）
SKIP_SWEEP_KINDS = {"catalog", "rebar", "notes", "archive"}
# 精确类没有或不够时，按此顺序补页（大样/其它常被粗看标错）
SWEEP_FILL_KINDS = (
    "detail",
    "other",
    "quantity",
    "pier",
    "foundation",
    "bearing",
    "cross_section",
    "elevation",
    "general_layout",
    "project_notes",
    "geology",
)
# 建模硬缺口 → 细看页类。前几项是启发式专页；后面是分类不准时的扩扫
GAP_FOCUS_KINDS = {
    "spansM": ["project_notes", "general_layout", "elevation"],
    "girderType": ["project_notes", "general_layout"],
    "material": ["project_notes", "general_layout"],
    "girderCount": ["cross_section", "general_layout", "quantity"],
    "girderSpacingM": ["cross_section", "general_layout"],
    "girderSection": ["cross_section", "detail"],
    "pierHeightM": ["pier", "elevation", "general_layout"],
    "pierSection": ["pier", "detail", "cross_section"],
    "capBeam": ["pier", "detail", "cross_section"],
    "bearing": ["bearing", "detail", "other", "quantity", "pier"],
    "foundation": ["foundation", "pier", "detail", "other", "geology"],
    "skewOrCurve": ["general_layout", "site_plan", "elevation"],
}
# 本轮细看：说明 + 总布置/立面 + 数量表；结构专页另见 FINE_ROUND1_STRUCTURE_KINDS
FINE_ROUND1_KINDS = ("project_notes", "general_layout", "elevation", "quantity")
# 全册第一枪就要带上：横断、墩柱、基础、垫石（否则柱高/桩径永远进不了联表）
FINE_ROUND1_STRUCTURE_KINDS = ("cross_section", "pier", "foundation", "bearing")
PAGE_KINDS = {
    "catalog",
    "notes",
    "project_notes",
    "general_layout",
    "elevation",
    "site_plan",
    "cross_section",
    "quantity",
    "rebar",
    "pier",
    "foundation",
    "bearing",
    "geology",
    "archive",
    "detail",
    "other",
}

CARRIAGEWAY_ZH = {
    "left": "左幅",
    "right": "右幅",
    "undivided": "不分幅",
}

QUOTA_MARKERS = (
    "quota",
    "insufficient",
    "arrearage",
    "balance",
    "exceeded",
    "额度",
    "余额不足",
    "欠费",
    "未开通",
    "未购买",
    "accessdenied.unpurchased",
    "prepaid",
)
