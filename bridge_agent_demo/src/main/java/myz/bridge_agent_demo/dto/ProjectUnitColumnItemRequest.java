package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO：写入一联时的一根墩柱。同一墩上左右柱高度不同必须两条。
 */
@Data
public class ProjectUnitColumnItemRequest {

    @NotNull(message = "柱序号不能为空")
    @Min(value = 1, message = "同一墩上柱序号从 1 开始")
    private Integer seq;

    /** left / right / inner / outer；不传则空 */
    private String side;

    /** 盖梁底至承台顶/桩顶，米；未测可空 */
    @DecimalMin(value = "0", inclusive = false, message = "柱高必须大于 0")
    private BigDecimal heightM;

    @Pattern(regexp = "drawing|cad|agent|manual", message = "来源只能是 drawing、cad、agent 或 manual")
    private String source;
}
