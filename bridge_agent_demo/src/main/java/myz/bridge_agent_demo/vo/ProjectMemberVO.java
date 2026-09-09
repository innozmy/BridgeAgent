package myz.bridge_agent_demo.vo;

import lombok.Data;

@Data
public class ProjectMemberVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long userId;
    private String username;
    private String nickname;
    /** read / operate / overlay */
    private String perm;
    private Boolean overlay;
}
