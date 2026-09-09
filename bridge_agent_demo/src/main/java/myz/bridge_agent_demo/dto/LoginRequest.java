package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求体。对应 {@code POST /api/auth/login}。
 * 用户不存在 / 密码不对的文案在 Service 里分开说，这里只拦空值。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "请输入用户名")
    @Size(max = 32, message = "用户名不能超过32字")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(max = 128, message = "密码过长")
    private String password;
}
