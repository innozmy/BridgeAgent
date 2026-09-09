package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新建项目请求体。对应 {@code POST /api/projects}。
 * 只有名称、幅面必填；标号、跨径都可以以后再填。
 */
@Data
public class ProjectCreateRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称不能超过200字")
    private String name;

    /** left / right / undivided */
    @NotBlank(message = "幅面不能为空")
    @Pattern(regexp = "left|right|undivided", message = "幅面只能是 left、right 或 undivided")
    private String carriageway;

    @Size(max = 64, message = "项目标号不能超过64字")
    private String code;

    @Size(max = 2000, message = "简介不能超过2000字")
    private String intro;

    @Size(max = 64, message = "地区不能超过64字")
    private String region;

    private LocalDate openedOn;

    /** at_opening / current_review，可空 */
    @Pattern(regexp = "at_opening|current_review", message = "规范策略只能是 at_opening 或 current_review")
    private String codeStrategy;
}
