package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.dto.TaskCardRequest;
import myz.bridge_agent_demo.vo.ModelTaskVO;

import java.util.List;

/**
 * Service：任务卡起草、问询落卡、人同意后开跑。写账本仍只走识图终态规则。
 */
public interface TaskCardService {

    /** 任务页手写一张待同意卡。 */
    ModelTaskVO draft(Long projectId, TaskCardRequest request);

    /** 同意前改范围/指令。 */
    ModelTaskVO edit(Long projectId, Long taskId, TaskCardRequest request);

    /** 问询校验后落 proposed；非法返回 null。 */
    ModelTaskVO proposeFromInquiry(Long projectId, Long threadId, TaskCardRequest request);

    /**
     * 同意并开跑。识图后台调 Python；建模/分析只记时间线。
     *
     * @param threadId 可空；从问询点同意时带上，用于写 event
     */
    ModelTaskVO agree(Long projectId, Long taskId, Long threadId);

    /** 拒绝任务卡，不跑 Agent。 */
    ModelTaskVO dismiss(Long projectId, Long taskId, Long threadId);

    List<ModelTaskVO> listProposedByThread(Long projectId, Long threadId);
}
