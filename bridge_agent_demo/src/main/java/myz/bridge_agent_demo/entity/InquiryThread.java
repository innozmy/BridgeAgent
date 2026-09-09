package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code inquiry_thread}：按项目 + 用户私有的问询会话，不共享。 */
@Data
@TableName("inquiry_thread")
public class InquiryThread {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** 会话主人；列表与读写只允许此人 */
    private Long userId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
