package myz.bridge_agent_demo.entity;

import lombok.Data;

/**
 * 账本单个可识图字段的来源。落在 {@code project.field_meta} JSON 里。
 * {@code source=drawing} 表示当前值来自识图写入；{@code manual} 表示人在概览改过。
 */
@Data
public class FieldProvenance {

    /** drawing / manual；空表示还没写过这条来源 */
    private String source;
    /** 上次识图读到的值，供与当前账本对照；跨径用「40+60+40」这种规范化字符串 */
    private String drawingValue;
}
