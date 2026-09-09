package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.dto.SapModelRegisterRequest;
import myz.bridge_agent_demo.vo.ProjectSapModelVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.util.List;

/**
 * 项目 SAP 模型版本：列表、预览元数据、下载、删除。
 * 建模 Agent 落盘时走 {@link #register}；浏览器不直传 Python。
 */
public interface SapModelService {

    List<ProjectSapModelVO> list(Long projectId);

    ProjectSapModelVO get(Long projectId, Long modelId);

    ResponseEntity<Resource> download(Long projectId, Long modelId);

    /**
     * 删除该版本行及磁盘文件，避免版本堆着占盘。不改账本。
     */
    void delete(Long projectId, Long modelId);

    /**
     * 把一份已生成的 .sdb 纳入版本表。seq 取该项目当前最大 +1。
     *
     * @param content 模型文件流；会按内容哈希去重，同哈希复用已有行
     */
    ProjectSapModelVO register(Long projectId, SapModelRegisterRequest request, InputStream content);
}
