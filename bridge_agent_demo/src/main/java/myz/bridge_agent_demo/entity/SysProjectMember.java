package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code sys_project_member}：普通用户在某项目的 read/operate。 */
@Data
@TableName("sys_project_member")
public class SysProjectMember {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long userId;
    /** read / operate */
    private String perm;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
