package myz.bridge_agent_demo.dto;

import lombok.Data;
import myz.bridge_agent_demo.vo.KnowledgePendingRefVO;
import myz.bridge_agent_demo.vo.KnowledgeSearchHitVO;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO：Python {@code /v1/knowledge/search} 的组包。向量检索只在 Python。
 */
@Data
public class PythonKnowledgeSearchResponse {

    private Boolean ok;
    private String error;
    private String notice;
    private List<KnowledgeSearchHitVO> hits = new ArrayList<>();
    private List<KnowledgePendingRefVO> pendingFigures = new ArrayList<>();
    private List<KnowledgePendingRefVO> pendingTables = new ArrayList<>();
}
