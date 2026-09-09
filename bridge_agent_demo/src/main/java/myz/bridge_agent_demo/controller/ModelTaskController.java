package myz.bridge_agent_demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.common.Result;
import myz.bridge_agent_demo.dto.ModelTaskCreateRequest;
import myz.bridge_agent_demo.dto.ModelTaskStatusRequest;
import myz.bridge_agent_demo.dto.TaskCardActionRequest;
import myz.bridge_agent_demo.dto.TaskCardRequest;
import myz.bridge_agent_demo.service.TaskCardService;
import myz.bridge_agent_demo.service.DrawingParseService;
import myz.bridge_agent_demo.service.ModelTaskService;
import myz.bridge_agent_demo.service.PermissionService;
import myz.bridge_agent_demo.vo.ModelTaskVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 干活页任务卡：起草、同意/拒绝、时间线、识图写入确认。不是问询。 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class ModelTaskController {

    private final ModelTaskService modelTaskService;
    private final DrawingParseService drawingParseService;
    private final TaskCardService taskCardService;
    private final PermissionService permissionService;

    @GetMapping
    public Result<List<ModelTaskVO>> list(@PathVariable Long projectId) {
        permissionService.requireRead(projectId);
        log.info("查询建模任务 {}", projectId);
        return Result.success(modelTaskService.list(projectId));
    }

    @PostMapping
    public Result<ModelTaskVO> create(@PathVariable Long projectId,
                                      @Valid @RequestBody ModelTaskCreateRequest request) {
        permissionService.requireRead(projectId);
        log.info("创建建模任务 {} {}", projectId, request.getKind());
        return Result.success(modelTaskService.create(projectId, request.getTitle(), request.getKind()));
    }

    @PutMapping("/{taskId}/status")
    public Result<ModelTaskVO> updateStatus(@PathVariable Long projectId,
                                            @PathVariable Long taskId,
                                            @Valid @RequestBody ModelTaskStatusRequest request) {
        permissionService.requireOperate(projectId);
        log.info("改任务状态 {} / {} -> {}", projectId, taskId, request.getStatus());
        return Result.success(modelTaskService.updateStatus(projectId, taskId, request.getStatus()));
    }

    @PostMapping("/cards")
    public Result<ModelTaskVO> draftCard(@PathVariable Long projectId,
                                         @Valid @RequestBody TaskCardRequest request) {
        permissionService.requireRead(projectId);
        log.info("起草任务卡 {} {}", projectId, request.getKind());
        return Result.success(taskCardService.draft(projectId, request));
    }

    @PutMapping("/{taskId}/card")
    public Result<ModelTaskVO> editCard(@PathVariable Long projectId,
                                        @PathVariable Long taskId,
                                        @Valid @RequestBody TaskCardRequest request) {
        permissionService.requireOperate(projectId);
        return Result.success(taskCardService.edit(projectId, taskId, request));
    }

    @PostMapping("/{taskId}/agree")
    public Result<ModelTaskVO> agree(@PathVariable Long projectId,
                                     @PathVariable Long taskId,
                                     @RequestBody(required = false) TaskCardActionRequest request) {
        permissionService.requireOperate(projectId);
        Long threadId = request == null ? null : request.getInquiryThreadId();
        log.info("同意任务卡 {} / {}", projectId, taskId);
        return Result.success(taskCardService.agree(projectId, taskId, threadId));
    }

    @PostMapping("/{taskId}/dismiss")
    public Result<ModelTaskVO> dismiss(@PathVariable Long projectId,
                                       @PathVariable Long taskId,
                                       @RequestBody(required = false) TaskCardActionRequest request) {
        permissionService.requireOperate(projectId);
        Long threadId = request == null ? null : request.getInquiryThreadId();
        return Result.success(taskCardService.dismiss(projectId, taskId, threadId));
    }

    /**
     * 识别本项目图纸。立刻返回 running，后台识图。
     */
    @PostMapping("/parse")
    public Result<ModelTaskVO> parse(@PathVariable Long projectId) {
        permissionService.requireOperate(projectId);
        log.info("识别项目图纸 {}", projectId);
        return Result.success(drawingParseService.parse(projectId));
    }

    /** 确认覆盖账本。仅对待确认的识图任务有效。 */
    @PostMapping("/{taskId}/parse/confirm")
    public Result<ModelTaskVO> confirmParse(@PathVariable Long projectId, @PathVariable Long taskId) {
        permissionService.requireOperate(projectId);
        log.info("确认识图写入 {} / {}", projectId, taskId);
        return Result.success(drawingParseService.confirm(projectId, taskId));
    }

    /** 放弃提案，不改联跨径和主梁形式。 */
    @PostMapping("/{taskId}/parse/reject")
    public Result<ModelTaskVO> rejectParse(@PathVariable Long projectId, @PathVariable Long taskId) {
        permissionService.requireOperate(projectId);
        log.info("放弃识图写入 {} / {}", projectId, taskId);
        return Result.success(drawingParseService.reject(projectId, taskId));
    }
}
