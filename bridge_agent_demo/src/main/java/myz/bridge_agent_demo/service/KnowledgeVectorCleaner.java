package myz.bridge_agent_demo.service;

/**
 * Service：通知 Python 按文献删除 Milvus 行。解析/合并/分割作废或删文献时调用。
 */
public interface KnowledgeVectorCleaner {

    /**
     * 尽力删除该文献向量。Python 不可达时只打日志，不抛给页面。
     *
     * @param documentId 知识文献 id
     */
    void drop(Long documentId);
}
