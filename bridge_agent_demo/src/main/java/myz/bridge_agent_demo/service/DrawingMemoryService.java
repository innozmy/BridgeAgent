package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.dto.PythonParseResponse;

import java.util.List;

/**
 * 识图过程记忆：页地图、工种 STM 索引、项目 LTM。只由 Spring 写 MySQL。
 */
public interface DrawingMemoryService {

    /**
     * 同任务多轮识图的中间轮：只落该 fileId 页地图与 STM 索引，不写 LTM（终态才写）。
     */
    void submitRoundMap(Long projectId, List<Long> fileIds, PythonParseResponse response);

    /**
     * 识图 HTTP 成功返回后提交可复用记忆（含冲突待确认：页地图仍有用）。
     *
     * @param outcome done / waiting / failed，写入 LTM 索引
     */
    void submitParseResult(Long projectId, Long taskId, List<Long> fileIds, PythonParseResponse response, String outcome);

    /** 任务失败且无页地图时只记 LTM 教训。 */
    void submitParseFailure(Long projectId, Long taskId, String reason);

    /** 人放弃识图提案：STM 墓碑，LTM 记一笔。 */
    void submitParseRejected(Long projectId, Long taskId);
}
