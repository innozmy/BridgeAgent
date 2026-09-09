package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProjectMemberAssignRequest {

    @NotNull
    private Long userId;

    @NotBlank
    @Pattern(regexp = "read|operate", message = "只能是 read 或 operate")
    private String perm;
}
