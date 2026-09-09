package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对应表 {@code project_unit_support}：一联上的一个墩或一台。
 * 同一墩上的多根柱在 {@link ProjectUnitColumn}，不在本行拆成两墩。
 */
@Data
@TableName("project_unit_support")
public class ProjectUnitSupport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long unitId;
    /** 沿联向序号，从 0 起；n 跨通常 0..n */
    private Integer seq;
    /** 如 P1 / 1# / 0#台；可空 */
    private String code;
    /** pier 桥墩 / abutment 桥台 */
    private String kind;
    /** drawing / cad / agent / manual */
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 组装用，非列：该墩台上的各柱 */
    @TableField(exist = false)
    private List<ProjectUnitColumn> columns;
}
