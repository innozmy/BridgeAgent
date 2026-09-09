package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code project_file}：只存元数据，PDF/DWG 二进制在磁盘 {@code data/files/{projectId}/}。
 */
@Data
@TableName("project_file")
public class ProjectFile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** 用户归类：drawing 图纸 / cad / other */
    private String kind;
    /** 上传时的原始文件名，下载时用这个当附件名 */
    private String originalName;
    /** 相对存储根目录的路径，例如 {@code 2/abc...pdf} */
    private String storagePath;
    /** 内容指纹；同一项目相同哈希视为同一份文件 */
    private String sha256;
    private String mimeType;
    private Long sizeBytes;
    /** uploaded 已上传，尚未识图；后续 parsing / parsed / failed */
    private String parseStatus;
    private LocalDateTime createdAt;
}
