package myz.bridge_agent_demo.dto;

import lombok.Data;

/**
 * 建模任务落盘一份 SAP 模型时的元数据（controller 层暂不开放上传；由建模服务调用）。
 */
@Data
public class SapModelRegisterRequest {

    /** 产出该文件的建模任务，可空 */
    private Long taskId;
    /** SAP2000 版本，如 24.2.0 */
    private String sapVersion;
    /** 下载时用的文件名，如 task-12.sdb */
    private String originalName;
    private Integer frameCount;
    private Integer jointCount;
    /** estimated / 借用土层 / 二期缺失等 */
    private String note;
    /** joints+frames JSON；没有几何预览可空 */
    private String previewJson;
}
