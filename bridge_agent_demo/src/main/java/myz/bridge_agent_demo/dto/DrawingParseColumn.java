package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO：识图提案里一根墩柱。Python JSON 与 {@code proposal_json} 共用。
 */
@Data
public class DrawingParseColumn {

    /** 同一墩上柱序号，从 1 起 */
    private Integer seq;
    /** left / right / inner / outer，可空 */
    private String side;
    /** 柱高，米 */
    private BigDecimal heightM;
}
