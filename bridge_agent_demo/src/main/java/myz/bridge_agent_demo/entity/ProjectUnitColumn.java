package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对应表 {@code project_unit_column}：某墩台上的一根墩柱。
 * 双柱且左右高度不同必须两行；高度口径为盖梁底（或墩顶）至承台顶/桩顶。
 */
@Data
@TableName("project_unit_column")
public class ProjectUnitColumn {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supportId;
    /** 同一墩上柱序号，从 1 起 */
    private Integer seq;
    /** left / right / inner / outer，可空 */
    private String side;
    /** 柱高，米；未测可空 */
    private BigDecimal heightM;
    /** drawing / cad / agent / manual */
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
