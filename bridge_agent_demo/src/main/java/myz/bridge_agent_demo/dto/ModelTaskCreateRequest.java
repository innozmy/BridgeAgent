package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 旧入口：起草任务卡请走 {@code POST /cards}。{@code kind} 必须是枚举。 */
@Data
public class ModelTaskCreateRequest {

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 200, message = "标题不能超过200字")
    private String title;

    @NotBlank(message = "请选择或填写任务类型")
    @Size(max = 64, message = "类型不能超过64字")
    private String kind;
}
