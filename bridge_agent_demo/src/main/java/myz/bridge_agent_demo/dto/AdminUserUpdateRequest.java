package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserUpdateRequest {

    @Size(max = 64)
    private String nickname;

    private Long roleId;

    /** enabled / disabled */
    private String status;

    private String password;
}
