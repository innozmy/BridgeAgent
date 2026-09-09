package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.vo.FileBatchUploadVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 项目图纸的上传、下载、删除（元数据 + 磁盘）。 */
public interface ProjectFileService {

    /**
     * 一次可多份。白名单仅 pdf/dwg/dxf；同项目相同 SHA-256 视为已存在。
     *
     * @param kind drawing / cad / other，整批共用一个归类
     */
    FileBatchUploadVO upload(Long projectId, String kind, List<MultipartFile> files);

    /** 返回文件流，不包 Result；失败仍走全局异常变成 JSON */
    ResponseEntity<Resource> download(Long projectId, Long fileId);

    void delete(Long projectId, Long fileId);
}
