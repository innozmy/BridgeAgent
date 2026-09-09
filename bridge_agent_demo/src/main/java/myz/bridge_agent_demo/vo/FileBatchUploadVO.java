package myz.bridge_agent_demo.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次多文件上传的结果：成功的进 {@code saved}，格式不对的进 {@code errors}。
 * 只要有一份成功就整体 code=1；全部失败则抛业务异常。
 */
@Data
public class FileBatchUploadVO {
    private List<FileUploadItemVO> saved = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
}
