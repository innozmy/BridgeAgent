package myz.bridge_agent_demo.vo;

import lombok.Data;
import myz.bridge_agent_demo.entity.ProjectFile;

/** 单份上传结果。{@code duplicate=true} 表示同项目已有相同哈希，返回旧记录、未再插一行。 */
@Data
public class FileUploadItemVO {
    private ProjectFile file;
    private boolean duplicate;
}
