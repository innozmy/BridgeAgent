package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.entity.KnowledgeDocument;

import java.util.List;
import java.util.Map;

/**
 * Service：组 RAG 启用集，不依赖作业调度，避免与注入包服务循环依赖。
 */
public interface KnowledgeScopeService {

    /**
     * v1 检索范围：启用 ∩ 已嵌入 ∩ {@code category=code}。手册/案例不进。
     */
    List<KnowledgeDocument> listEnabledEmbeddedCodes(Long projectId);

    /**
     * 问询/建模注入包字段：id、名称、sha256、文献磁盘根。
     * Python 只打开这些路径下的 parse/ 做补发。
     */
    Map<String, Object> buildKnowledgeScope(Long projectId);
}
