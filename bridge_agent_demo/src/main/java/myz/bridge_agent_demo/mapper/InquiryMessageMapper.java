package myz.bridge_agent_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import myz.bridge_agent_demo.entity.InquiryMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InquiryMessageMapper extends BaseMapper<InquiryMessage> {
}
