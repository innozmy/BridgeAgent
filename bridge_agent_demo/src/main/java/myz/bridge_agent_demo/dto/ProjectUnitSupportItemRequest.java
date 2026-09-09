package myz.bridge_agent_demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * DTO：写入一联时的一个墩或台。柱列表可空（只记墩号尚未量高）。
 */
@Data
public class ProjectUnitSupportItemRequest {

    @NotNull(message = "墩台序号不能为空")
    @Min(value = 0, message = "墩台序号从 0 开始")
    private Integer seq;

    /** 如 P1、1#、0#台 */
    private String code;

    /** 不传则服务端记为 pier */
    @Pattern(regexp = "pier|abutment", message = "墩台类型只能是 pier 或 abutment")
    private String kind;

    @Pattern(regexp = "drawing|cad|agent|manual", message = "来源只能是 drawing、cad、agent 或 manual")
    private String source;

    @Valid
    private List<ProjectUnitColumnItemRequest> columns;
}
