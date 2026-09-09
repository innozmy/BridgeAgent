package myz.bridge_agent_demo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.dto.PythonKnowledgeMergeResponse;
import myz.bridge_agent_demo.entity.KnowledgeDocument;
import myz.bridge_agent_demo.mapper.KnowledgeDocumentMapper;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.KnowledgeDocumentWrites;
import myz.bridge_agent_demo.service.KnowledgeMergeRunner;
import myz.bridge_agent_demo.service.PythonAgentClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service：调用 Python 合并作业，只写 merge/chunks.json，回写 merge_status。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeMergeRunnerImpl implements KnowledgeMergeRunner {

    private final KnowledgeDocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final PythonAgentClient pythonAgentClient;
    private final KnowledgeDocumentWrites documentWrites;

    @Override
    public void execute(Long documentId) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null || !StringUtils.hasText(doc.getSha256())) {
            log.warn("知识合并找不到文献 {}", documentId);
            return;
        }
        Path chunks = fileStorageService.knowledgeChunksPath(doc.getSha256());
        try {
            // merge 重写后第三步过期，只清 split/，不碰 parse/
            fileStorageService.deleteKnowledgeMergeArtifacts(doc.getSha256());
            fileStorageService.deleteKnowledgeSplitArtifacts(doc.getSha256());
            Path elements = fileStorageService.findKnowledgeElements(doc.getSha256());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("documentId", doc.getId());
            payload.put("sha256", doc.getSha256());
            payload.put("elementsPath", elements.toAbsolutePath().toString());
            payload.put("figureIndexPath", fileStorageService.knowledgeFigureIndexPath(doc.getSha256()).toAbsolutePath().toString());
            payload.put("tableIndexPath", fileStorageService.knowledgeTableIndexPath(doc.getSha256()).toAbsolutePath().toString());
            payload.put("chunksPath", chunks.toAbsolutePath().toString());
            PythonKnowledgeMergeResponse response = pythonAgentClient.mergeKnowledge(payload);
            boolean ok = response != null && Boolean.TRUE.equals(response.getOk()) && Files.exists(chunks);
            documentWrites.markMergeDone(documentId, ok ? "merged" : "failed");
            if (ok) {
                log.info("知识合并完成 id={} chunks={}", documentId, response.getChunkCount());
            } else {
                log.warn("知识合并未成功 id={} error={}",
                        documentId, response == null ? "python 无响应" : response.getError());
            }
        } catch (Exception e) {
            log.warn("知识合并异常 id={}: {}", documentId, e.getMessage());
            documentWrites.markMergeDone(documentId, Files.exists(chunks) ? "merged" : "failed");
        }
    }
}
