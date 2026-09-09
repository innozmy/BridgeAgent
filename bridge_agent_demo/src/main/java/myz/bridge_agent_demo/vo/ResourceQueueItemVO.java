package myz.bridge_agent_demo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 资源队列里的一行（controller / vo）。 */
@Data
public class ResourceQueueItemVO {

    /** drawing / knowledge / modeling */
    private String lane;
    /** running / queued */
    private String state;
    private String title;
    /** 知识行为空 */
    private Long projectId;
    private String projectName;
    /** task / knowledge */
    private String refType;
    private Long refId;
    /** parse / merge / split / embed；任务行可空 */
    private String step;
    private String actorUsername;
    private LocalDateTime updatedAt;
}
