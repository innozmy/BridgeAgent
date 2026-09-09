package myz.bridge_agent_demo.service;

/**
 * Service：后台把 split/ 与图/表目录嵌入本机 Milvus。与 {@link KnowledgeService} 拆开以免循环依赖。
 */
public interface KnowledgeEmbedRunner {

    /**
     * 已把 embed_status 置为 embedding 之后调用。只写 Milvus，不改 parse/merge/split 磁盘。
     */
    void execute(Long documentId);
}
