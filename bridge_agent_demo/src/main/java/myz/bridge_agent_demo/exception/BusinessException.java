package myz.bridge_agent_demo.exception;

/**
 * 可预期的业务错误（项目不存在、格式不支持等）。
 * 抛出后由 {@link GlobalExceptionHandler} 转成 {@code code=0} 的 JSON，不会走 500。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
