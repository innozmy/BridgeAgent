package myz.bridge_agent_demo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.dto.PythonKnowledgeParseResponse;
import myz.bridge_agent_demo.entity.KnowledgeDocument;
import myz.bridge_agent_demo.mapper.KnowledgeDocumentMapper;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.KnowledgeDocumentWrites;
import myz.bridge_agent_demo.service.KnowledgeParseRunner;
import myz.bridge_agent_demo.service.PythonAgentClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service：调用 Python 知识解析作业并回写 parse_status。不写工程记忆。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeParseRunnerImpl implements KnowledgeParseRunner {

    private final KnowledgeDocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final PythonAgentClient pythonAgentClient;
    private final KnowledgeDocumentWrites documentWrites;

    @Override
    public void execute(Long documentId, boolean force) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null || !StringUtils.hasText(doc.getSha256())) {
            log.warn("知识解析找不到文献 {}", documentId);
            return;
        }
        Path elementsWrite = fileStorageService.knowledgeElementsPath(doc.getSha256());
        try {
            Path pdf = fileStorageService.resolveKnowledgePdf(doc.getSha256(), doc.getStoragePath());
            Path parseDir = fileStorageService.knowledgeParseDir(doc.getSha256());
            if (force) {
                fileStorageService.deleteKnowledgeParseArtifacts(doc.getSha256());
                fileStorageService.deleteKnowledgeMergeArtifacts(doc.getSha256());
                fileStorageService.deleteKnowledgeSplitArtifacts(doc.getSha256());
            }
            Path elementsFound = fileStorageService.findKnowledgeElements(doc.getSha256());
            Path elementsForPython = (!force && Files.exists(elementsFound)) ? elementsFound : elementsWrite;
            Path figuresForPython = "parse".equals(parentName(elementsForPython))
                    ? fileStorageService.knowledgeFiguresDir(doc.getSha256())
                    : fileStorageService.knowledgeDocDir(doc.getSha256()).resolve("figures");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("documentId", doc.getId());
            payload.put("name", doc.getName());
            payload.put("familyCode", doc.getFamilyCode());
            payload.put("sha256", doc.getSha256());
            payload.put("pdfPath", pdf.toAbsolutePath().toString());
            payload.put("parseDir", parseDir.toAbsolutePath().toString());
            payload.put("elementsPath", elementsForPython.toAbsolutePath().toString());
            payload.put("figuresDir", figuresForPython.toAbsolutePath().toString());
            payload.put("force", force);
            PythonKnowledgeParseResponse response = pythonAgentClient.parseKnowledge(payload);
            boolean ok = response != null && Boolean.TRUE.equals(response.getOk());
            boolean hasSidecar = Files.exists(fileStorageService.findKnowledgeElements(doc.getSha256()));
            String status = ok || hasSidecar ? "parsed" : "failed";
            documentWrites.markParseDone(documentId, status, force);
            if (ok) {
                log.info("知识解析完成 id={} elements={} figures={} vl={}/{}",
                        documentId, response.getElementCount(), response.getFigureCount(),
                        response.getVlDone(), response.getVlFailed());
            } else {
                log.warn("知识解析未成功 id={} status={} error={}",
                        documentId, status, response == null ? "python 无响应" : response.getError());
            }
        } catch (Exception e) {
            log.warn("知识解析异常 id={}: {}", documentId, e.getMessage());
            String status = Files.exists(fileStorageService.findKnowledgeElements(doc.getSha256())) ? "parsed" : "failed";
            documentWrites.markParseDone(documentId, status, force);
        }
    }

    private String parentName(Path path) {
        Path parent = path.getParent();
        return parent == null ? "" : String.valueOf(parent.getFileName());
    }
}
