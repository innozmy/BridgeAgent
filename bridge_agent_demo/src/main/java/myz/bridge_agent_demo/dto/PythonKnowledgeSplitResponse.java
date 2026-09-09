package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO：Python {@code /v1/knowledge/split} 的摘要。chunks 在 split/chunks.json，不走 HTTP。
 */
@Data
public class PythonKnowledgeSplitResponse {

    private Boolean ok;
    private String error;
    private Integer chunkCount;
    private Integer splitSourceCount;
    private List<String> warnings = new ArrayList<>();
}
