package myz.bridge_agent_demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 整表替换某项目的联（跨径 + 可选墩柱）。对应 {@code PUT /api/projects/{id}/units}。
 * 先删旧联再插入（墩柱外键级联清）。空数组表示清空联。
 */
@Data
public class ProjectUnitBatchRequest {

    @NotNull(message = "联列表不能为 null")
    @Valid
    private List<ProjectUnitItemRequest> units;

    /** 人手保存必填，等于当前 {@code project.version}；识图写入可空（落库当下行） */
    private Integer version;
}
