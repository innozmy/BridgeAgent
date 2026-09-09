package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code model_task_event}：任务时间线的一行。 */
@Data
@TableName("model_task_event")
public class ModelTaskEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    /** HTTP 操作人；后台作业为空，username 用「系统」 */
    private Long actorUserId;
    private String actorUsername;
    private String body;
    private LocalDateTime createdAt;
}
