package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code sys_role}。内置四角色开关不可改。 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    /** 1 内置 */
    private Boolean builtin;
    private Boolean flagSuper;
    private Boolean flagKnowledge;
    private Boolean flagProjectAdmin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
