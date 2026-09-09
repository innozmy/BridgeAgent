package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.entity.KnowledgeDocument;
import myz.bridge_agent_demo.entity.ModelTask;
import myz.bridge_agent_demo.entity.Project;
import myz.bridge_agent_demo.mapper.KnowledgeDocumentMapper;
import myz.bridge_agent_demo.mapper.ModelTaskMapper;
import myz.bridge_agent_demo.mapper.ProjectMapper;
import myz.bridge_agent_demo.service.AgentJobLauncher;
import myz.bridge_agent_demo.service.JobDispatchService;
import myz.bridge_agent_demo.service.ModelTaskService;
import myz.bridge_agent_demo.service.PermissionService;
import myz.bridge_agent_demo.service.TaskKinds;
import myz.bridge_agent_demo.vo.ResourceQueueItemVO;
import myz.bridge_agent_demo.vo.ResourceQueueLaneVO;
import myz.bridge_agent_demo.vo.ResourceQueueVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * 三车道派发：MySQL queued 为重启账；每条车道可再向线程池塞 core+池内队列 个。
 * 按项目干活链挑选下一张，避免同一项目两张识图同时 running。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDispatchServiceImpl implements JobDispatchService {

    private static final Object DRAWING_LOCK = new Object();
    private static final Object MODELING_LOCK = new Object();
    private static final Object KNOWLEDGE_LOCK = new Object();

    private final ModelTaskMapper taskMapper;
    private final ModelTaskService modelTaskService;
    private final KnowledgeDocumentMapper documentMapper;
    private final ProjectMapper projectMapper;
    private final PermissionService permissionService;
    private final ObjectProvider<AgentJobLauncher> agentJobLauncher;

    private final ConcurrentHashMap<Long, Boolean> parseForce = new ConcurrentHashMap<>();

    @Value("${app.queue.drawing-core:2}")
    private int drawingCore;

    @Value("${app.queue.knowledge-core:2}")
    private int knowledgeCore;

    @Value("${app.queue.sap-core:1}")
    private int sapCore;

    @Value("${app.queue.pool-queue-capacity:5}")
    private int poolQueueCapacity;

    @Override
    public void offerTask(Long projectId, Long taskId) {
        ModelTask task = taskMapper.selectById(taskId);
        if (task == null || !projectId.equals(task.getProjectId())) {
            return;
        }
        if (TaskKinds.isDrawing(task.getKind())) {
            pumpDrawing();
        } else if (TaskKinds.MODELING.equals(task.getKind())) {
            pumpModeling();
        }
    }

    @Override
    public void resumeModeling(Long projectId, Long taskId) {
        try {
            agentJobLauncher.getObject().runModeling(projectId, taskId);
        } catch (RejectedExecutionException e) {
            log.warn("建模续跑被拒，稍后由调度再派 projectId={} taskId={}", projectId, taskId);
        }
    }

    @Override
    public void enqueueKnowledgeParse(Long documentId, boolean force) {
        parseForce.put(documentId, force);
        pumpKnowledge();
    }

    @Override
    public void enqueueKnowledgeMerge(Long documentId) {
        pumpKnowledge();
    }

    @Override
    public void enqueueKnowledgeSplit(Long documentId) {
        pumpKnowledge();
    }

    @Override
    public void enqueueKnowledgeEmbed(Long documentId) {
        pumpKnowledge();
    }

    @Override
    public void pumpDrawing() {
        synchronized (DRAWING_LOCK) {
            while (countDrawingRunning() < drawingInFlightLimit()) {
                ModelTask next = oldestQueuedDrawing();
                if (next == null) {
                    return;
                }
                if (!modelTaskService.casStatus(next.getProjectId(), next.getId(), "queued", "running")) {
                    continue;
                }
                try {
                    agentJobLauncher.getObject().runDrawing(next.getProjectId(), next.getId());
                } catch (RejectedExecutionException e) {
                    modelTaskService.casStatus(next.getProjectId(), next.getId(), "running", "queued");
                    log.warn("识图线程池拒收 taskId={}", next.getId());
                    return;
                }
            }
        }
    }

    @Override
    public void pumpModeling() {
        synchronized (MODELING_LOCK) {
            while (countModelingRunning() < modelingInFlightLimit()) {
                ModelTask next = oldestQueuedModeling();
                if (next == null) {
                    return;
                }
                if (!modelTaskService.casStatus(next.getProjectId(), next.getId(), "queued", "running")) {
                    continue;
                }
                try {
                    agentJobLauncher.getObject().runModeling(next.getProjectId(), next.getId());
                } catch (RejectedExecutionException e) {
                    modelTaskService.casStatus(next.getProjectId(), next.getId(), "running", "queued");
                    log.warn("建模线程池拒收 taskId={}", next.getId());
                    return;
                }
            }
        }
    }

    @Override
    public void pumpKnowledge() {
        synchronized (KNOWLEDGE_LOCK) {
            while (countKnowledgeRunning() < knowledgeInFlightLimit()) {
                KnowledgeSlot next = oldestQueuedKnowledge();
                if (next == null) {
                    return;
                }
                if (!claimKnowledge(next)) {
                    continue;
                }
                try {
                    submitKnowledge(next);
                } catch (RejectedExecutionException e) {
                    revertKnowledge(next);
                    log.warn("知识线程池拒收 documentId={} step={}", next.documentId, next.step);
                    return;
                }
            }
        }
    }

    @Override
    @Scheduled(fixedDelay = 5000)
    public void pumpAll() {
        pumpDrawing();
        pumpModeling();
        pumpKnowledge();
    }

    @Override
    public ResourceQueueVO snapshot() {
        List<Long> visible = permissionService.visibleProjectIds();
        Set<Long> allow = visible == null ? null : new HashSet<>(visible);
        Map<Long, String> names = projectNames();

        ResourceQueueVO vo = new ResourceQueueVO();
        vo.getLanes().add(drawingLane(allow, names));
        vo.getLanes().add(knowledgeLane());
        vo.getLanes().add(modelingLane(allow, names));
        return vo;
    }

    private ResourceQueueLaneVO drawingLane(Set<Long> allow, Map<Long, String> names) {
        ResourceQueueLaneVO lane = laneMeta("drawing", "识图", Math.max(1, drawingCore));
        List<ModelTask> tasks = taskMapper.selectList(
                Wrappers.<ModelTask>lambdaQuery()
                        .in(ModelTask::getKind, TaskKinds.DRAWING_FULL, TaskKinds.DRAWING_SUPPLEMENT, "图纸识别")
                        .in(ModelTask::getStatus, "running", "queued")
                        .orderByAsc(ModelTask::getUpdatedAt)
                        .orderByAsc(ModelTask::getId));
        fillTaskLane(lane, tasks, allow, names, "drawing", Math.max(1, drawingCore));
        return lane;
    }

    private ResourceQueueLaneVO modelingLane(Set<Long> allow, Map<Long, String> names) {
        ResourceQueueLaneVO lane = laneMeta("modeling", "建模", Math.max(1, sapCore));
        List<ModelTask> tasks = taskMapper.selectList(
                Wrappers.<ModelTask>lambdaQuery()
                        .eq(ModelTask::getKind, TaskKinds.MODELING)
                        .in(ModelTask::getStatus, "running", "queued")
                        .orderByAsc(ModelTask::getUpdatedAt)
                        .orderByAsc(ModelTask::getId));
        fillTaskLane(lane, tasks, allow, names, "modeling", Math.max(1, sapCore));
        return lane;
    }

    private ResourceQueueLaneVO knowledgeLane() {
        ResourceQueueLaneVO lane = laneMeta("knowledge", "知识库", Math.max(1, knowledgeCore));
        List<ResourceQueueItemVO> items = new ArrayList<>();
        for (KnowledgeDocument doc : documentMapper.selectList(null)) {
            addKnowledgeItem(items, doc, "parse", doc.getParseStatus(), "解析");
            addKnowledgeItem(items, doc, "merge", doc.getMergeStatus(), "合并");
            addKnowledgeItem(items, doc, "split", doc.getSplitStatus(), "分割");
            addKnowledgeItem(items, doc, "embed", doc.getEmbedStatus(), "嵌入");
        }
        items.sort(Comparator
                .comparing((ResourceQueueItemVO item) -> "running".equals(item.getState()) ? 0 : 1)
                .thenComparing(ResourceQueueItemVO::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        int active = (int) items.stream().filter(item -> "running".equals(item.getState())).count();
        int core = Math.max(1, knowledgeCore);
        markPoolWaitAsQueued(items, core);
        lane.setItems(items);
        lane.setRunning(Math.min(active, core));
        lane.setQueued(items.size() - lane.getRunning());
        return lane;
    }

    private void addKnowledgeItem(List<ResourceQueueItemVO> items, KnowledgeDocument doc,
                                  String step, String status, String stepLabel) {
        String state = knowledgeState(status);
        if (state == null) {
            return;
        }
        ResourceQueueItemVO item = new ResourceQueueItemVO();
        item.setLane("knowledge");
        item.setState(state);
        item.setTitle((doc.getName() == null ? "文献" : doc.getName()) + " · " + stepLabel);
        item.setRefType("knowledge");
        item.setRefId(doc.getId());
        item.setStep(step);
        item.setUpdatedAt(doc.getUpdatedAt());
        items.add(item);
    }

    private static String knowledgeState(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "queued" -> "queued";
            case "parsing", "merging", "splitting", "embedding" -> "running";
            default -> null;
        };
    }

    private void fillTaskLane(ResourceQueueLaneVO lane, List<ModelTask> tasks,
                              Set<Long> allow, Map<Long, String> names, String laneId, int core) {
        int mysqlRunning = 0;
        int mysqlQueued = 0;
        List<ResourceQueueItemVO> items = new ArrayList<>();
        for (ModelTask task : tasks) {
            if ("running".equals(task.getStatus())) {
                mysqlRunning++;
            } else {
                mysqlQueued++;
            }
            if (allow != null && !allow.contains(task.getProjectId())) {
                continue;
            }
            ResourceQueueItemVO item = new ResourceQueueItemVO();
            item.setLane(laneId);
            item.setState("running".equals(task.getStatus()) ? "running" : "queued");
            item.setTitle(task.getTitle());
            item.setProjectId(task.getProjectId());
            item.setProjectName(names.get(task.getProjectId()));
            item.setRefType("task");
            item.setRefId(task.getId());
            item.setActorUsername(task.getAgreedByUsername() != null
                    ? task.getAgreedByUsername() : task.getCreatedByUsername());
            item.setUpdatedAt(task.getUpdatedAt());
            items.add(item);
        }
        markPoolWaitAsQueued(items, core);
        lane.setRunning(Math.min(mysqlRunning, core));
        lane.setQueued(mysqlQueued + Math.max(0, mysqlRunning - core));
        lane.setItems(items);
    }

    /** 超出核心数的 running 视为已进线程池、还在等执行，页面上算排队。 */
    private void markPoolWaitAsQueued(List<ResourceQueueItemVO> items, int core) {
        List<ResourceQueueItemVO> occupying = items.stream()
                .filter(item -> "running".equals(item.getState()))
                .sorted(Comparator.comparing(ResourceQueueItemVO::getUpdatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (int i = core; i < occupying.size(); i++) {
            occupying.get(i).setState("queued");
        }
    }

    private ResourceQueueLaneVO laneMeta(String id, String name, int core) {
        ResourceQueueLaneVO lane = new ResourceQueueLaneVO();
        lane.setId(id);
        lane.setName(name);
        lane.setCore(core);
        return lane;
    }

    private Map<Long, String> projectNames() {
        return projectMapper.selectList(null).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a, HashMap::new));
    }

    private int drawingInFlightLimit() {
        return Math.max(1, drawingCore) + Math.max(0, poolQueueCapacity);
    }

    private int modelingInFlightLimit() {
        return Math.max(1, sapCore) + Math.max(0, poolQueueCapacity);
    }

    private int knowledgeInFlightLimit() {
        return Math.max(1, knowledgeCore) + Math.max(0, poolQueueCapacity);
    }

    private long countDrawingRunning() {
        Long n = taskMapper.selectCount(
                Wrappers.<ModelTask>lambdaQuery()
                        .eq(ModelTask::getStatus, "running")
                        .in(ModelTask::getKind, TaskKinds.DRAWING_FULL, TaskKinds.DRAWING_SUPPLEMENT, "图纸识别"));
        return n == null ? 0 : n;
    }

    private long countModelingRunning() {
        Long n = taskMapper.selectCount(
                Wrappers.<ModelTask>lambdaQuery()
                        .eq(ModelTask::getStatus, "running")
                        .eq(ModelTask::getKind, TaskKinds.MODELING));
        return n == null ? 0 : n;
    }

    private long countKnowledgeRunning() {
        Long n = documentMapper.selectCount(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .and(w -> w.eq(KnowledgeDocument::getParseStatus, "parsing")
                                .or().eq(KnowledgeDocument::getMergeStatus, "merging")
                                .or().eq(KnowledgeDocument::getSplitStatus, "splitting")
                                .or().eq(KnowledgeDocument::getEmbedStatus, "embedding")));
        return n == null ? 0 : n;
    }

    private ModelTask oldestQueuedDrawing() {
        List<ModelTask> queued = taskMapper.selectList(
                Wrappers.<ModelTask>lambdaQuery()
                        .eq(ModelTask::getStatus, "queued")
                        .in(ModelTask::getKind, TaskKinds.DRAWING_FULL, TaskKinds.DRAWING_SUPPLEMENT, "图纸识别")
                        .orderByAsc(ModelTask::getUpdatedAt)
                        .orderByAsc(ModelTask::getId));
        for (ModelTask task : queued) {
            if (modelTaskService.canClaimWorkChain(task)) {
                return task;
            }
        }
        return null;
    }

    private ModelTask oldestQueuedModeling() {
        List<ModelTask> queued = taskMapper.selectList(
                Wrappers.<ModelTask>lambdaQuery()
                        .eq(ModelTask::getStatus, "queued")
                        .eq(ModelTask::getKind, TaskKinds.MODELING)
                        .orderByAsc(ModelTask::getUpdatedAt)
                        .orderByAsc(ModelTask::getId));
        for (ModelTask task : queued) {
            if (modelTaskService.canClaimWorkChain(task)) {
                return task;
            }
        }
        return null;
    }

    private KnowledgeSlot oldestQueuedKnowledge() {
        List<KnowledgeSlot> slots = new ArrayList<>();
        for (KnowledgeDocument doc : documentMapper.selectList(null)) {
            if ("queued".equals(doc.getParseStatus())) {
                slots.add(new KnowledgeSlot(doc.getId(), "parse", doc.getUpdatedAt()));
            }
            if ("queued".equals(doc.getMergeStatus())) {
                slots.add(new KnowledgeSlot(doc.getId(), "merge", doc.getUpdatedAt()));
            }
            if ("queued".equals(doc.getSplitStatus())) {
                slots.add(new KnowledgeSlot(doc.getId(), "split", doc.getUpdatedAt()));
            }
            if ("queued".equals(doc.getEmbedStatus())) {
                slots.add(new KnowledgeSlot(doc.getId(), "embed", doc.getUpdatedAt()));
            }
        }
        return slots.stream()
                .min(Comparator.comparing((KnowledgeSlot s) -> s.updatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(s -> s.documentId))
                .orElse(null);
    }

    private boolean claimKnowledge(KnowledgeSlot slot) {
        var update = Wrappers.<KnowledgeDocument>lambdaUpdate().eq(KnowledgeDocument::getId, slot.documentId);
        int n = switch (slot.step) {
            case "parse" -> documentMapper.update(null, update
                    .set(KnowledgeDocument::getParseStatus, "parsing")
                    .eq(KnowledgeDocument::getParseStatus, "queued"));
            case "merge" -> documentMapper.update(null, update
                    .set(KnowledgeDocument::getMergeStatus, "merging")
                    .eq(KnowledgeDocument::getMergeStatus, "queued"));
            case "split" -> documentMapper.update(null, update
                    .set(KnowledgeDocument::getSplitStatus, "splitting")
                    .eq(KnowledgeDocument::getSplitStatus, "queued"));
            case "embed" -> documentMapper.update(null, update
                    .set(KnowledgeDocument::getEmbedStatus, "embedding")
                    .eq(KnowledgeDocument::getEmbedStatus, "queued"));
            default -> 0;
        };
        return n == 1;
    }

    private void revertKnowledge(KnowledgeSlot slot) {
        var update = Wrappers.<KnowledgeDocument>lambdaUpdate().eq(KnowledgeDocument::getId, slot.documentId);
        switch (slot.step) {
            case "parse" -> documentMapper.update(null, update
                    .set(KnowledgeDocument::getParseStatus, "queued")
                    .eq(KnowledgeDocument::getParseStatus, "parsing"));
            case "merge" -> documentMapper.update(null, update
                    .set(KnowledgeDocument::getMergeStatus, "queued")
                    .eq(KnowledgeDocument::getMergeStatus, "merging"));
            case "split" -> documentMapper.update(null, update
                    .set(KnowledgeDocument::getSplitStatus, "queued")
                    .eq(KnowledgeDocument::getSplitStatus, "splitting"));
            case "embed" -> documentMapper.update(null, update
                    .set(KnowledgeDocument::getEmbedStatus, "queued")
                    .eq(KnowledgeDocument::getEmbedStatus, "embedding"));
            default -> {
            }
        }
    }

    private void submitKnowledge(KnowledgeSlot slot) {
        AgentJobLauncher launcher = agentJobLauncher.getObject();
        switch (slot.step) {
            case "parse" -> launcher.runKnowledgeParse(slot.documentId, parseForce.getOrDefault(slot.documentId, false));
            case "merge" -> launcher.runKnowledgeMerge(slot.documentId);
            case "split" -> launcher.runKnowledgeSplit(slot.documentId);
            case "embed" -> launcher.runKnowledgeEmbed(slot.documentId);
            default -> {
            }
        }
    }

    private record KnowledgeSlot(long documentId, String step, java.time.LocalDateTime updatedAt) {
    }
}
