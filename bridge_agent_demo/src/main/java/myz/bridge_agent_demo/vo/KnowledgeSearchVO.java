package myz.bridge_agent_demo.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * VO：一次规范检索的组包结果。v1 当次不带 JPEG/表 HTML。
 */
@Data
public class KnowledgeSearchVO {

    /** 未启用、或自我纠正后仍不够时的「检索把握不足」；通过时为 null */
    private String notice;
    private List<KnowledgeSearchHitVO> hits = new ArrayList<>();
    private List<KnowledgePendingRefVO> pendingFigures = new ArrayList<>();
    private List<KnowledgePendingRefVO> pendingTables = new ArrayList<>();
}
