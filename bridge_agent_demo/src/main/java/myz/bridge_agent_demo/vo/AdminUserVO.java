package myz.bridge_agent_demo.vo;

import lombok.Data;

@Data
public class AdminUserVO {
    private Long id;
    private String username;
    private String nickname;
    private Long roleId;
    private String roleCode;
    private String roleName;
    private String status;
    private String avatarUrl;
    private Boolean coveredByHighRole;
}
