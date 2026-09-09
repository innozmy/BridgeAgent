package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对应表 {@code inquiry_message}。存 MySQL，多人看同一份，不放浏览器本地。 */
@Data
@TableName("inquiry_message")
public class InquiryMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long threadId;
    /** user / agent / event（event 只给人看，不喂模型） */
    private String role;
    private String body;
    private LocalDateTime createdAt;
}
