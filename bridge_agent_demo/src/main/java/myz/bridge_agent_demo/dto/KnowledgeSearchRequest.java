package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO：项目知识范围页试检索。启用集由服务端注入，客户端只传问句。
 */
@Data
public class KnowledgeSearchRequest {

    @NotBlank(message = "问句不能为空")
    @Size(max = 500, message = "问句过长")
    private String query;
}
