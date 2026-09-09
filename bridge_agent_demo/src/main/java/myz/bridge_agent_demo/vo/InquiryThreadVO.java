package myz.bridge_agent_demo.vo;

import lombok.Data;
import myz.bridge_agent_demo.entity.InquiryMessage;
import myz.bridge_agent_demo.entity.InquiryThread;

import java.util.ArrayList;
import java.util.List;

/** 会话详情：头 + 消息 + 本会话待同意任务卡。 */
@Data
public class InquiryThreadVO {
    private InquiryThread thread;
    private List<InquiryMessage> messages = new ArrayList<>();
    private List<ModelTaskVO> proposedCards = new ArrayList<>();
}
