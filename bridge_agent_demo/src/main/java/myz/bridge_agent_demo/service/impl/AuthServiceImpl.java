package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.audit.AuditActions;
import myz.bridge_agent_demo.auth.UserContext;
import myz.bridge_agent_demo.dto.LoginRequest;
import myz.bridge_agent_demo.dto.PasswordChangeRequest;
import myz.bridge_agent_demo.dto.ProfileUpdateRequest;
import myz.bridge_agent_demo.entity.SysProjectMember;
import myz.bridge_agent_demo.entity.SysRole;
import myz.bridge_agent_demo.entity.SysUser;
import myz.bridge_agent_demo.exception.BusinessException;
import myz.bridge_agent_demo.mapper.SysProjectMemberMapper;
import myz.bridge_agent_demo.mapper.SysRoleMapper;
import myz.bridge_agent_demo.mapper.SysUserMapper;
import myz.bridge_agent_demo.service.AuditService;
import myz.bridge_agent_demo.service.AuthService;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.JwtService;
import myz.bridge_agent_demo.service.PermissionService;
import myz.bridge_agent_demo.vo.AuthVO;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysProjectMemberMapper memberMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    @Override
    public AuthVO login(LoginRequest request) {
        String username = request.getUsername().trim();
        SysUser user = sysUserMapper.selectOne(
                Wrappers.lambdaQuery(SysUser.class).eq(SysUser::getUsername, username));
        if (user == null) {
            auditService.record(AuditActions.LOGIN_FAIL, false, null, username,
                    "user", null, username, null, "用户名错误", null, null);
            throw new BusinessException("用户名错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditService.record(AuditActions.LOGIN_FAIL, false, user.getId(), username,
                    "user", user.getId(), username, null, "密码错误", null, null);
            throw new BusinessException("密码错误");
        }
        if (!"enabled".equals(user.getStatus())) {
            auditService.record(AuditActions.LOGIN_FAIL, false, user.getId(), username,
                    "user", user.getId(), username, null, "账号已停用", null, null);
            throw new BusinessException("账号已停用");
        }
        // 新登录把 token_version +1，旧设备上的票立刻对不上 ver
        int ver = bumpTokenVersion(user.getId());
        SysRole role = loadRole(user);
        String token = jwtService.issue(user.getId(), user.getUsername(), ver);
        log.info("用户 {} 登录成功", user.getUsername());
        auditService.record(AuditActions.LOGIN_OK, true, user.getId(), user.getUsername(),
                "user", user.getId(), user.getUsername(), null, null, null, null);
        return toAuth(user, role, token, jwtService.expireAtEpochSecond(token));
    }

    @Override
    public AuthVO me() {
        UserContext.User current = UserContext.get();
        if (current == null) {
            throw new BusinessException("未登录或登录已失效");
        }
        SysUser user = sysUserMapper.selectById(current.id());
        return toAuth(user, loadRole(user), null, null);
    }

    @Override
    public AuthVO updateProfile(ProfileUpdateRequest request) {
        UserContext.User current = permissionService.requireUser();
        String before = current.nickname();
        SysUser patch = new SysUser();
        patch.setId(current.id());
        patch.setNickname(request.getNickname().trim());
        sysUserMapper.updateById(patch);
        auditService.record(AuditActions.USER_NICKNAME, true, current.id(), current.username(),
                "user", current.id(), current.username(), null, null, before, patch.getNickname());
        return me();
    }

    @Override
    public void changePassword(PasswordChangeRequest request) {
        UserContext.User current = permissionService.requireUser();
        SysUser user = sysUserMapper.selectById(current.id());
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            auditService.record(AuditActions.PASSWORD_CHANGE, false, current.id(), current.username(),
                    "user", current.id(), current.username(), null, "原密码错误", null, null);
            throw new BusinessException("原密码错误");
        }
        bumpPassword(user.getId(), request.getNewPassword());
        auditService.record(AuditActions.PASSWORD_CHANGE, true, current.id(), current.username(),
                "user", current.id(), current.username(), null, null, null, null);
    }

    @Override
    public void adminResetPassword(Long userId, String rawPassword) {
        bumpPassword(userId, rawPassword);
    }

    @Override
    public AuthVO uploadAvatar(MultipartFile file) {
        UserContext.User current = permissionService.requireUser();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择头像文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp"))) {
            throw new BusinessException("头像仅支持 png / jpg / webp");
        }
        try {
            FileStorageService.StoredFile stored = fileStorageService.storeAvatar(
                    current.id(), file.getOriginalFilename(), file.getInputStream());
            SysUser patch = new SysUser();
            patch.setId(current.id());
            patch.setAvatarPath(stored.storagePath());
            sysUserMapper.updateById(patch);
        } catch (IOException e) {
            throw new BusinessException("头像保存失败");
        }
        auditService.record(AuditActions.USER_AVATAR, true, current.id(), current.username(),
                "user", current.id(), current.username(), null, null, null, "已更换");
        return me();
    }

    @Override
    public ResponseEntity<Resource> avatar(Long userId) {
        permissionService.requireUser();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getAvatarPath())) {
            throw new BusinessException("没有头像");
        }
        Path path = fileStorageService.resolveAvatar(user.getAvatarPath());
        if (!Files.exists(path)) {
            throw new BusinessException("没有头像");
        }
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /** 改密文并废旧票。管理员重置与本人改密共用。 */
    void bumpPassword(Long userId, String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BusinessException("新密码不能为空");
        }
        SysUser existing = sysUserMapper.selectById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setPasswordHash(passwordEncoder.encode(rawPassword));
        int ver = existing.getTokenVersion() == null ? 1 : existing.getTokenVersion();
        patch.setTokenVersion(ver + 1);
        sysUserMapper.updateById(patch);
    }

    /**
     * 废掉该用户所有未过期票，返回新的 version 供签发。
     * CAS 避免两人同时登录都读到同一旧值、两张新票 ver 相同而并存。
     */
    private int bumpTokenVersion(Long userId) {
        SysUser existing = sysUserMapper.selectById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        int from = existing.getTokenVersion() == null ? 1 : existing.getTokenVersion();
        int to = from + 1;
        int n = sysUserMapper.update(null, Wrappers.lambdaUpdate(SysUser.class)
                .set(SysUser::getTokenVersion, to)
                .eq(SysUser::getId, userId)
                .eq(SysUser::getTokenVersion, from));
        if (n == 1) {
            return to;
        }
        SysUser again = sysUserMapper.selectById(userId);
        from = again.getTokenVersion() == null ? 1 : again.getTokenVersion();
        to = from + 1;
        n = sysUserMapper.update(null, Wrappers.lambdaUpdate(SysUser.class)
                .set(SysUser::getTokenVersion, to)
                .eq(SysUser::getId, userId)
                .eq(SysUser::getTokenVersion, from));
        if (n != 1) {
            throw new BusinessException("登录冲突，请重试");
        }
        return to;
    }

    private SysRole loadRole(SysUser user) {
        if (user == null || user.getRoleId() == null) {
            return null;
        }
        return sysRoleMapper.selectById(user.getRoleId());
    }

    private AuthVO toAuth(SysUser user, SysRole role, String token, Long expireAt) {
        UserContext.User ctx = permissionService.toContext(user, role);
        AuthVO vo = new AuthVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setExpireAt(expireAt);
        vo.setRoleCode(role == null ? null : role.getCode());
        vo.setRoleName(role == null ? null : role.getName());
        vo.setSuperFlag(ctx.superUser());
        vo.setKnowledge(ctx.canWriteKnowledge());
        vo.setProjectAdmin(ctx.projectAdmin() || ctx.superUser());
        vo.setAllProjectsOperate(ctx.canManageAllProjects());
        vo.setAvatarUrl(StringUtils.hasText(user.getAvatarPath()) ? "/api/auth/avatars/" + user.getId() : null);
        if (ctx.canManageAllProjects()) {
            vo.setProjectPerms(List.of());
        } else {
            vo.setProjectPerms(memberMapper.selectList(Wrappers.lambdaQuery(SysProjectMember.class)
                            .eq(SysProjectMember::getUserId, user.getId()))
                    .stream()
                    .map(row -> {
                        AuthVO.ProjectPermVO item = new AuthVO.ProjectPermVO();
                        item.setProjectId(row.getProjectId());
                        item.setPerm(row.getPerm());
                        return item;
                    })
                    .toList());
        }
        return vo;
    }
}
