package myz.bridge_agent_demo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型页列表/详情。不回磁盘路径，避免把存储布局暴露给浏览器。
 */
@Data
public class ProjectSapModelVO {

    private Long id;
    private Long projectId;
    private Long taskId;
    /** 项目内版本号 */
    private Integer seq;
    private String sapVersion;
    private String originalName;
    private Long sizeBytes;
    private Integer frameCount;
    private Integer jointCount;
    private String note;
    /** 线框预览 JSON；无几何时为 null */
    private String previewJson;
    private LocalDateTime createdAt;
}
