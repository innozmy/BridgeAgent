package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.dto.ProjectCreateRequest;
import myz.bridge_agent_demo.dto.ProjectParamBatchRequest;
import myz.bridge_agent_demo.dto.ProjectParamItemRequest;
import myz.bridge_agent_demo.dto.ProjectQuery;
import myz.bridge_agent_demo.dto.ProjectUnitBatchRequest;
import myz.bridge_agent_demo.dto.ProjectUnitColumnItemRequest;
import myz.bridge_agent_demo.dto.ProjectUnitItemRequest;
import myz.bridge_agent_demo.dto.ProjectUnitSupportItemRequest;
import myz.bridge_agent_demo.dto.ProjectUpdateRequest;
import myz.bridge_agent_demo.entity.FieldProvenance;
import myz.bridge_agent_demo.entity.Project;
import myz.bridge_agent_demo.entity.ProjectFieldMeta;
import myz.bridge_agent_demo.entity.ProjectFile;
import myz.bridge_agent_demo.entity.ProjectParam;
import myz.bridge_agent_demo.entity.ProjectUnit;
import myz.bridge_agent_demo.entity.ProjectUnitColumn;
import myz.bridge_agent_demo.entity.ProjectUnitSupport;
import myz.bridge_agent_demo.exception.BusinessException;
import myz.bridge_agent_demo.mapper.ProjectFileMapper;
import myz.bridge_agent_demo.mapper.ProjectMapper;
import myz.bridge_agent_demo.mapper.ProjectParamMapper;
import myz.bridge_agent_demo.mapper.ProjectUnitColumnMapper;
import myz.bridge_agent_demo.mapper.ProjectUnitMapper;
import myz.bridge_agent_demo.mapper.ProjectUnitSupportMapper;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.LedgerParamRules;
import myz.bridge_agent_demo.service.OptimisticWrites;
import myz.bridge_agent_demo.service.PermissionService;
import myz.bridge_agent_demo.service.ProjectService;
import myz.bridge_agent_demo.vo.ProjectVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 项目业务实现。{@code @RequiredArgsConstructor} 按构造器注入下面四个 final 依赖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectUnitMapper projectUnitMapper;
    private final ProjectUnitSupportMapper projectUnitSupportMapper;
    private final ProjectUnitColumnMapper projectUnitColumnMapper;
    private final ProjectParamMapper projectParamMapper;
    private final ProjectFileMapper projectFileMapper;
    private final FileStorageService fileStorageService;
    private final PermissionService permissionService;

    @Override
    @Transactional
    public ProjectVO create(ProjectCreateRequest request) {
        Project project = new Project();
        project.setName(request.getName().trim());
        project.setCarriageway(request.getCarriageway());
        project.setCode(blankToNull(request.getCode()));
        project.setIntro(blankToNull(request.getIntro()));
        project.setRegion(blankToNull(request.getRegion()));
        project.setOpenedOn(request.getOpenedOn());
        project.setCodeStrategy(blankToNull(request.getCodeStrategy()));
        // 新建一律草稿；建模任务以后再改 status
        project.setStatus("draft");
        project.setVersion(0);
        projectMapper.insert(project);
        return getById(project.getId());
    }

    /**
     * 前端进项目页只调这一次：布局把结果提供给概览、图纸等子页。
     * 幅面、标号在 project 行；跨径与墩柱在 units；扩展量在 params；图纸列表在 files。
     */
    @Override
    public ProjectVO getById(Long id) {
        Project project = requireProject(id);
        ProjectVO vo = new ProjectVO();
        BeanUtils.copyProperties(project, vo);
        List<ProjectUnit> units = projectUnitMapper.selectList(
                Wrappers.<ProjectUnit>lambdaQuery()
                        .eq(ProjectUnit::getProjectId, id)
                        .orderByAsc(ProjectUnit::getSeq));
        nestSupports(units);
        vo.setUnits(units);
        vo.setParams(projectParamMapper.selectList(
                Wrappers.<ProjectParam>lambdaQuery()
                        .eq(ProjectParam::getProjectId, id)
                        .orderByAsc(ProjectParam::getId)));
        vo.setFiles(projectFileMapper.selectList(
                Wrappers.<ProjectFile>lambdaQuery()
                        .eq(ProjectFile::getProjectId, id)
                        .orderByAsc(ProjectFile::getCreatedAt)));
        return vo;
    }

    @Override
    public List<Project> list(ProjectQuery query) {
        LambdaQueryWrapper<Project> wrapper = Wrappers.lambdaQuery();
        if (query != null) {
            // hasText 为 false 时该条件不拼进 SQL
            wrapper.like(StringUtils.hasText(query.getName()), Project::getName, query.getName())
                    .eq(StringUtils.hasText(query.getCode()), Project::getCode, query.getCode())
                    .eq(StringUtils.hasText(query.getRegion()), Project::getRegion, query.getRegion())
                    .eq(StringUtils.hasText(query.getCarriageway()), Project::getCarriageway, query.getCarriageway())
                    .eq(StringUtils.hasText(query.getStatus()), Project::getStatus, query.getStatus());
        }
        List<Long> visible = permissionService.visibleProjectIds();
        if (visible != null) {
            if (visible.isEmpty()) {
                return List.of();
            }
            wrapper.in(Project::getId, visible);
        }
        wrapper.orderByDesc(Project::getUpdatedAt);
        return projectMapper.selectList(wrapper);
    }

    /** 按字段是否出现做部分更新。概览手改把变动过的可识图字段标为 manual。 */
    @Override
    @Transactional
    public ProjectVO update(Long id, ProjectUpdateRequest request) {
        return patch(id, request, "manual");
    }

    @Override
    @Transactional
    public ProjectVO updateFromDrawing(Long id, ProjectUpdateRequest request) {
        return patch(id, request, "drawing");
    }

    /**
     * {@code origin=manual}：只在值真的变了时改来源。
     * {@code origin=drawing}：本次写出的可识图字段一律记 drawing，并更新对照值。
     */
    private ProjectVO patch(Long id, ProjectUpdateRequest request, String origin) {
        Project existing = requireProject(id);
        boolean drawing = "drawing".equals(origin);
        if (!drawing) {
            OptimisticWrites.requireVersion(request.getVersion());
            if (!request.getVersion().equals(existing.getVersion())) {
                throw new BusinessException(OptimisticWrites.CONFLICT);
            }
        }
        Project project = new Project();
        project.setId(id);
        ProjectFieldMeta meta = existing.getFieldMeta() == null ? new ProjectFieldMeta() : existing.getFieldMeta();
        boolean metaChanged = false;

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new BusinessException("项目名称不能为空");
            }
            project.setName(request.getName().trim());
        }
        if (request.getCarriageway() != null) {
            project.setCarriageway(request.getCarriageway());
        }
        if (request.getCode() != null) {
            String next = blankToNull(request.getCode());
            project.setCode(next);
            metaChanged |= touchTracked(meta::getCode, meta::setCode, existing.getCode(), next, origin);
        }
        if (request.getIntro() != null) {
            project.setIntro(blankToNull(request.getIntro()));
        }
        if (request.getRegion() != null) {
            String next = blankToNull(request.getRegion());
            project.setRegion(next);
            metaChanged |= touchTracked(meta::getRegion, meta::setRegion, existing.getRegion(), next, origin);
        }
        if (request.getOpenedOn() != null) {
            project.setOpenedOn(request.getOpenedOn());
        }
        if (request.getCodeStrategy() != null) {
            project.setCodeStrategy(blankToNull(request.getCodeStrategy()));
        }
        if (request.getGirderType() != null) {
            String next = blankToNull(request.getGirderType());
            project.setGirderType(next);
            metaChanged |= touchTracked(meta::getGirderType, meta::setGirderType, existing.getGirderType(), next, origin);
        }
        if (request.getLayoutType() != null) {
            String next = blankToNull(request.getLayoutType());
            project.setLayoutType(next);
            metaChanged |= touchTracked(meta::getLayoutType, meta::setLayoutType, existing.getLayoutType(), next, origin);
        }
        if (request.getMaterial() != null) {
            String next = blankToNull(request.getMaterial());
            project.setMaterial(next);
            metaChanged |= touchTracked(meta::getMaterial, meta::setMaterial, existing.getMaterial(), next, origin);
        }
        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }
        if (metaChanged) {
            project.setFieldMeta(meta);
        }
        updateProjectRow(project, existing.getVersion(), drawing);
        return getById(id);
    }

    @Override
    @Transactional
    public void mergeDrawingMeta(Long id, ProjectFieldMeta patch) {
        if (patch == null) {
            return;
        }
        Project existing = requireProject(id);
        ProjectFieldMeta meta = existing.getFieldMeta() == null ? new ProjectFieldMeta() : existing.getFieldMeta();
        mergeDrawingValue(meta::getCode, meta::setCode, patch.getCode());
        mergeDrawingValue(meta::getGirderType, meta::setGirderType, patch.getGirderType());
        mergeDrawingValue(meta::getLayoutType, meta::setLayoutType, patch.getLayoutType());
        mergeDrawingValue(meta::getMaterial, meta::setMaterial, patch.getMaterial());
        mergeDrawingValue(meta::getRegion, meta::setRegion, patch.getRegion());
        mergeDrawingValue(meta::getSpansM, meta::setSpansM, patch.getSpansM());
        if (patch.getGaps() != null) {
            meta.setGaps(patch.getGaps());
        }
        if (patch.getHasLayoutPages() != null) {
            meta.setHasLayoutPages(patch.getHasLayoutPages());
        }
        Project row = new Project();
        row.setId(id);
        row.setFieldMeta(meta);
        updateProjectRow(row, existing.getVersion(), true);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireProject(id);
        // 外键 ON DELETE CASCADE 会带走 unit、support/column、file、param、SAP 模型行
        projectMapper.deleteById(id);
        try {
            fileStorageService.deleteProjectDir(id);
        } catch (IOException e) {
            log.warn("清理项目文件目录失败 {}", id, e);
        }
        try {
            fileStorageService.deleteProjectModelsDir(id);
        } catch (IOException e) {
            log.warn("清理项目 SAP 模型目录失败 {}", id, e);
        }
        try {
            fileStorageService.deleteProjectDir(id);
        } catch (IOException e) {
            log.warn("清理项目文件目录失败 {}", id, e);
        }
        try {
            fileStorageService.deleteProjectModelsDir(id);
        } catch (IOException e) {
            log.warn("清理项目 SAP 模型目录失败 {}", id, e);
        }
    }

    /**
     * 全量替换联：先校验序号，记下旧墩柱（请求未带 supports 时按联 seq 保留），再删旧插新。
     * {@code supports == null} 保留该联原墩柱；空列表清空。
     */
    @Override
    @Transactional
    public ProjectVO replaceUnits(Long id, ProjectUnitBatchRequest request) {
        Project existing = requireProject(id);
        // 人手带 version：先 CAS 再改联，避免删插后才发现冲突。识图不带 version，认落库当下的行。
        lockProjectVersion(existing, request.getVersion(), request.getVersion() == null);
        Set<Integer> seqs = new HashSet<>();
        for (ProjectUnitItemRequest item : request.getUnits()) {
            if (!seqs.add(item.getSeq())) {
                throw new BusinessException("联序号不能重复: " + item.getSeq());
            }
            validateSupportSeqs(item);
        }
        Map<Integer, List<ProjectUnitSupport>> oldSupports = loadSupportsByUnitSeq(id);
        projectUnitMapper.delete(Wrappers.<ProjectUnit>lambdaQuery().eq(ProjectUnit::getProjectId, id));
        for (ProjectUnitItemRequest item : request.getUnits()) {
            ProjectUnit unit = new ProjectUnit();
            unit.setProjectId(id);
            unit.setSeq(item.getSeq());
            unit.setSpansM(item.getSpansM());
            unit.setLengthM(sumSpans(item.getSpansM()));
            unit.setSource(item.getSource() == null ? "manual" : item.getSource());
            projectUnitMapper.insert(unit);
            List<ProjectUnitSupportItemRequest> incoming = item.getSupports();
            if (incoming != null) {
                insertSupports(unit.getId(), incoming, unit.getSource());
            } else {
                reinsertSupports(unit.getId(), oldSupports.get(item.getSeq()), unit.getSource());
            }
        }
        String unitSource = request.getUnits().isEmpty()
                ? "manual"
                : (request.getUnits().get(0).getSource() == null ? "manual" : request.getUnits().get(0).getSource());
        Project afterLock = projectMapper.selectById(id);
        ProjectFieldMeta meta = afterLock.getFieldMeta() == null ? new ProjectFieldMeta() : afterLock.getFieldMeta();
        FieldProvenance spans = meta.getSpansM() == null ? new FieldProvenance() : meta.getSpansM();
        String formatted = formatUnitSpans(request.getUnits());
        spans.setSource(unitSource);
        if ("drawing".equals(unitSource) && StringUtils.hasText(formatted)) {
            spans.setDrawingValue(formatted);
        }
        meta.setSpansM(spans);
        Project row = new Project();
        row.setId(id);
        row.setFieldMeta(meta);
        // version 已在 lock 时 +1；此处只改 JSON，不带 version 以免拦截器再拦一次
        projectMapper.updateById(row);
        return getById(id);
    }

    @Override
    @Transactional
    public ProjectVO replaceParams(Long id, ProjectParamBatchRequest request) {
        Project existing = requireProject(id);
        OptimisticWrites.requireVersion(request.getVersion());
        lockProjectVersion(existing, request.getVersion(), false);
        Set<String> keys = new HashSet<>();
        for (ProjectParamItemRequest item : request.getItems()) {
            String key = LedgerParamRules.canonicalBagKey(item.getParamKey(), item.getLabel());
            item.setParamKey(key);
            if (LedgerParamRules.reservedKey(key) || LedgerParamRules.reservedLabel(item.getLabel())) {
                throw new BusinessException("「" + item.getLabel() + "」属于固定列或联/墩柱表，不能放进其他参数");
            }
            if (!keys.add(key.toLowerCase(Locale.ROOT))) {
                throw new BusinessException("参数键不能重复: " + key);
            }
        }
        projectParamMapper.delete(Wrappers.<ProjectParam>lambdaQuery().eq(ProjectParam::getProjectId, id));
        for (ProjectParamItemRequest item : request.getItems()) {
            insertParam(id, item, item.getSource() == null ? "manual" : item.getSource());
        }
        return getById(id);
    }

    @Override
    @Transactional
    public boolean upsertParamsFromDrawing(Long id, List<ProjectParamItemRequest> items, boolean overwrite) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        requireProject(id);
        List<ProjectParam> existing = projectParamMapper.selectList(
                Wrappers.<ProjectParam>lambdaQuery().eq(ProjectParam::getProjectId, id));
        Map<String, ProjectParam> byKey = new HashMap<>();
        Map<String, ProjectParam> byLabel = new HashMap<>();
        for (ProjectParam row : existing) {
            byKey.put(row.getParamKey().toLowerCase(Locale.ROOT), row);
            byLabel.put(row.getLabel().trim(), row);
        }
        boolean wrote = false;
        for (ProjectParamItemRequest item : items) {
            String label = item.getLabel() == null ? "" : item.getLabel().trim();
            String key = LedgerParamRules.canonicalBagKey(item.getParamKey(), label);
            item.setParamKey(key);
            if (key.isEmpty() || LedgerParamRules.reservedKey(key) || LedgerParamRules.reservedLabel(label)) {
                continue;
            }
            ProjectParam found = byKey.get(key.toLowerCase(Locale.ROOT));
            if (found == null && !label.isEmpty()) {
                found = byLabel.get(label);
            }
            if (found == null) {
                insertParam(id, item, "drawing");
                wrote = true;
                continue;
            }
            if (!overwrite) {
                continue;
            }
            if (sameText(found.getValueText(), item.getValueText())
                    && sameText(found.getUnit(), item.getUnit())
                    && sameText(found.getLabel(), label)) {
                continue;
            }
            ProjectParam patch = new ProjectParam();
            patch.setId(found.getId());
            patch.setLabel(label.isEmpty() ? found.getLabel() : label);
            patch.setValueText(blankToNull(item.getValueText()));
            patch.setUnit(blankToNull(item.getUnit()));
            patch.setSource("drawing");
            patch.setVersion(found.getVersion());
            if (projectParamMapper.updateById(patch) == 0) {
                ProjectParam again = projectParamMapper.selectById(found.getId());
                if (again == null) {
                    throw new BusinessException(OptimisticWrites.DRAWING_CONFLICT);
                }
                patch.setVersion(again.getVersion());
                if (projectParamMapper.updateById(patch) == 0) {
                    throw new BusinessException(OptimisticWrites.DRAWING_CONFLICT);
                }
            }
            wrote = true;
        }
        return wrote;
    }

    /**
     * 可识图字段碰到来源。manual 只在值变化时改标；drawing 每次写出都更新对照值。
     */
    private boolean touchTracked(Supplier<FieldProvenance> getter,
                                 Consumer<FieldProvenance> setter,
                                 String previous,
                                 String next,
                                 String origin) {
        FieldProvenance node = getter.get();
        if (node == null) {
            node = new FieldProvenance();
            setter.accept(node);
        }
        if ("drawing".equals(origin)) {
            node.setSource("drawing");
            if (StringUtils.hasText(next)) {
                node.setDrawingValue(next.trim());
            }
            return true;
        }
        if (sameText(previous, next)) {
            return false;
        }
        node.setSource("manual");
        return true;
    }

    private void mergeDrawingValue(Supplier<FieldProvenance> getter,
                                   Consumer<FieldProvenance> setter,
                                   FieldProvenance incoming) {
        if (incoming == null) {
            return;
        }
        FieldProvenance node = getter.get();
        if (node == null) {
            node = new FieldProvenance();
            setter.accept(node);
        }
        if (StringUtils.hasText(incoming.getDrawingValue())) {
            node.setDrawingValue(incoming.getDrawingValue().trim());
        }
        if (StringUtils.hasText(incoming.getSource())) {
            node.setSource(incoming.getSource());
        }
    }

    private boolean sameText(String left, String right) {
        String a = blankToNull(left);
        String b = blankToNull(right);
        return Objects.equals(a, b);
    }

    private String formatUnitSpans(List<ProjectUnitItemRequest> units) {
        return units.stream()
                .map(unit -> unit.getSpansM().stream().map(BigDecimal::toPlainString).collect(Collectors.joining("+")))
                .collect(Collectors.joining(" | "));
    }

    private Project requireProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        return project;
    }

    private BigDecimal sumSpans(List<BigDecimal> spans) {
        return spans.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);
    }

    /** 空白当「没填」，写成数据库 NULL，避免存一堆空字符串 */
    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 批量查出本项目联的墩台与柱，挂到 {@code supports}，避免 N+1。
     */
    private void nestSupports(List<ProjectUnit> units) {
        if (units == null || units.isEmpty()) {
            return;
        }
        List<Long> unitIds = units.stream().map(ProjectUnit::getId).toList();
        List<ProjectUnitSupport> supports = projectUnitSupportMapper.selectList(
                Wrappers.<ProjectUnitSupport>lambdaQuery()
                        .in(ProjectUnitSupport::getUnitId, unitIds)
                        .orderByAsc(ProjectUnitSupport::getSeq));
        Map<Long, List<ProjectUnitSupport>> byUnit = new LinkedHashMap<>();
        for (ProjectUnitSupport support : supports) {
            byUnit.computeIfAbsent(support.getUnitId(), key -> new ArrayList<>()).add(support);
        }
        if (!supports.isEmpty()) {
            List<Long> supportIds = supports.stream().map(ProjectUnitSupport::getId).toList();
            List<ProjectUnitColumn> columns = projectUnitColumnMapper.selectList(
                    Wrappers.<ProjectUnitColumn>lambdaQuery()
                            .in(ProjectUnitColumn::getSupportId, supportIds)
                            .orderByAsc(ProjectUnitColumn::getSeq));
            Map<Long, List<ProjectUnitColumn>> bySupport = new HashMap<>();
            for (ProjectUnitColumn column : columns) {
                bySupport.computeIfAbsent(column.getSupportId(), key -> new ArrayList<>()).add(column);
            }
            for (ProjectUnitSupport support : supports) {
                support.setColumns(bySupport.getOrDefault(support.getId(), List.of()));
            }
        }
        for (ProjectUnit unit : units) {
            unit.setSupports(byUnit.getOrDefault(unit.getId(), List.of()));
        }
    }

    /** 删联前按联 seq 记下墩柱，请求未带 supports 时原样写回。 */
    private Map<Integer, List<ProjectUnitSupport>> loadSupportsByUnitSeq(Long projectId) {
        List<ProjectUnit> units = projectUnitMapper.selectList(
                Wrappers.<ProjectUnit>lambdaQuery()
                        .eq(ProjectUnit::getProjectId, projectId)
                        .orderByAsc(ProjectUnit::getSeq));
        nestSupports(units);
        Map<Integer, List<ProjectUnitSupport>> bySeq = new HashMap<>();
        for (ProjectUnit unit : units) {
            bySeq.put(unit.getSeq(), unit.getSupports() == null ? List.of() : unit.getSupports());
        }
        return bySeq;
    }

    private void validateSupportSeqs(ProjectUnitItemRequest item) {
        if (item.getSupports() == null) {
            return;
        }
        Set<Integer> seqs = new HashSet<>();
        for (ProjectUnitSupportItemRequest support : item.getSupports()) {
            if (!seqs.add(support.getSeq())) {
                throw new BusinessException("第 " + item.getSeq() + " 联墩台序号不能重复: " + support.getSeq());
            }
            if (support.getColumns() == null) {
                continue;
            }
            Set<Integer> colSeqs = new HashSet<>();
            for (ProjectUnitColumnItemRequest column : support.getColumns()) {
                if (!colSeqs.add(column.getSeq())) {
                    throw new BusinessException("第 " + item.getSeq() + " 联墩台 " + support.getSeq()
                            + " 的柱序号不能重复: " + column.getSeq());
                }
            }
        }
    }

    private void insertSupports(Long unitId, List<ProjectUnitSupportItemRequest> supports, String fallbackSource) {
        for (ProjectUnitSupportItemRequest item : supports) {
            ProjectUnitSupport row = new ProjectUnitSupport();
            row.setUnitId(unitId);
            row.setSeq(item.getSeq());
            row.setCode(blankToNull(item.getCode()));
            row.setKind(normalizeKind(item.getKind()));
            row.setSource(item.getSource() == null ? fallbackSource : item.getSource());
            projectUnitSupportMapper.insert(row);
            if (item.getColumns() == null) {
                continue;
            }
            for (ProjectUnitColumnItemRequest column : item.getColumns()) {
                insertColumn(row.getId(), column, row.getSource());
            }
        }
    }

    /** 把旧实体树按新 unitId 再插入（replaceUnits 保留未提交的墩柱）。 */
    private void reinsertSupports(Long unitId, List<ProjectUnitSupport> old, String fallbackSource) {
        if (old == null || old.isEmpty()) {
            return;
        }
        for (ProjectUnitSupport support : old) {
            ProjectUnitSupport row = new ProjectUnitSupport();
            row.setUnitId(unitId);
            row.setSeq(support.getSeq());
            row.setCode(support.getCode());
            row.setKind(normalizeKind(support.getKind()));
            row.setSource(support.getSource() == null ? fallbackSource : support.getSource());
            projectUnitSupportMapper.insert(row);
            if (support.getColumns() == null) {
                continue;
            }
            for (ProjectUnitColumn column : support.getColumns()) {
                ProjectUnitColumnItemRequest item = new ProjectUnitColumnItemRequest();
                item.setSeq(column.getSeq());
                item.setSide(column.getSide());
                item.setHeightM(column.getHeightM());
                item.setSource(column.getSource());
                insertColumn(row.getId(), item, row.getSource());
            }
        }
    }

    private void insertColumn(Long supportId, ProjectUnitColumnItemRequest item, String fallbackSource) {
        ProjectUnitColumn row = new ProjectUnitColumn();
        row.setSupportId(supportId);
        row.setSeq(item.getSeq());
        row.setSide(normalizeSide(item.getSide()));
        row.setHeightM(item.getHeightM());
        row.setSource(item.getSource() == null ? fallbackSource : item.getSource());
        projectUnitColumnMapper.insert(row);
    }

    private void insertParam(Long projectId, ProjectParamItemRequest item, String source) {
        ProjectParam row = new ProjectParam();
        row.setProjectId(projectId);
        row.setParamKey(item.getParamKey().trim());
        row.setLabel(item.getLabel().trim());
        row.setValueText(blankToNull(item.getValueText()));
        row.setUnit(blankToNull(item.getUnit()));
        row.setSource(source);
        row.setVersion(0);
        projectParamMapper.insert(row);
    }

    /**
     * {@code updateById} 必须带当前 version。识图冲突时重读一行再试一次，仍失败则进时间线文案。
     */
    private void updateProjectRow(Project patch, Integer expectedVersion, boolean retryOnce) {
        int expected = expectedVersion == null ? 0 : expectedVersion;
        patch.setVersion(expected);
        if (projectMapper.updateById(patch) > 0) {
            return;
        }
        if (retryOnce && patch.getId() != null) {
            Project fresh = projectMapper.selectById(patch.getId());
            if (fresh != null) {
                patch.setVersion(fresh.getVersion());
                if (projectMapper.updateById(patch) > 0) {
                    return;
                }
            }
            throw new BusinessException(OptimisticWrites.DRAWING_CONFLICT);
        }
        throw new BusinessException(OptimisticWrites.CONFLICT);
    }

    /**
     * 先把 {@code project.version} +1，再改联或整袋，避免删插后才发现冲突。
     * 人手必须对上请求里的 version；识图用当前行，失败再读一次。
     */
    private int lockProjectVersion(Project existing, Integer clientVersion, boolean drawing) {
        if (drawing) {
            int expected = existing.getVersion() == null ? 0 : existing.getVersion();
            if (casBumpProject(existing.getId(), expected)) {
                return expected;
            }
            Project fresh = requireProject(existing.getId());
            expected = fresh.getVersion() == null ? 0 : fresh.getVersion();
            if (casBumpProject(existing.getId(), expected)) {
                return expected;
            }
            throw new BusinessException(OptimisticWrites.DRAWING_CONFLICT);
        }
        OptimisticWrites.requireVersion(clientVersion);
        if (!clientVersion.equals(existing.getVersion())) {
            throw new BusinessException(OptimisticWrites.CONFLICT);
        }
        if (!casBumpProject(existing.getId(), clientVersion)) {
            throw new BusinessException(OptimisticWrites.CONFLICT);
        }
        return clientVersion;
    }

    /** Lambda 只改 version，不走实体拦截器，避免和 updateById 叠两次。 */
    private boolean casBumpProject(Long id, int expected) {
        return projectMapper.update(null, Wrappers.<Project>lambdaUpdate()
                .set(Project::getVersion, expected + 1)
                .eq(Project::getId, id)
                .eq(Project::getVersion, expected)) == 1;
    }

    /** 入参：识图或前端的 kind 原文。返回：pier / abutment。 */
    private String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return "pier";
        }
        String text = kind.trim().toLowerCase(Locale.ROOT);
        if (text.contains("台") || "abutment".equals(text)) {
            return "abutment";
        }
        return "pier";
    }

    /** 入参：左右内外原文。返回：枚举值或 null，不把中文原样入库。 */
    private String normalizeSide(String side) {
        if (side == null || side.isBlank()) {
            return null;
        }
        String text = side.trim().toLowerCase(Locale.ROOT);
        if (text.contains("左") || "left".equals(text)) {
            return "left";
        }
        if (text.contains("右") || "right".equals(text)) {
            return "right";
        }
        if (text.contains("内") || "inner".equals(text)) {
            return "inner";
        }
        if (text.contains("外") || "outer".equals(text)) {
            return "outer";
        }
        return null;
    }
}
