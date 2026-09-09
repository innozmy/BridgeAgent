package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.entity.InquiryMessage;
import myz.bridge_agent_demo.vo.ProjectVO;

import java.util.List;
import java.util.Map;

/**
 * Service：组 Spring→Python 的只读注入包。不写库。
 */
public interface AgentInjectPackService {

    /**
     * 问询注入：账本投影、本会话摘要 + 最近窗口、工种 STM 索引、页地图索引、LTM 索引、
     * {@code knowledgeScope}（启用∩已嵌入∩规范，含文献磁盘根）。
     * 不含全书 {@code pages[]}、不含未确认 {@code proposal_json}。
     *
     * @param history 不含本轮用户新消息；本轮问题走 {@code currentUserText}
     * @return 直接作为 {@code POST /v1/agents/inquiry} 请求体
     */
    Map<String, Object> buildInquiryPack(Long projectId, Long threadId,
                                         List<InquiryMessage> history, String currentUserText);

    /**
     * 账本只读投影：固定列、联（含墩柱）、field_meta、参数袋。
     * 识图与问询共用；未确认提案不在这里。
     */
    Map<String, Object> buildLedger(ProjectVO project);

    /**
     * 识图图纸目录：本项目全部文件的 fileId / 文件名 / kind / 页地图索引（kindCounts、总页、gaps）。
     * 无像素、无磁盘路径，供 Agent 填 needFiles。
     */
    List<Map<String, Object>> buildDrawingCatalog(ProjectVO project);

    /**
     * 建模注入：账本投影、建模 STM、图纸目录与页地图指针、上一版模型元数据、本机 SAP 输出路径、
     * {@code knowledgeScope}、LTM 只读索引（最近 20 条）。check/spec/build 不读 LTM。
     */
    Map<String, Object> buildModelingPack(Long projectId, Long taskId);
}
