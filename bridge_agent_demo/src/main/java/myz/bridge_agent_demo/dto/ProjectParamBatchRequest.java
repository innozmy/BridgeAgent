package myz.bridge_agent_demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 整袋替换某项目的扩展参数。对应 {@code PUT /api/projects/{id}/params}。
 * 空数组表示清空袋内项，不改固定列和联表。
 */
@Data
public class ProjectParamBatchRequest {

    @NotNull(message = "参数列表不能为 null")
    @Valid
    private List<ProjectParamItemRequest> items;

    /** 袋级锁：等于当前 {@code project.version}，先 CAS 再删插袋行 */
    private Integer version;
}
