package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.entity.InquiryThread;
import myz.bridge_agent_demo.vo.InquiryThreadVO;

import java.util.List;

/** 项目问询。展示走会话表；发消息时 Spring 调 Python 问询图，不改账本。 */
public interface InquiryService {

    List<InquiryThread> listThreads(Long projectId);

    InquiryThread createThread(Long projectId);

    InquiryThreadVO getThread(Long projectId, Long threadId);

    /**
     * 写入用户消息，注入账本/STM 后调 Python 问询图，再写入助手回复。
     * 不改跨径等账本字段。
     */
    InquiryThreadVO postMessage(Long projectId, Long threadId, String body);

    void deleteThread(Long projectId, Long threadId);
}
