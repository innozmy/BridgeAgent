package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.dto.PythonKnowledgeEmbedResponse;
import myz.bridge_agent_demo.entity.KnowledgeDocument;
import myz.bridge_agent_demo.mapper.KnowledgeDocumentMapper;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.KnowledgeEmbedRunner;
import myz.bridge_agent_demo.service.PythonAgentClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service：调用 Python 嵌入作业，回写 embed_status。不连 Milvus。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeEmbedRunnerImpl implements KnowledgeEmbedRunner {

    private final KnowledgeDocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final PythonAgentClient pythonAgentClient;

    @Override
    public void execute(Long documentId) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.warn("知识嵌入找不到文献 {}", documentId);
            return;
        }
        if (!StringUtils.hasText(doc.getSha256())) {
            log.warn("知识嵌入缺少 sha256 {}", documentId);
            mark(documentId, "failed");
            return;
        }
        Path splitChunks = fileStorageService.knowledgeSplitChunksPath(doc.getSha256());
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("documentId", doc.getId());
            payload.put("sha256", doc.getSha256());
            payload.put("category", doc.getCategory());
            payload.put("familyCode", doc.getFamilyCode());
            payload.put("region", doc.getRegion());
            payload.put("specialty", doc.getSpecialty());
            payload.put("splitChunksPath", splitChunks.toAbsolutePath().toString());
            payload.put("elementsPath", fileStorageService.findKnowledgeElements(doc.getSha256()).toAbsolutePath().toString());
            payload.put("figureIndexPath", fileStorageService.knowledgeFigureIndexPath(doc.getSha256()).toAbsolutePath().toString());
            payload.put("tableIndexPath", fileStorageService.knowledgeTableIndexPath(doc.getSha256()).toAbsolutePath().toString());
            PythonKnowledgeEmbedResponse response = pythonAgentClient.embedKnowledge(payload);
            boolean ok = response != null && Boolean.TRUE.equals(response.getOk());
            mark(documentId, ok ? "embedded" : "failed");
            if (ok) {
                log.info("知识嵌入完成 id={} rows={} text={} fig={} tab={}",
                        documentId, response.getRowCount(), response.getTextCount(),
                        response.getFigureCount(), response.getTableCount());
            } else {
                log.warn("知识嵌入未成功 id={} error={}",
                        documentId, response == null ? "python 无响应" : response.getError());
            }
        } catch (Exception e) {
            log.warn("知识嵌入异常 id={}: {}", documentId, e.getMessage());
            mark(documentId, "failed");
        }
    }

    /** 只改 embed_status，避免 updateById 把并发中的 parse/merge/split 盖回去。 */
    private void mark(Long documentId, String status) {
        documentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getEmbedStatus, status)
                .eq(KnowledgeDocument::getId, documentId));
    }
}
