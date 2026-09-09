package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import myz.bridge_agent_demo.auth.UserContext;
import myz.bridge_agent_demo.entity.SysProjectMember;
import myz.bridge_agent_demo.entity.SysRole;
import myz.bridge_agent_demo.entity.SysUser;
import myz.bridge_agent_demo.exception.ForbiddenException;
import myz.bridge_agent_demo.mapper.SysProjectMemberMapper;
import myz.bridge_agent_demo.service.PermissionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final SysProjectMemberMapper memberMapper;

    @Override
    public UserContext.User requireUser() {
        UserContext.User user = UserContext.get();
        if (user == null) {
            throw new ForbiddenException("未登录或登录已失效");
        }
        return user;
    }

    @Override
    public Access projectAccess(long projectId) {
        UserContext.User user = requireUser();
        if (user.canManageAllProjects()) {
            return Access.OPERATE;
        }
        SysProjectMember row = memberMapper.selectOne(Wrappers.lambdaQuery(SysProjectMember.class)
                .eq(SysProjectMember::getProjectId, projectId)
                .eq(SysProjectMember::getUserId, user.id()));
        if (row == null) {
            return Access.NONE;
        }
        return "operate".equals(row.getPerm()) ? Access.OPERATE : Access.READ;
    }

    @Override
    public void requireRead(long projectId) {
        if (projectAccess(projectId) == Access.NONE) {
            throw new ForbiddenException("没有该项目的访问权限");
        }
    }

    @Override
    public void requireOperate(long projectId) {
        if (projectAccess(projectId) != Access.OPERATE) {
            throw new ForbiddenException("没有该项目的操作权限");
        }
    }

    @Override
    public void requireKnowledgeWrite() {
        if (!requireUser().canWriteKnowledge()) {
            throw new ForbiddenException("没有知识库管理权限");
        }
    }

    @Override
    public void requireManageProjects() {
        if (!requireUser().canManageAllProjects()) {
            throw new ForbiddenException("没有项目管理权限");
        }
    }

    @Override
    public void requireSuper() {
        if (!requireUser().superUser()) {
            throw new ForbiddenException("需要超级管理员");
        }
    }

    @Override
    public void requireAssignMembers() {
        if (!requireUser().canManageAllProjects()) {
            throw new ForbiddenException("没有项目层权限的分配资格");
        }
    }

    @Override
    public List<Long> visibleProjectIds() {
        UserContext.User user = requireUser();
        if (user.canManageAllProjects()) {
            return null;
        }
        return memberMapper.selectList(Wrappers.lambdaQuery(SysProjectMember.class)
                        .eq(SysProjectMember::getUserId, user.id()))
                .stream()
                .map(SysProjectMember::getProjectId)
                .toList();
    }

    @Override
    public boolean canManageAllProjects() {
        return requireUser().canManageAllProjects();
    }

    @Override
    public UserContext.User toContext(SysUser user, SysRole role) {
        boolean superUser = role != null && Boolean.TRUE.equals(role.getFlagSuper());
        boolean knowledge = superUser || (role != null && Boolean.TRUE.equals(role.getFlagKnowledge()));
        boolean projectAdmin = superUser || (role != null && Boolean.TRUE.equals(role.getFlagProjectAdmin()));
        String code = role == null ? "" : role.getCode();
        return new UserContext.User(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                code,
                superUser,
                knowledge,
                projectAdmin,
                user.getAvatarPath());
    }
}
