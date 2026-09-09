package myz.bridge_agent_demo.exception;

import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.common.Result;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * 全局异常处理：Controller 里抛出的异常在这里收口成统一 {@link Result}，前端只认 code/msg。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 已登录但权限不够 */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Result<Void>> handleForbidden(ForbiddenException e) {
        log.warn("无权限：{}", e.getMessage());
        return ResponseEntity.status(403).body(Result.error(e.getMessage()));
    }

    /** 业务校验失败，把原话返回给前端提示 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.error(e.getMessage());
    }

    /** JSON 请求体上的 @Valid 失败（例如建项缺名称） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        return Result.error(firstFieldMessage(e.getBindingResult().getFieldError()));
    }

    /** 查询参数、表单绑定失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        return Result.error(firstFieldMessage(e.getFieldError()));
    }

    /** 数据库唯一约束，例如同一项目重复的文件哈希 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一约束冲突", e);
        return Result.error("数据已存在，请勿重复提交");
    }

    /** 单个文件超过 spring.servlet.multipart.max-file-size（200MB） */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        log.warn("上传超出大小限制", e);
        return Result.error("单个文件不能超过 200MB");
    }

    /** 其它 multipart 解析失败；有时超限会包在这层里面 */
    @ExceptionHandler(MultipartException.class)
    public Result<Void> handleMultipart(MultipartException e) {
        log.warn("上传失败", e);
        if (e.getCause() instanceof MaxUploadSizeExceededException) {
            return Result.error("单个文件不能超过 200MB");
        }
        return Result.error("文件上传失败，请检查大小与格式");
    }

    /** 兜底：避免把堆栈直接暴露给前端 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未处理异常", e);
        return Result.error("出错了，请联系管理员");
    }

    private String firstFieldMessage(FieldError error) {
        if (error == null || error.getDefaultMessage() == null) {
            return "参数不合法";
        }
        return error.getDefaultMessage();
    }
}
