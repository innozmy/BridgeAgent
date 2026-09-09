package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO：落在 {@code model_task.proposal_json} 里的识图提案。
 * 冲突时整份待确认，确认前不写 {@code project_unit} / 主梁 / 结构形式。
 */
@Data
public class DrawingParseProposal {

    private String girderType;
    private String layoutType;
    /** 可空；确认写入时才进 project.material */
    private String material;
    private String code;
    private String region;
    private List<DrawingParseUnit> units = new ArrayList<>();
    /** 细看摘录；Spring 路由进参数袋，墩柱高以 units.supports 为准不落袋 */
    private List<DrawingExtractItem> extracts = new ArrayList<>();
    /** 与账本不一致的字段；确认页展示覆盖前/后 */
    private List<LedgerConflictItem> conflicts = new ArrayList<>();
    /** 本次送给 Python 的 PDF 文件 id，确认/失败时只改这些行的 parse_status */
    private List<Long> fileIds = new ArrayList<>();
    private String note;
    private List<String> gaps = new ArrayList<>();
    private Boolean hasLayoutPages;
}
