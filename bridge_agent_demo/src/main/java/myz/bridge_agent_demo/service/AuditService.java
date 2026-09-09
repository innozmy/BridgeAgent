package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.vo.AuditPageVO;

/**
 * 权限审计。写失败不得打断登录/改密等主流程。
 */
public interface AuditService {

    /**
     * 追加一条。actor 为空时用当前 {@code UserContext}。
     *
     * @param action        {@link myz.bridge_agent_demo.audit.AuditActions}
     * @param success       是否成功
     * @param actorUserId   可空
     * @param actorUsername 必填（登录失败为尝试名）
     * @param targetType    user / role / project_member，可空
     * @param targetId      可空
     * @param targetLabel   展示快照，可空
     * @param projectId     成员授权时有值
     * @param reason        失败短句
     * @param beforeText    改前
     * @param afterText     改后
     */
    void record(String action, boolean success, Long actorUserId, String actorUsername,
                String targetType, Long targetId, String targetLabel, Long projectId,
                String reason, String beforeText, String afterText);

    /**
     * 超管分页倒序。
     *
     * @param action         动作筛选，空则全部
     * @param actorUsername  操作者用户名模糊，空则全部
     * @param page           从 1 起
     * @param size           每页条数
     */
    AuditPageVO list(String action, String actorUsername, long page, long size);
}
