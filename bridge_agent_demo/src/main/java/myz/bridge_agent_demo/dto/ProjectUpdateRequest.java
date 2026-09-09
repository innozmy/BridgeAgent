package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 修改项目请求体。字段为 null 表示「这次不改」；空字符串会清成数据库 null。
 */
@Data
public class ProjectUpdateRequest {

    @Size(max = 200, message = "项目名称不能超过200字")
    private String name;

    @Pattern(regexp = "left|right|undivided", message = "幅面只能是 left、right 或 undivided")
    private String carriageway;

    @Size(max = 64, message = "项目标号不能超过64字")
    private String code;

    @Size(max = 2000, message = "简介不能超过2000字")
    private String intro;

    @Size(max = 64, message = "地区不能超过64字")
    private String region;

    private LocalDate openedOn;

    @Pattern(regexp = "at_opening|current_review|", message = "规范策略只能是 at_opening 或 current_review")
    private String codeStrategy;

    @Size(max = 64, message = "主梁形式不能超过64字")
    private String girderType;

    @Size(max = 64, message = "结构形式不能超过64字")
    private String layoutType;

    @Size(max = 32, message = "材料不能超过32字")
    private String material;

    @Pattern(regexp = "draft|modeling|validating|calibrating|done", message = "状态不合法")
    private String status;

    /** 人手保存必填，等于当前 {@code project.version}；识图写入忽略此字段 */
    private Integer version;
}
