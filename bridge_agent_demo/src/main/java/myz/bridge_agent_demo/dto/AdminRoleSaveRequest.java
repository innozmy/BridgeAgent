package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminRoleSaveRequest {

    @NotBlank
    @Size(max = 32)
    private String code;

    @NotBlank
    @Size(max = 64)
    private String name;

    private Boolean flagSuper;
    private Boolean flagKnowledge;
    private Boolean flagProjectAdmin;
}
