package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.entity.ModelTask;
import myz.bridge_agent_demo.vo.ModelTaskVO;

import java.util.List;

/** 任务卡账本。同意后才 running；waiting 仅表示识图写入冲突。 */
public interface ModelTaskService {

    List<ModelTaskVO> list(Long projectId);

    /** 单条任务 + 时间线；项目不匹配时视为不存在 */
    ModelTaskVO get(Long projectId, Long taskId);

    ModelTaskVO create(Long projectId, String title, String kind);

    /**
     * 插入任务卡。{@code status} 为 proposed 或 running。
     */
    ModelTaskVO insertCard(ModelTask draft);

    boolean hasRunningDrawing(Long projectId);

    /** 是否有进行中的建模任务 */
    boolean hasRunningModeling(Long projectId);

    /** 干活链占用：识图或建模任一 running / queued（自动子识图不算另开一条链，但仍占链，拦新卡） */
    boolean hasRunningWorkChain(Long projectId);

    /**
     * 锁住项目行，串行化本项目开跑（同意卡 / 图纸一键识图）。
     * 必须在事务里调用。
     */
    void lockWorkChain(Long projectId);

    /**
     * 状态 CAS。成功则写时间线。派发 queued→running、同意 proposed→queued 用这个，避免双开。
     */
    boolean casStatus(Long projectId, Long taskId, String from, String to);

    /**
     * 派发前：本卡能否占用项目链。排除自己。
     * 仅允许「父建模 running + 本卡是其子识图」。
     */
    boolean canClaimWorkChain(ModelTask task);

    ModelTaskVO updateStatus(Long projectId, Long taskId, String status);

    /** 更新卡上的范围与指令，仅 proposed。 */
    ModelTaskVO updateCard(Long projectId, Long taskId, ModelTask patch);

    /** 往时间线追加一行，不改状态。有登录人则记下，否则 actor=系统 */
    void appendEvent(Long taskId, String body);

    /** 把当前 HTTP 用户记为同意开跑人 */
    void stampAgreed(Long projectId, Long taskId);

    /**
     * 保存识图提案 JSON。冲突待确认时与 {@code waiting} 一起用；一致或已写入也可留作底稿。
     */
    void saveProposal(Long projectId, Long taskId, String proposalJson);
}
