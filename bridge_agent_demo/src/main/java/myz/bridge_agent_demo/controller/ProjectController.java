package myz.bridge_agent_demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.common.Result;
import myz.bridge_agent_demo.dto.ProjectCreateRequest;
import myz.bridge_agent_demo.dto.ProjectParamBatchRequest;
import myz.bridge_agent_demo.dto.ProjectQuery;
import myz.bridge_agent_demo.dto.ProjectUnitBatchRequest;
import myz.bridge_agent_demo.dto.ProjectUpdateRequest;
import myz.bridge_agent_demo.dto.KnowledgeSearchRequest;
import myz.bridge_agent_demo.entity.Project;
import myz.bridge_agent_demo.service.AdminRbacService;
import myz.bridge_agent_demo.service.PermissionService;
import myz.bridge_agent_demo.service.ProjectFileService;
import myz.bridge_agent_demo.service.KnowledgeService;
import myz.bridge_agent_demo.service.ProjectService;
import myz.bridge_agent_demo.vo.FileBatchUploadVO;
import myz.bridge_agent_demo.vo.KnowledgeSearchVO;
import myz.bridge_agent_demo.vo.ProjectKnowledgeVO;
import myz.bridge_agent_demo.vo.ProjectMemberVO;
import myz.bridge_agent_demo.vo.ProjectVO;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * HTTP 入口，只做路由和参数接收，业务在 Service。
 * <p>
 * 注意：没有 {@code /overview} 这种路径。那是 Vue 前端页面；
 * 概览要的幅面、跨径来自 {@code GET /api/projects/{id}}。
 */
@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectFileService projectFileService;
    private final KnowledgeService knowledgeService;
    private final PermissionService permissionService;
    private final AdminRbacService adminRbacService;

    /** 工作台列表 */
    @GetMapping
    public Result<List<Project>> list(ProjectQuery query) {
        log.info("查询项目列表 {}", query);
        return Result.success(projectService.list(query));
    }

    /**
     * 项目详情。前端 {@code ProjectLayout} 进项目时调用，再分发给概览/图纸等 Tab。
     */
    @GetMapping("/{id}")
    public Result<ProjectVO> getById(@PathVariable Long id) {
        permissionService.requireRead(id);
        log.info("查询项目 {}", id);
        return Result.success(projectService.getById(id));
    }

    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody ProjectCreateRequest request) {
        permissionService.requireManageProjects();
        log.info("新建项目 {}", request.getName());
        return Result.success(projectService.create(request));
    }

    /** 概览保存。请求必须带当前 {@code version}，冲突不覆盖。 */
    @PutMapping("/{id}")
    public Result<ProjectVO> update(@PathVariable Long id, @Valid @RequestBody ProjectUpdateRequest request) {
        permissionService.requireOperate(id);
        log.info("修改项目 {}", id);
        return Result.success(projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.requireManageProjects();
        log.info("删除项目 {}", id);
        projectService.delete(id);
        return Result.success();
    }

    /** 整表替换联。人手保存带 {@code version}；识图写入可不带（认落库当下行）。 */
    @PutMapping("/{id}/units")
    public Result<ProjectVO> replaceUnits(@PathVariable Long id,
                                          @Valid @RequestBody ProjectUnitBatchRequest request) {
        permissionService.requireOperate(id);
        log.info("替换项目联跨径 {}", id);
        return Result.success(projectService.replaceUnits(id, request));
    }

    /**
     * 概览「其他参数」整袋保存。空数组清空袋，不改联表。
     * 必须带当前 {@code project.version} 做袋级锁。
     */
    @PutMapping("/{id}/params")
    public Result<ProjectVO> replaceParams(@PathVariable Long id,
                                           @Valid @RequestBody ProjectParamBatchRequest request) {
        permissionService.requireOperate(id);
        log.info("替换项目扩展参数 {} count={}", id, request.getItems() == null ? 0 : request.getItems().size());
        return Result.success(projectService.replaceParams(id, request));
    }

    /**
     * 多文件上传。不要用 JSON 的 Content-Type；前端用 FormData，字段名 {@code files}、{@code kind}。
     */
    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileBatchUploadVO> upload(@PathVariable Long id,
                                            @RequestParam(defaultValue = "drawing") String kind,
                                            @RequestParam("files") List<MultipartFile> files) {
        permissionService.requireOperate(id);
        log.info("上传项目文件 {} kind={} count={}", id, kind, files == null ? 0 : files.size());
        return Result.success(projectFileService.upload(id, kind, files));
    }

    /** 附件下载，返回二进制流而不是 Result */
    @GetMapping("/{id}/files/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId) {
        permissionService.requireRead(id);
        log.info("下载项目文件 {} / {}", id, fileId);
        return projectFileService.download(id, fileId);
    }

    @DeleteMapping("/{id}/files/{fileId}")
    public Result<Void> deleteFile(@PathVariable Long id, @PathVariable Long fileId) {
        permissionService.requireOperate(id);
        log.info("删除项目文件 {} / {}", id, fileId);
        projectFileService.delete(id, fileId);
        return Result.success();
    }

    /** 项目知识范围：总库全部文献 + 本项目已启用 id */
    @GetMapping("/{id}/knowledge")
    public Result<ProjectKnowledgeVO> listKnowledge(@PathVariable Long id) {
        permissionService.requireRead(id);
        log.info("查询项目知识范围 {}", id);
        return Result.success(knowledgeService.listProjectKnowledge(id));
    }

    /**
     * 在启用且已嵌入的规范里试检索。不接到 Agent；文献范围由服务端注入。
     */
    @PostMapping("/{id}/knowledge/search")
    public Result<KnowledgeSearchVO> searchKnowledge(@PathVariable Long id,
                                                    @Valid @RequestBody KnowledgeSearchRequest request) {
        permissionService.requireRead(id);
        log.info("项目 {} 试检索规范", id);
        return Result.success(knowledgeService.searchProject(id, request.getQuery()));
    }

    @PutMapping("/{id}/knowledge/{docId}")
    public Result<Void> enableKnowledge(@PathVariable Long id, @PathVariable Long docId) {
        permissionService.requireOperate(id);
        log.info("启用文献 {} / {}", id, docId);
        knowledgeService.enable(id, docId);
        return Result.success();
    }

    @DeleteMapping("/{id}/knowledge/{docId}")
    public Result<Void> disableKnowledge(@PathVariable Long id, @PathVariable Long docId) {
        permissionService.requireOperate(id);
        log.info("取消启用文献 {} / {}", id, docId);
        knowledgeService.disable(id, docId);
        return Result.success();
    }

    @GetMapping("/{id}/members")
    public Result<List<ProjectMemberVO>> members(@PathVariable Long id) {
        permissionService.requireRead(id);
        return Result.success(adminRbacService.listProjectMembers(id));
    }
}
