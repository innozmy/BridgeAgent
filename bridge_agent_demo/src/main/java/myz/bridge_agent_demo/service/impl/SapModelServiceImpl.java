package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.dto.SapModelRegisterRequest;
import myz.bridge_agent_demo.entity.Project;
import myz.bridge_agent_demo.entity.ProjectSapModel;
import myz.bridge_agent_demo.exception.BusinessException;
import myz.bridge_agent_demo.mapper.ProjectMapper;
import myz.bridge_agent_demo.mapper.ProjectSapModelMapper;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.SapModelService;
import myz.bridge_agent_demo.vo.ProjectSapModelVO;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * SAP 模型版本：落 {@code project_sap_model} + {@code data/models/{projectId}/}。
 * 建模图尚未接入时列表可为空；人可删旧版本以免占盘。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SapModelServiceImpl implements SapModelService {

    private final ProjectMapper projectMapper;
    private final ProjectSapModelMapper sapModelMapper;
    private final FileStorageService fileStorageService;

    @Override
    public List<ProjectSapModelVO> list(Long projectId) {
        requireProject(projectId);
        return sapModelMapper.selectList(
                        Wrappers.<ProjectSapModel>lambdaQuery()
                                .eq(ProjectSapModel::getProjectId, projectId)
                                .orderByDesc(ProjectSapModel::getSeq))
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public ProjectSapModelVO get(Long projectId, Long modelId) {
        return toVo(requireModel(projectId, modelId));
    }

    @Override
    public ResponseEntity<Resource> download(Long projectId, Long modelId) {
        ProjectSapModel model = requireModel(projectId, modelId);
        Path path = fileStorageService.resolveModel(model.getStoragePath());
        if (!Files.exists(path)) {
            throw new BusinessException("模型文件不在磁盘上");
        }
        String encoded = URLEncoder.encode(model.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentLength(path.toFile().length())
                .body(new FileSystemResource(path));
    }

    @Override
    @Transactional
    public void delete(Long projectId, Long modelId) {
        ProjectSapModel model = requireModel(projectId, modelId);
        sapModelMapper.deleteById(model.getId());
        try {
            fileStorageService.deleteModel(model.getStoragePath());
        } catch (IOException e) {
            log.warn("删除 SAP 模型磁盘文件失败 {}", model.getStoragePath(), e);
        }
    }

    @Override
    @Transactional
    public ProjectSapModelVO register(Long projectId, SapModelRegisterRequest request, InputStream content) {
        requireProject(projectId);
        if (request == null || !StringUtils.hasText(request.getOriginalName())) {
            throw new BusinessException("模型文件名不能为空");
        }
        if (content == null) {
            throw new BusinessException("模型文件不能为空");
        }
        FileStorageService.StoredFile stored;
        try {
            stored = fileStorageService.storeModel(projectId, request.getOriginalName().trim(), content);
        } catch (IOException e) {
            log.error("保存 SAP 模型失败 project={}", projectId, e);
            throw new BusinessException("保存模型失败");
        }
        ProjectSapModel existing = sapModelMapper.selectOne(
                Wrappers.<ProjectSapModel>lambdaQuery()
                        .eq(ProjectSapModel::getProjectId, projectId)
                        .eq(ProjectSapModel::getSha256, stored.sha256()));
        if (existing != null) {
            // 同内容不占第二份盘；补全预览/说明
            patchIfBlank(existing, request);
            sapModelMapper.updateById(existing);
            return toVo(sapModelMapper.selectById(existing.getId()));
        }
        ProjectSapModel row = new ProjectSapModel();
        row.setProjectId(projectId);
        row.setTaskId(request.getTaskId());
        row.setSeq(nextSeq(projectId));
        row.setSapVersion(blankToNull(request.getSapVersion()));
        row.setOriginalName(request.getOriginalName().trim());
        row.setStoragePath(stored.storagePath());
        row.setSha256(stored.sha256());
        row.setSizeBytes(stored.sizeBytes());
        row.setFrameCount(request.getFrameCount());
        row.setJointCount(request.getJointCount());
        row.setNote(blankToNull(request.getNote()));
        row.setPreviewJson(blankToNull(request.getPreviewJson()));
        sapModelMapper.insert(row);
        return toVo(sapModelMapper.selectById(row.getId()));
    }

    private int nextSeq(Long projectId) {
        ProjectSapModel latest = sapModelMapper.selectOne(
                Wrappers.<ProjectSapModel>lambdaQuery()
                        .eq(ProjectSapModel::getProjectId, projectId)
                        .orderByDesc(ProjectSapModel::getSeq)
                        .last("LIMIT 1"));
        return latest == null || latest.getSeq() == null ? 1 : latest.getSeq() + 1;
    }

    private void patchIfBlank(ProjectSapModel existing, SapModelRegisterRequest request) {
        if (!StringUtils.hasText(existing.getPreviewJson()) && StringUtils.hasText(request.getPreviewJson())) {
            existing.setPreviewJson(request.getPreviewJson());
        }
        if (!StringUtils.hasText(existing.getNote()) && StringUtils.hasText(request.getNote())) {
            existing.setNote(request.getNote());
        }
        if (existing.getFrameCount() == null && request.getFrameCount() != null) {
            existing.setFrameCount(request.getFrameCount());
        }
        if (existing.getJointCount() == null && request.getJointCount() != null) {
            existing.setJointCount(request.getJointCount());
        }
        if (!StringUtils.hasText(existing.getSapVersion()) && StringUtils.hasText(request.getSapVersion())) {
            existing.setSapVersion(request.getSapVersion());
        }
    }

    private ProjectSapModel requireModel(Long projectId, Long modelId) {
        requireProject(projectId);
        ProjectSapModel model = sapModelMapper.selectById(modelId);
        if (model == null || !projectId.equals(model.getProjectId())) {
            throw new BusinessException("模型版本不存在");
        }
        return model;
    }

    private void requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
    }

    private ProjectSapModelVO toVo(ProjectSapModel row) {
        ProjectSapModelVO vo = new ProjectSapModelVO();
        vo.setId(row.getId());
        vo.setProjectId(row.getProjectId());
        vo.setTaskId(row.getTaskId());
        vo.setSeq(row.getSeq());
        vo.setSapVersion(row.getSapVersion());
        vo.setOriginalName(row.getOriginalName());
        vo.setSizeBytes(row.getSizeBytes());
        vo.setFrameCount(row.getFrameCount());
        vo.setJointCount(row.getJointCount());
        vo.setNote(row.getNote());
        vo.setPreviewJson(row.getPreviewJson());
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
