package myz.bridge_agent_demo.service.impl;

import myz.bridge_agent_demo.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 项目图纸进 {@code data/files/{projectId}/}，知识 PDF 进 {@code data/knowledge/{sha256}/}，
 * SAP 模型进 {@code data/models/{projectId}/}。
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path filesRoot;
    private final Path knowledgeRoot;
    private final Path modelsRoot;
    private final Path avatarRoot;

    public FileStorageServiceImpl(
            @Value("${app.storage-root:data/files}") String storageRoot,
            @Value("${app.knowledge-storage-root:data/knowledge}") String knowledgeStorageRoot,
            @Value("${app.model-storage-root:data/models}") String modelStorageRoot,
            @Value("${app.avatar-storage-root:data/avatars}") String avatarStorageRoot) {
        this.filesRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.knowledgeRoot = Path.of(knowledgeStorageRoot).toAbsolutePath().normalize();
        this.modelsRoot = Path.of(modelStorageRoot).toAbsolutePath().normalize();
        this.avatarRoot = Path.of(avatarStorageRoot).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(Long projectId, String originalName, InputStream input) throws IOException {
        return writeHashed(filesRoot, projectId + "/", originalName, input);
    }

    @Override
    public Path resolve(String storagePath) {
        return safeResolve(filesRoot, storagePath);
    }

    @Override
    public void delete(String storagePath) throws IOException {
        deleteUnder(filesRoot, storagePath);
    }

    @Override
    public void deleteProjectDir(Long projectId) throws IOException {
        deleteTree(filesRoot.resolve(String.valueOf(projectId)).normalize(), filesRoot);
    }

    @Override
    public StoredFile storeKnowledge(String originalName, InputStream input) throws IOException {
        Files.createDirectories(knowledgeRoot);
        Path temp = Files.createTempFile(knowledgeRoot, "up-", ".tmp");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (DigestInputStream digested = new DigestInputStream(input, digest);
             OutputStream output = Files.newOutputStream(temp)) {
            digested.transferTo(output);
        }
        String sha256 = HexFormat.of().formatHex(digest.digest());
        long size = Files.size(temp);
        String hash = hashedName(sha256);
        Path neu = safeResolve(knowledgeRoot, hash + "/source.pdf");
        Path old = safeResolve(knowledgeRoot, hash + ".pdf");
        // 旧平铺 PDF 不搬家；相同内容再上传沿用旧文件，避免双份
        if (Files.exists(neu) || Files.exists(old)) {
            Files.deleteIfExists(temp);
            String relative = Files.exists(neu) ? hash + "/source.pdf" : hash + ".pdf";
            Path kept = Files.exists(neu) ? neu : old;
            return new StoredFile(relative.replace('\\', '/'), sha256, Files.size(kept));
        }
        Files.createDirectories(neu.getParent());
        Files.move(temp, neu);
        return new StoredFile((hash + "/source.pdf").replace('\\', '/'), sha256, size);
    }

    @Override
    public Path resolveKnowledge(String storagePath) {
        return safeResolve(knowledgeRoot, storagePath);
    }

    @Override
    public void deleteKnowledge(String storagePath) throws IOException {
        deleteUnder(knowledgeRoot, storagePath);
    }

    @Override
    public Path resolveKnowledgePdf(String sha256, String storagePath) {
        if (StringUtils.hasText(storagePath)) {
            Path stored = safeResolve(knowledgeRoot, storagePath);
            if (Files.exists(stored)) {
                return stored;
            }
        }
        String hash = hashedName(sha256);
        Path neu = safeResolve(knowledgeRoot, hash + "/source.pdf");
        if (Files.exists(neu)) {
            return neu;
        }
        return safeResolve(knowledgeRoot, hash + ".pdf");
    }

    @Override
    public Path knowledgeDocDir(String sha256) {
        return safeResolve(knowledgeRoot, hashedName(sha256));
    }

    @Override
    public Path knowledgeElementsPath(String sha256) {
        return knowledgeParseDir(sha256).resolve("elements.json");
    }

    @Override
    public Path findKnowledgeElements(String sha256) {
        Path neu = knowledgeElementsPath(sha256);
        if (Files.exists(neu)) {
            return neu;
        }
        Path old = safeResolve(knowledgeRoot, hashedName(sha256) + ".elements.json");
        if (Files.exists(old)) {
            return old;
        }
        return neu;
    }

    @Override
    public Path knowledgeFiguresDir(String sha256) {
        return knowledgeParseDir(sha256).resolve("figures");
    }

    @Override
    public Path knowledgeParseDir(String sha256) {
        Path dir = knowledgeDocDir(sha256).resolve("parse").normalize();
        if (!dir.startsWith(knowledgeRoot)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return dir;
    }

    @Override
    public Path knowledgeFigureIndexPath(String sha256) {
        return knowledgeParseDir(sha256).resolve("figures.json");
    }

    @Override
    public Path knowledgeTableIndexPath(String sha256) {
        return knowledgeParseDir(sha256).resolve("tables.json");
    }

    @Override
    public Path knowledgeMergeDir(String sha256) {
        Path dir = knowledgeDocDir(sha256).resolve("merge").normalize();
        if (!dir.startsWith(knowledgeRoot)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return dir;
    }

    @Override
    public Path knowledgeChunksPath(String sha256) {
        return knowledgeMergeDir(sha256).resolve("chunks.json");
    }

    @Override
    public Path knowledgeSplitDir(String sha256) {
        Path dir = knowledgeDocDir(sha256).resolve("split").normalize();
        if (!dir.startsWith(knowledgeRoot)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return dir;
    }

    @Override
    public Path knowledgeSplitChunksPath(String sha256) {
        return knowledgeSplitDir(sha256).resolve("chunks.json");
    }

    @Override
    public void deleteKnowledgeParseArtifacts(String sha256) throws IOException {
        String hash = hashedName(sha256);
        deleteTree(knowledgeParseDir(hash), knowledgeRoot);
        Files.deleteIfExists(safeResolve(knowledgeRoot, hash + ".elements.json"));
        Path legacyFigures = knowledgeDocDir(hash).resolve("figures").normalize();
        if (legacyFigures.startsWith(knowledgeRoot) && Files.isDirectory(legacyFigures)) {
            deleteTree(legacyFigures, knowledgeRoot);
        }
    }

    @Override
    public void deleteKnowledgeMergeArtifacts(String sha256) throws IOException {
        deleteTree(knowledgeMergeDir(sha256), knowledgeRoot);
    }

    @Override
    public void deleteKnowledgeSplitArtifacts(String sha256) throws IOException {
        deleteTree(knowledgeSplitDir(sha256), knowledgeRoot);
    }

    @Override
    public void deleteKnowledgeDocumentFiles(String sha256, String storagePath) throws IOException {
        if (StringUtils.hasText(sha256) && sha256.matches("[0-9a-fA-F]{64}")) {
            String hash = hashedName(sha256);
            deleteTree(knowledgeDocDir(hash), knowledgeRoot);
            Files.deleteIfExists(safeResolve(knowledgeRoot, hash + ".pdf"));
            Files.deleteIfExists(safeResolve(knowledgeRoot, hash + ".elements.json"));
        }
        deleteKnowledge(storagePath);
    }

    /** 只用内容指纹当目录名，避免路径穿越。 */
    private String hashedName(String sha256) {
        if (!StringUtils.hasText(sha256) || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("非法知识文件指纹");
        }
        return sha256.toLowerCase();
    }

    @Override
    public StoredFile storeModel(Long projectId, String originalName, InputStream input) throws IOException {
        return writeHashed(modelsRoot, projectId + "/", originalName, input);
    }

    @Override
    public Path resolveModel(String storagePath) {
        return safeResolve(modelsRoot, storagePath);
    }

    @Override
    public void deleteModel(String storagePath) throws IOException {
        deleteUnder(modelsRoot, storagePath);
    }

    @Override
    public void deleteProjectModelsDir(Long projectId) throws IOException {
        deleteTree(modelsRoot.resolve(String.valueOf(projectId)).normalize(), modelsRoot);
    }

    @Override
    public Path prepareModelDir(Long projectId) throws IOException {
        Path dir = modelsRoot.resolve(String.valueOf(projectId)).normalize();
        if (!dir.startsWith(modelsRoot)) {
            throw new IOException("模型目录越界");
        }
        Files.createDirectories(dir);
        return dir;
    }

    @Override
    public StoredFile storeAvatar(Long userId, String originalName, InputStream input) throws IOException {
        return writeHashed(avatarRoot, userId + "/", originalName, input);
    }

    @Override
    public Path resolveAvatar(String storagePath) {
        return safeResolve(avatarRoot, storagePath);
    }

    private StoredFile writeHashed(Path root, String prefix, String originalName, InputStream input)
            throws IOException {
        Files.createDirectories(root);
        Path temp = Files.createTempFile(root, "up-", ".tmp");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (DigestInputStream digested = new DigestInputStream(input, digest);
             OutputStream output = Files.newOutputStream(temp)) {
            digested.transferTo(output);
        }
        String sha256 = HexFormat.of().formatHex(digest.digest());
        long size = Files.size(temp);
        String ext = extension(originalName);
        String relative = prefix + sha256 + (ext.isEmpty() ? "" : "." + ext);
        Path dest = safeResolve(root, relative);
        Files.createDirectories(dest.getParent());
        if (Files.exists(dest)) {
            Files.deleteIfExists(temp);
        } else {
            Files.move(temp, dest);
        }
        return new StoredFile(relative.replace('\\', '/'), sha256, size);
    }

    private Path safeResolve(Path root, String storagePath) {
        Path path = root.resolve(storagePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return path;
    }

    private void deleteUnder(Path root, String storagePath) throws IOException {
        if (!StringUtils.hasText(storagePath)) {
            return;
        }
        Files.deleteIfExists(safeResolve(root, storagePath));
    }

    /** 删项目时清整个子目录；dir 必须落在 root 下。 */
    private void deleteTree(Path dir, Path root) throws IOException {
        if (!dir.startsWith(root) || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 删除项目时尽力清理磁盘
                        }
                    });
        }
    }

    private String extension(String originalName) {
        if (originalName == null) {
            return "";
        }
        String name = originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }
}
