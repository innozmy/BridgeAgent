package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.entity.KnowledgeDocument;
import myz.bridge_agent_demo.vo.KnowledgeParseStartVO;
import myz.bridge_agent_demo.vo.KnowledgeSearchVO;
import myz.bridge_agent_demo.vo.KnowledgeUploadVO;
import myz.bridge_agent_demo.vo.ProjectKnowledgeVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 公司知识总库 + 项目启用集。解析由 {@link #startParse} 后台触发，Python 写 sidecar。
 */
public interface KnowledgeService {

    List<KnowledgeDocument> listDocuments();

    /**
     * 上传一本 PDF 并登记元数据。仅允许 pdf。
     *
     * @param familyCode 规范号，如 JTG D62；可空
     */
    KnowledgeUploadVO upload(String name, String category, String familyCode, String region,
                             String specialty, LocalDate effectiveFrom, LocalDate effectiveTo,
                             MultipartFile file);

    ResponseEntity<Resource> download(Long documentId);

    /** 删总库记录、各项目启用关系、磁盘文件 */
    void deleteDocument(Long documentId);

    /**
     * 人在知识库点「解析」。立刻返回；后台调 Python。
     *
     * @param force 已解析完成时须 true 才清空重跑；附图 VL 未完时 false 会续跑
     */
    KnowledgeParseStartVO startParse(Long documentId, boolean force);

    /**
     * 人在知识库点「合并」。立刻返回；后台对着 parse/ 写 merge/，不改第一步文件。
     *
     * @param force 已合并完成时须 true 才重写 merge/
     */
    KnowledgeParseStartVO startMerge(Long documentId, boolean force);

    /**
     * 人在知识库点「分割」。立刻返回；后台对着 merge/ 写 split/，不改前两步文件。
     *
     * @param force 已分割完成时须 true 才重写 split/
     */
    KnowledgeParseStartVO startSplit(Long documentId, boolean force);

    /**
     * 人在知识库点「嵌入」。立刻返回；后台对着 split/ 与图/表目录写入本机 Milvus。
     *
     * @param force 已嵌入完成时须 true 才重写向量
     */
    KnowledgeParseStartVO startEmbed(Long documentId, boolean force);

    /**
     * 按本项目启用且已嵌入的规范做混搜。问句由人填；文献 ID 服务端注入。
     * 不把结果接到 Agent。
     */
    KnowledgeSearchVO searchProject(Long projectId, String query);

    /**
     * 组注入包用的规范范围：启用 ∩ 已嵌入 ∩ {@code category=code}。
     * 含文献磁盘根路径，供 Python 同 HTTP 内补发 JPEG/表 HTML；不扫总库。
     *
     * @return {@code documentIds}、{@code documentNames}、{@code documents[{documentId,name,sha256,rootPath}]}
     */
    Map<String, Object> buildKnowledgeScope(Long projectId);

    ProjectKnowledgeVO listProjectKnowledge(Long projectId);

    void enable(Long projectId, Long documentId);

    void disable(Long projectId, Long documentId);
}
