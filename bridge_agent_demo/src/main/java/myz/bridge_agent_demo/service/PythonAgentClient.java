package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.dto.PythonInquiryResponse;
import myz.bridge_agent_demo.dto.PythonKnowledgeMergeResponse;
import myz.bridge_agent_demo.dto.PythonKnowledgeParseResponse;
import myz.bridge_agent_demo.dto.PythonKnowledgeSplitResponse;
import myz.bridge_agent_demo.dto.PythonKnowledgeEmbedResponse;
import myz.bridge_agent_demo.dto.PythonKnowledgeSearchResponse;
import myz.bridge_agent_demo.dto.PythonModelingResponse;
import myz.bridge_agent_demo.dto.PythonParseRequest;
import myz.bridge_agent_demo.dto.PythonParseResponse;

import java.util.Map;

/**
 * Service：调 Python Agent 的 HTTP 客户端。实现里不碰 Mapper。
 */
public interface PythonAgentClient {

    /**
     * 把本项目 PDF 路径交给 Python 认图。
     *
     * @param request 项目标号、幅面、文件绝对路径
     * @return Python 结构化结果；网络失败由调用方记任务失败，不要抛成 500
     */
    PythonParseResponse parseDrawings(PythonParseRequest request);

    /**
     * 问询图：注入账本与 STM，取只读回复。
     *
     * @param payload Spring 组好的注入包
     * @return 网络失败返回 null，由问询服务写成助手错误句，不抛 500
     */
    PythonInquiryResponse inquire(Map<String, Object> payload);

    /**
     * 建模图：注入账本与建模 STM，取 needSupplement 或本机 sapPath。
     *
     * @return 网络失败返回 null，由建模服务记任务失败
     */
    PythonModelingResponse model(Map<String, Object> payload);

    /**
     * 知识 PDF 解析：路径由 Spring 指定，Python 写 parse/ sidecar 与附图。
     *
     * @return 网络失败返回 null，由知识服务改 parse_status
     */
    PythonKnowledgeParseResponse parseKnowledge(Map<String, Object> payload);

    /**
     * 知识合并：只读 parse 产物，写 merge/chunks.json。
     *
     * @return 网络失败返回 null，由知识服务改 merge_status
     */
    PythonKnowledgeMergeResponse mergeKnowledge(Map<String, Object> payload);

    /**
     * 知识过长分割：只读 merge/chunks.json，写 split/chunks.json。
     *
     * @return 网络失败返回 null，由知识服务改 split_status
     */
    PythonKnowledgeSplitResponse splitKnowledge(Map<String, Object> payload);

    /**
     * 知识嵌入：只读 split/ 与图/表目录，写入本机 Milvus。
     *
     * @return 网络失败返回 null，由知识服务改 embed_status
     */
    PythonKnowledgeEmbedResponse embedKnowledge(Map<String, Object> payload);

    /**
     * 知识检索：问句 + 启用集 documentIds，Python 混搜 RRF 后组包。
     *
     * @return 网络失败返回 null，由知识服务转成业务错误
     */
    PythonKnowledgeSearchResponse searchKnowledge(Map<String, Object> payload);

    /**
     * 按 documentId 删除 Milvus 行。失败只打日志。
     */
    void deleteKnowledgeVectors(Map<String, Object> payload);
}
