package myz.bridge_agent_demo.vo;

import lombok.Data;

/**
 * VO：待补发的图或表。必须带文献 id，避免不同 PDF 的目录 id 撞车。
 */
@Data
public class KnowledgePendingRefVO {

    private Long documentId;
    /** parse 目录里的图/表 id，如 p12-i1 */
    private String refId;
}
