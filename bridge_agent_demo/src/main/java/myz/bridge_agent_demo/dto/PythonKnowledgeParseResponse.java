package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO：Python {@code /v1/knowledge/parse} 的摘要。全量 elements 在 sidecar，不走 HTTP。
 */
@Data
public class PythonKnowledgeParseResponse {

    private Boolean ok;
    private String error;
    private Integer elementCount;
    private Integer tableCount;
    private Integer figureCount;
    private Integer vlDone;
    private Integer vlFailed;
    private Integer vlSkipped;
    private Integer figureIndexCount;
    private Integer tableIndexCount;
    private Boolean resumed;
    private List<String> warnings = new ArrayList<>();
}
