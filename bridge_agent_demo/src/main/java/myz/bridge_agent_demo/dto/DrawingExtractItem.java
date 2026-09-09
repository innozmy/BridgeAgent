package myz.bridge_agent_demo.dto;

import lombok.Data;

/**
 * DTO：识图摘录一项。Python 只交含义与数，落点由 Spring 路由到固定列或参数袋。
 * 墩柱高走联表子表，不进本对象落袋。
 */
@Data
public class DrawingExtractItem {

    /** 建议键，可空；Spring 再与固定列 / 已有袋去重 */
    private String key;
    /** 专业中文名 */
    private String label;
    /** 数字或短文本；Jackson 可能是 Number */
    private Object value;
    private String unit;
    /** PDF 页码（与 Python 页序一致） */
    private Integer page;
    /** 如 estimated、单幅/双幅说明 */
    private String note;
}
