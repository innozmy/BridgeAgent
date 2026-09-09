package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import myz.bridge_agent_demo.config.mybatis.FieldMetaJsonTypeHandler;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对应表 {@code project}：一座桥的一幅 = 一行。
 * 建项只强制名称 + 幅面；跨径、主梁形式等可后补。
 * {@code autoResultMap = true} 才能让 field_meta 的 JSON 类型处理器生效。
 */
@Data
@TableName(value = "project", autoResultMap = true)
public class Project {

    /** 系统主键，前端路由 /api/projects/{id} 都用它 */
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** 幅面：left 左幅 / right 右幅 / undivided 不分幅 */
    private String carriageway;
    /** 工程标号，允许重复、允许空 */
    private String code;
    private String intro;
    private String region;
    /** 建成/通车日；只知道年份时约定存该年-01-01 */
    private LocalDate openedOn;
    /** at_opening 按落地时点规范 / current_review 按现行复核 */
    private String codeStrategy;
    /** 主梁形式，项目级，识图或会话后可改 */
    private String girderType;
    /** 结构形式：简支 / 连续 / 桥面连续等 */
    private String layoutType;
    /** 材料摘要，如 C50 */
    private String material;
    /** 可识图字段的来源、上次识图值、缺口；库里是 JSON */
    @TableField(typeHandler = FieldMetaJsonTypeHandler.class)
    private ProjectFieldMeta fieldMeta;
    /** draft / modeling / validating / calibrating / done */
    private String status;
    /** 行级乐观锁；updateById 必须带当前值 */
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
