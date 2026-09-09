package myz.bridge_agent_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 发一条问询。服务端转 Python 问询图，回复写入同一会话。 */
@Data
public class InquiryMessageRequest {

    @NotBlank(message = "消息不能为空")
    @Size(max = 4000, message = "消息过长")
    private String body;
}
