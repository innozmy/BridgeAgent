package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO：参数袋一行。禁止与固定列 / 墩柱子表同义（跨径、柱高等）。
 */
@Data
public class ProjectParamItemRequest {

    @NotBlank(message = "参数键不能为空")
    @Size(max = 64, message = "参数键过长")
    private String paramKey;

    @NotBlank(message = "参数名称不能为空")
    @Size(max = 128, message = "参数名称过长")
    private String label;

    @Size(max = 512, message = "参数值过长")
    private String valueText;

    @Size(max = 32, message = "单位过长")
    private String unit;

    @Pattern(regexp = "drawing|cad|agent|manual", message = "来源只能是 drawing、cad、agent 或 manual")
    private String source;
}
