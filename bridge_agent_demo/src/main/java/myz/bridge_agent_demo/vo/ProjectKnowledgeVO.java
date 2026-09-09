package myz.bridge_agent_demo.vo;

import lombok.Data;
import myz.bridge_agent_demo.entity.KnowledgeDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * 某项目的知识范围：全部文献 + 已启用的 id。
 * 前端按 familyCode 分组，主标签用生效日最新的那本。
 */
@Data
public class ProjectKnowledgeVO {
    private List<KnowledgeDocument> documents = new ArrayList<>();
    private List<Long> enabledIds = new ArrayList<>();
}
