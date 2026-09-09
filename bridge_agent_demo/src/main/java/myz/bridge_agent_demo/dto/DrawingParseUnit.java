package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO：识图提案里的一联。Python 返回、任务 proposal_json 共用这一形状。
 * 墩柱高走 {@code supports}，不要塞进 {@code spansM}。
 */
@Data
public class DrawingParseUnit {

    /** 联序号，从 1 起 */
    private Integer seq;
    /** 跨径，单位米 */
    private List<BigDecimal> spansM;
    /** 本联墩台；缺测可空或省略 */
    private List<DrawingParseSupport> supports;
}
