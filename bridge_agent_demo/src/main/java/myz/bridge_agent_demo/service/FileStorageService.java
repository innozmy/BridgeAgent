package myz.bridge_agent_demo.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * 本地磁盘存储。二进制不进 MySQL。
 * 根目录见配置 {@code app.storage-root}，默认 {@code data/files}。
 */
public interface FileStorageService {

    /**
     * 写入项目目录，文件名用内容 SHA-256，避免同内容多份副本。
     *
     * @return 相对路径、哈希、字节数，供 {@code project_file} 落库
     */
    StoredFile store(Long projectId, String originalName, InputStream input) throws IOException;

    /** 相对路径 → 绝对路径；禁止跳出存储根目录（防路径穿越） */
    Path resolve(String storagePath);

    void delete(String storagePath) throws IOException;

    void deleteProjectDir(Long projectId) throws IOException;

    /**
     * 写入 {@code data/knowledge/{sha256}/source.pdf}。
     * 若磁盘上已有旧平铺 {@code {sha256}.pdf}，不搬家、也不再写一份 source.pdf。
     */
    StoredFile storeKnowledge(String originalName, InputStream input) throws IOException;

    Path resolveKnowledge(String storagePath);

    void deleteKnowledge(String storagePath) throws IOException;

    /**
     * 打开 PDF：先登记路径，没有则试 {@code {sha256}/source.pdf}，再试旧 {@code {sha256}.pdf}。
     */
    Path resolveKnowledgePdf(String sha256, String storagePath);

    /** 文献根目录 {@code data/knowledge/{sha256}/}，不含旧平铺 PDF。 */
    Path knowledgeDocDir(String sha256);

    /** 解析写入路径 {@code {sha256}/parse/elements.json}（新作业始终写这里）。 */
    Path knowledgeElementsPath(String sha256);

    /**
     * 已有 sidecar：新路径优先，否则旧 {@code {sha256}.elements.json}。
     * 都不存在时仍返回新路径，便于调用方判断 {@code Files.exists}。
     */
    Path findKnowledgeElements(String sha256);

    /** {@code {sha256}/parse/figures/} */
    Path knowledgeFiguresDir(String sha256);

    /** {@code {sha256}/parse/} */
    Path knowledgeParseDir(String sha256);

    Path knowledgeFigureIndexPath(String sha256);

    Path knowledgeTableIndexPath(String sha256);

    Path knowledgeMergeDir(String sha256);

    Path knowledgeChunksPath(String sha256);

    Path knowledgeSplitDir(String sha256);

    Path knowledgeSplitChunksPath(String sha256);

    /**
     * 只删 parse/ 与旧平铺 sidecar/figures，不删 PDF，不删 merge/、split/。
     */
    void deleteKnowledgeParseArtifacts(String sha256) throws IOException;

    /** 只删 merge/，不动 parse/ 与 PDF。 */
    void deleteKnowledgeMergeArtifacts(String sha256) throws IOException;

    /** 只删 split/，不动 parse/ 与 merge/。 */
    void deleteKnowledgeSplitArtifacts(String sha256) throws IOException;

    /** 删文献时清整个 {@code {sha256}/} 以及旧平铺 PDF/sidecar。 */
    void deleteKnowledgeDocumentFiles(String sha256, String storagePath) throws IOException;

    /**
     * 写入项目 SAP 模型目录 {@code data/models/{projectId}/}，文件名用内容 SHA-256。
     */
    StoredFile storeModel(Long projectId, String originalName, InputStream input) throws IOException;

    /** 相对模型根目录 → 绝对路径；禁止跳出模型根 */
    Path resolveModel(String storagePath);

    void deleteModel(String storagePath) throws IOException;

    /** 删项目时清该项目全部 SAP 模型文件 */
    void deleteProjectModelsDir(Long projectId) throws IOException;

    StoredFile storeAvatar(Long userId, String originalName, InputStream input) throws IOException;

    Path resolveAvatar(String storagePath);

    /**
     * 确保 {@code data/models/{projectId}/} 存在，返回绝对路径给 Python 写临时 .sdb。
     */
    Path prepareModelDir(Long projectId) throws IOException;

    /** 落盘结果，给文件表用 */
    record StoredFile(String storagePath, String sha256, long sizeBytes) {
    }
}
