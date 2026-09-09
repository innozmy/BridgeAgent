package myz.bridge_agent_demo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.service.KnowledgeVectorCleaner;
import myz.bridge_agent_demo.service.PythonAgentClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service：转调 Python 删除 Milvus 行。本类不直连向量库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeVectorCleanerImpl implements KnowledgeVectorCleaner {

    private final PythonAgentClient pythonAgentClient;

    @Override
    public void drop(Long documentId) {
        if (documentId == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentId", documentId);
        try {
            pythonAgentClient.deleteKnowledgeVectors(payload);
        } catch (Exception e) {
            log.warn("知识向量删除异常 documentId={}: {}", documentId, e.getMessage());
        }
    }
}
