package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code project_param}：已确认的扩展结构参数，一行一个 key。
 * 不与固定列 / {@code field_meta} 所管含义重复（跨径、主梁等不进本表）。
 */
@Data
@TableName("project_param")
public class ProjectParam {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** 稳定键，项目内唯一 */
    private String paramKey;
    /** 给人看的专业名词 */
    private String label;
    private String valueText;
    /** 单位，如 m；可空 */
    private String unit;
    /** drawing / cad / agent / manual */
    private String source;
    /** 行级乐观锁；识图覆盖袋项时带当前值 */
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
