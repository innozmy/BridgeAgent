package myz.bridge_agent_demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.common.Result;
import myz.bridge_agent_demo.dto.InquiryMessageRequest;
import myz.bridge_agent_demo.entity.InquiryThread;
import myz.bridge_agent_demo.service.InquiryService;
import myz.bridge_agent_demo.service.PermissionService;
import myz.bridge_agent_demo.vo.InquiryThreadVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 项目问询：会话按登录用户私有，不共享。 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;
    private final PermissionService permissionService;

    @GetMapping
    public Result<List<InquiryThread>> list(@PathVariable Long projectId) {
        permissionService.requireRead(projectId);
        log.info("查询问询会话 {}", projectId);
        return Result.success(inquiryService.listThreads(projectId));
    }

    @PostMapping
    public Result<InquiryThread> create(@PathVariable Long projectId) {
        permissionService.requireRead(projectId);
        log.info("新建问询会话 {}", projectId);
        return Result.success(inquiryService.createThread(projectId));
    }

    @GetMapping("/{threadId}")
    public Result<InquiryThreadVO> get(@PathVariable Long projectId, @PathVariable Long threadId) {
        permissionService.requireRead(projectId);
        return Result.success(inquiryService.getThread(projectId, threadId));
    }

    @PostMapping("/{threadId}/messages")
    public Result<InquiryThreadVO> post(@PathVariable Long projectId,
                                        @PathVariable Long threadId,
                                        @Valid @RequestBody InquiryMessageRequest request) {
        permissionService.requireRead(projectId);
        log.info("问询发消息 {} / {}", projectId, threadId);
        return Result.success(inquiryService.postMessage(projectId, threadId, request.getBody()));
    }

    @DeleteMapping("/{threadId}")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long threadId) {
        permissionService.requireRead(projectId);
        log.info("删除问询会话 {} / {}", projectId, threadId);
        inquiryService.deleteThread(projectId, threadId);
        return Result.success();
    }
}
