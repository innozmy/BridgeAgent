package myz.bridge_agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import myz.bridge_agent_demo.entity.ProjectSapModel;
import org.apache.ibatis.annotations.Mapper;

/** 项目 SAP 模型版本表；二进制不在这里。 */
@Mapper
public interface ProjectSapModelMapper extends BaseMapper<ProjectSapModel> {
}
