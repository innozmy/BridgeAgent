package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code sys_user}：登录账号一行，一人一角色。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String nickname;
    private Long roleId;
    private String passwordHash;
    private Integer tokenVersion;
    /** enabled / disabled */
    private String status;
    /** 相对头像根目录，可空 */
    private String avatarPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
