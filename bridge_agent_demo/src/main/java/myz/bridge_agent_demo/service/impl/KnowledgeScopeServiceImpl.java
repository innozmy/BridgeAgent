package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import myz.bridge_agent_demo.entity.KnowledgeDocument;
import myz.bridge_agent_demo.entity.ProjectKnowledge;
import myz.bridge_agent_demo.mapper.KnowledgeDocumentMapper;
import myz.bridge_agent_demo.mapper.ProjectKnowledgeMapper;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.KnowledgeScopeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 只读组包：查启用关系与文献元数据，拼 rootPath。不调 Python、不开作业。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeScopeServiceImpl implements KnowledgeScopeService {

    private final KnowledgeDocumentMapper documentMapper;
    private final ProjectKnowledgeMapper projectKnowledgeMapper;
    private final FileStorageService fileStorageService;

    @Override
    public List<KnowledgeDocument> listEnabledEmbeddedCodes(Long projectId) {
        LinkedHashSet<Long> enabled = new LinkedHashSet<>(
                projectKnowledgeMapper.selectList(
                                Wrappers.<ProjectKnowledge>lambdaQuery()
                                        .eq(ProjectKnowledge::getProjectId, projectId))
                        .stream()
                        .map(ProjectKnowledge::getDocumentId)
                        .toList());
        if (enabled.isEmpty()) {
            return List.of();
        }
        List<KnowledgeDocument> found = documentMapper.selectList(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .in(KnowledgeDocument::getId, enabled)
                        .eq(KnowledgeDocument::getCategory, "code")
                        .eq(KnowledgeDocument::getEmbedStatus, "embedded"));
        Map<Long, KnowledgeDocument> byId = new LinkedHashMap<>();
        for (KnowledgeDocument doc : found) {
            byId.put(doc.getId(), doc);
        }
        List<KnowledgeDocument> ordered = new ArrayList<>();
        for (Long id : enabled) {
            KnowledgeDocument doc = byId.get(id);
            if (doc != null) {
                ordered.add(doc);
            }
        }
        return ordered;
    }

    @Override
    public Map<String, Object> buildKnowledgeScope(Long projectId) {
        List<KnowledgeDocument> docs = listEnabledEmbeddedCodes(projectId);
        List<Long> ids = new ArrayList<>();
        Map<String, String> names = new LinkedHashMap<>();
        List<Map<String, Object>> documents = new ArrayList<>();
        for (KnowledgeDocument doc : docs) {
            Long id = doc.getId();
            ids.add(id);
            names.put(String.valueOf(id), doc.getName() == null ? "" : doc.getName());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("documentId", id);
            item.put("name", doc.getName());
            item.put("sha256", doc.getSha256());
            item.put("familyCode", doc.getFamilyCode());
            String rootPath = "";
            if (StringUtils.hasText(doc.getSha256())) {
                rootPath = fileStorageService.knowledgeDocDir(doc.getSha256()).toAbsolutePath().toString();
            }
            item.put("rootPath", rootPath);
            documents.add(item);
        }
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("documentIds", ids);
        scope.put("documentNames", names);
        scope.put("documents", documents);
        return scope;
    }
}
