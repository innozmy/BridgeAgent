package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO：识图 Agent 向 Spring 索取下一份项目图纸。fileId 必须来自本轮注入的 drawingCatalog，不得编造路径。
 */
@Data
public class PythonNeedFileItem {

    private Long fileId;
    /** 下一轮细看页类；空则按该份默认（说明+总布置）。不得只含 rebar */
    private List<String> focusKinds = new ArrayList<>();
    /** 给人看的时间线原因，如「本册没有墩柱构造」 */
    private String reason;
}
