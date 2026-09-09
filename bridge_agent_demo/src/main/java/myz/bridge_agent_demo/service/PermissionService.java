package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.auth.UserContext;
import myz.bridge_agent_demo.entity.SysRole;
import myz.bridge_agent_demo.entity.SysUser;

import java.util.List;

/**
 * HTTP 请求内鉴权。后台 {@code @Async} 不要调这里（没有 UserContext）。
 */
public interface PermissionService {

    enum Access {
        NONE,
        READ,
        OPERATE
    }

    UserContext.User requireUser();

    Access projectAccess(long projectId);

    void requireRead(long projectId);

    void requireOperate(long projectId);

    void requireKnowledgeWrite();

    void requireManageProjects();

    void requireSuper();

    void requireAssignMembers();

    /** 超管/项目管理员看全部；否则成员表里的项目 id */
    List<Long> visibleProjectIds();

    boolean canManageAllProjects();

    UserContext.User toContext(SysUser user, SysRole role);
}
