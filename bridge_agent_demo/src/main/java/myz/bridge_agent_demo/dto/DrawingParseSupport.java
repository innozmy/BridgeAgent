package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.util.List;

/**
 * DTO：识图提案里一个墩或台。双柱写在 {@code columns}，不要拆成两个 support。
 */
@Data
public class DrawingParseSupport {

    /** 沿联向序号，从 0 起 */
    private Integer seq;
    /** 如 P1 / 1# / 0#台 */
    private String code;
    /** pier / abutment */
    private String kind;
    private List<DrawingParseColumn> columns;
}
