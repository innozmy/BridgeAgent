package myz.bridge_agent_demo.dto;

import lombok.Data;

/**
 * DTO：送给 Python 的一份 PDF。path 是本机绝对路径，由 Spring 保管文件。
 */
@Data
public class PythonParseFileItem {

    private Long fileId;
    private String originalName;
    /** 磁盘绝对路径；Python 用这个打开 PDF */
    private String path;
    /** 与 path 相同。个别 JSON 栈会碰到字段名 path，多带一份以免识图端拿不到文件。 */
    private String absolutePath;
    /** 内容指纹；页地图按 fileId+sha256 复用 */
    private String sha256;
}
