package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.dto.TaskCardRequest;
import myz.bridge_agent_demo.entity.DrawingPageMap;
import myz.bridge_agent_demo.entity.InquiryMessage;
import myz.bridge_agent_demo.entity.InquiryStm;
import myz.bridge_agent_demo.entity.ModelTask;
import myz.bridge_agent_demo.entity.ProjectFile;
import myz.bridge_agent_demo.exception.BusinessException;
import myz.bridge_agent_demo.mapper.DrawingPageMapMapper;
import myz.bridge_agent_demo.mapper.InquiryMessageMapper;
import myz.bridge_agent_demo.mapper.InquiryStmMapper;
import myz.bridge_agent_demo.mapper.ProjectFileMapper;
import myz.bridge_agent_demo.service.JobDispatchService;
import myz.bridge_agent_demo.service.ModelTaskService;
import myz.bridge_agent_demo.service.TaskCardService;
import myz.bridge_agent_demo.service.TaskKinds;
import myz.bridge_agent_demo.vo.ModelTaskVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 校验任务卡并在人同意后启动。问询不能直接 running。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCardServiceImpl implements TaskCardService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ModelTaskService modelTaskService;
    private final ProjectFileMapper projectFileMapper;
    private final DrawingPageMapMapper drawingPageMapMapper;
    private final InquiryMessageMapper inquiryMessageMapper;
    private final InquiryStmMapper inquiryStmMapper;
    private final JobDispatchService jobDispatchService;

    @Override
    @Transactional
    public ModelTaskVO draft(Long projectId, TaskCardRequest request) {
        ModelTask card = buildCard(projectId, null, request, true);
        card.setStatus("proposed");
        card.setOrigin("draft");
        return modelTaskService.insertCard(card);
    }

    @Override
    @Transactional
    public ModelTaskVO edit(Long projectId, Long taskId, TaskCardRequest request) {
        ModelTask patch = buildCard(projectId, null, request, true);
        return modelTaskService.updateCard(projectId, taskId, patch);
    }

    @Override
    @Transactional
    public ModelTaskVO proposeFromInquiry(Long projectId, Long threadId, TaskCardRequest request) {
        if (request == null || !TaskKinds.DRAWING_SUPPLEMENT.equals(request.getKind())) {
            return null;
        }
        try {
            ModelTask card = buildCard(projectId, threadId, request, false);
            card.setStatus("proposed");
            card.setInquiryThreadId(threadId);
            card.setOrigin("inquiry");
            return modelTaskService.insertCard(card);
        } catch (BusinessException e) {
            log.info("问询任务卡未落库：{}", e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public ModelTaskVO agree(Long projectId, Long taskId, Long threadId) {
        // 先锁项目行，再读卡、查链、CAS，避免两人同时同意两张卡都看见「链是空的」
        modelTaskService.lockWorkChain(projectId);
        ModelTaskVO vo = modelTaskService.get(projectId, taskId);
        ModelTask task = vo.getTask();
        if (!"proposed".equals(task.getStatus())) {
            throw new BusinessException("只能同意待处理的任务卡");
        }
        if (modelTaskService.hasRunningWorkChain(projectId)) {
            throw new BusinessException("已有识图或建模进行中。");
        }
        if (TaskKinds.ANALYSIS.equals(task.getKind())) {
            modelTaskService.updateStatus(projectId, taskId, "running");
            modelTaskService.stampAgreed(projectId, taskId);
            modelTaskService.appendEvent(taskId, "本工种 Python 图仍为骨架，已记录本轮指令，未调用求解器。");
            modelTaskService.updateStatus(projectId, taskId, "done");
            return modelTaskService.get(projectId, taskId);
        }
        if (!modelTaskService.casStatus(projectId, taskId, "proposed", "queued")) {
            throw new BusinessException("只能同意待处理的任务卡");
        }
        modelTaskService.stampAgreed(projectId, taskId);
        modelTaskService.appendEvent(taskId, "已同意任务卡，进入资源队列。");
        if (threadId != null) {
            insertEvent(threadId, "已提交任务 #" + taskId + "（" + task.getTitle() + "），已转到任务页。");
            rememberSubmitted(threadId, taskId);
        }
        if (TaskKinds.MODELING.equals(task.getKind())) {
            modelTaskService.appendEvent(taskId, "排队或开跑后将做建模检查；硬缺口将自动补充识别，不启残缺 SAP。");
        }
        afterCommit(() -> jobDispatchService.offerTask(projectId, taskId));
        return modelTaskService.get(projectId, taskId);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Override
    @Transactional
    public ModelTaskVO dismiss(Long projectId, Long taskId, Long threadId) {
        ModelTaskVO vo = modelTaskService.get(projectId, taskId);
        if (!"proposed".equals(vo.getTask().getStatus())) {
            throw new BusinessException("只能拒绝待同意的任务卡");
        }
        modelTaskService.updateStatus(projectId, taskId, "rejected");
        modelTaskService.appendEvent(taskId, "已拒绝任务卡，未执行。");
        if (threadId != null) {
            insertEvent(threadId, "已拒绝任务卡 #" + taskId + "。");
        }
        return modelTaskService.get(projectId, taskId);
    }

    @Override
    public List<ModelTaskVO> listProposedByThread(Long projectId, Long threadId) {
        return modelTaskService.list(projectId).stream()
                .filter(item -> "proposed".equals(item.getTask().getStatus())
                        && threadId.equals(item.getTask().getInquiryThreadId()))
                .collect(Collectors.toList());
    }

    private ModelTask buildCard(Long projectId, Long threadId, TaskCardRequest request, boolean allowFull) {
        String kind = request.getKind() == null ? "" : request.getKind().trim();
        if (!TaskKinds.ALL.contains(kind)) {
            throw new BusinessException("工种不合法");
        }
        if (!allowFull && !TaskKinds.DRAWING_SUPPLEMENT.equals(kind)) {
            throw new BusinessException("问询只能提议补充识别");
        }
        if (StringUtils.hasText(request.getDirective()) && request.getDirective().length() > 500) {
            throw new BusinessException("本轮指令不能超过500字");
        }
        List<String> kinds = request.getPageKinds() == null ? List.of() : request.getPageKinds();
        for (String pageKind : kinds) {
            if (!TaskKinds.PAGE_KINDS.contains(pageKind) || "rebar".equals(pageKind)) {
                throw new BusinessException("页类不合法或不抽钢筋：" + pageKind);
            }
        }
        if (TaskKinds.DRAWING_SUPPLEMENT.equals(kind)) {
            if (request.getFileId() == null) {
                throw new BusinessException("补充识别必须指定图纸");
            }
            if (kinds.isEmpty()) {
                throw new BusinessException("补充识别必须选择要细看的页类");
            }
            ProjectFile file = projectFileMapper.selectById(request.getFileId());
            if (file == null || !projectId.equals(file.getProjectId())) {
                throw new BusinessException("图纸不属于本项目");
            }
            DrawingPageMap map = drawingPageMapMapper.selectOne(
                    Wrappers.lambdaQuery(DrawingPageMap.class)
                            .eq(DrawingPageMap::getProjectId, projectId)
                            .eq(DrawingPageMap::getFileId, request.getFileId()));
            if (map == null) {
                throw new BusinessException("请先全册识别，再做补充识别");
            }
        }
        ModelTask card = new ModelTask();
        card.setProjectId(projectId);
        card.setKind(kind);
        card.setTitle(TaskKinds.titleOf(kind));
        card.setFileId(request.getFileId());
        card.setPageKindsJson(writeJson(kinds));
        card.setUnitSeq(request.getUnitSeq());
        card.setSupportCode(blankToNull(request.getSupportCode()));
        card.setDirective(blankToNull(request.getDirective()));
        card.setProposeReason(blankToNull(request.getProposeReason()));
        card.setInquiryThreadId(threadId);
        return card;
    }

    private void insertEvent(Long threadId, String body) {
        InquiryMessage message = new InquiryMessage();
        message.setThreadId(threadId);
        message.setRole("event");
        message.setBody(body);
        inquiryMessageMapper.insert(message);
    }

    private void rememberSubmitted(Long threadId, Long taskId) {
        InquiryStm stm = inquiryStmMapper.selectOne(
                Wrappers.lambdaQuery(InquiryStm.class).eq(InquiryStm::getThreadId, threadId));
        List<Long> ids = new ArrayList<>();
        if (stm != null && StringUtils.hasText(stm.getSubmittedJson())) {
            try {
                ids.addAll(JSON.readValue(stm.getSubmittedJson(), new TypeReference<List<Long>>() { }));
            } catch (JacksonException ignored) {
                // 坏 JSON 则重写
            }
        }
        if (!ids.contains(taskId)) {
            ids.add(taskId);
        }
        String json;
        try {
            json = JSON.writeValueAsString(ids);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
        if (stm == null) {
            InquiryStm row = new InquiryStm();
            row.setThreadId(threadId);
            row.setSubmittedJson(json);
            inquiryStmMapper.insert(row);
            return;
        }
        stm.setSubmittedJson(json);
        inquiryStmMapper.updateById(stm);
    }

    private String writeJson(List<String> kinds) {
        try {
            return JSON.writeValueAsString(kinds == null ? List.of() : kinds);
        } catch (JacksonException e) {
            return "[]";
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
