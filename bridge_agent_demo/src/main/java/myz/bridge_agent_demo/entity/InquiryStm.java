package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code inquiry_stm}：一条问询会话一份给模型用的短记忆。
 * 全文展示仍走 {@code inquiry_message}。
 */
@Data
@TableName("inquiry_stm")
public class InquiryStm {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long threadId;
    /** 窗口之前的摘要；可空 */
    private String summary;
    /** 最近轮次对应的消息 id JSON 数组 */
    private String recentJson;
    /** 已同意提交的 taskId JSON 数组 */
    private String submittedJson;
    private LocalDateTime updatedAt;
}
