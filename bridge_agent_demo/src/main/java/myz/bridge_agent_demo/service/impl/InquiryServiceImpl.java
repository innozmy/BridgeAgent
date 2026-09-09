package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.auth.UserContext;
import myz.bridge_agent_demo.dto.PythonInquiryResponse;
import myz.bridge_agent_demo.dto.TaskCardRequest;
import myz.bridge_agent_demo.service.TaskCardService;
import myz.bridge_agent_demo.service.TaskKinds;
import myz.bridge_agent_demo.entity.InquiryMessage;
import myz.bridge_agent_demo.entity.InquiryStm;
import myz.bridge_agent_demo.entity.InquiryThread;
import myz.bridge_agent_demo.entity.Project;
import myz.bridge_agent_demo.exception.BusinessException;
import myz.bridge_agent_demo.mapper.InquiryMessageMapper;
import myz.bridge_agent_demo.mapper.InquiryStmMapper;
import myz.bridge_agent_demo.mapper.InquiryThreadMapper;
import myz.bridge_agent_demo.mapper.ProjectMapper;
import myz.bridge_agent_demo.service.AgentInjectPackService;
import myz.bridge_agent_demo.service.InquiryService;
import myz.bridge_agent_demo.service.PythonAgentClient;
import myz.bridge_agent_demo.vo.InquiryThreadVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 问询会话：展示走 {@code inquiry_message}；模型上下文走注入包 + {@code inquiry_stm}。
 * 列表/读写按登录用户过滤，超管也不能看别人的会话。
 * 调 Python 不包在事务里，避免占着连接等模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryServiceImpl implements InquiryService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ProjectMapper projectMapper;
    private final InquiryThreadMapper threadMapper;
    private final InquiryMessageMapper messageMapper;
    private final InquiryStmMapper inquiryStmMapper;
    private final AgentInjectPackService agentInjectPackService;
    private final PythonAgentClient pythonAgentClient;
    private final TaskCardService taskCardService;

    @Value("${app.memory.inquiry-window-turns:8}")
    private int windowTurns;

    /** 只列出当前用户在该项目下的会话；{@code user_id} 为空的旧行不展示。 */
    @Override
    public List<InquiryThread> listThreads(Long projectId) {
        requireProject(projectId);
        long userId = currentUserId();
        return threadMapper.selectList(
                Wrappers.<InquiryThread>lambdaQuery()
                        .eq(InquiryThread::getProjectId, projectId)
                        .eq(InquiryThread::getUserId, userId)
                        .orderByDesc(InquiryThread::getUpdatedAt));
    }

    @Override
    @Transactional
    public InquiryThread createThread(Long projectId) {
        requireProject(projectId);
        InquiryThread thread = new InquiryThread();
        thread.setProjectId(projectId);
        thread.setUserId(currentUserId());
        thread.setTitle("新会话");
        threadMapper.insert(thread);
        return thread;
    }

    @Override
    public InquiryThreadVO getThread(Long projectId, Long threadId) {
        return toVo(requireThread(projectId, threadId));
    }

    @Override
    public InquiryThreadVO postMessage(Long projectId, Long threadId, String body) {
        InquiryThread thread = requireThread(projectId, threadId);
        String text = body.trim();
        insertMessage(thread.getId(), "user", text);
        if ("新会话".equals(thread.getTitle())) {
            thread.setTitle(text.length() > 40 ? text.substring(0, 40) : text);
            threadMapper.updateById(thread);
        }

        List<InquiryMessage> all = loadMessages(thread.getId());
        InquiryMessage current = all.get(all.size() - 1);
        List<InquiryMessage> history = all.size() <= 1 ? List.of() : all.subList(0, all.size() - 1);
        Map<String, Object> pack = agentInjectPackService.buildInquiryPack(
                projectId, thread.getId(), history, current.getBody());
        log.info("问询转发 Python projectId={} threadId={}", projectId, thread.getId());
        String reply = askAgent(projectId, thread.getId(), pack);
        insertMessage(thread.getId(), "agent", reply);
        upsertInquiryStm(thread.getId(), pack);
        thread.setUpdatedAt(LocalDateTime.now());
        threadMapper.updateById(thread);
        return toVo(thread);
    }

    @Override
    @Transactional
    public void deleteThread(Long projectId, Long threadId) {
        requireThread(projectId, threadId);
        // inquiry_message / inquiry_stm 有 ON DELETE CASCADE
        threadMapper.deleteById(threadId);
    }

    /**
     * 网络失败或 Python 报错都写成一条助手消息，会话仍可继续，不把用户那句回滚。
     */
    private String askAgent(Long projectId, Long threadId, Map<String, Object> pack) {
        PythonInquiryResponse response = pythonAgentClient.inquire(pack);
        if (response == null) {
            return "问询 Agent 暂时连不上（Python 未启动或超时）。问询不会改模型。";
        }
        if (Boolean.TRUE.equals(response.getOk()) && StringUtils.hasText(response.getReply())) {
            acceptTaskCard(projectId, threadId, response.getTaskCard());
            return response.getReply().trim();
        }
        String error = StringUtils.hasText(response.getError()) ? response.getError() : "问询失败，未返回文字。";
        return error;
    }

    private void acceptTaskCard(Long projectId, Long threadId, PythonInquiryResponse.TaskCardHint hint) {
        if (hint == null || hint.getFileId() == null) {
            return;
        }
        TaskCardRequest request = new TaskCardRequest();
        request.setKind(TaskKinds.DRAWING_SUPPLEMENT);
        request.setFileId(hint.getFileId());
        request.setPageKinds(hint.getPageKinds());
        request.setDirective(hint.getDirective());
        request.setProposeReason(hint.getReason());
        taskCardService.proposeFromInquiry(projectId, threadId, request);
    }

    private void upsertInquiryStm(Long threadId, Map<String, Object> pack) {
        String summary = "";
        Object stmRaw = pack.get("inquiryStm");
        if (stmRaw instanceof Map<?, ?> stm) {
            Object raw = stm.get("summary");
            summary = raw == null ? "" : String.valueOf(raw);
        }
        List<InquiryMessage> all = loadMessages(threadId);
        int keep = Math.max(1, windowTurns) * 2;
        int from = Math.max(0, all.size() - keep);
        List<Long> recentIds = new ArrayList<>();
        for (InquiryMessage message : all.subList(from, all.size())) {
            recentIds.add(message.getId());
        }
        String recentJson;
        try {
            recentJson = JSON.writeValueAsString(recentIds);
        } catch (JacksonException e) {
            throw new IllegalStateException("问询 STM 序列化失败", e);
        }
        InquiryStm existing = inquiryStmMapper.selectOne(
                Wrappers.lambdaQuery(InquiryStm.class).eq(InquiryStm::getThreadId, threadId));
        if (existing == null) {
            InquiryStm row = new InquiryStm();
            row.setThreadId(threadId);
            row.setSummary(summary);
            row.setRecentJson(recentJson);
            inquiryStmMapper.insert(row);
            return;
        }
        existing.setSummary(summary);
        existing.setRecentJson(recentJson);
        inquiryStmMapper.updateById(existing);
    }

    private InquiryThreadVO toVo(InquiryThread thread) {
        InquiryThreadVO vo = new InquiryThreadVO();
        vo.setThread(thread);
        vo.setMessages(loadMessages(thread.getId()));
        vo.setProposedCards(taskCardService.listProposedByThread(thread.getProjectId(), thread.getId()));
        return vo;
    }

    private List<InquiryMessage> loadMessages(Long threadId) {
        return messageMapper.selectList(
                Wrappers.<InquiryMessage>lambdaQuery()
                        .eq(InquiryMessage::getThreadId, threadId)
                        .orderByAsc(InquiryMessage::getCreatedAt)
                        .orderByAsc(InquiryMessage::getId));
    }

    private void insertMessage(Long threadId, String role, String body) {
        InquiryMessage message = new InquiryMessage();
        message.setThreadId(threadId);
        message.setRole(role);
        message.setBody(body);
        messageMapper.insert(message);
    }

    private InquiryThread requireThread(Long projectId, Long threadId) {
        requireProject(projectId);
        InquiryThread thread = threadMapper.selectById(threadId);
        if (thread == null || !projectId.equals(thread.getProjectId())
                || thread.getUserId() == null || thread.getUserId() != currentUserId()) {
            throw new BusinessException("会话不存在");
        }
        return thread;
    }

    private long currentUserId() {
        UserContext.User user = UserContext.get();
        if (user == null) {
            throw new BusinessException("未登录或登录已失效");
        }
        return user.id();
    }

    private void requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
    }
}
