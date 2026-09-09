package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.entity.Project;
import myz.bridge_agent_demo.entity.ProjectFile;
import myz.bridge_agent_demo.exception.BusinessException;
import myz.bridge_agent_demo.mapper.ProjectFileMapper;
import myz.bridge_agent_demo.mapper.ProjectMapper;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.ProjectFileService;
import myz.bridge_agent_demo.vo.FileBatchUploadVO;
import myz.bridge_agent_demo.vo.FileUploadItemVO;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 图纸业务：校验扩展名、落盘、写 {@code project_file}、按项目下载/删除。
 * 第一版不解析、不填跨径，{@code parse_status} 保持 uploaded。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectFileServiceImpl implements ProjectFileService {

    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "dwg", "dxf");
    private static final Set<String> KINDS = Set.of("drawing", "cad", "other");
    private static final String UNSUPPORTED_HINT =
            "目前只支持 PDF、DWG、DXF。JPG/PNG 暂不入库；识图阶段开放后，Agent 才可以用视觉工具读取。";

    private final ProjectMapper projectMapper;
    private final ProjectFileMapper projectFileMapper;
    private final FileStorageService fileStorageService;

    @Override
    public FileBatchUploadVO upload(Long projectId, String kind, List<MultipartFile> files) {
        requireProject(projectId);
        String resolvedKind = StringUtils.hasText(kind) ? kind.trim() : "drawing";
        if (!KINDS.contains(resolvedKind)) {
            throw new BusinessException("文件归类只能是 drawing、cad 或 other");
        }
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请选择文件");
        }

        FileBatchUploadVO result = new FileBatchUploadVO();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                result.getErrors().add("存在空文件，已跳过");
                continue;
            }
            String originalName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
            String ext = extension(originalName);
            if (!ALLOWED_EXT.contains(ext)) {
                result.getErrors().add(originalName + "：" + UNSUPPORTED_HINT);
                continue;
            }
            try {
                FileStorageService.StoredFile stored =
                        fileStorageService.store(projectId, originalName, file.getInputStream());
                ProjectFile existing = findByHash(projectId, stored.sha256());
                if (existing != null) {
                    result.getSaved().add(item(existing, true));
                    continue;
                }
                ProjectFile row = new ProjectFile();
                row.setProjectId(projectId);
                row.setKind(resolvedKind);
                row.setOriginalName(originalName);
                row.setStoragePath(stored.storagePath());
                row.setSha256(stored.sha256());
                row.setMimeType(resolveMime(ext, file.getContentType()));
                row.setSizeBytes(stored.sizeBytes());
                row.setParseStatus("uploaded");
                try {
                    projectFileMapper.insert(row);
                    result.getSaved().add(item(row, false));
                } catch (DuplicateKeyException e) {
                    // 并发下两个请求可能同时通过「先查再插」，唯一键兜底
                    ProjectFile again = findByHash(projectId, stored.sha256());
                    if (again == null) {
                        throw e;
                    }
                    result.getSaved().add(item(again, true));
                }
            } catch (IOException e) {
                log.error("保存文件失败 {}", originalName, e);
                result.getErrors().add(originalName + "：保存失败");
            }
        }

        // 全失败（例如只传了 jpg）当成业务错误，前端弹出提示
        if (result.getSaved().isEmpty()) {
            if (result.getErrors().isEmpty()) {
                throw new BusinessException("请选择文件");
            }
            throw new BusinessException(String.join("；", result.getErrors()));
        }
        return result;
    }

    @Override
    public ResponseEntity<Resource> download(Long projectId, Long fileId) {
        ProjectFile file = requireFile(projectId, fileId);
        Path path = fileStorageService.resolve(file.getStoragePath());
        if (!Files.exists(path)) {
            throw new BusinessException("文件不在磁盘上");
        }
        String encoded = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if ("application/pdf".equalsIgnoreCase(file.getMimeType())
                || file.getOriginalName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentLength(path.toFile().length())
                .body(new FileSystemResource(path));
    }

    @Override
    public void delete(Long projectId, Long fileId) {
        ProjectFile file = requireFile(projectId, fileId);
        // 先删库再删盘：列表立刻没有这一行；盘删失败只打日志
        projectFileMapper.deleteById(file.getId());
        try {
            fileStorageService.delete(file.getStoragePath());
        } catch (IOException e) {
            log.warn("删除磁盘文件失败 {}", file.getStoragePath(), e);
        }
    }

    private ProjectFile requireFile(Long projectId, Long fileId) {
        requireProject(projectId);
        ProjectFile file = projectFileMapper.selectById(fileId);
        if (file == null || !projectId.equals(file.getProjectId())) {
            throw new BusinessException("文件不存在");
        }
        return file;
    }

    private void requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
    }

    private ProjectFile findByHash(Long projectId, String sha256) {
        return projectFileMapper.selectOne(
                Wrappers.<ProjectFile>lambdaQuery()
                        .eq(ProjectFile::getProjectId, projectId)
                        .eq(ProjectFile::getSha256, sha256));
    }

    private FileUploadItemVO item(ProjectFile file, boolean duplicate) {
        FileUploadItemVO item = new FileUploadItemVO();
        item.setFile(file);
        item.setDuplicate(duplicate);
        return item;
    }

    private String extension(String originalName) {
        String name = originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveMime(String ext, String contentType) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "dwg" -> "application/acad";
            case "dxf" -> "application/dxf";
            default -> StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
        };
    }
}
