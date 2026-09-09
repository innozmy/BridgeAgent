package myz.bridge_agent_demo.dto;

import lombok.Data;

/**
 * DTO：识图提案与账本某字段不一致。给人看覆盖前/后，确认前不写库。
 */
@Data
public class LedgerConflictItem {

    /** 冲突字段中文名，如 墩柱高、桩径 */
    private String field;
    /** 账本当前值 */
    private String before;
    /** 本轮识别值（确认后才覆盖） */
    private String after;
}
