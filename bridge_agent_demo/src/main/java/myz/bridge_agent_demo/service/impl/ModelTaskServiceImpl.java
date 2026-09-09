package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import myz.bridge_agent_demo.auth.JobActors;
import myz.bridge_agent_demo.entity.ModelTask;
import myz.bridge_agent_demo.entity.ModelTaskEvent;
import myz.bridge_agent_demo.entity.Project;
import myz.bridge_agent_demo.exception.BusinessException;
import myz.bridge_agent_demo.mapper.ModelTaskEventMapper;
import myz.bridge_agent_demo.mapper.ModelTaskMapper;
import myz.bridge_agent_demo.mapper.ProjectMapper;
import myz.bridge_agent_demo.service.ModelTaskService;
import myz.bridge_agent_demo.vo.ModelTaskVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import myz.bridge_agent_demo.service.TaskKinds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务卡与时间线：作业链操作人写在现有表上，不另建审计表。
 * HTTP 线程记下登录名；{@code @Async} 无 UserContext 时时间线记「系统」。
 */
@Service
@RequiredArgsConstructor
public class ModelTaskServiceImpl implements ModelTaskService {

    private static final Set<String> STATUSES = Set.of(
            "proposed", "queued", "running", "waiting", "done", "failed", "rejected");
    private static final Map<String, String> STATUS_LABEL = Map.of(
            "proposed", "待同意",
            "queued", "排队",
            "running", "进行中",
            "waiting", "待写入确认",
            "done", "完成",
            "failed", "失败",
            "rejected", "已拒绝");

    private final ProjectMapper projectMapper;
    private final ModelTaskMapper taskMapper;
    private final ModelTaskEventMapper eventMapper;

    @Override
    public List<ModelTaskVO> list(Long projectId) {
        requireProject(projectId);
        List<ModelTask> tasks = taskMapper.selectList(
                Wrappers.<ModelTask>lambdaQuery()
                        .eq(ModelTask::getProjectId, projectId)
                        .orderByDesc(ModelTask::getUpdatedAt));
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Long> ids = tasks.stream().map(ModelTask::getId).toList();
        Map<Long, List<ModelTaskEvent>> events = eventMapper.selectList(
                        Wrappers.<ModelTaskEvent>lambdaQuery()
                                .in(ModelTaskEvent::getTaskId, ids)
                                .orderByAsc(ModelTaskEvent::getCreatedAt)
                                .orderByAsc(ModelTaskEvent::getId))
                .stream()
                .collect(Collectors.groupingBy(ModelTaskEvent::getTaskId));
        List<ModelTaskVO> result = new ArrayList<>();
        for (ModelTask task : tasks) {
            ModelTaskVO vo = new ModelTaskVO();
            vo.setTask(task);
            vo.setEvents(events.getOrDefault(task.getId(), List.of()));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public ModelTaskVO create(Long projectId, String title, String kind) {
        requireProject(projectId);
        String kindKey = kind == null ? "" : kind.trim();
        if (!TaskKinds.ALL.contains(kindKey)) {
            throw new BusinessException("工种不合法");
        }
        ModelTask task = new ModelTask();
        task.setProjectId(projectId);
        task.setTitle(title == null || title.isBlank() ? TaskKinds.titleOf(kindKey) : title.trim());
        task.setKind(kindKey);
        task.setStatus("proposed");
        task.setOrigin("draft");
        JobActors.fillCreator(task);
        taskMapper.insert(task);
        append(task.getId(), "任务卡已创建，类型：" + TaskKinds.titleOf(kindKey) + "。待同意后执行。");
        return get(projectId, task.getId());
    }

    @Override
    @Transactional
    public ModelTaskVO insertCard(ModelTask draft) {
        requireProject(draft.getProjectId());
        if (draft.getTitle() == null || draft.getTitle().isBlank()) {
            draft.setTitle(TaskKinds.titleOf(draft.getKind()));
        }
        JobActors.fillCreator(draft);
        taskMapper.insert(draft);
        append(draft.getId(), "任务卡已创建：" + draft.getTitle() + "（" + STATUS_LABEL.getOrDefault(draft.getStatus(), draft.getStatus()) + "）。");
        return get(draft.getProjectId(), draft.getId());
    }

    @Override
    public boolean hasRunningDrawing(Long projectId) {
        requireProject(projectId);
        Long n = taskMapper.selectCount(
                Wrappers.<ModelTask>lambdaQuery()
                        .eq(ModelTask::getProjectId, projectId)
                        .in(ModelTask::getStatus, "running", "queued")
                        .in(ModelTask::getKind, TaskKinds.DRAWING_FULL, TaskKinds.DRAWING_SUPPLEMENT, "图纸识别"));
        return n != null && n > 0;
    }

    @Override
    public boolean hasRunningModeling(Long projectId) {
        requireProject(projectId);
        Long n = taskMapper.selectCount(
                Wrappers.<ModelTask>lambdaQuery()
                        .eq(ModelTask::getProjectId, projectId)
                        .in(ModelTask::getStatus, "running", "queued")
                        .eq(ModelTask::getKind, TaskKinds.MODELING));
        return n != null && n > 0;
    }

    @Override
    public boolean hasRunningWorkChain(Long projectId) {
        return hasRunningDrawing(projectId) || hasRunningModeling(projectId);
    }

    /**
     * {@code SELECT … FOR UPDATE} 锁项目行，直到本事务提交。另一人同时同意会被堵住，提交后再看见已占链。
     */
    @Override
    public void lockWorkChain(Long projectId) {
        Project project = projectMapper.selectOne(
                Wrappers.<Project>lambdaQuery()
                        .eq(Project::getId, projectId)
                        .last("FOR UPDATE"));
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
    }

    @Override
    public boolean casStatus(Long projectId, Long taskId, String from, String to) {
        if (!STATUSES.contains(to) || !STATUSES.contains(from)) {
            throw new BusinessException("状态不合法");
        }
        int n = taskMapper.update(null, Wrappers.<ModelTask>lambdaUpdate()
                .set(ModelTask::getStatus, to)
                .eq(ModelTask::getId, taskId)
                .eq(ModelTask::getProjectId, projectId)
                .eq(ModelTask::getStatus, from));
        if (n == 1) {
            String fromLabel = STATUS_LABEL.getOrDefault(from, from);
            String toLabel = STATUS_LABEL.getOrDefault(to, to);
            append(taskId, "状态：" + fromLabel + " → " + toLabel);
            return true;
        }
        return false;
    }

    @Override
    public boolean canClaimWorkChain(ModelTask task) {
        if (task == null || task.getId() == null || task.getProjectId() == null) {
            return false;
        }
        List<ModelTask> others = taskMapper.selectList(
                Wrappers.<ModelTask>lambdaQuery()
                        .eq(ModelTask::getProjectId, task.getProjectId())
                        .ne(ModelTask::getId, task.getId())
                        .in(ModelTask::getStatus, "running", "queued")
                        .in(ModelTask::getKind, TaskKinds.DRAWING_FULL, TaskKinds.DRAWING_SUPPLEMENT,
                                "图纸识别", TaskKinds.MODELING));
        if (others.isEmpty()) {
            return true;
        }
        // 同一条链上的自动子识图：只允许占链者是「running 的父建模」
        if (TaskKinds.isDrawing(task.getKind()) && task.getParentTaskId() != null && others.size() == 1) {
            ModelTask parent = others.get(0);
            return TaskKinds.MODELING.equals(parent.getKind())
                    && "running".equals(parent.getStatus())
                    && task.getParentTaskId().equals(parent.getId());
        }
        return false;
    }

    @Override
    @Transactional
    public ModelTaskVO updateCard(Long projectId, Long taskId, ModelTask patch) {
        ModelTask task = requireTask(projectId, taskId);
        if (!"proposed".equals(task.getStatus())) {
            throw new BusinessException("只能改待同意的任务卡");
        }
        if (patch.getKind() != null) {
            task.setKind(patch.getKind());
            task.setTitle(TaskKinds.titleOf(patch.getKind()));
        }
        task.setFileId(patch.getFileId());
        task.setPageKindsJson(patch.getPageKindsJson());
        task.setUnitSeq(patch.getUnitSeq());
        task.setSupportCode(patch.getSupportCode());
        task.setDirective(patch.getDirective());
        taskMapper.updateById(task);
        append(task.getId(), "已修改任务卡范围或指令。");
        return get(projectId, taskId);
    }

    @Override
    @Transactional
    public ModelTaskVO updateStatus(Long projectId, Long taskId, String status) {
        if (!STATUSES.contains(status)) {
            throw new BusinessException("状态不合法");
        }
        ModelTask task = requireTask(projectId, taskId);
        if (status.equals(task.getStatus())) {
            return get(projectId, taskId);
        }
        String from = STATUS_LABEL.getOrDefault(task.getStatus(), task.getStatus());
        String to = STATUS_LABEL.getOrDefault(status, status);
        task.setStatus(status);
        taskMapper.updateById(task);
        append(task.getId(), "状态：" + from + " → " + to);
        return get(projectId, taskId);
    }

    @Override
    public ModelTaskVO get(Long projectId, Long taskId) {
        ModelTask task = requireTask(projectId, taskId);
        ModelTaskVO vo = new ModelTaskVO();
        vo.setTask(task);
        vo.setEvents(eventMapper.selectList(
                Wrappers.<ModelTaskEvent>lambdaQuery()
                        .eq(ModelTaskEvent::getTaskId, taskId)
                        .orderByAsc(ModelTaskEvent::getCreatedAt)
                        .orderByAsc(ModelTaskEvent::getId)));
        return vo;
    }

    /** 识图过程、冲突说明都走这一行，不写问询表 */
    @Override
    public void appendEvent(Long taskId, String body) {
        append(taskId, body);
    }

    @Override
    public void stampAgreed(Long projectId, Long taskId) {
        ModelTask task = requireTask(projectId, taskId);
        JobActors.fillAgreed(task);
        taskMapper.updateById(task);
    }

    /** 只存提案，不改状态；冲突时再单独把任务打回 waiting */
    @Override
    @Transactional
    public void saveProposal(Long projectId, Long taskId, String proposalJson) {
        ModelTask task = requireTask(projectId, taskId);
        task.setProposalJson(proposalJson);
        taskMapper.updateById(task);
    }

    private void append(Long taskId, String body) {
        ModelTaskEvent event = new ModelTaskEvent();
        event.setTaskId(taskId);
        event.setBody(body);
        JobActors.stampEvent(event);
        eventMapper.insert(event);
    }

    private ModelTask requireTask(Long projectId, Long taskId) {
        requireProject(projectId);
        ModelTask task = taskMapper.selectById(taskId);
        if (task == null || !projectId.equals(task.getProjectId())) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    private void requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
    }
}
