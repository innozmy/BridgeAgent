package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code agent_stm}：任务类工种短期记忆，(项目, agentKind) 一行。
 * 只放索引/墓碑/短摘要；全书页地图在 {@link DrawingPageMap}。问询不用本表。
 */
@Data
@TableName("agent_stm")
public class AgentStm {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** drawing_parse / modeling 等 */
    private String agentKind;
    private String bodyJson;
    private LocalDateTime updatedAt;
}
