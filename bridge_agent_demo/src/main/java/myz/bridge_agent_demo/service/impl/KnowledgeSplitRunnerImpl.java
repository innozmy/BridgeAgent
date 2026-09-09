package myz.bridge_agent_demo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.dto.PythonKnowledgeSplitResponse;
import myz.bridge_agent_demo.entity.KnowledgeDocument;
import myz.bridge_agent_demo.mapper.KnowledgeDocumentMapper;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.KnowledgeDocumentWrites;
import myz.bridge_agent_demo.service.KnowledgeSplitRunner;
import myz.bridge_agent_demo.service.PythonAgentClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service：调用 Python 分割作业，只写 split/chunks.json，回写 split_status。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSplitRunnerImpl implements KnowledgeSplitRunner {

    private final KnowledgeDocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final PythonAgentClient pythonAgentClient;
    private final KnowledgeDocumentWrites documentWrites;

    @Override
    public void execute(Long documentId) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null || !StringUtils.hasText(doc.getSha256())) {
            log.warn("知识分割找不到文献 {}", documentId);
            return;
        }
        Path splitChunks = fileStorageService.knowledgeSplitChunksPath(doc.getSha256());
        try {
            fileStorageService.deleteKnowledgeSplitArtifacts(doc.getSha256());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("documentId", doc.getId());
            payload.put("sha256", doc.getSha256());
            payload.put("mergeChunksPath", fileStorageService.knowledgeChunksPath(doc.getSha256()).toAbsolutePath().toString());
            payload.put("splitChunksPath", splitChunks.toAbsolutePath().toString());
            payload.put("elementsPath", fileStorageService.findKnowledgeElements(doc.getSha256()).toAbsolutePath().toString());
            payload.put("figureIndexPath", fileStorageService.knowledgeFigureIndexPath(doc.getSha256()).toAbsolutePath().toString());
            payload.put("tableIndexPath", fileStorageService.knowledgeTableIndexPath(doc.getSha256()).toAbsolutePath().toString());
            PythonKnowledgeSplitResponse response = pythonAgentClient.splitKnowledge(payload);
            boolean ok = response != null && Boolean.TRUE.equals(response.getOk()) && Files.exists(splitChunks);
            documentWrites.markSplitDone(documentId, ok ? "split" : "failed");
            if (ok) {
                log.info("知识分割完成 id={} chunks={} splitSources={}",
                        documentId, response.getChunkCount(), response.getSplitSourceCount());
            } else {
                log.warn("知识分割未成功 id={} error={}",
                        documentId, response == null ? "python 无响应" : response.getError());
            }
        } catch (Exception e) {
            log.warn("知识分割异常 id={}: {}", documentId, e.getMessage());
            documentWrites.markSplitDone(documentId, Files.exists(splitChunks) ? "split" : "failed");
        }
    }
}
