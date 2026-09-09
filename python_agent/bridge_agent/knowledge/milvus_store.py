"""本机 Milvus：knowledge_chunk 的建表、按文献删除、批量写入、混搜。不连业务 MySQL。"""

from __future__ import annotations

from pymilvus import AnnSearchRequest, DataType, MilvusClient, RRFRanker

from bridge_agent.settings import (
    EMBED_DIM,
    EMBED_MODEL,
    MILVUS_COLLECTION,
    MILVUS_TOKEN,
    MILVUS_URI,
    SEARCH_ANN_LIMIT,
    SEARCH_RRF_CANDIDATES,
    SEARCH_RRF_K,
)

_client: MilvusClient | None = None


def client() -> MilvusClient:
    global _client
    if _client is None:
        kwargs = {"uri": MILVUS_URI}
        if MILVUS_TOKEN:
            kwargs["token"] = MILVUS_TOKEN
        _client = MilvusClient(**kwargs)
    return _client


def ensure_collection() -> None:
    store = client()
    if store.has_collection(MILVUS_COLLECTION):
        store.load_collection(MILVUS_COLLECTION)
        return
    schema = store.create_schema(auto_id=False, enable_dynamic_field=False)
    schema.add_field("id", DataType.VARCHAR, max_length=128, is_primary=True)
    schema.add_field("kind", DataType.VARCHAR, max_length=16)
    schema.add_field("dense", DataType.FLOAT_VECTOR, dim=EMBED_DIM)
    schema.add_field("sparse", DataType.SPARSE_FLOAT_VECTOR)
    schema.add_field("document_id", DataType.INT64)
    schema.add_field("sha256", DataType.VARCHAR, max_length=64)
    schema.add_field("ref_id", DataType.VARCHAR, max_length=64)
    schema.add_field("parent_chunk_id", DataType.VARCHAR, max_length=32)
    schema.add_field("category", DataType.VARCHAR, max_length=16)
    schema.add_field("family_code", DataType.VARCHAR, max_length=64)
    schema.add_field("region", DataType.VARCHAR, max_length=64)
    schema.add_field("specialty", DataType.VARCHAR, max_length=64)
    schema.add_field("clause_no", DataType.VARCHAR, max_length=32)
    schema.add_field("figure_no", DataType.VARCHAR, max_length=32)
    schema.add_field("table_no", DataType.VARCHAR, max_length=32)
    # 8192 字节：写入侧截断 2048 字；Milvus 的 max_length 按字节计
    schema.add_field("body", DataType.VARCHAR, max_length=8192)
    schema.add_field("page_numbers", DataType.ARRAY, element_type=DataType.INT32, max_capacity=64)
    schema.add_field(
        "mentioned_figure_ids",
        DataType.ARRAY,
        element_type=DataType.VARCHAR,
        max_capacity=32,
        max_length=64,
    )
    schema.add_field(
        "mentioned_table_ids",
        DataType.ARRAY,
        element_type=DataType.VARCHAR,
        max_capacity=32,
        max_length=64,
    )
    schema.add_field("embed_model", DataType.VARCHAR, max_length=64)
    index_params = store.prepare_index_params()
    index_params.add_index(
        field_name="dense",
        index_type="HNSW",
        metric_type="COSINE",
        params={"M": 16, "efConstruction": 256},
    )
    index_params.add_index(
        field_name="sparse",
        index_type="SPARSE_INVERTED_INDEX",
        metric_type="IP",
    )
    for name in ("kind", "document_id", "category", "family_code", "clause_no", "figure_no", "table_no"):
        index_params.add_index(field_name=name, index_type="INVERTED")
    store.create_collection(
        collection_name=MILVUS_COLLECTION,
        schema=schema,
        index_params=index_params,
    )
    store.load_collection(MILVUS_COLLECTION)


def delete_document(document_id: int) -> int:
    """按文献删行。collection 不存在视为已空。"""
    store = client()
    if not store.has_collection(MILVUS_COLLECTION):
        return 0
    result = store.delete(collection_name=MILVUS_COLLECTION, filter=f"document_id == {int(document_id)}")
    if isinstance(result, dict):
        return int(result.get("delete_count") or 0)
    return 0


_UPSERT_BATCH = 64


def upsert_rows(rows: list[dict]) -> int:
    if not rows:
        return 0
    ensure_collection()
    store = client()
    payload = []
    for row in rows:
        item = dict(row)
        item["embed_model"] = EMBED_MODEL
        payload.append(item)
    for start in range(0, len(payload), _UPSERT_BATCH):
        store.upsert(collection_name=MILVUS_COLLECTION, data=payload[start : start + _UPSERT_BATCH])
    return len(payload)


_OUTPUT_FIELDS = [
    "kind",
    "document_id",
    "sha256",
    "ref_id",
    "parent_chunk_id",
    "category",
    "family_code",
    "region",
    "specialty",
    "clause_no",
    "figure_no",
    "table_no",
    "body",
    "page_numbers",
    "mentioned_figure_ids",
    "mentioned_table_ids",
]


def hybrid_search(
    dense: list[float],
    sparse: dict[int, float],
    document_ids: list[int],
    category: str = "code",
    ann_limit: int | None = None,
    rrf_k: int | None = None,
    rrf_candidates: int | None = None,
) -> list[dict]:
    """
    dense COSINE + sparse IP，RRF 融合。过滤启用集和 category。
    返回按 RRF 顺序的标量行（不含向量、不含分数）。
    """
    ids = sorted({int(item) for item in document_ids})
    if not ids or not dense:
        return []
    ensure_collection()
    expr = 'document_id in [%s] and category == "%s"' % (",".join(str(i) for i in ids), category.replace('"', ""))
    each = int(ann_limit or SEARCH_ANN_LIMIT)
    fused = int(rrf_candidates or SEARCH_RRF_CANDIDATES)
    sparse_map = sparse or {0: 1e-6}
    reqs = [
        AnnSearchRequest(
            data=[dense],
            anns_field="dense",
            param={"metric_type": "COSINE", "params": {"ef": 64}},
            limit=each,
            expr=expr,
        ),
        AnnSearchRequest(
            data=[sparse_map],
            anns_field="sparse",
            param={"metric_type": "IP"},
            limit=each,
            expr=expr,
        ),
    ]
    raw = client().hybrid_search(
        collection_name=MILVUS_COLLECTION,
        reqs=reqs,
        ranker=RRFRanker(k=int(rrf_k or SEARCH_RRF_K)),
        limit=fused,
        output_fields=_OUTPUT_FIELDS,
    )
    return [_entity(hit) for hit in _first_hits(raw)]


def _first_hits(raw) -> list:
    if not raw:
        return []
    if isinstance(raw, list) and raw and isinstance(raw[0], list):
        return list(raw[0])
    if isinstance(raw, list):
        return list(raw)
    return []


def _entity(hit) -> dict:
    if isinstance(hit, dict):
        entity = hit.get("entity")
        if isinstance(entity, dict):
            return dict(entity)
        return {key: value for key, value in hit.items() if key not in ("distance", "score")}
    entity = getattr(hit, "entity", None)
    if isinstance(entity, dict):
        return dict(entity)
    if entity is not None and hasattr(entity, "items"):
        return dict(entity)
    return {}
