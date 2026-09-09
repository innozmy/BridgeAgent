package myz.bridge_agent_demo.vo;

import lombok.Data;

import java.util.List;

/**
 * 登录 / me：票 + 展示名 + 权限开关。密码与 token_version 不回传。权限不进 JWT。
 */
@Data
public class AuthVO {

    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private Long expireAt;
    private String roleCode;
    private String roleName;
    private Boolean superFlag;
    private Boolean knowledge;
    private Boolean projectAdmin;
    /** 超管/项目管理员为 true 时 projectPerms 可空，表示全部项目操作 */
    private Boolean allProjectsOperate;
    private List<ProjectPermVO> projectPerms;
    private String avatarUrl;

    @Data
    public static class ProjectPermVO {
        private Long projectId;
        /** read / operate */
        private String perm;
    }
}
