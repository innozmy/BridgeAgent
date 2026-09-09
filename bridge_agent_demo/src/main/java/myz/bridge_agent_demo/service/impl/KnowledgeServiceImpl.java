package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.dto.PythonKnowledgeSearchResponse;
import myz.bridge_agent_demo.entity.KnowledgeDocument;
import myz.bridge_agent_demo.entity.Project;
import myz.bridge_agent_demo.entity.ProjectKnowledge;
import myz.bridge_agent_demo.exception.BusinessException;
import myz.bridge_agent_demo.mapper.KnowledgeDocumentMapper;
import myz.bridge_agent_demo.mapper.ProjectKnowledgeMapper;
import myz.bridge_agent_demo.mapper.ProjectMapper;
import myz.bridge_agent_demo.service.JobDispatchService;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.KnowledgeService;
import myz.bridge_agent_demo.service.KnowledgeScopeService;
import myz.bridge_agent_demo.service.KnowledgeVectorCleaner;
import myz.bridge_agent_demo.service.PythonAgentClient;
import myz.bridge_agent_demo.vo.KnowledgeParseStartVO;
import myz.bridge_agent_demo.vo.KnowledgeSearchVO;
import myz.bridge_agent_demo.vo.KnowledgeUploadVO;
import myz.bridge_agent_demo.vo.ProjectKnowledgeVO;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 知识总库：上传 PDF；解析由人点按钮后台触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Set<String> CATEGORIES = Set.of("code", "manual", "case");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final KnowledgeDocumentMapper documentMapper;
    private final ProjectKnowledgeMapper projectKnowledgeMapper;
    private final ProjectMapper projectMapper;
    private final FileStorageService fileStorageService;
    private final JobDispatchService jobDispatchService;
    private final KnowledgeVectorCleaner knowledgeVectorCleaner;
    private final PythonAgentClient pythonAgentClient;
    private final KnowledgeScopeService knowledgeScopeService;

    @Override
    public List<KnowledgeDocument> listDocuments() {
        return documentMapper.selectList(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .orderByAsc(KnowledgeDocument::getCategory)
                        .orderByAsc(KnowledgeDocument::getFamilyCode)
                        .orderByDesc(KnowledgeDocument::getEffectiveFrom)
                        .orderByAsc(KnowledgeDocument::getId));
    }

    @Override
    public KnowledgeUploadVO upload(String name, String category, String familyCode, String region,
                                    String specialty, LocalDate effectiveFrom, LocalDate effectiveTo,
                                    MultipartFile file) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("文献名称不能为空");
        }
        if (!CATEGORIES.contains(category)) {
            throw new BusinessException("分类只能是 code、manual 或 case");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择 PDF 文件");
        }
        String originalName = file.getOriginalFilename() == null ? "unnamed.pdf" : file.getOriginalFilename();
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BusinessException("知识文件目前只支持 PDF");
        }

        try {
            FileStorageService.StoredFile stored = fileStorageService.storeKnowledge(originalName, file.getInputStream());
            KnowledgeDocument existing = findByHash(stored.sha256());
            if (existing != null) {
                return item(existing, true);
            }
            KnowledgeDocument row = new KnowledgeDocument();
            row.setName(name.trim());
            row.setCategory(category);
            row.setFamilyCode(blankToNull(familyCode));
            row.setRegion(blankToNull(region));
            row.setSpecialty(blankToNull(specialty));
            row.setEffectiveFrom(effectiveFrom);
            row.setEffectiveTo(effectiveTo);
            row.setOriginalName(originalName);
            row.setStoragePath(stored.storagePath());
            row.setSha256(stored.sha256());
            row.setMimeType("application/pdf");
            row.setSizeBytes(stored.sizeBytes());
            row.setParseStatus("unparsed");
            row.setMergeStatus("unmerged");
            row.setSplitStatus("unsplit");
            row.setEmbedStatus("unembedded");
            try {
                documentMapper.insert(row);
                return item(row, false);
            } catch (DuplicateKeyException e) {
                KnowledgeDocument again = findByHash(stored.sha256());
                if (again == null) {
                    throw e;
                }
                return item(again, true);
            }
        } catch (IOException e) {
            log.error("保存知识文件失败 {}", originalName, e);
            throw new BusinessException("保存文件失败");
        }
    }

    @Override
    public ResponseEntity<Resource> download(Long documentId) {
        KnowledgeDocument doc = requireDocument(documentId);
        if (!StringUtils.hasText(doc.getStoragePath())) {
            throw new BusinessException("该文献还没有上传 PDF");
        }
        Path path = fileStorageService.resolveKnowledgePdf(doc.getSha256(), doc.getStoragePath());
        if (!Files.exists(path)) {
            throw new BusinessException("文件不在磁盘上");
        }
        String filename = StringUtils.hasText(doc.getOriginalName()) ? doc.getOriginalName() : doc.getName() + ".pdf";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentLength(path.toFile().length())
                .body(new FileSystemResource(path));
    }

    @Override
    public void deleteDocument(Long documentId) {
        KnowledgeDocument doc = requireDocument(documentId);
        if ("embedding".equals(doc.getEmbedStatus())) {
            throw new BusinessException("正在嵌入，请稍候");
        }
        // 先清向量再删行：删完就没有 documentId 可过滤
        knowledgeVectorCleaner.drop(documentId);
        documentMapper.deleteById(documentId);
        try {
            if (StringUtils.hasText(doc.getSha256())) {
                fileStorageService.deleteKnowledgeDocumentFiles(doc.getSha256(), doc.getStoragePath());
            } else {
                fileStorageService.deleteKnowledge(doc.getStoragePath());
            }
        } catch (IOException e) {
            log.warn("删除知识文件失败 {}", doc.getStoragePath(), e);
        }
    }

    @Override
    public KnowledgeParseStartVO startParse(Long documentId, boolean force) {
        KnowledgeDocument doc = requireDocument(documentId);
        if (!StringUtils.hasText(doc.getStoragePath()) || !StringUtils.hasText(doc.getSha256())) {
            throw new BusinessException("该文献还没有 PDF，无法解析");
        }
        Path pdf = fileStorageService.resolveKnowledgePdf(doc.getSha256(), doc.getStoragePath());
        if (!Files.exists(pdf)) {
            throw new BusinessException("PDF 不在磁盘上");
        }
        rejectIfPipelineBusy(doc);
        Path elements = fileStorageService.findKnowledgeElements(doc.getSha256());
        boolean vlPending = sidecarVlPending(elements);
        boolean complete = "parsed".equals(doc.getParseStatus()) && Files.exists(elements) && !vlPending;
        KnowledgeParseStartVO vo = new KnowledgeParseStartVO();
        if (complete && !force) {
            vo.setNeedConfirm(true);
            return vo;
        }
        var parseUpdate = Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getParseStatus, "queued")
                .eq(KnowledgeDocument::getId, documentId)
                .ne(KnowledgeDocument::getParseStatus, "parsing")
                .ne(KnowledgeDocument::getParseStatus, "queued");
        // 强制重解析会清 merge/split/嵌入，立刻改状态免得界面仍显示已合并/已分割/已嵌入
        boolean wipeDownstream = force && complete;
        if (wipeDownstream) {
            parseUpdate.set(KnowledgeDocument::getMergeStatus, "unmerged")
                    .set(KnowledgeDocument::getSplitStatus, "unsplit")
                    .set(KnowledgeDocument::getEmbedStatus, "unembedded");
        }
        int updated = documentMapper.update(null, parseUpdate);
        if (updated == 0) {
            throw new BusinessException("正在解析，请稍候");
        }
        if (wipeDownstream) {
            knowledgeVectorCleaner.drop(documentId);
        }
        jobDispatchService.enqueueKnowledgeParse(documentId, wipeDownstream);
        vo.setStarted(true);
        return vo;
    }

    @Override
    public KnowledgeParseStartVO startMerge(Long documentId, boolean force) {
        KnowledgeDocument doc = requireDocument(documentId);
        if (!"parsed".equals(doc.getParseStatus())) {
            throw new BusinessException("请先解析成功再合并");
        }
        Path elements = fileStorageService.findKnowledgeElements(doc.getSha256());
        if (!Files.exists(elements)) {
            throw new BusinessException("解析结果不在磁盘上，请先解析");
        }
        rejectIfPipelineBusy(doc);
        boolean complete = "merged".equals(doc.getMergeStatus())
                && Files.exists(fileStorageService.knowledgeChunksPath(doc.getSha256()));
        KnowledgeParseStartVO vo = new KnowledgeParseStartVO();
        if (complete && !force) {
            vo.setNeedConfirm(true);
            return vo;
        }
        // 重写 merge 后 split 与嵌入作废，立刻改状态
        int updated = documentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getMergeStatus, "queued")
                .set(KnowledgeDocument::getSplitStatus, "unsplit")
                .set(KnowledgeDocument::getEmbedStatus, "unembedded")
                .eq(KnowledgeDocument::getId, documentId)
                .ne(KnowledgeDocument::getMergeStatus, "merging")
                .ne(KnowledgeDocument::getMergeStatus, "queued"));
        if (updated == 0) {
            throw new BusinessException("正在合并，请稍候");
        }
        knowledgeVectorCleaner.drop(documentId);
        jobDispatchService.enqueueKnowledgeMerge(documentId);
        vo.setStarted(true);
        return vo;
    }

    @Override
    public KnowledgeParseStartVO startSplit(Long documentId, boolean force) {
        KnowledgeDocument doc = requireDocument(documentId);
        if (!"merged".equals(doc.getMergeStatus())) {
            throw new BusinessException("请先合并成功再分割");
        }
        Path mergeChunks = fileStorageService.knowledgeChunksPath(doc.getSha256());
        if (!Files.exists(mergeChunks)) {
            throw new BusinessException("合并结果不在磁盘上，请先合并");
        }
        rejectIfPipelineBusy(doc);
        boolean complete = "split".equals(doc.getSplitStatus())
                && Files.exists(fileStorageService.knowledgeSplitChunksPath(doc.getSha256()));
        KnowledgeParseStartVO vo = new KnowledgeParseStartVO();
        if (complete && !force) {
            vo.setNeedConfirm(true);
            return vo;
        }
        int updated = documentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getSplitStatus, "queued")
                .set(KnowledgeDocument::getEmbedStatus, "unembedded")
                .eq(KnowledgeDocument::getId, documentId)
                .ne(KnowledgeDocument::getSplitStatus, "splitting")
                .ne(KnowledgeDocument::getSplitStatus, "queued"));
        if (updated == 0) {
            throw new BusinessException("正在分割，请稍候");
        }
        knowledgeVectorCleaner.drop(documentId);
        jobDispatchService.enqueueKnowledgeSplit(documentId);
        vo.setStarted(true);
        return vo;
    }

    @Override
    public KnowledgeParseStartVO startEmbed(Long documentId, boolean force) {
        KnowledgeDocument doc = requireDocument(documentId);
        if (!"split".equals(doc.getSplitStatus())) {
            throw new BusinessException("请先分割成功再嵌入");
        }
        Path splitChunks = fileStorageService.knowledgeSplitChunksPath(doc.getSha256());
        if (!Files.exists(splitChunks)) {
            throw new BusinessException("分割结果不在磁盘上，请先分割");
        }
        rejectIfPipelineBusy(doc);
        boolean complete = "embedded".equals(doc.getEmbedStatus());
        KnowledgeParseStartVO vo = new KnowledgeParseStartVO();
        if (complete && !force) {
            vo.setNeedConfirm(true);
            return vo;
        }
        int updated = documentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
                .set(KnowledgeDocument::getEmbedStatus, "queued")
                .eq(KnowledgeDocument::getId, documentId)
                .ne(KnowledgeDocument::getEmbedStatus, "embedding")
                .ne(KnowledgeDocument::getEmbedStatus, "queued"));
        if (updated == 0) {
            throw new BusinessException("正在嵌入，请稍候");
        }
        jobDispatchService.enqueueKnowledgeEmbed(documentId);
        vo.setStarted(true);
        return vo;
    }

    /**
     * 只把启用且已嵌入的规范 ID/名称交给 Python；模型不能自带文献范围。
     */
    @Override
    public KnowledgeSearchVO searchProject(Long projectId, String query) {
        requireProject(projectId);
        if (!StringUtils.hasText(query)) {
            throw new BusinessException("问句为空");
        }
        List<KnowledgeDocument> scope = knowledgeScopeService.listEnabledEmbeddedCodes(projectId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query.trim());
        payload.put("documentIds", scope.stream().map(KnowledgeDocument::getId).toList());
        Map<String, String> names = new LinkedHashMap<>();
        for (KnowledgeDocument doc : scope) {
            names.put(String.valueOf(doc.getId()), doc.getName());
        }
        payload.put("documentNames", names);
        PythonKnowledgeSearchResponse resp = pythonAgentClient.searchKnowledge(payload);
        if (resp == null) {
            throw new BusinessException("知识检索服务不可用，请确认 Python Agent 已启动");
        }
        if (!Boolean.TRUE.equals(resp.getOk())) {
            throw new BusinessException(StringUtils.hasText(resp.getError()) ? resp.getError() : "知识检索失败");
        }
        KnowledgeSearchVO vo = new KnowledgeSearchVO();
        vo.setNotice(resp.getNotice());
        vo.setHits(resp.getHits() == null ? List.of() : resp.getHits());
        vo.setPendingFigures(resp.getPendingFigures() == null ? List.of() : resp.getPendingFigures());
        vo.setPendingTables(resp.getPendingTables() == null ? List.of() : resp.getPendingTables());
        return vo;
    }

    @Override
    public Map<String, Object> buildKnowledgeScope(Long projectId) {
        return knowledgeScopeService.buildKnowledgeScope(projectId);
    }

    @Override
    public ProjectKnowledgeVO listProjectKnowledge(Long projectId) {
        requireProject(projectId);
        ProjectKnowledgeVO vo = new ProjectKnowledgeVO();
        vo.setDocuments(listDocuments());
        vo.setEnabledIds(projectKnowledgeMapper.selectList(
                        Wrappers.<ProjectKnowledge>lambdaQuery()
                                .eq(ProjectKnowledge::getProjectId, projectId))
                .stream()
                .map(ProjectKnowledge::getDocumentId)
                .toList());
        return vo;
    }

    @Override
    public void enable(Long projectId, Long documentId) {
        requireProject(projectId);
        requireDocument(documentId);
        ProjectKnowledge existing = projectKnowledgeMapper.selectOne(
                Wrappers.<ProjectKnowledge>lambdaQuery()
                        .eq(ProjectKnowledge::getProjectId, projectId)
                        .eq(ProjectKnowledge::getDocumentId, documentId));
        if (existing != null) {
            return;
        }
        ProjectKnowledge row = new ProjectKnowledge();
        row.setProjectId(projectId);
        row.setDocumentId(documentId);
        try {
            projectKnowledgeMapper.insert(row);
        } catch (DuplicateKeyException ignored) {
            // 并发下视为已启用
        }
    }

    @Override
    public void disable(Long projectId, Long documentId) {
        requireProject(projectId);
        projectKnowledgeMapper.delete(
                Wrappers.<ProjectKnowledge>lambdaQuery()
                        .eq(ProjectKnowledge::getProjectId, projectId)
                        .eq(ProjectKnowledge::getDocumentId, documentId));
    }

    private KnowledgeDocument findByHash(String sha256) {
        return documentMapper.selectOne(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .eq(KnowledgeDocument::getSha256, sha256));
    }

    private KnowledgeDocument requireDocument(Long id) {
        KnowledgeDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException("文献不存在");
        }
        return doc;
    }

    /** 解析/合并/分割/嵌入互斥，避免一边写磁盘一边写 Milvus。 */
    private void rejectIfPipelineBusy(KnowledgeDocument doc) {
        if ("parsing".equals(doc.getParseStatus()) || "queued".equals(doc.getParseStatus())) {
            throw new BusinessException("正在解析或已在队列，请稍候");
        }
        if ("merging".equals(doc.getMergeStatus()) || "queued".equals(doc.getMergeStatus())) {
            throw new BusinessException("正在合并或已在队列，请稍候");
        }
        if ("splitting".equals(doc.getSplitStatus()) || "queued".equals(doc.getSplitStatus())) {
            throw new BusinessException("正在分割或已在队列，请稍候");
        }
        if ("embedding".equals(doc.getEmbedStatus()) || "queued".equals(doc.getEmbedStatus())) {
            throw new BusinessException("正在嵌入或已在队列，请稍候");
        }
    }

    /** sidecar 里还有待千问的附图时，解析按钮应续跑而不是弹确认重跑。 */
    private boolean sidecarVlPending(Path elementsPath) {
        if (!Files.exists(elementsPath)) {
            return false;
        }
        try {
            JsonNode root = JSON.readTree(Files.readString(elementsPath));
            JsonNode figures = root.get("figures");
            if (figures == null || !figures.isArray() || figures.isEmpty()) {
                return false;
            }
            for (JsonNode fig : figures) {
                JsonNode statusNode = fig.get("vlStatus");
                String status = statusNode == null || statusNode.isNull() ? "" : statusNode.toString().replace("\"", "");
                if (!StringUtils.hasText(status) || "pending".equals(status)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("读取知识 sidecar 失败 {}: {}", elementsPath, e.getMessage());
            return false;
        }
    }

    private void requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
    }

    private KnowledgeUploadVO item(KnowledgeDocument document, boolean duplicate) {
        KnowledgeUploadVO vo = new KnowledgeUploadVO();
        vo.setDocument(document);
        vo.setDuplicate(duplicate);
        return vo;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
