package myz.bridge_agent_demo.service;

/**
 * Service：人已同意的建模任务，后台调 Python check/spec/build。
 */
public interface ModelingService {

    /**
     * 执行已是 running 的建模任务。硬缺口则自动插补充识别子任务；齐了则调 SAP 存 .sdb。
     */
    void executeJob(Long projectId, Long taskId);

    /**
     * 子识图终态：waiting 则父任务继续 running 等人确认；done 则续跑建模；
     * failed（含放弃）则父建模失败。
     */
    void onDrawingChildSettled(Long projectId, Long childTaskId, String childStatus);
}
