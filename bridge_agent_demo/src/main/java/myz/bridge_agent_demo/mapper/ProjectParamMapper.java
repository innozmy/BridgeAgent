package myz.bridge_agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import myz.bridge_agent_demo.entity.ProjectParam;
import org.apache.ibatis.annotations.Mapper;

/** 扩展结构参数袋。 */
@Mapper
public interface ProjectParamMapper extends BaseMapper<ProjectParam> {
}
