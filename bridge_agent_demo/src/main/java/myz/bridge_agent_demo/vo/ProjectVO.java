package myz.bridge_agent_demo.vo;

import lombok.Data;
import myz.bridge_agent_demo.entity.ProjectFieldMeta;
import myz.bridge_agent_demo.entity.ProjectFile;
import myz.bridge_agent_demo.entity.ProjectParam;
import myz.bridge_agent_demo.entity.ProjectUnit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目详情返回给前端的聚合对象（概览、图纸栏共用这一包）。
 * 比 {@link myz.bridge_agent_demo.entity.Project} 多了联（含墩柱）、参数袋和文件列表。
 */
@Data
public class ProjectVO {
    private Long id;
    private String name;
    /** left / right / undivided，前端再翻译成左幅/右幅/不分幅 */
    private String carriageway;
    private String code;
    private String intro;
    private String region;
    private LocalDate openedOn;
    private String codeStrategy;
    private String girderType;
    private String layoutType;
    private String material;
    /** 可识图字段来源 / 上次识图值 / 缺口，供概览标注与冲突条 */
    private ProjectFieldMeta fieldMeta;
    private String status;
    /** 行级乐观锁，保存概览/联/参数袋时原样带回 */
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 该项目下全部联，按 seq 升序；每联可带 supports（墩台与柱高） */
    private List<ProjectUnit> units;
    /** 扩展结构参数袋，不含固定列与墩柱高 */
    private List<ProjectParam> params;
    /** 该项目下全部文件元数据 */
    private List<ProjectFile> files;
}
