package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code model_task}：一张任务卡一轮工作，不是群聊。
 * {@code kind} 为枚举；{@code status=proposed} 待同意，{@code waiting} 仅表示识图写入冲突。
 */
@Data
@TableName("model_task")
public class ModelTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String title;
    /** drawing_full / drawing_supplement / modeling / analysis */
    private String kind;
    /** proposed / queued / running / waiting / done / failed / rejected */
    private String status;
    /** 识图待确认提案 JSON；有值且 waiting 时不得默默覆盖账本 */
    private String proposalJson;
    private Long fileId;
    /** 页 kind JSON 数组，如 ["pier"] */
    private String pageKindsJson;
    private Integer unitSeq;
    private String supportCode;
    /** 本轮指令，可空，最长 500 */
    private String directive;
    private String proposeReason;
    private Long inquiryThreadId;
    /** 自动补充识别所属的父建模任务 */
    private Long parentTaskId;
    /** draft / inquiry / drawing_button / auto_supplement */
    private String origin;
    private Long createdByUserId;
    private String createdByUsername;
    private Long agreedByUserId;
    private String agreedByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
