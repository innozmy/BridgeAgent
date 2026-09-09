package myz.bridge_agent_demo.dto;

import lombok.Data;

/** DTO：同意或拒绝任务卡时带上问询会话，便于写 event。 */
@Data
public class TaskCardActionRequest {
    private Long inquiryThreadId;
}
