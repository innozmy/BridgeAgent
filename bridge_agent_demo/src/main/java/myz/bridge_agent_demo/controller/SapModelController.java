package myz.bridge_agent_demo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.common.Result;
import myz.bridge_agent_demo.service.PermissionService;
import myz.bridge_agent_demo.service.SapModelService;
import myz.bridge_agent_demo.vo.ProjectSapModelVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目模型页：列出各版本 SAP 文件、下载、删除。
 * 建模图尚未接入时列表为空；几何预览来自 {@code previewJson}。
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/models")
@RequiredArgsConstructor
public class SapModelController {

    private final SapModelService sapModelService;
    private final PermissionService permissionService;

    @GetMapping
    public Result<List<ProjectSapModelVO>> list(@PathVariable Long projectId) {
        permissionService.requireRead(projectId);
        log.info("查询 SAP 模型版本 {}", projectId);
        return Result.success(sapModelService.list(projectId));
    }

    @GetMapping("/{modelId}")
    public Result<ProjectSapModelVO> get(@PathVariable Long projectId, @PathVariable Long modelId) {
        permissionService.requireRead(projectId);
        return Result.success(sapModelService.get(projectId, modelId));
    }

    @GetMapping("/{modelId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long projectId, @PathVariable Long modelId) {
        permissionService.requireRead(projectId);
        log.info("下载 SAP 模型 {} / {}", projectId, modelId);
        return sapModelService.download(projectId, modelId);
    }

    @DeleteMapping("/{modelId}")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long modelId) {
        permissionService.requireOperate(projectId);
        log.info("删除 SAP 模型 {} / {}", projectId, modelId);
        sapModelService.delete(projectId, modelId);
        return Result.success();
    }
}
