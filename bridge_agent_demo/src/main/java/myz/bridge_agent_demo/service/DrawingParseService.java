package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.vo.ModelTaskVO;

/**
 * Service：图纸识别闭环。人在图纸页点「识别本项目」直接开跑，过程进任务时间线，不进问询。
 * Python 只认图；写 {@code project_unit} / 主梁形式必须经本服务。
 */
public interface DrawingParseService {

    /**
     * 建一条「图纸识别」任务，把 PDF 送给 Python。
     * 账本为空则直接写入；已有联跨径或主梁且不一致则整份待确认，禁止部分写入。
     * 立刻建 running 任务并返回；真正调 Python 在 {@link #executeJob}。
     */
    ModelTaskVO parse(Long projectId);

    /**
     * 后台执行已 running 的全册或补充识图。不要从 Controller 同步调用。
     */
    void executeJob(Long projectId, Long taskId);

    /**
     * 人确认后按提案覆盖账本（联跨径来源记 drawing）。
     *
     * @param taskId 必须是本项目下带提案、仍为 waiting 的识图任务
     */
    ModelTaskVO confirm(Long projectId, Long taskId);

    /** 放弃提案，账本不动，任务失败 */
    ModelTaskVO reject(Long projectId, Long taskId);
}
