package myz.bridge_agent_demo.service;

import myz.bridge_agent_demo.dto.ProjectCreateRequest;
import myz.bridge_agent_demo.dto.ProjectParamBatchRequest;
import myz.bridge_agent_demo.dto.ProjectParamItemRequest;
import myz.bridge_agent_demo.dto.ProjectQuery;
import myz.bridge_agent_demo.dto.ProjectUnitBatchRequest;
import myz.bridge_agent_demo.dto.ProjectUpdateRequest;
import myz.bridge_agent_demo.entity.Project;
import myz.bridge_agent_demo.entity.ProjectFieldMeta;
import myz.bridge_agent_demo.vo.ProjectVO;

import java.util.List;

/**
 * 项目账本：建项、改项、查详情、换联（跨径+墩柱）、换参数袋。
 * Controller 只调这里，不直接碰 Mapper。
 */
public interface ProjectService {

    ProjectVO create(ProjectCreateRequest request);

    /** 详情 = 项目行 + 联（含墩柱）+ 参数袋 + 文件列表；概览从这里出 */
    ProjectVO getById(Long id);

    List<Project> list(ProjectQuery query);

    ProjectVO update(Long id, ProjectUpdateRequest request);

    /**
     * 识图写入项目字段。出现的列标为 drawing，并记下 drawingValue。
     * 与 {@link #update} 的差别：概览手改走 update（来源 manual），识图走这里。
     */
    ProjectVO updateFromDrawing(Long id, ProjectUpdateRequest request);

    /**
     * 合并本次识图读到的对照值与缺口。不改 girder_type 等账本列本身
     * （那些已由 {@link #updateFromDrawing} / {@link #replaceUnits} 写过）。
     */
    void mergeDrawingMeta(Long id, ProjectFieldMeta patch);

    /** 删库行（联、文件元数据级联），并尽量清掉磁盘目录 */
    void delete(Long id);

    ProjectVO replaceUnits(Long id, ProjectUnitBatchRequest request);

    /**
     * 概览整袋替换扩展参数。空列表清空袋；不得写入与固定列/墩柱同义的 key。
     */
    ProjectVO replaceParams(Long id, ProjectParamBatchRequest request);

    /**
     * 识图按 key/label upsert 参数袋。{@code overwrite=false} 只补空行；已有行跳过。
     * 保留键（跨径、柱高等）直接忽略。
     *
     * @param items 路由后的袋项（已排除固定列）
     * @param overwrite 确认覆盖时为 true
     * @return 是否实际写入过至少一行
     */
    boolean upsertParamsFromDrawing(Long id, List<ProjectParamItemRequest> items, boolean overwrite);
}
