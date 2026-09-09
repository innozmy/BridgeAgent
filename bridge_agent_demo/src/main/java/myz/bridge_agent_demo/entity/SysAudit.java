package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code sys_audit}。权限与登录留痕，不是项目作业时间线。
 */
@Data
@TableName("sys_audit")
public class SysAudit {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDateTime createdAt;
    /** 登录失败可空 */
    private Long actorUserId;
    private String actorUsername;
    private String action;
    /** user / role / project_member */
    private String targetType;
    private Long targetId;
    private String targetLabel;
    private Long projectId;
    private Boolean success;
    private String reason;
    private String beforeText;
    private String afterText;
    private String ip;
    private String userAgent;
}
