package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应表 {@code project_sap_model}：一个项目下的一份 SAP 模型版本。
 * 二进制在 {@code data/models/{projectId}/}，本表只存元数据与线框预览 JSON。
 */
@Data
@TableName("project_sap_model")
public class ProjectSapModel {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** 产出该文件的建模任务；任务删了仍可留模型 */
    private Long taskId;
    /** 项目内版本号，从 1 起，删除后不回收 */
    private Integer seq;
    /** 所用 SAP2000 版本，如 24.2.0 */
    private String sapVersion;
    private String originalName;
    /** 相对模型存储根的路径 */
    private String storagePath;
    private String sha256;
    private Long sizeBytes;
    /** 框架数，可空（尚未导出统计时） */
    private Integer frameCount;
    /** 节点数，可空 */
    private Integer jointCount;
    /** estimated、借用土层、二期缺失等给人看的说明 */
    private String note;
    /** joints + frames 的 JSON 文本，模型页画线框 */
    private String previewJson;
    private LocalDateTime createdAt;
}
