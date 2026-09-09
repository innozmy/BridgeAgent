package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import myz.bridge_agent_demo.config.mybatis.SpansJsonTypeHandler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对应表 {@code project_unit}：一个项目下的一联。
 * 跨径在 {@code spans_m}；墩台与每根墩柱高度在子表，经 {@code supports} 组装，不进跨径 JSON。
 * {@code autoResultMap = true} 才能让 spans_m 的 JSON 类型处理器生效。
 */
@Data
@TableName(value = "project_unit", autoResultMap = true)
public class ProjectUnit {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** 联序号，从 1 起；同一项目内唯一 */
    private Integer seq;

    /** 跨径数组，单位米，如 [40, 60, 40]；库里是 JSON */
    @TableField(typeHandler = SpansJsonTypeHandler.class)
    private List<BigDecimal> spansM;

    /** 联长 = 跨径之和，写入时由服务侧重算，不信前端 */
    private BigDecimal lengthM;
    /** 来源：drawing / cad / agent / manual */
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 组装用，非列：本联墩台（含各柱） */
    @TableField(exist = false)
    private List<ProjectUnitSupport> supports;
}
