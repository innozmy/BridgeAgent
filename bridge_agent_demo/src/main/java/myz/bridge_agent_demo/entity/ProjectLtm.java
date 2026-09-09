package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code project_ltm}：每个项目一份长期记忆。
 * 只存过程索引与教训，不存结构尺寸。由 Spring 在任务终态写入。
 */
@Data
@TableName("project_ltm")
public class ProjectLtm {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** JSON 正文，库列 body_json */
    private String bodyJson;
    private LocalDateTime updatedAt;
}
