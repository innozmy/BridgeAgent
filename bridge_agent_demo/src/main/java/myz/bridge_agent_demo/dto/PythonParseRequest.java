package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO：Spring 调 Python 识图的请求体。Python 不连库，只根据这些字段认图。
 */
@Data
public class PythonParseRequest {

    private Long projectId;
    /** left / right / undivided，混合图只采这一幅 */
    private String carriageway;
    /** 工程标号，可空；混合图只采与本项目一致的标号 */
    private String code;
    /**
     * 账本只读投影：固定列、联（含墩柱）、field_meta、参数袋。
     * 供同义去重；未确认提案不在这里。
     */
    private Map<String, Object> ledger;
    private String mode;
    /** 补充识别要细看的页 kind */
    private List<String> focusKinds = new ArrayList<>();
    /** 本轮指令，可空 */
    private String directive;
    /** 已有页地图，补充识别或回调已扫过的文件时跳过全册粗看 */
    private Map<String, Object> existingPageMap;
    private List<PythonParseFileItem> files = new ArrayList<>();
    /**
     * 本项目全部图纸元数据（fileId / 文件名 / 页地图索引），无像素、无磁盘路径。
     * Agent 只能从这里填 needFiles.fileId。
     */
    private List<Map<String, Object>> drawingCatalog = new ArrayList<>();
    /** 本任务已经打开过的 fileId，禁止再要 */
    private List<Long> alreadyFetchedFileIds = new ArrayList<>();
}
