package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.dto.PythonParseResponse;
import myz.bridge_agent_demo.entity.AgentStm;
import myz.bridge_agent_demo.entity.DrawingPageMap;
import myz.bridge_agent_demo.entity.ProjectLtm;
import myz.bridge_agent_demo.mapper.AgentStmMapper;
import myz.bridge_agent_demo.mapper.DrawingPageMapMapper;
import myz.bridge_agent_demo.mapper.ProjectLtmMapper;
import myz.bridge_agent_demo.service.DrawingMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 Python 返回的页地图与过程索引写入 MySQL，不写 Python 磁盘。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrawingMemoryServiceImpl implements DrawingMemoryService {

    public static final String KIND_DRAWING = "drawing_parse";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DrawingPageMapMapper drawingPageMapMapper;
    private final AgentStmMapper agentStmMapper;
    private final ProjectLtmMapper projectLtmMapper;

    @Override
    @Transactional
    public void submitRoundMap(Long projectId, List<Long> fileIds, PythonParseResponse response) {
        Map<String, Object> pageMap = response == null ? null : response.getPageMap();
        if (pageMap != null && !pageMap.isEmpty()) {
            upsertPageMap(projectId, pageMap);
        }
        Boolean hasLayout = response == null ? null : response.getHasLayoutPages();
        upsertDrawingStm(projectId, fileIds, hasLayout, null);
    }

    @Override
    @Transactional
    public void submitParseResult(Long projectId, Long taskId, List<Long> fileIds, PythonParseResponse response, String outcome) {
        Map<String, Object> pageMap = response == null ? null : response.getPageMap();
        if (pageMap != null && !pageMap.isEmpty()) {
            upsertPageMap(projectId, pageMap);
        }
        Boolean hasLayout = response == null ? null : response.getHasLayoutPages();
        upsertDrawingStm(projectId, fileIds, hasLayout, null);
        appendLtm(projectId, Map.of(
                "kind", KIND_DRAWING,
                "taskId", taskId,
                "fileIds", fileIds == null ? List.of() : fileIds,
                "outcome", outcome,
                "hasLayoutPages", hasLayout != null && hasLayout,
                "at", Instant.now().toString()
        ));
    }

    @Override
    @Transactional
    public void submitParseFailure(Long projectId, Long taskId, String reason) {
        appendLtm(projectId, Map.of(
                "kind", KIND_DRAWING,
                "taskId", taskId,
                "outcome", "failed",
                "lesson", reason == null ? "" : clip(reason, 500),
                "at", Instant.now().toString()
        ));
    }

    @Override
    @Transactional
    public void submitParseRejected(Long projectId, Long taskId) {
        upsertDrawingStm(projectId, null, null, taskId);
        appendLtm(projectId, Map.of(
                "kind", KIND_DRAWING,
                "taskId", taskId,
                "outcome", "rejected",
                "at", Instant.now().toString()
        ));
    }

    private void upsertPageMap(Long projectId, Map<String, Object> pageMap) {
        Object fileRaw = pageMap.get("fileId");
        if (!(fileRaw instanceof Number)) {
            log.warn("页地图缺少 fileId，未写入 projectId={}", projectId);
            return;
        }
        long fileId = ((Number) fileRaw).longValue();
        String sha = pageMap.get("sha256") == null ? "" : String.valueOf(pageMap.get("sha256"));
        String json;
        try {
            json = JSON.writeValueAsString(pageMap);
        } catch (JacksonException e) {
            log.warn("页地图序列化失败 {}", e.getMessage());
            return;
        }
        DrawingPageMap existing = drawingPageMapMapper.selectOne(
                Wrappers.lambdaQuery(DrawingPageMap.class)
                        .eq(DrawingPageMap::getProjectId, projectId)
                        .eq(DrawingPageMap::getFileId, fileId));
        if (existing == null) {
            DrawingPageMap row = new DrawingPageMap();
            row.setProjectId(projectId);
            row.setFileId(fileId);
            row.setSha256(sha.isBlank() ? "0".repeat(64) : sha);
            row.setMapJson(json);
            drawingPageMapMapper.insert(row);
            return;
        }
        existing.setSha256(sha.isBlank() ? existing.getSha256() : sha);
        existing.setMapJson(json);
        drawingPageMapMapper.updateById(existing);
    }

    @SuppressWarnings("unchecked")
    private void upsertDrawingStm(Long projectId, List<Long> fileIds, Boolean hasLayout, Long rejectedTaskId) {
        AgentStm existing = agentStmMapper.selectOne(
                Wrappers.lambdaQuery(AgentStm.class)
                        .eq(AgentStm::getProjectId, projectId)
                        .eq(AgentStm::getAgentKind, KIND_DRAWING));
        Map<String, Object> body = existing == null ? new LinkedHashMap<>() : readMap(existing.getBodyJson());
        Set<Long> ids = new LinkedHashSet<>();
        Object prev = body.get("fileIds");
        if (prev instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number n) {
                    ids.add(n.longValue());
                }
            }
        }
        if (fileIds != null) {
            ids.addAll(fileIds);
        }
        body.put("fileIds", new ArrayList<>(ids));
        if (hasLayout != null) {
            body.put("hasLayoutPages", hasLayout);
        }
        List<Long> tombs = new ArrayList<>();
        Object tombRaw = body.get("rejectedTaskIds");
        if (tombRaw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number n) {
                    tombs.add(n.longValue());
                }
            }
        }
        if (rejectedTaskId != null && !tombs.contains(rejectedTaskId)) {
            tombs.add(rejectedTaskId);
        }
        body.put("rejectedTaskIds", tombs);
        String json = writeMap(body);
        if (existing == null) {
            AgentStm row = new AgentStm();
            row.setProjectId(projectId);
            row.setAgentKind(KIND_DRAWING);
            row.setBodyJson(json);
            agentStmMapper.insert(row);
            return;
        }
        existing.setBodyJson(json);
        agentStmMapper.updateById(existing);
    }

    @SuppressWarnings("unchecked")
    private void appendLtm(Long projectId, Map<String, Object> entry) {
        ProjectLtm existing = projectLtmMapper.selectOne(
                Wrappers.lambdaQuery(ProjectLtm.class).eq(ProjectLtm::getProjectId, projectId));
        Map<String, Object> body = existing == null ? new LinkedHashMap<>() : readMap(existing.getBodyJson());
        List<Object> entries = new ArrayList<>();
        Object raw = body.get("entries");
        if (raw instanceof List<?> list) {
            entries.addAll(list);
        }
        entries.add(entry);
        body.put("entries", entries);
        String json = writeMap(body);
        if (existing == null) {
            ProjectLtm row = new ProjectLtm();
            row.setProjectId(projectId);
            row.setBodyJson(json);
            projectLtmMapper.insert(row);
            return;
        }
        existing.setBodyJson(json);
        projectLtmMapper.updateById(existing);
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JacksonException e) {
            log.warn("记忆 JSON 损坏，按空对象继续 {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String writeMap(Map<String, Object> body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IllegalStateException("记忆 JSON 序列化失败", e);
        }
    }

    private static String clip(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
