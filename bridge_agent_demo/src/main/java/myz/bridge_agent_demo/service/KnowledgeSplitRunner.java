package myz.bridge_agent_demo.service;

/**
 * Service：后台执行知识过长分割，与 {@link KnowledgeService} 拆开以免和启动器循环依赖。
 */
public interface KnowledgeSplitRunner {

    /**
     * 已把 split_status 置为 splitting 之后调用。只写 split/，不改 parse/ 与 merge/。
     */
    void execute(Long documentId);
}
