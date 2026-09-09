package myz.bridge_agent_demo.dto;

import lombok.Data;

/**
 * DTO：Python {@code /v1/knowledge/embed} 的摘要。向量在 Milvus，不走 HTTP。
 */
@Data
public class PythonKnowledgeEmbedResponse {

    private Boolean ok;
    private String error;
    private Integer rowCount;
    private Integer textCount;
    private Integer figureCount;
    private Integer tableCount;
    private String embedModel;
}
