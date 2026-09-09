package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO：Python {@code /v1/knowledge/merge} 的摘要。chunks 在 merge/chunks.json，不走 HTTP。
 */
@Data
public class PythonKnowledgeMergeResponse {

    private Boolean ok;
    private String error;
    private Integer chunkCount;
    private Integer mentionedFigureChunkCount;
    private Integer mentionedTableChunkCount;
    private List<String> warnings = new ArrayList<>();
}
