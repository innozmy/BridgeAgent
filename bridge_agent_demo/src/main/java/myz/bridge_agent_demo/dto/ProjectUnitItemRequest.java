package myz.bridge_agent_demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一批次替换里的「一联」：序号 + 跨径；墩台可附带。
 * {@code supports == null} 表示保留该联原墩柱；空数组表示清空墩柱。
 */
@Data
public class ProjectUnitItemRequest {

    @NotNull(message = "联序号不能为空")
    @Min(value = 1, message = "联序号从 1 开始")
    private Integer seq;

    @NotEmpty(message = "跨径不能为空")
    private List<@NotNull @DecimalMin(value = "0", inclusive = false, message = "跨径必须大于 0") BigDecimal> spansM;

    /** 不传则服务端记为 manual */
    @Pattern(regexp = "drawing|cad|agent|manual", message = "来源只能是 drawing、cad、agent 或 manual")
    private String source;

    @Valid
    private List<ProjectUnitSupportItemRequest> supports;
}
