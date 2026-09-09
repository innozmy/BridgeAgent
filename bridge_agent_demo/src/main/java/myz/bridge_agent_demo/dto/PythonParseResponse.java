package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO：Python 识图返回。{@code ok=false} 才算真正失败（无 PDF、密钥/额度、调用失败）。
 * 缺跨径等缺口时 {@code ok=true}，由 Spring 部分写入并记下 gaps。
 */
@Data
public class PythonParseResponse {

    private Boolean ok;
    private String error;
    private String girderType;
    private String layoutType;
    /** 从图签/说明识别的材料摘要，如 C50；看不清则为空 */
    private String material;
    /** 图签上的工程标号；账本已有标号时只作核对，空账本才写入 */
    private String code;
    /** 图框或说明里的地区，看不清则为空 */
    private String region;
    private List<DrawingParseUnit> units = new ArrayList<>();
    /** 细看摘录汇总；页地图里也有一份，Spring 用顶层这份路由落袋 */
    private List<DrawingExtractItem> extracts = new ArrayList<>();
    private String note;
    /** 未读出的字段：spansM / girderType / layoutType / material / missing_layout */
    private List<String> gaps = new ArrayList<>();
    /** 粗看是否见到总布置或立面 */
    private Boolean hasLayoutPages;
    /** 本轮页地图，由 Spring 写入 drawing_page_map；Python 不落盘 */
    private Map<String, Object> pageMap;
    /**
     * 还要打开的项目 PDF。ok=true 且本列表非空时：本轮页地图可先落库，账本空写/冲突确认等全部轮次结束。
     */
    private List<PythonNeedFileItem> needFiles = new ArrayList<>();
}
