package myz.bridge_agent_demo.vo;

import lombok.Data;
import myz.bridge_agent_demo.entity.KnowledgeDocument;

/** 上传知识文件的结果。{@code duplicate=true} 表示全公司已有相同 PDF，返回旧记录。 */
@Data
public class KnowledgeUploadVO {
    private KnowledgeDocument document;
    private boolean duplicate;
}
