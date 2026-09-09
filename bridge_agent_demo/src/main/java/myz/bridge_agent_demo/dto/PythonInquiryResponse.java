package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.util.List;

/**
 * DTO：Python 问询返回。{@code ok=true} 时用 {@code reply}；失败时 {@code error} 给人看。
 */
@Data
public class PythonInquiryResponse {

    private Boolean ok;
    private String error;
    private String reply;
    /** 问询最多一张补充识别卡；非法由 Spring 丢弃 */
    private TaskCardHint taskCard;

    @Data
    public static class TaskCardHint {
        private Long fileId;
        private List<String> pageKinds;
        private String directive;
        private String reason;
    }
}
