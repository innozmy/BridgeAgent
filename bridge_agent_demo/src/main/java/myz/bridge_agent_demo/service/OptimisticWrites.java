package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.exception.BusinessException;

/**
 * Service 辅助：行级乐观锁冲突文案。人数不多，不铺悲观锁。
 */
public final class OptimisticWrites {

    /** 人手保存：版本对不上，提示刷新，不覆盖。 */
    public static final String CONFLICT = "数据已被他人修改，请刷新后再保存";

    /** 识图写入：落库当下版本冲突且重试仍失败。 */
    public static final String DRAWING_CONFLICT = "账本刚被他人修改，本轮未写入，请刷新概览后确认或再跑补充识别";

    public static final String MISSING_VERSION = "请刷新后再保存";

    private OptimisticWrites() {
    }

    /** 人手写必须带当前行 version。 */
    public static void requireVersion(Integer version) {
        if (version == null) {
            throw new BusinessException(MISSING_VERSION);
        }
    }

    public static void requireUpdated(int rows) {
        if (rows == 0) {
            throw new BusinessException(CONFLICT);
        }
    }

    public static boolean isConflict(BusinessException e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }
        String msg = e.getMessage();
        return CONFLICT.equals(msg) || DRAWING_CONFLICT.equals(msg) || MISSING_VERSION.equals(msg);
    }
}
