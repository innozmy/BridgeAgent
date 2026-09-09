package myz.bridge_agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import myz.bridge_agent_demo.entity.Project;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目表访问。继承 {@link BaseMapper} 后自带 insert/selectById/updateById/deleteById，无需 XML。
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
