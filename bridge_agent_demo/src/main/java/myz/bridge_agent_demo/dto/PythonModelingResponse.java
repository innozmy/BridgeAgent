package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO：{@code POST /v1/agents/modeling} 的结构化响应。
 * 硬缺口走 {@code needSupplement}；建成则带本机 sapPath，由 Spring 登记版本。
 */
@Data
public class PythonModelingResponse {

    private Boolean ok;
    private String error;
    /** NO_PAGE：扩扫后仍缺，需上传专页或手填 */
    private String code;
    private Boolean ready;
    private PythonNeedSupplement needSupplement;
    private List<Map<String, Object>> missingHard = new ArrayList<>();
    private List<String> gaps = new ArrayList<>();
    private String sapPath;
    private String sapVersion;
    private Map<String, Object> previewJson;
    private Integer jointCount;
    private Integer frameCount;
    private String note;
    /** consult 规范覆盖层 notices，进时间线；不写账本 */
    private List<Map<String, Object>> notices = new ArrayList<>();
}
