package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserCreateRequest {

    @NotBlank(message = "请输入用户名")
    @Size(max = 32)
    private String username;

    @NotBlank(message = "请输入昵称")
    @Size(max = 64)
    private String nickname;

    @NotBlank(message = "请输入密码")
    @Size(min = 4, max = 128)
    private String password;

    @NotNull(message = "请选择角色")
    private Long roleId;
}
