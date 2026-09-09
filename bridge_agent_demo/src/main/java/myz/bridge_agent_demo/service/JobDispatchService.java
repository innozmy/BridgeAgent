package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.vo.ResourceQueueVO;

/**
 * Service：三车道派发。排队落 MySQL，线程池只执行、不囤任务。
 */
public interface JobDispatchService {

    /** 识图 / 建模卡进入车道：有空位则 running 并提交，否则保持 queued。 */
    void offerTask(Long projectId, Long taskId);

    /** 父建模已是 running、子识图结束后再占建模线程。 */
    void resumeModeling(Long projectId, Long taskId);

    void enqueueKnowledgeParse(Long documentId, boolean force);

    void enqueueKnowledgeMerge(Long documentId);

    void enqueueKnowledgeSplit(Long documentId);

    void enqueueKnowledgeEmbed(Long documentId);

    void pumpDrawing();

    void pumpModeling();

    void pumpKnowledge();

    /** 三车道各泵一次。启动回收孤儿后调用，把仍是 queued 的顶上来。 */
    void pumpAll();

    /** 登录可见的资源占用；识图/建模按项目权限过滤。 */
    ResourceQueueVO snapshot();
}
