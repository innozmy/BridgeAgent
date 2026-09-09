package myz.bridge_agent_demo.exception;

/**
 * 已登录但权限不够。由全局处理写成 HTTP 403 + {@code Result}，与业务 200+code=0、登录 401 分开。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
