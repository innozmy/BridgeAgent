package myz.bridge_agent_demo.service;

/**
 * Service：把已 running 的识图任务丢到后台线程，避免占着 HTTP。
 */
public interface AgentJobLauncher {

    void runDrawing(Long projectId, Long taskId);

    /** 已 running 的建模任务丢到后台，避免占着同意接口。 */
    void runModeling(Long projectId, Long taskId);

    /**
     * 知识库解析丢到后台。{@code force=true} 时清空 parse/ 后全量重跑（并清过期 merge/）。
     */
    void runKnowledgeParse(Long documentId, boolean force);

    /** 知识库合并丢到后台。只写 merge/。 */
    void runKnowledgeMerge(Long documentId);

    /** 知识库过长分割丢到后台。只写 split/。 */
    void runKnowledgeSplit(Long documentId);

    /** 知识库嵌入丢到后台。只写本机 Milvus。 */
    void runKnowledgeEmbed(Long documentId);
}
