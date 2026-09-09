package myz.bridge_agent_demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import myz.bridge_agent_demo.common.Result;
import myz.bridge_agent_demo.dto.AdminRoleSaveRequest;
import myz.bridge_agent_demo.dto.AdminUserCreateRequest;
import myz.bridge_agent_demo.dto.AdminUserUpdateRequest;
import myz.bridge_agent_demo.dto.ProjectMemberAssignRequest;
import myz.bridge_agent_demo.entity.SysRole;
import myz.bridge_agent_demo.service.AdminRbacService;
import myz.bridge_agent_demo.service.AuditService;
import myz.bridge_agent_demo.vo.AdminUserVO;
import myz.bridge_agent_demo.vo.AuditPageVO;
import myz.bridge_agent_demo.vo.ProjectMemberVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 系统管理：用户 / 角色 / 项目层成员。鉴权在 Service。 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminRbacService adminRbacService;
    private final AuditService auditService;

    @GetMapping("/audits")
    public Result<AuditPageVO> audits(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.success(auditService.list(action, actor, page, size));
    }

    @GetMapping("/users")
    public Result<List<AdminUserVO>> users() {
        return Result.success(adminRbacService.listUsers());
    }

    @PostMapping("/users")
    public Result<AdminUserVO> createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        return Result.success(adminRbacService.createUser(request));
    }

    @PutMapping("/users/{id}")
    public Result<AdminUserVO> updateUser(@PathVariable Long id, @RequestBody AdminUserUpdateRequest request) {
        return Result.success(adminRbacService.updateUser(id, request));
    }

    @GetMapping("/roles")
    public Result<List<SysRole>> roles() {
        return Result.success(adminRbacService.listRoles());
    }

    @PostMapping("/roles")
    public Result<SysRole> createRole(@Valid @RequestBody AdminRoleSaveRequest request) {
        return Result.success(adminRbacService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    public Result<SysRole> updateRole(@PathVariable Long id, @Valid @RequestBody AdminRoleSaveRequest request) {
        return Result.success(adminRbacService.updateRole(id, request));
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        adminRbacService.deleteRole(id);
        return Result.success();
    }

    @GetMapping("/projects/{projectId}/members")
    public Result<List<ProjectMemberVO>> members(@PathVariable Long projectId) {
        return Result.success(adminRbacService.listProjectMembers(projectId));
    }

    @PutMapping("/projects/{projectId}/members")
    public Result<Void> assign(@PathVariable Long projectId, @Valid @RequestBody ProjectMemberAssignRequest request) {
        adminRbacService.assignMember(projectId, request);
        return Result.success();
    }

    @DeleteMapping("/projects/{projectId}/members/{userId}")
    public Result<Void> remove(@PathVariable Long projectId, @PathVariable Long userId) {
        adminRbacService.removeMember(projectId, userId);
        return Result.success();
    }
}
