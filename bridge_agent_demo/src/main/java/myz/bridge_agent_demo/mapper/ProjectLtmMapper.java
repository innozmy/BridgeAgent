package myz.bridge_agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import myz.bridge_agent_demo.entity.ProjectLtm;
import org.apache.ibatis.annotations.Mapper;

/** 项目长期记忆。 */
@Mapper
public interface ProjectLtmMapper extends BaseMapper<ProjectLtm> {
}
