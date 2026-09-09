package myz.bridge_agent_demo.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 超管看的审计列表。controller / vo */
@Data
public class AuditPageVO {

    private Long total;
    private Long page;
    private Long size;
    private List<Row> records;

    @Data
    public static class Row {
        private Long id;
        private LocalDateTime createdAt;
        private Long actorUserId;
        private String actorUsername;
        private String action;
        private String targetType;
        private Long targetId;
        private String targetLabel;
        private Long projectId;
        private Boolean success;
        private String reason;
        private String beforeText;
        private String afterText;
        private String ip;
    }
}
