package myz.bridge_agent_demo.common;

import lombok.Data;

/**
 * 所有 JSON 接口的统一包装（下载图纸除外，下载直接返回文件流）。
 * <p>
 * 前端约定：{@code code == 1} 成功，读 {@code data}；{@code code == 0} 失败，读 {@code msg}。
 */
@Data
public class Result<T> {

    /** 1 成功，0 失败 */
    private Integer code;
    /** 给人看的说明；成功时一般为 "success" */
    private String msg;
    /** 业务数据；失败时通常为 null */
    private T data;

    /** 无 body 的成功，例如删除项目 */
    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(1);
        result.setMsg("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.setCode(0);
        result.setMsg(msg);
        return result;
    }
}
