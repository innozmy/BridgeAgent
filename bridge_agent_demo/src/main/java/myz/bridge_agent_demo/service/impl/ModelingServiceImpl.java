package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.dto.DrawingParseProposal;
import myz.bridge_agent_demo.dto.LedgerConflictItem;
import myz.bridge_agent_demo.dto.PythonModelingResponse;
import myz.bridge_agent_demo.dto.PythonNeedSupplement;
import myz.bridge_agent_demo.dto.SapModelRegisterRequest;
import myz.bridge_agent_demo.entity.AgentStm;
import myz.bridge_agent_demo.entity.ModelTask;
import myz.bridge_agent_demo.entity.ProjectFile;
import myz.bridge_agent_demo.entity.ProjectLtm;
import myz.bridge_agent_demo.exception.BusinessException;
import myz.bridge_agent_demo.mapper.AgentStmMapper;
import myz.bridge_agent_demo.mapper.ProjectFileMapper;
import myz.bridge_agent_demo.mapper.ProjectLtmMapper;
import myz.bridge_agent_demo.service.AgentInjectPackService;
import myz.bridge_agent_demo.service.JobDispatchService;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.ModelTaskService;
import myz.bridge_agent_demo.service.ModelingService;
import myz.bridge_agent_demo.service.PythonAgentClient;
import myz.bridge_agent_demo.service.SapModelService;
import myz.bridge_agent_demo.service.TaskKinds;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 建模编排：规则 check 由 Python 做；本类校验 needSupplement、开子识图、登记 .sdb。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelingServiceImpl implements ModelingService {

    public static final String KIND_MODELING_STM = "modeling";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ModelTaskService modelTaskService;
    private final AgentInjectPackService agentInjectPackService;
    private final PythonAgentClient pythonAgentClient;
    private final SapModelService sapModelService;
    private final FileStorageService fileStorageService;
    private final ProjectFileMapper projectFileMapper;
    private final AgentStmMapper agentStmMapper;
    private final ProjectLtmMapper projectLtmMapper;
    private final ObjectProvider<JobDispatchService> jobDispatchService;

    @Value("${app.modeling.max-auto-supplements:4}")
    private int maxAutoSupplements;

    @Value("${app.modeling.stm-tombstone-limit:80}")
    private int tombstoneLimit;

    @Override
    public void executeJob(Long projectId, Long taskId) {
        try {
            runJob(projectId, taskId);
        } catch (Exception e) {
            log.warn("建模后台异常 projectId={} taskId={}", projectId, taskId, e);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            failParent(projectId, taskId, "建模后台异常：" + reason, "failed");
        }
    }

    private void runJob(Long projectId, Long taskId) {
        ModelTask task = modelTaskService.get(projectId, taskId).getTask();
        if (!TaskKinds.MODELING.equals(task.getKind())) {
            log.warn("建模后台跳过：不是建模工种 projectId={} taskId={} kind={}",
                    projectId, taskId, task.getKind());
            return;
        }
        if ("queued".equals(task.getStatus())) {
            modelTaskService.casStatus(projectId, taskId, "queued", "running");
            task = modelTaskService.get(projectId, taskId).getTask();
        }
        if (!"running".equals(task.getStatus())) {
            log.warn("建模后台跳过：状态不是进行中 projectId={} taskId={} status={}",
                    projectId, taskId, task.getStatus());
            return;
        }
        log.info("建模后台开始 projectId={} taskId={}", projectId, taskId);
        Map<String, Object> pack = agentInjectPackService.buildModelingPack(projectId, taskId);
        PythonModelingResponse response = pythonAgentClient.model(pack);
        if (response == null) {
            failParent(projectId, taskId, "无法连接 Python 建模 Agent（8001）。", "failed");
            return;
        }
        PythonNeedSupplement need = response.getNeedSupplement();
        if (need != null && need.getFileId() != null) {
            openSupplement(projectId, task, need);
            return;
        }
        if (Boolean.FALSE.equals(response.getOk()) || "NO_PAGE".equals(response.getCode())) {
            String err = StringUtils.hasText(response.getError())
                    ? response.getError()
                    : "硬缺口无法自动补识别。请上传对应专页或在概览参数袋手填。";
            failParent(projectId, taskId, err, "failed");
            return;
        }
        if (!Boolean.TRUE.equals(response.getReady()) || !StringUtils.hasText(response.getSapPath())) {
            failParent(projectId, taskId, StringUtils.hasText(response.getError())
                    ? response.getError() : "建模未返回模型文件。", "failed");
            return;
        }
        registerSap(projectId, taskId, response);
    }

    /**
     * 校验 fileId 后插 running 子识图，不走同意卡。次数用尽则父任务失败。
     */
    private void openSupplement(Long projectId, ModelTask parent, PythonNeedSupplement need) {
        Map<String, Object> stm = loadModelingStm(projectId);
        resetCountIfNewModelingTask(stm, parent.getId());
        int used = asInt(stm.get("supplementCount"));
        if (used >= maxAutoSupplements) {
            failParent(projectId, parent.getId(),
                    "自动补充识别已达 " + maxAutoSupplements + " 次，硬缺口仍在。请上传专页或手填参数袋后再同意建模卡。",
                    "failed");
            return;
        }
        Long fileId = need.getFileId();
        ProjectFile file = projectFileMapper.selectById(fileId);
        if (file == null || !projectId.equals(file.getProjectId())) {
            failParent(projectId, parent.getId(), "建模索取的图纸不在本项目目录，未扣次数。", "failed");
            return;
        }
        String name = file.getOriginalName() == null ? "" : file.getOriginalName().toLowerCase();
        if (name.endsWith(".dwg") || name.endsWith(".dxf")) {
            failParent(projectId, parent.getId(), "不能对 CAD 做自动补充识别。", "failed");
            return;
        }
        stm.put("supplementCount", used + 1);
        stm.put("supplementTaskId", parent.getId());
        String asked = need.getMissingKeys() == null || need.getMissingKeys().isEmpty()
                ? null : need.getMissingKeys().get(0);
        addTombstone(stm, fileId, asked == null ? need.getMissingKeys() : List.of(asked),
                need.getSupportCode(), need.getSweep());
        saveModelingStm(projectId, stm);

        ModelTask child = new ModelTask();
        child.setProjectId(projectId);
        child.setKind(TaskKinds.DRAWING_SUPPLEMENT);
        child.setTitle(TaskKinds.titleOf(TaskKinds.DRAWING_SUPPLEMENT));
        child.setStatus("queued");
        child.setFileId(fileId);
        child.setPageKindsJson(writeJson(need.getFocusKinds()));
        child.setSupportCode(blank(need.getSupportCode()));
        child.setDirective(clip("建模硬缺口：" + (need.getReason() == null ? "" : need.getReason()), 500));
        child.setParentTaskId(parent.getId());
        child.setOrigin("auto_supplement");
        child.setAgreedByUserId(parent.getAgreedByUserId());
        child.setAgreedByUsername(parent.getAgreedByUsername());
        Long childId = modelTaskService.insertCard(child).getTask().getId();
        modelTaskService.appendEvent(parent.getId(),
                "硬缺口汇总后自动开补充识别 #" + childId + "（图纸 " + file.getOriginalName()
                        + "，一次补 "
                        + (need.getMissingKeys() == null || need.getMissingKeys().isEmpty()
                        ? "硬缺口" : String.join("、", need.getMissingKeys()))
                        + "）。"
                        + (need.getReason() == null ? "" : need.getReason()));
        modelTaskService.appendEvent(childId,
                "系统自动启动（建模开跑人："
                        + (parent.getAgreedByUsername() == null ? "—" : parent.getAgreedByUsername())
                        + "），无需再点同意。");
        jobDispatchService.getObject().offerTask(projectId, childId);
    }

    private void registerSap(Long projectId, Long taskId, PythonModelingResponse response) {
        Path path = Path.of(response.getSapPath());
        if (!Files.isRegularFile(path)) {
            failParent(projectId, taskId, "【SAP2000】模型文件未落到磁盘。", "failed");
            return;
        }
        try {
            // 未 NewBlank 的空壳或截断复合文档只有几 KB，SAP 打开会报「流的结尾之外」
            if (Files.size(path) < 32_768) {
                failParent(projectId, taskId,
                        "【SAP2000】模型文件过小（" + Files.size(path)
                                + " 字节），空壳或未写完，未登记。请重试建模卡。",
                        "failed");
                return;
            }
        } catch (IOException e) {
            failParent(projectId, taskId, "【SAP2000】无法读取模型文件大小。", "failed");
            return;
        }
        SapModelRegisterRequest request = new SapModelRegisterRequest();
        request.setTaskId(taskId);
        request.setSapVersion(response.getSapVersion());
        request.setOriginalName("task-" + taskId + ".sdb");
        request.setFrameCount(response.getFrameCount());
        request.setJointCount(response.getJointCount());
        String note = response.getNote();
        if (response.getGaps() != null && !response.getGaps().isEmpty()) {
            note = (note == null ? "" : note + "；") + "软缺口 " + String.join("、", response.getGaps());
        }
        if (response.getNotices() != null && !response.getNotices().isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (Map<String, Object> item : response.getNotices()) {
                if (item == null) {
                    continue;
                }
                Object text = item.get("text");
                if (text == null || !StringUtils.hasText(String.valueOf(text))) {
                    continue;
                }
                Object citation = item.get("citation");
                String cite = citation == null ? "" : String.valueOf(citation).trim();
                lines.add((cite.isEmpty() ? "" : cite + " ") + text);
            }
            if (!lines.isEmpty()) {
                note = (note == null ? "" : note + "；") + "规范咨询 " + String.join("；", lines);
            }
        }
        request.setNote(note);
        if (response.getPreviewJson() != null) {
            try {
                request.setPreviewJson(JSON.writeValueAsString(response.getPreviewJson()));
            } catch (JacksonException e) {
                log.warn("previewJson 序列化失败 {}", e.getMessage());
            }
        }
        try (InputStream in = Files.newInputStream(path)) {
            sapModelService.register(projectId, request, in);
        } catch (IOException | BusinessException e) {
            failParent(projectId, taskId, "登记模型失败：" + e.getMessage(), "failed");
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件删不掉不影响版本表
        }
        modelTaskService.updateStatus(projectId, taskId, "done");
        modelTaskService.appendEvent(taskId, "已保存本机 SAP 模型并登记版本。"
                + (note == null ? "" : note));
        appendLtm(projectId, Map.of(
                "kind", KIND_MODELING_STM,
                "taskId", taskId,
                "outcome", "done",
                "at", Instant.now().toString()));
    }

    @Override
    public void onDrawingChildSettled(Long projectId, Long childTaskId, String childStatus) {
        ModelTask child = modelTaskService.get(projectId, childTaskId).getTask();
        if (child.getParentTaskId() == null) {
            return;
        }
        Long parentId = child.getParentTaskId();
        ModelTask parent = modelTaskService.get(projectId, parentId).getTask();
        if (!TaskKinds.MODELING.equals(parent.getKind()) || !"running".equals(parent.getStatus())) {
            return;
        }
        if ("waiting".equals(childStatus)) {
            String detail = conflictSummary(child.getProposalJson());
            modelTaskService.appendEvent(parentId,
                    "子识图 #" + childTaskId + " 与账本冲突，未写入。"
                            + (detail.isEmpty() ? "请打开该子任务查看覆盖前/后。" : detail)
                            + " 请在子任务上确认写入后，才会继续建模。");
            return;
        }
        if ("failed".equals(childStatus) || "rejected".equals(childStatus)) {
            failParent(projectId, parentId, "放弃写入或子识图失败，硬缺口未补。请处理后再同意一张建模卡。", "failed");
            return;
        }
        if ("done".equals(childStatus)) {
            modelTaskService.appendEvent(parentId, "子识图 #" + childTaskId + " 已结束，继续建模检查。");
            jobDispatchService.getObject().resumeModeling(projectId, parentId);
        }
    }

    private void failParent(Long projectId, Long taskId, String reason, String outcome) {
        modelTaskService.updateStatus(projectId, taskId, "failed");
        modelTaskService.appendEvent(taskId, reason);
        appendLtm(projectId, Map.of(
                "kind", KIND_MODELING_STM,
                "taskId", taskId,
                "outcome", outcome,
                "lesson", clip(reason, 500),
                "at", Instant.now().toString()));
    }

    /**
     * 自动补识次数跟当前建模任务走，不跨卡累加（上一张卡用过 1 次，新卡仍从 0 起）。
     */
    private void resetCountIfNewModelingTask(Map<String, Object> stm, Long taskId) {
        Object prev = stm.get("supplementTaskId");
        long prevId = prev instanceof Number n ? n.longValue() : -1L;
        if (prevId != taskId) {
            stm.put("supplementCount", 0);
            stm.put("supplementTaskId", taskId);
        }
    }

    private Map<String, Object> loadModelingStm(Long projectId) {
        AgentStm row = agentStmMapper.selectOne(
                Wrappers.lambdaQuery(AgentStm.class)
                        .eq(AgentStm::getProjectId, projectId)
                        .eq(AgentStm::getAgentKind, KIND_MODELING_STM));
        if (row == null || !StringUtils.hasText(row.getBodyJson())) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("supplementCount", 0);
            empty.put("tombstones", new ArrayList<>());
            return empty;
        }
        try {
            return JSON.readValue(row.getBodyJson(), new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JacksonException e) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("supplementCount", 0);
            empty.put("tombstones", new ArrayList<>());
            return empty;
        }
    }

    private void saveModelingStm(Long projectId, Map<String, Object> body) {
        Object tombs = body.get("tombstones");
        if (tombs instanceof List<?> list && list.size() > tombstoneLimit) {
            body.put("tombstones", new ArrayList<>(list.subList(list.size() - tombstoneLimit, list.size())));
        }
        String json;
        try {
            json = JSON.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
        AgentStm existing = agentStmMapper.selectOne(
                Wrappers.lambdaQuery(AgentStm.class)
                        .eq(AgentStm::getProjectId, projectId)
                        .eq(AgentStm::getAgentKind, KIND_MODELING_STM));
        if (existing == null) {
            AgentStm row = new AgentStm();
            row.setProjectId(projectId);
            row.setAgentKind(KIND_MODELING_STM);
            row.setBodyJson(json);
            agentStmMapper.insert(row);
            return;
        }
        existing.setBodyJson(json);
        agentStmMapper.updateById(existing);
    }

    /** 把子识图提案里的冲突项写成「覆盖前 → 覆盖后」，挂到父建模时间线。 */
    private String conflictSummary(String proposalJson) {
        if (!StringUtils.hasText(proposalJson)) {
            return "";
        }
        try {
            DrawingParseProposal proposal = JSON.readValue(proposalJson, DrawingParseProposal.class);
            if (proposal.getConflicts() == null || proposal.getConflicts().isEmpty()) {
                return "";
            }
            List<String> lines = new ArrayList<>();
            for (LedgerConflictItem item : proposal.getConflicts()) {
                String field = item.getField() == null ? "字段" : item.getField();
                lines.add(field + "：覆盖前「" + dash(item.getBefore()) + "」→ 覆盖后「" + dash(item.getAfter()) + "」");
            }
            return String.join("；", lines) + "。";
        } catch (JacksonException e) {
            return "";
        }
    }

    private static String dash(String value) {
        return StringUtils.hasText(value) ? value : "未给";
    }

    /**
     * 记下已对某 PDF 要过哪些硬缺口。sweep=true 表示识图侧会扩扫相关页，同一 key 不再对这份图空转。
     * 旧墓碑无 sweep 字段视为未遍历，下一张建模卡仍可再识一轮。
     */
    private void addTombstone(Map<String, Object> stm, Long fileId, List<String> keys,
                             String supportCode, Boolean sweep) {
        List<Object> tombs = new ArrayList<>();
        Object raw = stm.get("tombstones");
        if (raw instanceof List<?> list) {
            tombs.addAll(list);
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fileId", fileId);
        item.put("missingKeys", keys == null ? List.of() : keys);
        if (keys != null && !keys.isEmpty()) {
            item.put("askedKey", keys.get(0));
        }
        if (StringUtils.hasText(supportCode)) {
            item.put("supportCode", supportCode);
        }
        // 编排侧默认按扩扫记：Python 未带 sweep 时也视为本轮会遍历相关页
        item.put("sweep", sweep == null || Boolean.TRUE.equals(sweep));
        tombs.add(item);
        stm.put("tombstones", tombs);
    }

    @SuppressWarnings("unchecked")
    private void appendLtm(Long projectId, Map<String, Object> entry) {
        ProjectLtm existing = projectLtmMapper.selectOne(
                Wrappers.lambdaQuery(ProjectLtm.class).eq(ProjectLtm::getProjectId, projectId));
        Map<String, Object> body = new LinkedHashMap<>();
        if (existing != null && StringUtils.hasText(existing.getBodyJson())) {
            try {
                body = JSON.readValue(existing.getBodyJson(), new TypeReference<LinkedHashMap<String, Object>>() { });
            } catch (JacksonException ignored) {
                body = new LinkedHashMap<>();
            }
        }
        List<Object> entries = new ArrayList<>();
        Object raw = body.get("entries");
        if (raw instanceof List<?> list) {
            entries.addAll(list);
        }
        entries.add(entry);
        body.put("entries", entries);
        String json;
        try {
            json = JSON.writeValueAsString(body);
        } catch (JacksonException e) {
            return;
        }
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

    private String writeJson(List<String> kinds) {
        try {
            return JSON.writeValueAsString(kinds == null ? List.of() : kinds);
        } catch (JacksonException e) {
            return "[]";
        }
    }

    private static int asInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static String blank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
