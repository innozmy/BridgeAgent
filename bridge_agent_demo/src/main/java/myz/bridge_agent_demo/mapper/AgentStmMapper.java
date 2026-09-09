package myz.bridge_agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import myz.bridge_agent_demo.entity.AgentStm;
import org.apache.ibatis.annotations.Mapper;

/** 任务类工种短期记忆。 */
@Mapper
public interface AgentStmMapper extends BaseMapper<AgentStm> {
}
