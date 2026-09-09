package myz.bridge_agent_demo.service;

/**
 * Service：后台执行知识 PDF 解析，与 {@link KnowledgeService} 拆开以免和启动器循环依赖。
 */
public interface KnowledgeParseRunner {

    /**
     * 已把 parse_status 置为 parsing 之后调用。成功或「Unstructured 已写出 sidecar」为 parsed，否则 failed。
     *
     * @param force true 时先清 sidecar 再全量
     */
    void execute(Long documentId, boolean force);
}
