package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.entity.AgentStm;
import myz.bridge_agent_demo.entity.DrawingPageMap;
import myz.bridge_agent_demo.entity.FieldProvenance;
import myz.bridge_agent_demo.entity.InquiryMessage;
import myz.bridge_agent_demo.entity.InquiryStm;
import myz.bridge_agent_demo.entity.ProjectFieldMeta;
import myz.bridge_agent_demo.entity.ProjectFile;
import myz.bridge_agent_demo.entity.ModelTask;
import myz.bridge_agent_demo.entity.ProjectLtm;
import myz.bridge_agent_demo.entity.ProjectParam;
import myz.bridge_agent_demo.entity.ProjectSapModel;
import myz.bridge_agent_demo.entity.ProjectUnit;
import myz.bridge_agent_demo.entity.ProjectUnitColumn;
import myz.bridge_agent_demo.entity.ProjectUnitSupport;
import myz.bridge_agent_demo.mapper.AgentStmMapper;
import myz.bridge_agent_demo.mapper.DrawingPageMapMapper;
import myz.bridge_agent_demo.mapper.InquiryStmMapper;
import myz.bridge_agent_demo.mapper.ModelTaskMapper;
import myz.bridge_agent_demo.mapper.ProjectLtmMapper;
import myz.bridge_agent_demo.mapper.ProjectSapModelMapper;
import myz.bridge_agent_demo.service.AgentInjectPackService;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.KnowledgeScopeService;
import myz.bridge_agent_demo.service.ProjectService;
import myz.bridge_agent_demo.vo.ProjectVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 MySQL 里已提交的账本与过程索引打成问询/建模 HTTP 包。
 * 问询按窗口 / 约 70% 字符预算裁切（不裁账本数字、不裁 knowledgeScope 路径）。
 * 账本数字不压成散文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentInjectPackServiceImpl implements AgentInjectPackService {

    /** 千问输入按 32k 估；真实上限更大时只是更早裁切，不会撑爆 */
    private static final int MODEL_INPUT_TOKENS = 32_768;
    private static final int SUMMARY_MAX_CHARS = 4_000;
    private static final int OLDER_TURN_CHARS = 200;
    private static final int LTM_KEEP = 20;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ProjectService projectService;
    private final AgentStmMapper agentStmMapper;
    private final DrawingPageMapMapper drawingPageMapMapper;
    private final ProjectLtmMapper projectLtmMapper;
    private final InquiryStmMapper inquiryStmMapper;
    private final ModelTaskMapper modelTaskMapper;
    private final ProjectSapModelMapper projectSapModelMapper;
    private final FileStorageService fileStorageService;
    private final KnowledgeScopeService knowledgeScopeService;

    @Value("${app.memory.inquiry-window-turns:8}")
    private int windowTurns;

    @Value("${app.memory.context-budget-ratio:0.7}")
    private double budgetRatio;

    @Value("${app.sap.prog-id:CSI.SAP2000.API.SapObject}")
    private String sapProgId;

    @Value("${app.sap.version:22.0.0}")
    private String sapVersion;

    @Override
    public Map<String, Object> buildInquiryPack(Long projectId, Long threadId,
                                                List<InquiryMessage> history, String currentUserText) {
        List<InquiryMessage> usable = new ArrayList<>();
        if (history != null) {
            for (InquiryMessage message : history) {
                if (!"event".equals(message.getRole())) {
                    usable.add(message);
                }
            }
        }
        ProjectVO project = projectService.getById(projectId);
        int windowSize = Math.max(1, windowTurns) * 2;
        List<InquiryMessage> older = new ArrayList<>();
        List<InquiryMessage> recent = new ArrayList<>();
        splitWindow(usable, windowSize, older, recent);

        String summary = foldSummary(loadInquirySummary(threadId), older);

        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("projectId", projectId);
        pack.put("inquiryThreadId", threadId);
        pack.put("agentKind", "inquiry");
        pack.put("ledger", ledgerOf(project));
        pack.put("files", filesOf(project));
        pack.put("agentStm", agentStmOf(projectId));
        pack.put("pageMapIndex", pageMapIndexOf(projectId, project));
        pack.put("ltm", ltmOf(projectId));
        pack.put("inquiryStm", inquiryStmOf(summary, recent, loadSubmitted(threadId)));
        pack.put("message", currentUserText);
        // 启用∩已嵌入∩规范；含 rootPath 供 Python 补发，不扫总库
        pack.put("knowledgeScope", knowledgeScopeService.buildKnowledgeScope(projectId));
        trimToBudget(pack);
        return pack;
    }

    @Override
    public Map<String, Object> buildLedger(ProjectVO project) {
        return ledgerOf(project);
    }

    @Override
    public List<Map<String, Object>> buildDrawingCatalog(ProjectVO project) {
        List<Map<String, Object>> catalog = new ArrayList<>();
        if (project.getFiles() == null) {
            return catalog;
        }
        Map<Long, DrawingPageMap> maps = new LinkedHashMap<>();
        if (project.getId() != null) {
            List<DrawingPageMap> rows = drawingPageMapMapper.selectList(
                    Wrappers.lambdaQuery(DrawingPageMap.class).eq(DrawingPageMap::getProjectId, project.getId()));
            for (DrawingPageMap row : rows) {
                maps.put(row.getFileId(), row);
            }
        }
        for (ProjectFile file : project.getFiles()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileId", file.getId());
            item.put("originalName", file.getOriginalName());
            item.put("kind", file.getKind());
            item.put("parseStatus", file.getParseStatus());
            DrawingPageMap row = maps.get(file.getId());
            if (row != null && StringUtils.hasText(row.getMapJson())) {
                Map<String, Object> mapJson = readMap(row.getMapJson());
                item.put("totalPages", mapJson.get("totalPages"));
                item.put("kindCounts", kindCounts(mapJson.get("pages")));
                item.put("gaps", mapJson.get("gaps") == null ? List.of() : mapJson.get("gaps"));
            } else {
                item.put("kindCounts", Map.of());
                item.put("gaps", List.of());
            }
            catalog.add(item);
        }
        return catalog;
    }

    @Override
    public Map<String, Object> buildModelingPack(Long projectId, Long taskId) {
        ProjectVO project = projectService.getById(projectId);
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("projectId", projectId);
        pack.put("taskId", taskId);
        pack.put("agentKind", "modeling");
        pack.put("ledger", ledgerOf(project));
        pack.put("drawingCatalog", buildDrawingCatalog(project));
        pack.put("pageMapIndex", pageMapIndexOf(projectId, project));
        Map<String, Object> allStm = agentStmOf(projectId);
        Object modelingStm = allStm.get("modeling");
        pack.put("modelingStm", modelingStm instanceof Map<?, ?> ? modelingStm : Map.of());
        ModelTask task = modelTaskMapper.selectById(taskId);
        if (task != null && StringUtils.hasText(task.getDirective())) {
            pack.put("directive", task.getDirective());
        }
        ProjectSapModel latest = projectSapModelMapper.selectOne(
                Wrappers.lambdaQuery(ProjectSapModel.class)
                        .eq(ProjectSapModel::getProjectId, projectId)
                        .orderByDesc(ProjectSapModel::getSeq)
                        .last("LIMIT 1"));
        if (latest != null) {
            Map<String, Object> ptr = new LinkedHashMap<>();
            ptr.put("id", latest.getId());
            ptr.put("seq", latest.getSeq());
            ptr.put("sapVersion", latest.getSapVersion());
            ptr.put("storagePath", latest.getStoragePath());
            ptr.put("note", latest.getNote());
            pack.put("latestSapModel", ptr);
        }
        Map<String, Object> sap = new LinkedHashMap<>();
        sap.put("progId", sapProgId);
        sap.put("sapVersion", sapVersion);
        try {
            sap.put("sapOutputPath", fileStorageService.prepareModelDir(projectId)
                    .resolve("task-" + taskId + "-build.sdb").toAbsolutePath().toString());
        } catch (Exception e) {
            sap.put("sapOutputPath", "");
        }
        pack.put("sap", sap);
        // consult 检索范围 + 补发路径；check/spec/build 不读 LTM
        pack.put("knowledgeScope", knowledgeScopeService.buildKnowledgeScope(projectId));
        pack.put("ltm", ltmOf(projectId));
        return pack;
    }

    private void splitWindow(List<InquiryMessage> history, int windowSize,
                             List<InquiryMessage> older, List<InquiryMessage> recent) {
        if (history.size() <= windowSize) {
            recent.addAll(history);
            return;
        }
        older.addAll(history.subList(0, history.size() - windowSize));
        recent.addAll(history.subList(history.size() - windowSize, history.size()));
    }

    private String loadInquirySummary(Long threadId) {
        InquiryStm row = inquiryStmMapper.selectOne(
                Wrappers.lambdaQuery(InquiryStm.class).eq(InquiryStm::getThreadId, threadId));
        return row == null ? "" : (row.getSummary() == null ? "" : row.getSummary());
    }

    private String foldSummary(String existing, List<InquiryMessage> older) {
        if (older.isEmpty()) {
            return existing == null ? "" : existing.trim();
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(existing)) {
            sb.append(existing.trim()).append('\n');
        }
        for (InquiryMessage message : older) {
            if ("event".equals(message.getRole())) {
                continue;
            }
            sb.append("agent".equals(message.getRole()) ? "助手：" : "用户：");
            sb.append(clip(nullToEmpty(message.getBody()), OLDER_TURN_CHARS)).append('\n');
        }
        return clip(sb.toString().trim(), SUMMARY_MAX_CHARS);
    }

    private Map<String, Object> ledgerOf(ProjectVO project) {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("confirmed", true);
        ledger.put("name", project.getName());
        ledger.put("carriageway", project.getCarriageway());
        ledger.put("code", project.getCode());
        ledger.put("intro", project.getIntro());
        ledger.put("region", project.getRegion());
        ledger.put("openedOn", project.getOpenedOn() == null ? null : project.getOpenedOn().toString());
        ledger.put("codeStrategy", project.getCodeStrategy());
        ledger.put("girderType", project.getGirderType());
        ledger.put("layoutType", project.getLayoutType());
        ledger.put("material", project.getMaterial());
        ledger.put("status", project.getStatus());
        List<Map<String, Object>> units = new ArrayList<>();
        if (project.getUnits() != null) {
            for (ProjectUnit unit : project.getUnits()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("seq", unit.getSeq());
                row.put("spansM", unit.getSpansM());
                row.put("lengthM", unit.getLengthM());
                row.put("source", unit.getSource());
                row.put("supports", supportsOf(unit));
                units.add(row);
            }
        }
        ledger.put("units", units);
        ledger.put("fieldMeta", fieldMetaOf(project.getFieldMeta()));
        ledger.put("params", paramsOf(project.getParams()));
        return ledger;
    }

    /** 把一联的墩台/柱打成投影，数字保持原样不压散文。 */
    private List<Map<String, Object>> supportsOf(ProjectUnit unit) {
        List<Map<String, Object>> supports = new ArrayList<>();
        if (unit.getSupports() == null) {
            return supports;
        }
        for (ProjectUnitSupport support : unit.getSupports()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", support.getSeq());
            row.put("code", support.getCode());
            row.put("kind", support.getKind());
            row.put("source", support.getSource());
            List<Map<String, Object>> columns = new ArrayList<>();
            if (support.getColumns() != null) {
                for (ProjectUnitColumn column : support.getColumns()) {
                    Map<String, Object> col = new LinkedHashMap<>();
                    col.put("seq", column.getSeq());
                    col.put("side", column.getSide());
                    col.put("heightM", column.getHeightM());
                    col.put("source", column.getSource());
                    columns.add(col);
                }
            }
            row.put("columns", columns);
            supports.add(row);
        }
        return supports;
    }

    private Map<String, Object> fieldMetaOf(ProjectFieldMeta meta) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (meta == null) {
            return out;
        }
        putProvenance(out, "code", meta.getCode());
        putProvenance(out, "girderType", meta.getGirderType());
        putProvenance(out, "layoutType", meta.getLayoutType());
        putProvenance(out, "material", meta.getMaterial());
        putProvenance(out, "region", meta.getRegion());
        putProvenance(out, "spansM", meta.getSpansM());
        out.put("gaps", meta.getGaps() == null ? List.of() : meta.getGaps());
        out.put("hasLayoutPages", meta.getHasLayoutPages());
        return out;
    }

    private void putProvenance(Map<String, Object> out, String key, FieldProvenance provenance) {
        if (provenance == null) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", provenance.getSource());
        row.put("drawingValue", provenance.getDrawingValue());
        out.put(key, row);
    }

    private List<Map<String, Object>> paramsOf(List<ProjectParam> rows) {
        List<Map<String, Object>> params = new ArrayList<>();
        if (rows == null) {
            return params;
        }
        for (ProjectParam row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", row.getParamKey());
            item.put("label", row.getLabel());
            item.put("value", row.getValueText());
            item.put("unit", row.getUnit());
            item.put("source", row.getSource());
            params.add(item);
        }
        return params;
    }

    private List<Map<String, Object>> filesOf(ProjectVO project) {
        List<Map<String, Object>> files = new ArrayList<>();
        if (project.getFiles() == null) {
            return files;
        }
        for (ProjectFile file : project.getFiles()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileId", file.getId());
            item.put("originalName", file.getOriginalName());
            item.put("kind", file.getKind());
            item.put("parseStatus", file.getParseStatus());
            files.add(item);
        }
        return files;
    }

    private Map<String, Object> agentStmOf(Long projectId) {
        List<AgentStm> rows = agentStmMapper.selectList(
                Wrappers.lambdaQuery(AgentStm.class).eq(AgentStm::getProjectId, projectId));
        Map<String, Object> byKind = new LinkedHashMap<>();
        for (AgentStm row : rows) {
            byKind.put(row.getAgentKind(), readMap(row.getBodyJson()));
        }
        return byKind;
    }

    private List<Map<String, Object>> pageMapIndexOf(Long projectId, ProjectVO project) {
        Map<Long, String> names = new LinkedHashMap<>();
        if (project.getFiles() != null) {
            for (ProjectFile file : project.getFiles()) {
                names.put(file.getId(), file.getOriginalName());
            }
        }
        List<DrawingPageMap> rows = drawingPageMapMapper.selectList(
                Wrappers.lambdaQuery(DrawingPageMap.class).eq(DrawingPageMap::getProjectId, projectId));
        List<Map<String, Object>> index = new ArrayList<>();
        for (DrawingPageMap row : rows) {
            Map<String, Object> mapJson = readMap(row.getMapJson());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileId", row.getFileId());
            item.put("originalName", names.get(row.getFileId()));
            item.put("sha256", row.getSha256());
            item.put("totalPages", mapJson.get("totalPages"));
            item.put("kindCounts", kindCounts(mapJson.get("pages")));
            item.put("gaps", mapJson.get("gaps"));
            Object extracts = mapJson.get("extracts");
            item.put("extractCount", extracts instanceof List<?> list ? list.size() : 0);
            index.add(item);
        }
        return index;
    }

    private Map<String, Integer> kindCounts(Object pagesRaw) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (!(pagesRaw instanceof List<?> pages)) {
            return counts;
        }
        for (Object item : pages) {
            if (!(item instanceof Map<?, ?> page)) {
                continue;
            }
            Object kind = page.get("kind");
            String key = kind == null ? "other" : String.valueOf(kind);
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Object> ltmOf(Long projectId) {
        ProjectLtm row = projectLtmMapper.selectOne(
                Wrappers.lambdaQuery(ProjectLtm.class).eq(ProjectLtm::getProjectId, projectId));
        Map<String, Object> body = row == null ? new LinkedHashMap<>() : readMap(row.getBodyJson());
        Object raw = body.get("entries");
        List<Object> entries = new ArrayList<>();
        if (raw instanceof List<?> list) {
            int from = Math.max(0, list.size() - LTM_KEEP);
            entries.addAll(list.subList(from, list.size()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entries", entries);
        return out;
    }

    private List<Long> loadSubmitted(Long threadId) {
        InquiryStm row = inquiryStmMapper.selectOne(
                Wrappers.lambdaQuery(InquiryStm.class).eq(InquiryStm::getThreadId, threadId));
        if (row == null || !StringUtils.hasText(row.getSubmittedJson())) {
            return List.of();
        }
        try {
            return JSON.readValue(row.getSubmittedJson(), new TypeReference<List<Long>>() { });
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private Map<String, Object> inquiryStmOf(String summary, List<InquiryMessage> recent, List<Long> submitted) {
        Map<String, Object> stm = new LinkedHashMap<>();
        stm.put("summary", summary == null ? "" : summary);
        stm.put("submittedTaskIds", submitted == null ? List.of() : submitted);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (InquiryMessage message : recent) {
            if ("event".equals(message.getRole())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("body", message.getBody());
            messages.add(item);
        }
        stm.put("recentMessages", messages);
        return stm;
    }

    /**
     * 超预算时先砍页索引细节和 LTM，再缩短摘要与窗口正文；账本与 knowledgeScope 保持原样。
     */
    @SuppressWarnings("unchecked")
    private void trimToBudget(Map<String, Object> pack) {
        int budget = Math.max(1024, (int) (MODEL_INPUT_TOKENS * budgetRatio));
        if (estimatedTokens(pack) <= budget) {
            return;
        }
        Object indexRaw = pack.get("pageMapIndex");
        if (indexRaw instanceof List<?> index) {
            List<Map<String, Object>> slim = new ArrayList<>();
            for (Object item : index) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fileId", map.get("fileId"));
                row.put("originalName", map.get("originalName"));
                row.put("totalPages", map.get("totalPages"));
                slim.add(row);
            }
            pack.put("pageMapIndex", slim);
        }
        if (estimatedTokens(pack) <= budget) {
            return;
        }
        Object ltmRaw = pack.get("ltm");
        if (ltmRaw instanceof Map<?, ?> ltm) {
            Map<String, Object> slim = new LinkedHashMap<>();
            Object entries = ltm.get("entries");
            if (entries instanceof List<?> list && list.size() > 5) {
                slim.put("entries", new ArrayList<>(list.subList(list.size() - 5, list.size())));
            } else {
                slim.put("entries", entries);
            }
            pack.put("ltm", slim);
        }
        if (estimatedTokens(pack) <= budget) {
            return;
        }
        Object stmRaw = pack.get("inquiryStm");
        if (stmRaw instanceof Map<?, ?> stmMap) {
            Map<String, Object> stm = new LinkedHashMap<>((Map<String, Object>) stmMap);
            stm.put("summary", clip(String.valueOf(stm.get("summary") == null ? "" : stm.get("summary")), 1500));
            Object recentRaw = stm.get("recentMessages");
            if (recentRaw instanceof List<?> recent) {
                List<Map<String, Object>> clipped = new ArrayList<>();
                for (Object item : recent) {
                    if (!(item instanceof Map<?, ?> msg)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("role", msg.get("role"));
                    row.put("body", clip(String.valueOf(msg.get("body") == null ? "" : msg.get("body")), 500));
                    clipped.add(row);
                }
                stm.put("recentMessages", clipped);
            }
            pack.put("inquiryStm", stm);
        }
        if (estimatedTokens(pack) > budget) {
            log.warn("问询注入包仍超过预算 tokens≈{} budget={}", estimatedTokens(pack), budget);
        }
    }

    private int estimatedTokens(Map<String, Object> pack) {
        try {
            return JSON.writeValueAsString(pack).length() / 2;
        } catch (JacksonException e) {
            return Integer.MAX_VALUE;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JacksonException e) {
            log.warn("注入包 JSON 损坏，按空对象继续 {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static String clip(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
