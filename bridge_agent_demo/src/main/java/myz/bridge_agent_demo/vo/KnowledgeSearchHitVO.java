package myz.bridge_agent_demo.vo;

import lombok.Data;

import java.util.List;

/**
 * VO：一条检索命中的短出处。不含 RRF 分数、不含 JPEG/表 HTML。
 */
@Data
public class KnowledgeSearchHitVO {

    /** text / figure / table */
    private String kind;
    private String body;
    private Long documentId;
    private String documentName;
    private String familyCode;
    private String clauseNo;
    private String figureNo;
    private String tableNo;
    private List<Integer> pageNumbers;
    private String refId;
    private List<String> mentionedFigureIds;
    private List<String> mentionedTableIds;
}
