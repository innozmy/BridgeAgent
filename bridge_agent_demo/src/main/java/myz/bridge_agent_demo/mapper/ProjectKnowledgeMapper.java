package myz.bridge_agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import myz.bridge_agent_demo.entity.ProjectKnowledge;
import org.apache.ibatis.annotations.Mapper;

/** 项目启用集。 */
@Mapper
public interface ProjectKnowledgeMapper extends BaseMapper<ProjectKnowledge> {
}
