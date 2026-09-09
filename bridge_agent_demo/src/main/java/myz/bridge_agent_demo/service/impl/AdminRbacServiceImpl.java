package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import myz.bridge_agent_demo.audit.AuditActions;
import myz.bridge_agent_demo.entity.Project;
import myz.bridge_agent_demo.entity.SysProjectMember;
import myz.bridge_agent_demo.entity.SysRole;
import myz.bridge_agent_demo.entity.SysUser;
import myz.bridge_agent_demo.exception.BusinessException;
import myz.bridge_agent_demo.mapper.ProjectMapper;
import myz.bridge_agent_demo.mapper.SysProjectMemberMapper;
import myz.bridge_agent_demo.mapper.SysRoleMapper;
import myz.bridge_agent_demo.mapper.SysUserMapper;
import myz.bridge_agent_demo.dto.AdminRoleSaveRequest;
import myz.bridge_agent_demo.dto.AdminUserCreateRequest;
import myz.bridge_agent_demo.dto.AdminUserUpdateRequest;
import myz.bridge_agent_demo.dto.ProjectMemberAssignRequest;
import myz.bridge_agent_demo.service.AdminRbacService;
import myz.bridge_agent_demo.service.AuditService;
import myz.bridge_agent_demo.service.AuthService;
import myz.bridge_agent_demo.service.PermissionService;
import myz.bridge_agent_demo.vo.AdminUserVO;
import myz.bridge_agent_demo.vo.ProjectMemberVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRbacServiceImpl implements AdminRbacService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysProjectMemberMapper memberMapper;
    private final ProjectMapper projectMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final PermissionService permissionService;
    private final AuditService auditService;

    @Override
    public List<AdminUserVO> listUsers() {
        permissionService.requireAssignMembers();
        Map<Long, SysRole> roles = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r));
        return userMapper.selectList(Wrappers.lambdaQuery(SysUser.class).orderByAsc(SysUser::getId))
                .stream()
                .map(u -> toUserVo(u, roles.get(u.getRoleId())))
                .toList();
    }

    @Override
    @Transactional
    public AdminUserVO createUser(AdminUserCreateRequest request) {
        permissionService.requireSuper();
        SysRole role = requireRole(request.getRoleId());
        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setNickname(request.getNickname().trim());
        user.setRoleId(role.getId());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setTokenVersion(1);
        user.setStatus("enabled");
        userMapper.insert(user);
        auditService.record(AuditActions.USER_CREATE, true, null, null,
                "user", user.getId(), user.getUsername(), null, null, null, role.getCode());
        return toUserVo(user, role);
    }

    @Override
    @Transactional
    public AdminUserVO updateUser(Long userId, AdminUserUpdateRequest request) {
        permissionService.requireSuper();
        SysUser existing = userMapper.selectById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        SysRole newRole = request.getRoleId() == null ? roleMapper.selectById(existing.getRoleId())
                : requireRole(request.getRoleId());
        if (isSuper(roleMapper.selectById(existing.getRoleId()))
                && (!isSuper(newRole) || "disabled".equals(request.getStatus()))) {
            ensureNotLastSuper(userId);
        }
        SysUser patch = new SysUser();
        patch.setId(userId);
        boolean hasColumnPatch = false;
        if (StringUtils.hasText(request.getNickname())) {
            patch.setNickname(request.getNickname().trim());
            hasColumnPatch = true;
        }
        if (request.getRoleId() != null) {
            patch.setRoleId(request.getRoleId());
            hasColumnPatch = true;
            if (isCovered(newRole)) {
                memberMapper.delete(Wrappers.lambdaQuery(SysProjectMember.class)
                        .eq(SysProjectMember::getUserId, userId));
            }
        }
        if (StringUtils.hasText(request.getStatus())) {
            if (!"enabled".equals(request.getStatus()) && !"disabled".equals(request.getStatus())) {
                throw new BusinessException("状态不合法");
            }
            patch.setStatus(request.getStatus());
            hasColumnPatch = true;
            if ("disabled".equals(request.getStatus())) {
                int ver = existing.getTokenVersion() == null ? 1 : existing.getTokenVersion();
                patch.setTokenVersion(ver + 1);
            }
        }
        // 只重置密码时补丁只有 id，MP 会拼出空 SET 导致 500
        if (hasColumnPatch) {
            userMapper.updateById(patch);
        }
        if (StringUtils.hasText(request.getPassword())) {
            authService.adminResetPassword(userId, request.getPassword());
            auditService.record(AuditActions.PASSWORD_RESET, true, null, null,
                    "user", userId, existing.getUsername(), null, null, null, null);
        }
        if (StringUtils.hasText(request.getNickname())) {
            auditService.record(AuditActions.USER_NICKNAME, true, null, null,
                    "user", userId, existing.getUsername(), null, null,
                    existing.getNickname(), request.getNickname().trim());
        }
        if (request.getRoleId() != null && !Objects.equals(request.getRoleId(), existing.getRoleId())) {
            SysRole oldRole = roleMapper.selectById(existing.getRoleId());
            auditService.record(AuditActions.USER_ROLE, true, null, null,
                    "user", userId, existing.getUsername(), null, null,
                    oldRole == null ? null : oldRole.getCode(), newRole.getCode());
        }
        if (StringUtils.hasText(request.getStatus()) && !request.getStatus().equals(existing.getStatus())) {
            auditService.record(AuditActions.USER_STATUS, true, null, null,
                    "user", userId, existing.getUsername(), null, null,
                    existing.getStatus(), request.getStatus());
        }
        SysUser updated = userMapper.selectById(userId);
        return toUserVo(updated, roleMapper.selectById(updated.getRoleId()));
    }

    @Override
    public List<SysRole> listRoles() {
        permissionService.requireSuper();
        return roleMapper.selectList(Wrappers.lambdaQuery(SysRole.class).orderByAsc(SysRole::getId));
    }

    @Override
    public SysRole createRole(AdminRoleSaveRequest request) {
        permissionService.requireSuper();
        SysRole role = new SysRole();
        role.setCode(request.getCode().trim());
        role.setName(request.getName().trim());
        role.setBuiltin(false);
        role.setFlagSuper(Boolean.TRUE.equals(request.getFlagSuper()));
        role.setFlagKnowledge(Boolean.TRUE.equals(request.getFlagKnowledge()));
        role.setFlagProjectAdmin(Boolean.TRUE.equals(request.getFlagProjectAdmin()));
        roleMapper.insert(role);
        auditService.record(AuditActions.ROLE_CREATE, true, null, null,
                "role", role.getId(), role.getCode(), null, null, null, role.getName());
        return role;
    }

    @Override
    public SysRole updateRole(Long roleId, AdminRoleSaveRequest request) {
        permissionService.requireSuper();
        SysRole existing = requireRole(roleId);
        if (Boolean.TRUE.equals(existing.getBuiltin())) {
            throw new BusinessException("内置角色不可改开关");
        }
        SysRole patch = new SysRole();
        patch.setId(roleId);
        patch.setName(request.getName().trim());
        patch.setFlagSuper(Boolean.TRUE.equals(request.getFlagSuper()));
        patch.setFlagKnowledge(Boolean.TRUE.equals(request.getFlagKnowledge()));
        patch.setFlagProjectAdmin(Boolean.TRUE.equals(request.getFlagProjectAdmin()));
        roleMapper.updateById(patch);
        auditService.record(AuditActions.ROLE_UPDATE, true, null, null,
                "role", roleId, existing.getCode(), null, null, existing.getName(), request.getName().trim());
        return roleMapper.selectById(roleId);
    }

    @Override
    public void deleteRole(Long roleId) {
        permissionService.requireSuper();
        SysRole existing = requireRole(roleId);
        if (Boolean.TRUE.equals(existing.getBuiltin())) {
            throw new BusinessException("内置角色不可删除");
        }
        Long used = userMapper.selectCount(Wrappers.lambdaQuery(SysUser.class).eq(SysUser::getRoleId, roleId));
        if (used != null && used > 0) {
            throw new BusinessException("仍有用户使用该角色");
        }
        roleMapper.deleteById(roleId);
        auditService.record(AuditActions.ROLE_DELETE, true, null, null,
                "role", roleId, existing.getCode(), null, null, existing.getName(), null);
    }

    @Override
    public List<ProjectMemberVO> listProjectMembers(Long projectId) {
        permissionService.requireRead(projectId);
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        List<ProjectMemberVO> rows = new ArrayList<>();
        Map<Long, SysUser> users = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, SysRole> roles = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r));
        for (SysUser user : users.values()) {
            SysRole role = roles.get(user.getRoleId());
            if (isCovered(role) && "enabled".equals(user.getStatus())) {
                ProjectMemberVO vo = new ProjectMemberVO();
                vo.setProjectId(projectId);
                vo.setProjectName(project.getName());
                vo.setUserId(user.getId());
                vo.setUsername(user.getUsername());
                vo.setNickname(user.getNickname());
                vo.setPerm("operate");
                vo.setOverlay(true);
                rows.add(vo);
            }
        }
        for (SysProjectMember row : memberMapper.selectList(Wrappers.lambdaQuery(SysProjectMember.class)
                .eq(SysProjectMember::getProjectId, projectId))) {
            SysUser user = users.get(row.getUserId());
            if (user == null) {
                continue;
            }
            ProjectMemberVO vo = new ProjectMemberVO();
            vo.setId(row.getId());
            vo.setProjectId(projectId);
            vo.setProjectName(project.getName());
            vo.setUserId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setPerm(row.getPerm());
            vo.setOverlay(false);
            rows.add(vo);
        }
        return rows;
    }

    @Override
    @Transactional
    public void assignMember(Long projectId, ProjectMemberAssignRequest request) {
        permissionService.requireAssignMembers();
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException("项目不存在");
        }
        SysUser user = userMapper.selectById(request.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        SysRole role = roleMapper.selectById(user.getRoleId());
        if (isCovered(role)) {
            throw new BusinessException("该用户已由高层权限覆盖全部项目，不必再分配");
        }
        SysProjectMember existing = memberMapper.selectOne(Wrappers.lambdaQuery(SysProjectMember.class)
                .eq(SysProjectMember::getProjectId, projectId)
                .eq(SysProjectMember::getUserId, request.getUserId()));
        String before = existing == null ? null : existing.getPerm();
        if (existing == null) {
            SysProjectMember row = new SysProjectMember();
            row.setProjectId(projectId);
            row.setUserId(request.getUserId());
            row.setPerm(request.getPerm());
            memberMapper.insert(row);
        } else {
            SysProjectMember patch = new SysProjectMember();
            patch.setId(existing.getId());
            patch.setPerm(request.getPerm());
            memberMapper.updateById(patch);
        }
        auditService.record(AuditActions.MEMBER_ASSIGN, true, null, null,
                "project_member", request.getUserId(), user.getUsername(), projectId, null,
                before, request.getPerm());
    }

    @Override
    public void removeMember(Long projectId, Long userId) {
        permissionService.requireAssignMembers();
        SysUser user = userMapper.selectById(userId);
        SysProjectMember existing = memberMapper.selectOne(Wrappers.lambdaQuery(SysProjectMember.class)
                .eq(SysProjectMember::getProjectId, projectId)
                .eq(SysProjectMember::getUserId, userId));
        String before = existing == null ? null : existing.getPerm();
        memberMapper.delete(Wrappers.lambdaQuery(SysProjectMember.class)
                .eq(SysProjectMember::getProjectId, projectId)
                .eq(SysProjectMember::getUserId, userId));
        auditService.record(AuditActions.MEMBER_REMOVE, true, null, null,
                "project_member", userId,
                user == null ? String.valueOf(userId) : user.getUsername(),
                projectId, null, before, null);
    }

    private void ensureNotLastSuper(Long userId) {
        List<SysUser> supers = userMapper.selectList(null).stream()
                .filter(u -> "enabled".equals(u.getStatus()))
                .filter(u -> isSuper(roleMapper.selectById(u.getRoleId())))
                .toList();
        if (supers.size() <= 1 && supers.stream().anyMatch(u -> Objects.equals(u.getId(), userId))) {
            throw new BusinessException("不能停用或降级最后一个超级管理员");
        }
    }

    private SysRole requireRole(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    private static boolean isSuper(SysRole role) {
        return role != null && Boolean.TRUE.equals(role.getFlagSuper());
    }

    private static boolean isCovered(SysRole role) {
        return isSuper(role) || (role != null && Boolean.TRUE.equals(role.getFlagProjectAdmin()));
    }

    private AdminUserVO toUserVo(SysUser user, SysRole role) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRoleId(user.getRoleId());
        vo.setRoleCode(role == null ? null : role.getCode());
        vo.setRoleName(role == null ? null : role.getName());
        vo.setStatus(user.getStatus());
        vo.setAvatarUrl(StringUtils.hasText(user.getAvatarPath()) ? "/api/auth/avatars/" + user.getId() : null);
        vo.setCoveredByHighRole(isCovered(role));
        return vo;
    }
}
