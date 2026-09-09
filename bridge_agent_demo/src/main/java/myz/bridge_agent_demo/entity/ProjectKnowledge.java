package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code project_knowledge}：这个项目启用了哪本文献。
 * 有这一行就是启用；删除行即取消。不要把 enabled 放在文献表上。
 */
@Data
@TableName("project_knowledge")
public class ProjectKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long documentId;
    private LocalDateTime createdAt;
}
