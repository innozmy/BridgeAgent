package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.dto.AdminRoleSaveRequest;
import myz.bridge_agent_demo.dto.AdminUserCreateRequest;
import myz.bridge_agent_demo.dto.AdminUserUpdateRequest;
import myz.bridge_agent_demo.dto.ProjectMemberAssignRequest;
import myz.bridge_agent_demo.entity.SysRole;
import myz.bridge_agent_demo.vo.AdminUserVO;
import myz.bridge_agent_demo.vo.ProjectMemberVO;

import java.util.List;

public interface AdminRbacService {

    List<AdminUserVO> listUsers();

    AdminUserVO createUser(AdminUserCreateRequest request);

    AdminUserVO updateUser(Long userId, AdminUserUpdateRequest request);

    List<SysRole> listRoles();

    SysRole createRole(AdminRoleSaveRequest request);

    SysRole updateRole(Long roleId, AdminRoleSaveRequest request);

    void deleteRole(Long roleId);

    List<ProjectMemberVO> listProjectMembers(Long projectId);

    void assignMember(Long projectId, ProjectMemberAssignRequest request);

    void removeMember(Long projectId, Long userId);
}
