package myz.bridge_agent_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对应表 {@code knowledge_document}：公司总库一条文献一行。
 * PDF 在 {@code data/knowledge/{sha256}/}；同一规范不同年份靠 {@code familyCode} 分组。
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** code / manual / case */
    private String category;
    /** 如 JTG D62；空则界面上单独一行、没有年版下拉 */
    private String familyCode;
    private String region;
    private String specialty;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String originalName;
    private String storagePath;
    private String sha256;
    private String mimeType;
    private Long sizeBytes;
    /** unparsed / queued / parsing / parsed / failed */
    private String parseStatus;
    /** unmerged / queued / merging / merged / failed */
    private String mergeStatus;
    /** unsplit / queued / splitting / split / failed */
    private String splitStatus;
    /** unembedded / queued / embedding / embedded / failed */
    private String embedStatus;
    /** 行级乐观锁；四步作业禁止整行 updateById，只改状态列 */
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
