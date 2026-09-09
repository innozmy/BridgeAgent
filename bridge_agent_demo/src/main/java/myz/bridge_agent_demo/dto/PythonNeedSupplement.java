package myz.bridge_agent_demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO：建模图要求 Spring 开补充识别。fileId 必须能在图纸目录里对上。
 */
@Data
public class PythonNeedSupplement {

    private Long fileId;
    private List<String> focusKinds = new ArrayList<>();
    private List<String> missingKeys = new ArrayList<>();
    private String supportCode;
    private String reason;
    /** true：本轮已按相关页类扩扫，墓碑后同一 fileId+key 不再空转 */
    private Boolean sweep;
}
