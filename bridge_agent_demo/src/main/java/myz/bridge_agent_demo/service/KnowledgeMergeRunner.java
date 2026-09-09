package myz.bridge_agent_demo.service;

/**
 * Service：后台执行知识 chunk 合并，与 {@link KnowledgeService} 拆开以免和启动器循环依赖。
 */
public interface KnowledgeMergeRunner {

    /**
     * 已把 merge_status 置为 merging 之后调用。只写 merge/，不改 parse/。
     */
    void execute(Long documentId);
}
