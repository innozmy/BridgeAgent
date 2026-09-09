package myz.bridge_agent_demo.service.impl;

import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.dto.PythonInquiryResponse;
import myz.bridge_agent_demo.dto.PythonKnowledgeMergeResponse;
import myz.bridge_agent_demo.dto.PythonKnowledgeParseResponse;
import myz.bridge_agent_demo.dto.PythonKnowledgeEmbedResponse;
import myz.bridge_agent_demo.dto.PythonKnowledgeSearchResponse;
import myz.bridge_agent_demo.dto.PythonKnowledgeSplitResponse;
import myz.bridge_agent_demo.dto.PythonModelingResponse;
import myz.bridge_agent_demo.dto.PythonParseFileItem;
import myz.bridge_agent_demo.dto.PythonParseRequest;
import myz.bridge_agent_demo.dto.PythonParseResponse;
import myz.bridge_agent_demo.service.PythonAgentClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service 实现：自己拼 JSON 再 POST Python。
 * 识图走 {@code /v1/parse-drawings}；问询走 {@code /v1/agents/inquiry}；
 * 知识解析 / 合并 / 分割 / 嵌入 / 检索走 {@code /v1/knowledge/*}。
 * 不用 RestClient.body(DTO)，那个转换器会把 files 弄丢。
 */
@Slf4j
@Service
public class PythonAgentClientImpl implements PythonAgentClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI parseUri;
    private final URI inquireUri;
    private final URI modelUri;
    private final URI knowledgeParseUri;
    private final URI knowledgeMergeUri;
    private final URI knowledgeSplitUri;
    private final URI knowledgeEmbedUri;
    private final URI knowledgeSearchUri;
    private final URI knowledgeVectorDeleteUri;
    private final Duration readTimeout;
    private final Duration modelingTimeout;
    private final Duration knowledgeParseTimeout;
    private final Duration knowledgeMergeTimeout;
    private final Duration knowledgeSplitTimeout;
    private final Duration knowledgeEmbedTimeout;
    private final Duration knowledgeSearchTimeout;

    public PythonAgentClientImpl(
            @Qualifier("pythonAgentHttpClient") HttpClient pythonAgentHttpClient,
            @Value("${app.python-agent-url:http://127.0.0.1:8001}") String baseUrl,
            @Value("${app.python-agent-read-timeout-seconds:180}") long readTimeoutSeconds,
            @Value("${app.python-agent-modeling-timeout-seconds:900}") long modelingTimeoutSeconds,
            @Value("${app.python-knowledge-parse-timeout-seconds:1800}") long knowledgeParseTimeoutSeconds,
            @Value("${app.python-knowledge-merge-timeout-seconds:120}") long knowledgeMergeTimeoutSeconds,
            @Value("${app.python-knowledge-split-timeout-seconds:300}") long knowledgeSplitTimeoutSeconds,
            @Value("${app.python-knowledge-embed-timeout-seconds:600}") long knowledgeEmbedTimeoutSeconds,
            @Value("${app.python-knowledge-search-timeout-seconds:120}") long knowledgeSearchTimeoutSeconds) {
        this.httpClient = pythonAgentHttpClient;
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.parseUri = URI.create(root + "/v1/parse-drawings");
        this.inquireUri = URI.create(root + "/v1/agents/inquiry");
        this.modelUri = URI.create(root + "/v1/agents/modeling");
        this.knowledgeParseUri = URI.create(root + "/v1/knowledge/parse");
        this.knowledgeMergeUri = URI.create(root + "/v1/knowledge/merge");
        this.knowledgeSplitUri = URI.create(root + "/v1/knowledge/split");
        this.knowledgeEmbedUri = URI.create(root + "/v1/knowledge/embed");
        this.knowledgeSearchUri = URI.create(root + "/v1/knowledge/search");
        this.knowledgeVectorDeleteUri = URI.create(root + "/v1/knowledge/vectors/delete");
        this.readTimeout = Duration.ofSeconds(readTimeoutSeconds);
        this.modelingTimeout = Duration.ofSeconds(modelingTimeoutSeconds);
        this.knowledgeParseTimeout = Duration.ofSeconds(knowledgeParseTimeoutSeconds);
        this.knowledgeMergeTimeout = Duration.ofSeconds(knowledgeMergeTimeoutSeconds);
        this.knowledgeSplitTimeout = Duration.ofSeconds(knowledgeSplitTimeoutSeconds);
        this.knowledgeEmbedTimeout = Duration.ofSeconds(knowledgeEmbedTimeoutSeconds);
        this.knowledgeSearchTimeout = Duration.ofSeconds(knowledgeSearchTimeoutSeconds);
    }

    /**
     * 把项目标号、幅面、PDF 绝对路径交给 Python。网络失败返回 null，由识图服务记任务失败。
     */
    @Override
    public PythonParseResponse parseDrawings(PythonParseRequest request) {
        String body;
        try {
            body = JSON.writeValueAsString(toPayload(request));
        } catch (JacksonException e) {
            log.warn("识图请求序列化失败：{}", e.getMessage());
            return null;
        }
        log.info("送给 Python：{} 请求体={}", describeAll(request), body);
        return postJson(parseUri, body, PythonParseResponse.class, "识图");
    }

    /**
     * 问询只读；失败返回 null，由 {@code InquiryService} 写成助手消息。
     */
    @Override
    public PythonInquiryResponse inquire(Map<String, Object> payload) {
        String body;
        try {
            body = JSON.writeValueAsString(payload);
        } catch (JacksonException e) {
            log.warn("问询请求序列化失败：{}", e.getMessage());
            return null;
        }
        log.info("送给 Python 问询 projectId={} threadId={} chars={}",
                payload.get("projectId"), payload.get("inquiryThreadId"), body.length());
        return postJson(inquireUri, body, PythonInquiryResponse.class, "问询");
    }

    /**
     * 建模只认注入包；失败返回 null，由 {@code ModelingService} 写成任务失败。
     */
    @Override
    public PythonModelingResponse model(Map<String, Object> payload) {
        String body;
        try {
            body = JSON.writeValueAsString(payload);
        } catch (JacksonException e) {
            log.warn("建模请求序列化失败：{}", e.getMessage());
            return null;
        }
        log.info("送给 Python 建模 projectId={} taskId={} chars={}",
                payload.get("projectId"), payload.get("taskId"), body.length());
        return postJson(modelUri, body, PythonModelingResponse.class, "建模", modelingTimeout);
    }

    /**
     * 知识解析可能含 Unstructured + 多张附图 VL，单独更长超时。
     */
    @Override
    public PythonKnowledgeParseResponse parseKnowledge(Map<String, Object> payload) {
        String body;
        try {
            body = JSON.writeValueAsString(payload);
        } catch (JacksonException e) {
            log.warn("知识解析请求序列化失败：{}", e.getMessage());
            return null;
        }
        log.info("送给 Python 知识解析 documentId={} force={} pdf={}",
                payload.get("documentId"), payload.get("force"), payload.get("pdfPath"));
        return postJson(knowledgeParseUri, body, PythonKnowledgeParseResponse.class, "知识解析", knowledgeParseTimeout);
    }

    /**
     * 合并只读 parse、写 merge/，超时短于解析。
     */
    @Override
    public PythonKnowledgeMergeResponse mergeKnowledge(Map<String, Object> payload) {
        String body;
        try {
            body = JSON.writeValueAsString(payload);
        } catch (JacksonException e) {
            log.warn("知识合并请求序列化失败：{}", e.getMessage());
            return null;
        }
        log.info("送给 Python 知识合并 documentId={} elements={}",
                payload.get("documentId"), payload.get("elementsPath"));
        return postJson(knowledgeMergeUri, body, PythonKnowledgeMergeResponse.class, "知识合并", knowledgeMergeTimeout);
    }

    /**
     * 分割只读 merge、写 split/；embedding 按句分批，超时介于合并与解析之间。
     */
    @Override
    public PythonKnowledgeSplitResponse splitKnowledge(Map<String, Object> payload) {
        String body;
        try {
            body = JSON.writeValueAsString(payload);
        } catch (JacksonException e) {
            log.warn("知识分割请求序列化失败：{}", e.getMessage());
            return null;
        }
        log.info("送给 Python 知识分割 documentId={} merge={}",
                payload.get("documentId"), payload.get("mergeChunksPath"));
        return postJson(knowledgeSplitUri, body, PythonKnowledgeSplitResponse.class, "知识分割", knowledgeSplitTimeout);
    }

    /**
     * 嵌入只读 split、写本机 Milvus；含多批 dense&sparse，超时长于分割。
     */
    @Override
    public PythonKnowledgeEmbedResponse embedKnowledge(Map<String, Object> payload) {
        String body;
        try {
            body = JSON.writeValueAsString(payload);
        } catch (JacksonException e) {
            log.warn("知识嵌入请求序列化失败：{}", e.getMessage());
            return null;
        }
        log.info("送给 Python 知识嵌入 documentId={} split={}",
                payload.get("documentId"), payload.get("splitChunksPath"));
        return postJson(knowledgeEmbedUri, body, PythonKnowledgeEmbedResponse.class, "知识嵌入", knowledgeEmbedTimeout);
    }

    /**
     * 检索只走一次问句 embedding + Milvus 混搜，超时短于嵌入。
     */
    @Override
    public PythonKnowledgeSearchResponse searchKnowledge(Map<String, Object> payload) {
        String body;
        try {
            body = JSON.writeValueAsString(payload);
        } catch (JacksonException e) {
            log.warn("知识检索请求序列化失败：{}", e.getMessage());
            return null;
        }
        log.info("送给 Python 知识检索 docs={} q={}",
                payload.get("documentIds"), payload.get("query"));
        return postJson(knowledgeSearchUri, body, PythonKnowledgeSearchResponse.class, "知识检索", knowledgeSearchTimeout);
    }

    /**
     * 作废或删文献时清向量；失败不抛。
     */
    @Override
    public void deleteKnowledgeVectors(Map<String, Object> payload) {
        String body;
        try {
            body = JSON.writeValueAsString(payload);
        } catch (JacksonException e) {
            log.warn("知识向量删除序列化失败：{}", e.getMessage());
            return;
        }
        log.info("送给 Python 知识向量删除 documentId={}", payload.get("documentId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = postJson(knowledgeVectorDeleteUri, body, Map.class, "知识向量删除", Duration.ofSeconds(30));
        if (result == null || !Boolean.TRUE.equals(result.get("ok"))) {
            log.warn("知识向量删除未成功 documentId={} resp={}", payload.get("documentId"), result);
        }
    }

    private <T> T postJson(URI uri, String body, Class<T> type, String label) {
        return postJson(uri, body, type, label, readTimeout);
    }

    private <T> T postJson(URI uri, String body, Class<T> type, String label, Duration timeout) {
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Python {} HTTP {} {}", label, response.statusCode(), abbreviate(response.body()));
                return null;
            }
            return JSON.readValue(response.body(), type);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("调用 Python {} 被中断", label);
            return null;
        } catch (IOException e) {
            log.warn("调用 Python {} 失败：{}", label, e.getMessage());
            return null;
        } catch (JacksonException e) {
            log.warn("Python {} 响应不是合法 JSON：{}", label, e.getMessage());
            return null;
        }
    }

    /**
     * 显式 Map，字段名与 Python 约定一致（camelCase），不依赖 RestClient 对 DTO 的猜测。
     */
    private Map<String, Object> toPayload(PythonParseRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", request.getProjectId());
        payload.put("carriageway", request.getCarriageway());
        payload.put("code", request.getCode());
        if (request.getLedger() != null) {
            payload.put("ledger", request.getLedger());
        }
        List<Map<String, Object>> files = new ArrayList<>();
        if (request.getFiles() != null) {
            for (PythonParseFileItem file : request.getFiles()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fileId", file.getFileId());
                item.put("originalName", file.getOriginalName());
                item.put("path", file.getPath());
                item.put("absolutePath", file.getAbsolutePath() != null ? file.getAbsolutePath() : file.getPath());
                item.put("sha256", file.getSha256());
                files.add(item);
            }
        }
        payload.put("files", files);
        if (request.getMode() != null) {
            payload.put("mode", request.getMode());
        }
        if (request.getFocusKinds() != null && !request.getFocusKinds().isEmpty()) {
            payload.put("focusKinds", request.getFocusKinds());
        }
        if (request.getDirective() != null && !request.getDirective().isBlank()) {
            payload.put("directive", request.getDirective());
        }
        if (request.getExistingPageMap() != null) {
            payload.put("existingPageMap", request.getExistingPageMap());
        }
        if (request.getDrawingCatalog() != null && !request.getDrawingCatalog().isEmpty()) {
            payload.put("drawingCatalog", request.getDrawingCatalog());
        }
        if (request.getAlreadyFetchedFileIds() != null && !request.getAlreadyFetchedFileIds().isEmpty()) {
            payload.put("alreadyFetchedFileIds", request.getAlreadyFetchedFileIds());
        }
        return payload;
    }

    private String describeAll(PythonParseRequest request) {
        if (request.getFiles() == null || request.getFiles().isEmpty()) {
            return "0 份 PDF";
        }
        return request.getFiles().stream().map(this::describe).collect(Collectors.joining("；"));
    }

    private String describe(PythonParseFileItem file) {
        return file.getOriginalName() + " -> " + file.getPath();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300);
    }
}
