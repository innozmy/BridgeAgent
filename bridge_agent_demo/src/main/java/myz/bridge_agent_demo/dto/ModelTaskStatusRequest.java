package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 手改任务状态。第一版不自动流转。 */
@Data
public class ModelTaskStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "waiting|running|done|failed", message = "状态只能是 waiting、running、done 或 failed")
    private String status;
}
