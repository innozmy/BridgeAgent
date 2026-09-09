package myz.bridge_agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import myz.bridge_agent_demo.entity.InquiryStm;
import org.apache.ibatis.annotations.Mapper;

/** 问询会话短期记忆。 */
@Mapper
public interface InquiryStmMapper extends BaseMapper<InquiryStm> {
}
