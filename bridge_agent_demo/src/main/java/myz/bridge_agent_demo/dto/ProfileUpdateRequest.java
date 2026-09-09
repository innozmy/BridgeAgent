package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {

    @NotBlank(message = "请输入昵称")
    @Size(max = 64, message = "昵称不能超过64字")
    private String nickname;
}
