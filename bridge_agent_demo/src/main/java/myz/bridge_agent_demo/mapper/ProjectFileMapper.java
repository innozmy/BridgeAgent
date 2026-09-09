package myz.bridge_agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import myz.bridge_agent_demo.entity.ProjectFile;
import org.apache.ibatis.annotations.Mapper;

/** 项目文件元数据表访问；真正的文件内容不在这里。 */
@Mapper
public interface ProjectFileMapper extends BaseMapper<ProjectFile> {
}
