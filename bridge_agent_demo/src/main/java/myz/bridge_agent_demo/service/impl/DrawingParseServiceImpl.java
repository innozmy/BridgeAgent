package myz.bridge_agent_demo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.auth.JobActors;
import myz.bridge_agent_demo.dto.DrawingExtractItem;
import myz.bridge_agent_demo.dto.DrawingParseColumn;
import myz.bridge_agent_demo.dto.DrawingParseProposal;
import myz.bridge_agent_demo.dto.DrawingParseSupport;
import myz.bridge_agent_demo.dto.DrawingParseUnit;
import myz.bridge_agent_demo.dto.LedgerConflictItem;
import myz.bridge_agent_demo.dto.ProjectParamItemRequest;
import myz.bridge_agent_demo.dto.ProjectUnitBatchRequest;
import myz.bridge_agent_demo.dto.ProjectUnitColumnItemRequest;
import myz.bridge_agent_demo.dto.ProjectUnitItemRequest;
import myz.bridge_agent_demo.dto.ProjectUnitSupportItemRequest;
import myz.bridge_agent_demo.dto.ProjectUpdateRequest;
import myz.bridge_agent_demo.dto.PythonNeedFileItem;
import myz.bridge_agent_demo.dto.PythonParseFileItem;
import myz.bridge_agent_demo.dto.PythonParseRequest;
import myz.bridge_agent_demo.dto.PythonParseResponse;
import myz.bridge_agent_demo.entity.FieldProvenance;
import myz.bridge_agent_demo.entity.ModelTask;
import myz.bridge_agent_demo.entity.ProjectFieldMeta;
import myz.bridge_agent_demo.entity.ProjectFile;
import myz.bridge_agent_demo.entity.ProjectParam;
import myz.bridge_agent_demo.entity.ProjectUnit;
import myz.bridge_agent_demo.entity.ProjectUnitColumn;
import myz.bridge_agent_demo.entity.ProjectUnitSupport;
import myz.bridge_agent_demo.exception.BusinessException;
import myz.bridge_agent_demo.entity.DrawingPageMap;
import myz.bridge_agent_demo.mapper.DrawingPageMapMapper;
import myz.bridge_agent_demo.mapper.ProjectFileMapper;
import myz.bridge_agent_demo.service.JobDispatchService;
import myz.bridge_agent_demo.service.TaskKinds;
import myz.bridge_agent_demo.service.AgentInjectPackService;
import myz.bridge_agent_demo.service.DrawingMemoryService;
import myz.bridge_agent_demo.service.DrawingParseService;
import myz.bridge_agent_demo.service.FileStorageService;
import myz.bridge_agent_demo.service.LedgerParamRules;
import myz.bridge_agent_demo.service.ModelTaskService;
import myz.bridge_agent_demo.service.ModelingService;
import myz.bridge_agent_demo.service.OptimisticWrites;
import myz.bridge_agent_demo.service.ProjectService;
import myz.bridge_agent_demo.service.PythonAgentClient;
import myz.bridge_agent_demo.vo.ModelTaskVO;
import myz.bridge_agent_demo.vo.ProjectVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service 实现：识图编排。HTTP 调 Python 不包在长事务里，避免占着连接等视觉服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrawingParseServiceImpl implements DrawingParseService {

    public static final String KIND = TaskKinds.DRAWING_FULL;

    /** 同一 running 任务除首轮外最多再调 Python 两次（共 3 枪）。 */
    private static final int MAX_EXTRA_ROUNDS = 2;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ProjectService projectService;
    private final ModelTaskService modelTaskService;
    private final PythonAgentClient pythonAgentClient;
    private final FileStorageService fileStorageService;
    private final ProjectFileMapper projectFileMapper;
    private final DrawingPageMapMapper drawingPageMapMapper;
    private final DrawingMemoryService drawingMemoryService;
    private final AgentInjectPackService agentInjectPackService;
    private final ObjectProvider<JobDispatchService> jobDispatchService;
    private final ObjectProvider<ModelingService> modelingService;

    /**
     * 建 queued 任务后立刻返回；Python 在后台跑。
     * 锁项目行后再插卡，避免与同时同意的另一张卡抢链。
     */
    @Override
    @Transactional
    public ModelTaskVO parse(Long projectId) {
        modelTaskService.lockWorkChain(projectId);
        if (modelTaskService.hasRunningWorkChain(projectId)) {
            throw new BusinessException("已有识图或建模进行中，请到任务页查看。");
        }
        ModelTask card = new ModelTask();
        card.setProjectId(projectId);
        card.setKind(TaskKinds.DRAWING_FULL);
        card.setTitle(TaskKinds.titleOf(TaskKinds.DRAWING_FULL));
        card.setStatus("queued");
        card.setOrigin("drawing_button");
        JobActors.fillCreator(card);
        JobActors.fillAgreed(card);
        ModelTaskVO created = modelTaskService.insertCard(card);
        Long taskId = created.getTask().getId();
        modelTaskService.appendEvent(taskId, "已进入资源队列，识图车道有空位后开始识别。");
        afterCommit(() -> jobDispatchService.getObject().offerTask(projectId, taskId));
        return modelTaskService.get(projectId, taskId);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Override
    public void executeJob(Long projectId, Long taskId) {
        try {
            runExecuteJob(projectId, taskId);
        } catch (Exception e) {
            log.warn("识图后台异常 projectId={} taskId={}", projectId, taskId, e);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            fail(projectId, taskId, List.of(), "识图后台异常：" + reason);
        }
    }

    /**
     * 后台线程读到的状态可能仍是 {@code queued}（派发刚 CAS 完）。
     * 不能静默 return，否则库停在 running、调度只捞 queued，任务会永远「进行中」。
     */
    private ModelTask loadDrawingJob(Long projectId, Long taskId) {
        ModelTask task = modelTaskService.get(projectId, taskId).getTask();
        if (!TaskKinds.isDrawing(task.getKind())) {
            log.warn("识图后台跳过：不是识图工种 projectId={} taskId={} kind={}",
                    projectId, taskId, task.getKind());
            return null;
        }
        if ("queued".equals(task.getStatus())) {
            modelTaskService.casStatus(projectId, taskId, "queued", "running");
            task = modelTaskService.get(projectId, taskId).getTask();
        }
        if (!"running".equals(task.getStatus())) {
            log.warn("识图后台跳过：状态不是进行中 projectId={} taskId={} status={}",
                    projectId, taskId, task.getStatus());
            return null;
        }
        return task;
    }

    /** 真正调 Python；异常由 {@link #executeJob} 收成任务失败，避免一直卡在 running。 */
    private void runExecuteJob(Long projectId, Long taskId) {
        ModelTask task = loadDrawingJob(projectId, taskId);
        if (task == null) {
            return;
        }
        log.info("识图后台开始 projectId={} taskId={} kind={}", projectId, taskId, task.getKind());
        ProjectVO project = projectService.getById(projectId);
        List<ProjectFile> files = project.getFiles() == null ? List.of() : project.getFiles();
        for (ProjectFile file : files) {
            if (isCad(file)) {
                modelTaskService.appendEvent(taskId, "本步不识 CAD：" + file.getOriginalName());
            }
        }

        if (TaskKinds.DRAWING_SUPPLEMENT.equals(task.getKind()) && task.getFileId() == null) {
            fail(projectId, taskId, List.of(), "补充识别必须指定图纸。");
            return;
        }

        List<Map<String, Object>> catalog = agentInjectPackService.buildDrawingCatalog(project);
        ProjectFile primary = pickPrimaryPdf(task, files);
        if (primary == null) {
            drawingMemoryService.submitParseFailure(projectId, taskId, "没有可识别的 PDF。请先上传图纸；DWG/DXF 本步不识。");
            fail(projectId, taskId, List.of(), "没有可识别的 PDF。请先上传图纸；DWG/DXF 本步不识。");
            return;
        }
        if (TaskKinds.DRAWING_SUPPLEMENT.equals(task.getKind()) && loadPageMap(projectId, primary.getId()) == null) {
            fail(projectId, taskId, List.of(primary), "补充识别需要先全册识别以得到页地图。");
            return;
        }

        List<ProjectFile> opened = new ArrayList<>();
        Set<Long> alreadyFetched = new LinkedHashSet<>();
        Deque<PythonNeedFileItem> pending = new ArrayDeque<>();

        RoundCall first = parseOneRound(projectId, taskId, project, task, primary, catalog, alreadyFetched,
                TaskKinds.DRAWING_SUPPLEMENT.equals(task.getKind())
                        ? readPageKinds(task.getPageKindsJson())
                        : List.of(),
                true, List.of());
        if (first.failed()) {
            return;
        }
        opened.add(primary);
        PythonParseResponse merged = mergeResponses(null, first.response());
        drawingMemoryService.submitRoundMap(projectId, List.of(primary.getId()), first.response());
        pending.addAll(validateNeedFiles(projectId, taskId, files, alreadyFetched, first.response().getNeedFiles()));

        int extra = 0;
        while (!pending.isEmpty() && extra < MAX_EXTRA_ROUNDS) {
            PythonNeedFileItem hint = pending.removeFirst();
            if (hint.getFileId() == null || alreadyFetched.contains(hint.getFileId())) {
                continue;
            }
            ProjectFile next = findPdf(files, hint.getFileId());
            if (next == null) {
                continue;
            }
            extra++;
            String reason = StringUtils.hasText(hint.getReason()) ? hint.getReason() : "本轮缺图";
            modelTaskService.appendEvent(taskId, "回调图纸 #" + next.getId() + "：" + reason);
            RoundCall round = parseOneRound(projectId, taskId, project, task, next, catalog, alreadyFetched,
                    hint.getFocusKinds() == null ? List.of() : hint.getFocusKinds(), false, opened);
            if (round.failed()) {
                return;
            }
            opened.add(next);
            merged = mergeResponses(merged, round.response());
            drawingMemoryService.submitRoundMap(projectId, List.of(next.getId()), round.response());
            pending.addAll(validateNeedFiles(projectId, taskId, files, alreadyFetched, round.response().getNeedFiles()));
        }
        if (!pending.isEmpty()) {
            List<String> leftover = new ArrayList<>();
            for (PythonNeedFileItem hint : pending) {
                leftover.add("#" + hint.getFileId());
            }
            modelTaskService.appendEvent(taskId, "已达本任务图纸回调上限（额外 " + MAX_EXTRA_ROUNDS
                    + " 轮），未打开：" + String.join("、", leftover) + "。用已有摘录继续。");
        } else if (extra == 0 && first.response().getNeedFiles() != null && !first.response().getNeedFiles().isEmpty()) {
            modelTaskService.appendEvent(taskId, "目录里没有更多可调图纸（索取项均无效）。用已有摘录继续。");
        }

        DrawingParseProposal proposal = toProposal(merged, opened);
        String invalid = validateProposal(proposal);
        if (invalid != null) {
            drawingMemoryService.submitParseResult(projectId, taskId, proposal.getFileIds(), merged, "failed");
            fail(projectId, taskId, opened, invalid);
            return;
        }

        ProjectVO latest = projectService.getById(projectId);
        if (TaskKinds.DRAWING_SUPPLEMENT.equals(task.getKind())) {
            // 补充识别常把已入账的跨径/墩高再抽一遍；那些不是本轮要补的缺口，去掉以免整份卡住
            dropIncidentalProposalFields(latest, proposal);
        }
        List<LedgerConflictItem> conflicts = detectConflicts(latest, proposal);
        if (!conflicts.isEmpty()) {
            proposal.setConflicts(conflicts);
            markFiles(opened, "uploaded");
            modelTaskService.saveProposal(projectId, taskId, writeJson(proposal));
            modelTaskService.updateStatus(projectId, taskId, "waiting");
            drawingMemoryService.submitParseResult(projectId, taskId, proposal.getFileIds(), merged, "waiting");
            modelTaskService.appendEvent(taskId,
                    "识别结果与现有账本冲突，未写入任何字段。"
                            + formatConflicts(conflicts)
                            + "。请对照覆盖前/后后确认或放弃。");
            notifyModelingParent(projectId, taskId, "waiting");
            return;
        }

        boolean wroteUnits = hasProposalUnits(proposal) && !hasUnits(latest);
        try {
            boolean wrote = fillBlanks(projectId, latest, proposal);
            projectService.mergeDrawingMeta(projectId, toMetaPatch(proposal, wroteUnits));
            markFiles(opened, "parsed");
            modelTaskService.saveProposal(projectId, taskId, writeJson(proposal));
            modelTaskService.updateStatus(projectId, taskId, "done");
            drawingMemoryService.submitParseResult(projectId, taskId, proposal.getFileIds(), merged, "done");
            if (wrote) {
                modelTaskService.appendEvent(taskId, "账本对应字段为空，已按识别结果写入。来源记为 drawing。" + formatProposal(proposal));
            } else {
                modelTaskService.appendEvent(taskId, "识别完成。现有账本一致或本轮没有可直接写入的字段。" + formatProposal(proposal));
            }
            String gapNote = formatGaps(proposal);
            if (gapNote != null) {
                modelTaskService.appendEvent(taskId, gapNote);
            }
            if (StringUtils.hasText(proposal.getNote())) {
                modelTaskService.appendEvent(taskId, proposal.getNote());
            }
            notifyModelingParent(projectId, taskId, "done");
        } catch (BusinessException e) {
            if (OptimisticWrites.isConflict(e)) {
                fail(projectId, taskId, opened, OptimisticWrites.DRAWING_CONFLICT);
                return;
            }
            throw e;
        }
    }

    /**
     * 调一轮 Python。失败时已写任务失败。
     *
     * @param firstRound 全册首轮不带已有页地图（允许重扫）；补充识别首轮必须带；回调轮有地图则 supplement
     */
    private RoundCall parseOneRound(Long projectId, Long taskId, ProjectVO project, ModelTask task,
                                    ProjectFile file, List<Map<String, Object>> catalog,
                                    Set<Long> alreadyFetched, List<String> focusKinds, boolean firstRound,
                                    List<ProjectFile> alreadyOpened) {
        alreadyFetched.add(file.getId());
        PythonParseFileItem item = toFileItem(file);
        List<ProjectFile> touched = new ArrayList<>(alreadyOpened);
        if (!touched.contains(file)) {
            touched.add(file);
        }
        if (item == null) {
            fail(projectId, taskId, touched, "磁盘上找不到文件：" + file.getOriginalName());
            return RoundCall.fail();
        }

        PythonParseRequest request = new PythonParseRequest();
        request.setProjectId(projectId);
        request.setCarriageway(project.getCarriageway());
        request.setCode(project.getCode());
        request.setLedger(agentInjectPackService.buildLedger(project));
        request.setDirective(task.getDirective());
        request.setDrawingCatalog(catalog);
        request.setAlreadyFetchedFileIds(new ArrayList<>(alreadyFetched));
        request.getFiles().add(item);

        Map<String, Object> existing = loadPageMap(projectId, file.getId());
        boolean supplementTask = TaskKinds.DRAWING_SUPPLEMENT.equals(task.getKind());
        if (firstRound && supplementTask) {
            request.setMode("supplement");
            request.setExistingPageMap(existing);
        } else if (!firstRound && existing != null) {
            request.setMode("supplement");
            request.setExistingPageMap(existing);
        }
        List<String> kinds = stripRebar(focusKinds);
        if (!kinds.isEmpty()) {
            request.setFocusKinds(kinds);
        }

        markFiles(List.of(file), "parsing");
        modelTaskService.appendEvent(taskId,
                "已将 PDF「" + file.getOriginalName() + "」送给 Python（本项目图纸清单 "
                        + catalog.size() + " 份 PDF，本轮打开这一份）。混合图只采本项目标号与建项幅面。");

        PythonParseResponse response = pythonAgentClient.parseDrawings(request);
        if (response == null) {
            drawingMemoryService.submitParseFailure(projectId, taskId,
                    "Python 识图服务连不上。请先在 python_agent 目录执行 python app.py（端口 8001）。");
            fail(projectId, taskId, touched,
                    "Python 识图服务连不上。请先在 python_agent 目录执行 python app.py（端口 8001）。");
            return RoundCall.fail();
        }
        if (!Boolean.TRUE.equals(response.getOk())) {
            String reason = StringUtils.hasText(response.getError()) ? response.getError() : "Python 返回失败";
            drawingMemoryService.submitParseFailure(projectId, taskId, reason);
            fail(projectId, taskId, touched, reason);
            return RoundCall.fail();
        }
        return RoundCall.ok(response);
    }

    /** 全册只开第一份能读的 PDF；补充识别开任务卡上的那一份。其余只出现在图纸清单里。 */
    private ProjectFile pickPrimaryPdf(ModelTask task, List<ProjectFile> files) {
        if (task.getFileId() != null) {
            return findPdf(files, task.getFileId());
        }
        for (ProjectFile file : files) {
            if (isPdf(file) && existsOnDisk(file)) {
                return file;
            }
        }
        return null;
    }

    private ProjectFile findPdf(List<ProjectFile> files, Long fileId) {
        if (fileId == null) {
            return null;
        }
        for (ProjectFile file : files) {
            if (fileId.equals(file.getId()) && isPdf(file) && existsOnDisk(file)) {
                return file;
            }
        }
        return null;
    }

    private PythonParseFileItem toFileItem(ProjectFile file) {
        Path path = fileStorageService.resolve(file.getStoragePath());
        if (!Files.exists(path)) {
            return null;
        }
        String absolute = path.toAbsolutePath().toString();
        PythonParseFileItem item = new PythonParseFileItem();
        item.setFileId(file.getId());
        item.setOriginalName(file.getOriginalName());
        item.setPath(absolute);
        item.setAbsolutePath(absolute);
        item.setSha256(file.getSha256());
        return item;
    }

    private boolean existsOnDisk(ProjectFile file) {
        return Files.exists(fileStorageService.resolve(file.getStoragePath()));
    }

    private Map<String, Object> loadPageMap(Long projectId, Long fileId) {
        DrawingPageMap mapRow = drawingPageMapMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(DrawingPageMap.class)
                        .eq(DrawingPageMap::getProjectId, projectId)
                        .eq(DrawingPageMap::getFileId, fileId));
        if (mapRow == null || !StringUtils.hasText(mapRow.getMapJson())) {
            return null;
        }
        Map<String, Object> map = readMap(mapRow.getMapJson());
        return map.isEmpty() ? null : map;
    }

    /**
     * 校验 needFiles：必须是本项目 PDF、未打开过、不是 CAD、不是只抽钢筋。非法项丢弃并记时间线。
     */
    private List<PythonNeedFileItem> validateNeedFiles(Long projectId, Long taskId, List<ProjectFile> files,
                                                       Set<Long> alreadyFetched, List<PythonNeedFileItem> raw) {
        List<PythonNeedFileItem> valid = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return valid;
        }
        Set<Long> queued = new HashSet<>();
        for (PythonNeedFileItem hint : raw) {
            Long fileId = hint.getFileId();
            if (fileId == null) {
                modelTaskService.appendEvent(taskId, "丢弃图纸回调：未给 fileId。");
                continue;
            }
            if (alreadyFetched.contains(fileId) || queued.contains(fileId)) {
                modelTaskService.appendEvent(taskId, "丢弃图纸回调 #" + fileId + "：本任务已经打开过。");
                continue;
            }
            ProjectFile file = null;
            for (ProjectFile candidate : files) {
                if (fileId.equals(candidate.getId())) {
                    file = candidate;
                    break;
                }
            }
            if (file == null || (file.getProjectId() != null && !projectId.equals(file.getProjectId()))) {
                modelTaskService.appendEvent(taskId, "丢弃图纸回调 #" + fileId + "：不属于本项目。");
                continue;
            }
            if (isCad(file)) {
                modelTaskService.appendEvent(taskId, "丢弃图纸回调 #" + fileId + "：本步不识 CAD。");
                continue;
            }
            if (!isPdf(file)) {
                modelTaskService.appendEvent(taskId, "丢弃图纸回调 #" + fileId + "：不是 PDF。");
                continue;
            }
            if (!existsOnDisk(file)) {
                modelTaskService.appendEvent(taskId, "丢弃图纸回调 #" + fileId + "：磁盘上找不到文件。");
                continue;
            }
            List<String> kinds = stripRebar(hint.getFocusKinds());
            if (hint.getFocusKinds() != null && !hint.getFocusKinds().isEmpty() && kinds.isEmpty()) {
                modelTaskService.appendEvent(taskId, "丢弃图纸回调 #" + fileId + "：只抽钢筋，本步不做。");
                continue;
            }
            PythonNeedFileItem kept = new PythonNeedFileItem();
            kept.setFileId(fileId);
            kept.setFocusKinds(kinds);
            kept.setReason(hint.getReason());
            valid.add(kept);
            queued.add(fileId);
        }
        return valid;
    }

    private List<String> stripRebar(List<String> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String kind : kinds) {
            if (!StringUtils.hasText(kind)) {
                continue;
            }
            if ("rebar".equalsIgnoreCase(kind.trim())) {
                continue;
            }
            out.add(kind.trim());
        }
        return out;
    }

    /**
     * 后轮非空标量覆盖；有跨径则整份 units 用后轮；extracts 按 key+label+page 后轮覆盖。
     */
    private PythonParseResponse mergeResponses(PythonParseResponse acc, PythonParseResponse next) {
        if (acc == null) {
            refreshGaps(next);
            return next;
        }
        PythonParseResponse out = new PythonParseResponse();
        out.setOk(true);
        out.setGirderType(firstNonBlank(next.getGirderType(), acc.getGirderType()));
        out.setLayoutType(firstNonBlank(next.getLayoutType(), acc.getLayoutType()));
        out.setMaterial(firstNonBlank(next.getMaterial(), acc.getMaterial()));
        out.setCode(firstNonBlank(next.getCode(), acc.getCode()));
        out.setRegion(firstNonBlank(next.getRegion(), acc.getRegion()));
        if (next.getUnits() != null && !next.getUnits().isEmpty()) {
            out.setUnits(next.getUnits());
        } else {
            out.setUnits(acc.getUnits() == null ? new ArrayList<>() : acc.getUnits());
        }
        out.setExtracts(mergeExtractLists(mergeExtracts(acc), mergeExtracts(next)));
        out.setHasLayoutPages(Boolean.TRUE.equals(acc.getHasLayoutPages())
                || Boolean.TRUE.equals(next.getHasLayoutPages()));
        out.setPageMap(next.getPageMap());
        out.setNeedFiles(next.getNeedFiles());
        out.setNote(joinNotes(acc.getNote(), next.getNote()));
        refreshGaps(out);
        return out;
    }

    private void refreshGaps(PythonParseResponse response) {
        if (response == null) {
            return;
        }
        List<String> gaps = new ArrayList<>();
        if (response.getUnits() == null || response.getUnits().isEmpty()) {
            gaps.add("spansM");
            if (!Boolean.TRUE.equals(response.getHasLayoutPages())) {
                gaps.add("missing_layout");
            }
        }
        if (!StringUtils.hasText(response.getGirderType())) {
            gaps.add("girderType");
        }
        if (!StringUtils.hasText(response.getLayoutType())) {
            gaps.add("layoutType");
        }
        if (!StringUtils.hasText(response.getMaterial())) {
            gaps.add("material");
        }
        response.setGaps(gaps);
    }

    private List<DrawingExtractItem> mergeExtractLists(List<DrawingExtractItem> older, List<DrawingExtractItem> newer) {
        Map<String, DrawingExtractItem> merged = new LinkedHashMap<>();
        if (older != null) {
            for (DrawingExtractItem item : older) {
                merged.put(extractDedupeKey(item), item);
            }
        }
        if (newer != null) {
            for (DrawingExtractItem item : newer) {
                merged.put(extractDedupeKey(item), item);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private String extractDedupeKey(DrawingExtractItem item) {
        String key = item.getKey() == null ? "" : item.getKey();
        String label = item.getLabel() == null ? "" : item.getLabel();
        String page = item.getPage() == null ? "" : String.valueOf(item.getPage());
        return key + "\0" + label + "\0" + page;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred.trim() : fallback;
    }

    private String joinNotes(String left, String right) {
        if (!StringUtils.hasText(left)) {
            return blankToNull(right);
        }
        if (!StringUtils.hasText(right) || left.trim().equals(right.trim())) {
            return left.trim();
        }
        return left.trim() + " " + right.trim();
    }

    private record RoundCall(PythonParseResponse response, boolean failed) {
        static RoundCall ok(PythonParseResponse response) {
            return new RoundCall(response, false);
        }

        static RoundCall fail() {
            return new RoundCall(null, true);
        }
    }

    private List<String> readPageKinds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new tools.jackson.core.type.TypeReference<List<String>>() { });
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return JSON.readValue(json, new tools.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (JacksonException e) {
            return Map.of();
        }
    }

    /**
     * 人已看过冲突，按提案整份覆盖。不再做「只写空字段」。
     */
    @Override
    @Transactional
    public ModelTaskVO confirm(Long projectId, Long taskId) {
        ModelTask task = requireParseWaiting(projectId, taskId);
        DrawingParseProposal proposal = readProposal(task.getProposalJson());
        String invalid = validateProposal(proposal);
        if (invalid != null) {
            throw new BusinessException(invalid);
        }
        try {
            overwriteLedger(projectId, proposal, task);
        } catch (BusinessException e) {
            if (OptimisticWrites.isConflict(e)) {
                throw new BusinessException(OptimisticWrites.DRAWING_CONFLICT);
            }
            throw e;
        }
        markFilesByIds(proposal.getFileIds(), "parsed");
        modelTaskService.updateStatus(projectId, taskId, "done");
        drawingMemoryService.submitParseResult(projectId, taskId, proposal.getFileIds(), null, "confirmed");
        modelTaskService.appendEvent(taskId, "已按确认覆盖账本（联跨径、墩柱及提案中的项目字段与其他参数）。" + formatProposal(proposal));
        String gapNote = formatGaps(proposal);
        if (gapNote != null) {
            modelTaskService.appendEvent(taskId, gapNote);
        }
        notifyModelingParent(projectId, taskId, "done");
        return modelTaskService.get(projectId, taskId);
    }

    @Override
    @Transactional
    public ModelTaskVO reject(Long projectId, Long taskId) {
        ModelTask task = requireParseWaiting(projectId, taskId);
        DrawingParseProposal proposal = readProposal(task.getProposalJson());
        markFilesByIds(proposal.getFileIds(), "uploaded");
        modelTaskService.updateStatus(projectId, taskId, "failed");
        drawingMemoryService.submitParseRejected(projectId, taskId);
        modelTaskService.appendEvent(taskId, "用户放弃写入，账本未改。");
        notifyModelingParent(projectId, taskId, "failed");
        return modelTaskService.get(projectId, taskId);
    }

    private ModelTask requireParseWaiting(Long projectId, Long taskId) {
        ModelTaskVO vo = modelTaskService.get(projectId, taskId);
        ModelTask task = vo.getTask();
        if (!TaskKinds.isDrawing(task.getKind())) {
            throw new BusinessException("不是图纸识别任务");
        }
        if (!"waiting".equals(task.getStatus()) || !StringUtils.hasText(task.getProposalJson())) {
            throw new BusinessException("该任务没有待确认的识别结果");
        }
        return task;
    }

    private ModelTaskVO fail(Long projectId, Long taskId, List<ProjectFile> pdfs, String reason) {
        if (pdfs != null && !pdfs.isEmpty()) {
            markFiles(pdfs, "failed");
        }
        modelTaskService.updateStatus(projectId, taskId, "failed");
        modelTaskService.appendEvent(taskId, reason);
        notifyModelingParent(projectId, taskId, "failed");
        return modelTaskService.get(projectId, taskId);
    }

    /** 自动子识图结束时告诉父建模：确认后续跑、放弃则父失败。 */
    private void notifyModelingParent(Long projectId, Long taskId, String childStatus) {
        try {
            modelingService.getObject().onDrawingChildSettled(projectId, taskId, childStatus);
        } catch (Exception e) {
            log.warn("通知父建模失败 projectId={} taskId={}: {}", projectId, taskId, e.getMessage());
        }
    }

    /** 只填空字段；已有且一致的列不动。调用前必须已经判定无冲突。 */
    private boolean fillBlanks(Long projectId, ProjectVO current, DrawingParseProposal proposal) {
        boolean wrote = false;
        if (hasProposalUnits(proposal) && !hasUnits(current)) {
            projectService.replaceUnits(projectId, toBatch(proposal));
            wrote = true;
        } else if (hasUnits(current) && hasProposalSupports(proposal) && !hasAnyColumnHeight(current)) {
            // 跨径已在账本：只空写墩柱，不改 spans_m
            projectService.replaceUnits(projectId, toSupportMergeBatch(current, proposal));
            wrote = true;
        }
        if (applyProjectFields(projectId, current, proposal, false)) {
            wrote = true;
        }
        if (applyExtracts(projectId, proposal, false)) {
            wrote = true;
        }
        return wrote;
    }

    /** 确认后覆盖提案里给出的字段。补充识别且账本已有柱高时不整表替换；柱高全空时允许空写墩柱。 */
    private void overwriteLedger(Long projectId, DrawingParseProposal proposal, ModelTask task) {
        boolean supplement = TaskKinds.DRAWING_SUPPLEMENT.equals(task.getKind());
        ProjectVO current = projectService.getById(projectId);
        boolean wroteUnits = false;
        if (supplement && hasUnits(current) && hasAnyColumnHeight(current)) {
            wroteUnits = false;
        } else if (hasProposalUnits(proposal) && proposalHasSpans(proposal)) {
            projectService.replaceUnits(projectId, toBatch(proposal));
            wroteUnits = true;
        } else if (hasProposalSupports(proposal) && hasUnits(current)) {
            projectService.replaceUnits(projectId, toSupportMergeBatch(current, proposal));
            wroteUnits = true;
        }
        current = projectService.getById(projectId);
        applyProjectFields(projectId, current, proposal, true);
        applyExtracts(projectId, proposal, true);
        projectService.mergeDrawingMeta(projectId, toMetaPatch(proposal, wroteUnits));
    }

    /**
     * 补充识别只为补硬缺口。账本已有的主梁/跨径/墩高、以及已有的铺装/二期袋项若被模型顺带再读，从提案里拿掉，
     * 避免「整份冲突」挡住桩径等真正要写入的 extracts，也避免确认时冲掉墩高。
     */
    private void dropIncidentalProposalFields(ProjectVO current, DrawingParseProposal proposal) {
        if (StringUtils.hasText(current.getGirderType())) {
            proposal.setGirderType(null);
        }
        if (StringUtils.hasText(current.getLayoutType())) {
            proposal.setLayoutType(null);
        }
        if (StringUtils.hasText(current.getMaterial())) {
            proposal.setMaterial(null);
        }
        if (StringUtils.hasText(current.getCode())) {
            proposal.setCode(null);
        }
        if (StringUtils.hasText(current.getRegion())) {
            proposal.setRegion(null);
        }
        if (hasUnits(current) && hasAnyColumnHeight(current)) {
            proposal.setUnits(new ArrayList<>());
        } else if (hasUnits(current) && proposal.getUnits() != null) {
            // 联在、柱高全空：留下墩柱供空写，跨径仍以账本为准
            for (DrawingParseUnit unit : proposal.getUnits()) {
                unit.setSpansM(null);
            }
        }
        dropIncidentalExtracts(current, proposal);
    }

    /** 账本已有的铺装/二期/湿接缝不要拿来打架；桩径、支座布置仍留在提案里。 */
    private void dropIncidentalExtracts(ProjectVO current, DrawingParseProposal proposal) {
        if (proposal.getExtracts() == null || proposal.getExtracts().isEmpty()) {
            return;
        }
        Set<String> existingKeys = new HashSet<>();
        Set<String> existingLabels = new HashSet<>();
        if (current.getParams() != null) {
            for (ProjectParam row : current.getParams()) {
                if (row.getParamKey() != null) {
                    existingKeys.add(row.getParamKey().toLowerCase(Locale.ROOT));
                }
                if (StringUtils.hasText(row.getLabel())) {
                    existingLabels.add(row.getLabel().trim());
                }
            }
        }
        List<DrawingExtractItem> kept = new ArrayList<>();
        for (DrawingExtractItem extract : proposal.getExtracts()) {
            String key = extract.getKey() == null ? "" : extract.getKey().trim();
            String label = extract.getLabel() == null ? "" : extract.getLabel().trim();
            boolean already = (!key.isEmpty() && existingKeys.contains(key.toLowerCase(Locale.ROOT)))
                    || (!label.isEmpty() && existingLabels.contains(label));
            if (already && incidentalExtract(key, label)) {
                continue;
            }
            kept.add(extract);
        }
        proposal.setExtracts(kept);
    }

    private boolean incidentalExtract(String key, String label) {
        String blob = (key + " " + label).toLowerCase(Locale.ROOT);
        return blob.contains("铺装") || blob.contains("沥青") || blob.contains("sdl")
                || blob.contains("barrier") || blob.contains("二期")
                || blob.contains("wetjoint") || blob.contains("湿接缝")
                || blob.contains("pave") || blob.contains("asphalt");
    }

    private boolean proposalHasSpans(DrawingParseProposal proposal) {
        if (proposal.getUnits() == null) {
            return false;
        }
        for (DrawingParseUnit unit : proposal.getUnits()) {
            if (unit.getSpansM() != null && !unit.getSpansM().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 中间墩每根柱都有高度则视为齐；没有中间墩时桥台柱高也算。 */
    private boolean hasCompleteColumnHeights(ProjectVO project) {
        if (project.getUnits() == null || project.getUnits().isEmpty()) {
            return false;
        }
        List<ProjectUnitSupport> piers = new ArrayList<>();
        List<ProjectUnitSupport> all = new ArrayList<>();
        for (ProjectUnit unit : project.getUnits()) {
            if (unit.getSupports() == null) {
                continue;
            }
            for (ProjectUnitSupport support : unit.getSupports()) {
                all.add(support);
                if (!isAbutmentKind(support.getKind())) {
                    piers.add(support);
                }
            }
        }
        List<ProjectUnitSupport> targets = piers.isEmpty() ? all : piers;
        if (targets.isEmpty()) {
            return false;
        }
        for (ProjectUnitSupport support : targets) {
            if (!supportHasHeights(support)) {
                return false;
            }
        }
        return true;
    }

    private boolean isAbutmentKind(String kind) {
        if (!StringUtils.hasText(kind)) {
            return false;
        }
        String text = kind.trim();
        return "abutment".equalsIgnoreCase(text) || text.contains("台");
    }

    private boolean supportHasHeights(ProjectUnitSupport support) {
        if (support.getColumns() == null || support.getColumns().isEmpty()) {
            return false;
        }
        for (ProjectUnitColumn column : support.getColumns()) {
            if (column.getHeightM() == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把识图结果写入 project 行。{@code overwrite=false} 只补空；{@code true} 覆盖已有值。
     * 不改幅面、名称、通车日、规范策略——那些不是总布置图上的跨径几何。
     */
    private boolean applyProjectFields(Long projectId, ProjectVO current, DrawingParseProposal proposal, boolean overwrite) {
        ProjectUpdateRequest update = new ProjectUpdateRequest();
        boolean any = false;
        if (shouldWrite(current.getGirderType(), proposal.getGirderType(), overwrite)) {
            update.setGirderType(clip(proposal.getGirderType(), 64));
            any = true;
        }
        if (shouldWrite(current.getLayoutType(), proposal.getLayoutType(), overwrite)) {
            update.setLayoutType(clip(proposal.getLayoutType(), 64));
            any = true;
        }
        if (shouldWrite(current.getMaterial(), proposal.getMaterial(), overwrite)) {
            update.setMaterial(clip(proposal.getMaterial(), 32));
            any = true;
        }
        if (shouldWrite(current.getCode(), proposal.getCode(), overwrite)) {
            update.setCode(clip(proposal.getCode(), 64));
            any = true;
        }
        if (shouldWrite(current.getRegion(), proposal.getRegion(), overwrite)) {
            update.setRegion(clip(proposal.getRegion(), 64));
            any = true;
        }
        if (!any) {
            return false;
        }
        projectService.updateFromDrawing(projectId, update);
        return true;
    }

    private boolean shouldWrite(String existing, String proposed, boolean overwrite) {
        if (!StringUtils.hasText(proposed)) {
            return false;
        }
        return overwrite || !StringUtils.hasText(existing);
    }

    private String clip(String value, int max) {
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }

    private List<LedgerConflictItem> detectConflicts(ProjectVO current, DrawingParseProposal proposal) {
        List<LedgerConflictItem> conflicts = new ArrayList<>();
        if (hasUnits(current) && proposalHasSpans(proposal) && !sameUnits(current.getUnits(), proposal.getUnits())) {
            conflicts.add(conflict("联跨径", formatUnits(current.getUnits()), formatProposalUnits(proposal.getUnits())));
        }
        if (hasAnyColumnHeight(current) && hasProposalSupports(proposal)
                && !sameSupports(current.getUnits(), proposal.getUnits())) {
            conflicts.add(conflict("墩柱高", formatSupports(current.getUnits()),
                    formatProposalSupports(proposal.getUnits())));
        }
        conflicts.addAll(detectParamConflicts(current, proposal));
        if (StringUtils.hasText(current.getGirderType())
                && StringUtils.hasText(proposal.getGirderType())
                && !trimEquals(current.getGirderType(), proposal.getGirderType())) {
            conflicts.add(conflict("主梁形式", current.getGirderType(), proposal.getGirderType()));
        }
        if (StringUtils.hasText(current.getLayoutType())
                && StringUtils.hasText(proposal.getLayoutType())
                && !trimEquals(current.getLayoutType(), proposal.getLayoutType())) {
            conflicts.add(conflict("结构形式", current.getLayoutType(), proposal.getLayoutType()));
        }
        if (StringUtils.hasText(current.getMaterial())
                && StringUtils.hasText(proposal.getMaterial())
                && !trimEquals(current.getMaterial(), proposal.getMaterial())) {
            conflicts.add(conflict("材料", current.getMaterial(), proposal.getMaterial()));
        }
        if (StringUtils.hasText(current.getCode())
                && StringUtils.hasText(proposal.getCode())
                && !trimEquals(current.getCode(), proposal.getCode())) {
            conflicts.add(conflict("标号", current.getCode(), proposal.getCode()));
        }
        if (StringUtils.hasText(current.getRegion())
                && StringUtils.hasText(proposal.getRegion())
                && !trimEquals(current.getRegion(), proposal.getRegion())) {
            conflicts.add(conflict("地区", current.getRegion(), proposal.getRegion()));
        }
        return conflicts;
    }

    private LedgerConflictItem conflict(String field, String before, String after) {
        LedgerConflictItem item = new LedgerConflictItem();
        item.setField(field);
        item.setBefore(before);
        item.setAfter(after);
        return item;
    }

    private String formatConflicts(List<LedgerConflictItem> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (LedgerConflictItem item : conflicts) {
            lines.add(item.getField() + "：覆盖前「" + orDash(item.getBefore())
                    + "」→ 覆盖后「" + orDash(item.getAfter()) + "」");
        }
        return String.join("；", lines);
    }

    private String validateProposal(DrawingParseProposal proposal) {
        if (proposal == null) {
            return "识别提案损坏";
        }
        if (proposal.getUnits() == null) {
            proposal.setUnits(new ArrayList<>());
        }
        if (proposal.getUnits().isEmpty()) {
            return null;
        }
        for (DrawingParseUnit unit : proposal.getUnits()) {
            if (unit.getSeq() == null || unit.getSeq() < 1) {
                return "识别提案联序号不合法";
            }
            // 补充识别常只改参数袋，联上跨径已从提案拿掉；没有跨径不视为整份非法
            if (unit.getSpansM() == null || unit.getSpansM().isEmpty()) {
                continue;
            }
            String supportError = validateSupports(unit);
            if (supportError != null) {
                return supportError;
            }
        }
        long distinct = proposal.getUnits().stream().map(DrawingParseUnit::getSeq).distinct().count();
        if (distinct != proposal.getUnits().size()) {
            return "识别提案联序号重复";
        }
        return null;
    }

    private DrawingParseProposal toProposal(PythonParseResponse response, List<ProjectFile> pdfs) {
        DrawingParseProposal proposal = new DrawingParseProposal();
        proposal.setGirderType(blankToNull(response.getGirderType()));
        proposal.setLayoutType(blankToNull(response.getLayoutType()));
        proposal.setMaterial(blankToNull(response.getMaterial()));
        proposal.setCode(blankToNull(response.getCode()));
        proposal.setRegion(blankToNull(response.getRegion()));
        proposal.setUnits(response.getUnits() == null ? new ArrayList<>() : response.getUnits());
        proposal.setExtracts(mergeExtracts(response));
        proposal.setNote(response.getNote());
        proposal.setGaps(response.getGaps() == null ? new ArrayList<>() : response.getGaps());
        proposal.setHasLayoutPages(response.getHasLayoutPages());
        proposal.setFileIds(pdfs.stream().map(ProjectFile::getId).toList());
        return proposal;
    }

    private ProjectUnitBatchRequest toBatch(DrawingParseProposal proposal) {
        ProjectUnitBatchRequest batch = new ProjectUnitBatchRequest();
        List<ProjectUnitItemRequest> items = new ArrayList<>();
        List<DrawingParseUnit> units = new ArrayList<>(proposal.getUnits());
        units.sort(Comparator.comparing(DrawingParseUnit::getSeq));
        for (DrawingParseUnit unit : units) {
            ProjectUnitItemRequest item = new ProjectUnitItemRequest();
            item.setSeq(unit.getSeq());
            item.setSpansM(unit.getSpansM());
            item.setSource("drawing");
            item.setSupports(toSupportItems(unit.getSupports()));
            items.add(item);
        }
        batch.setUnits(items);
        return batch;
    }

    private boolean hasUnits(ProjectVO project) {
        return project.getUnits() != null && !project.getUnits().isEmpty();
    }

    private boolean hasProposalUnits(DrawingParseProposal proposal) {
        return proposal.getUnits() != null && !proposal.getUnits().isEmpty();
    }

    private boolean sameUnits(List<ProjectUnit> existing, List<DrawingParseUnit> proposed) {
        if (existing.size() != proposed.size()) {
            return false;
        }
        List<ProjectUnit> left = new ArrayList<>(existing);
        left.sort(Comparator.comparing(ProjectUnit::getSeq));
        List<DrawingParseUnit> right = new ArrayList<>(proposed);
        right.sort(Comparator.comparing(DrawingParseUnit::getSeq));
        for (int i = 0; i < left.size(); i++) {
            if (!Objects.equals(left.get(i).getSeq(), right.get(i).getSeq())) {
                return false;
            }
            if (!sameSpans(left.get(i).getSpansM(), right.get(i).getSpansM())) {
                return false;
            }
        }
        return true;
    }

    private boolean sameSpans(List<BigDecimal> left, List<BigDecimal> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i).compareTo(right.get(i)) != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean trimEquals(String a, String b) {
        return a.trim().equals(b.trim());
    }

    private void markFiles(List<ProjectFile> files, String status) {
        markFilesByIds(files.stream().map(ProjectFile::getId).toList(), status);
    }

    private void markFilesByIds(List<Long> ids, String status) {
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            ProjectFile patch = new ProjectFile();
            patch.setId(id);
            patch.setParseStatus(status);
            projectFileMapper.updateById(patch);
        }
    }

    private boolean isPdf(ProjectFile file) {
        String name = file.getOriginalName() == null ? "" : file.getOriginalName().toLowerCase(Locale.ROOT);
        String mime = file.getMimeType() == null ? "" : file.getMimeType().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || mime.contains("pdf");
    }

    private boolean isCad(ProjectFile file) {
        String name = file.getOriginalName() == null ? "" : file.getOriginalName().toLowerCase(Locale.ROOT);
        return name.endsWith(".dwg") || name.endsWith(".dxf");
    }

    private String writeJson(DrawingParseProposal proposal) {
        try {
            return JSON.writeValueAsString(proposal);
        } catch (JacksonException e) {
            throw new BusinessException("识别提案序列化失败");
        }
    }

    private DrawingParseProposal readProposal(String json) {
        try {
            return JSON.readValue(json, DrawingParseProposal.class);
        } catch (JacksonException e) {
            throw new BusinessException("识别提案损坏，无法确认");
        }
    }

    private String formatProposal(DrawingParseProposal proposal) {
        return " 标号=" + orDash(proposal.getCode())
                + " 主梁=" + orDash(proposal.getGirderType())
                + " 结构=" + orDash(proposal.getLayoutType())
                + " 材料=" + orDash(proposal.getMaterial())
                + " 地区=" + orDash(proposal.getRegion())
                + " 跨径=" + formatProposalUnits(proposal.getUnits())
                + " 墩柱=" + formatProposalSupports(proposal.getUnits());
    }

    private String orDash(String value) {
        return StringUtils.hasText(value) ? value : "未给";
    }

    private String formatProposalUnits(List<DrawingParseUnit> units) {
        if (units == null || units.isEmpty()) {
            return "未给";
        }
        return units.stream()
                .sorted(Comparator.comparing(DrawingParseUnit::getSeq))
                .map(unit -> "第" + unit.getSeq() + "联[" + joinSpans(unit.getSpansM()) + "]")
                .collect(Collectors.joining(","));
    }

    private String formatUnits(List<ProjectUnit> units) {
        return units.stream()
                .sorted(Comparator.comparing(ProjectUnit::getSeq))
                .map(unit -> "第" + unit.getSeq() + "联[" + joinSpans(unit.getSpansM()) + "]")
                .collect(Collectors.joining(","));
    }

    private String joinSpans(List<BigDecimal> spans) {
        if (spans == null) {
            return "";
        }
        return spans.stream().map(BigDecimal::toPlainString).collect(Collectors.joining("+"));
    }

    private String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 把本次识图读到的对照值、来源和缺口打成 field_meta 补丁。
     * 账本列本身已由 updateFromDrawing / replaceUnits 写过。
     */
    private ProjectFieldMeta toMetaPatch(DrawingParseProposal proposal, boolean wroteUnits) {
        ProjectFieldMeta patch = new ProjectFieldMeta();
        patch.setCode(drawingValueOf(proposal.getCode()));
        patch.setGirderType(drawingValueOf(proposal.getGirderType()));
        patch.setLayoutType(drawingValueOf(proposal.getLayoutType()));
        patch.setMaterial(drawingValueOf(proposal.getMaterial()));
        patch.setRegion(drawingValueOf(proposal.getRegion()));
        if (hasProposalUnits(proposal)) {
            FieldProvenance spans = new FieldProvenance();
            spans.setDrawingValue(canonicalSpans(proposal.getUnits()));
            if (wroteUnits) {
                spans.setSource("drawing");
            }
            patch.setSpansM(spans);
        }
        patch.setGaps(proposal.getGaps() == null ? new ArrayList<>() : proposal.getGaps());
        patch.setHasLayoutPages(proposal.getHasLayoutPages());
        return patch;
    }

    private FieldProvenance drawingValueOf(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        FieldProvenance node = new FieldProvenance();
        node.setDrawingValue(value.trim());
        return node;
    }

    private String canonicalSpans(List<DrawingParseUnit> units) {
        return units.stream()
                .sorted(Comparator.comparing(DrawingParseUnit::getSeq))
                .map(unit -> joinSpans(unit.getSpansM()))
                .collect(Collectors.joining(" | "));
    }

    private String formatGaps(DrawingParseProposal proposal) {
        List<String> gaps = proposal.getGaps() == null ? List.of() : proposal.getGaps();
        boolean missingLayout = Boolean.FALSE.equals(proposal.getHasLayoutPages()) || gaps.contains("missing_layout");
        List<String> lines = new ArrayList<>();
        if (missingLayout && !hasProposalUnits(proposal)) {
            lines.add("缺总布置/立面图纸：请到图纸页补充上传后再识别。");
        } else if (gaps.contains("spansM") && !hasProposalUnits(proposal)) {
            lines.add("有布置图但未读出跨径：请手改概览或在问询说明。");
        }
        if (gaps.contains("girderType") && !StringUtils.hasText(proposal.getGirderType())) {
            lines.add("未读出主梁形式。");
        }
        if (gaps.contains("layoutType") && !StringUtils.hasText(proposal.getLayoutType())) {
            lines.add("未读出结构形式。");
        }
        if (gaps.contains("material") && !StringUtils.hasText(proposal.getMaterial())) {
            lines.add("未读出材料。");
        }
        if (lines.isEmpty()) {
            return null;
        }
        return "完成但有缺口。" + String.join(" ", lines);
    }

    private String validateSupports(DrawingParseUnit unit) {
        if (unit.getSupports() == null || unit.getSupports().isEmpty()) {
            return null;
        }
        Set<Integer> seqs = new HashSet<>();
        int fallback = 0;
        for (DrawingParseSupport support : unit.getSupports()) {
            if (support.getSeq() == null) {
                support.setSeq(fallback);
            }
            fallback++;
            if (!seqs.add(support.getSeq())) {
                return "识别提案第 " + unit.getSeq() + " 联墩台序号重复";
            }
            if (support.getColumns() == null) {
                continue;
            }
            Set<Integer> colSeqs = new HashSet<>();
            int colFallback = 1;
            for (DrawingParseColumn column : support.getColumns()) {
                if (column.getSeq() == null) {
                    column.setSeq(colFallback);
                }
                colFallback++;
                if (!colSeqs.add(column.getSeq())) {
                    return "识别提案第 " + unit.getSeq() + " 联墩台 " + support.getSeq() + " 柱序号重复";
                }
            }
        }
        return null;
    }

    /**
     * 账本已有跨径时，用提案墩柱覆盖到对应联 seq，跨径仍用账本。
     */
    private ProjectUnitBatchRequest toSupportMergeBatch(ProjectVO current, DrawingParseProposal proposal) {
        Map<Integer, DrawingParseUnit> bySeq = new HashMap<>();
        if (proposal.getUnits() != null) {
            for (DrawingParseUnit unit : proposal.getUnits()) {
                bySeq.put(unit.getSeq(), unit);
            }
        }
        ProjectUnitBatchRequest batch = new ProjectUnitBatchRequest();
        List<ProjectUnitItemRequest> items = new ArrayList<>();
        for (ProjectUnit unit : current.getUnits()) {
            ProjectUnitItemRequest item = new ProjectUnitItemRequest();
            item.setSeq(unit.getSeq());
            item.setSpansM(unit.getSpansM());
            item.setSource(unit.getSource());
            DrawingParseUnit proposed = bySeq.get(unit.getSeq());
            if (proposed != null && proposed.getSupports() != null) {
                item.setSupports(toSupportItems(proposed.getSupports()));
            } else {
                item.setSupports(null);
            }
            items.add(item);
        }
        batch.setUnits(items);
        return batch;
    }

    private List<ProjectUnitSupportItemRequest> toSupportItems(List<DrawingParseSupport> supports) {
        if (supports == null || supports.isEmpty()) {
            return null;
        }
        List<ProjectUnitSupportItemRequest> out = new ArrayList<>();
        int fallback = 0;
        for (DrawingParseSupport support : supports) {
            ProjectUnitSupportItemRequest item = new ProjectUnitSupportItemRequest();
            item.setSeq(support.getSeq() == null ? fallback : support.getSeq());
            item.setCode(support.getCode());
            item.setKind(support.getKind());
            item.setSource("drawing");
            if (support.getColumns() != null) {
                List<ProjectUnitColumnItemRequest> columns = new ArrayList<>();
                int colFallback = 1;
                for (DrawingParseColumn column : support.getColumns()) {
                    ProjectUnitColumnItemRequest col = new ProjectUnitColumnItemRequest();
                    col.setSeq(column.getSeq() == null ? colFallback : column.getSeq());
                    col.setSide(column.getSide());
                    col.setHeightM(column.getHeightM());
                    col.setSource("drawing");
                    columns.add(col);
                    colFallback++;
                }
                item.setColumns(columns);
            }
            out.add(item);
            fallback++;
        }
        return out;
    }

    private boolean hasProposalSupports(DrawingParseProposal proposal) {
        if (proposal.getUnits() == null) {
            return false;
        }
        for (DrawingParseUnit unit : proposal.getUnits()) {
            if (unit.getSupports() != null && !unit.getSupports().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyColumnHeight(ProjectVO project) {
        if (project.getUnits() == null) {
            return false;
        }
        for (ProjectUnit unit : project.getUnits()) {
            if (unit.getSupports() == null) {
                continue;
            }
            for (ProjectUnitSupport support : unit.getSupports()) {
                if (support.getColumns() == null) {
                    continue;
                }
                for (ProjectUnitColumn column : support.getColumns()) {
                    if (column.getHeightM() != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean sameSupports(List<ProjectUnit> existing, List<DrawingParseUnit> proposed) {
        Map<String, String> left = supportFingerprint(existing);
        Map<String, String> right = proposalSupportFingerprint(proposed);
        return left.equals(right);
    }

    private Map<String, String> supportFingerprint(List<ProjectUnit> units) {
        Map<String, String> out = new HashMap<>();
        if (units == null) {
            return out;
        }
        for (ProjectUnit unit : units) {
            if (unit.getSupports() == null) {
                continue;
            }
            for (ProjectUnitSupport support : unit.getSupports()) {
                if (support.getColumns() == null) {
                    continue;
                }
                for (ProjectUnitColumn column : support.getColumns()) {
                    if (column.getHeightM() == null) {
                        continue;
                    }
                    String key = unit.getSeq() + "/" + support.getSeq() + "/" + column.getSeq();
                    out.put(key, column.getHeightM().stripTrailingZeros().toPlainString());
                }
            }
        }
        return out;
    }

    private Map<String, String> proposalSupportFingerprint(List<DrawingParseUnit> units) {
        Map<String, String> out = new HashMap<>();
        if (units == null) {
            return out;
        }
        for (DrawingParseUnit unit : units) {
            if (unit.getSupports() == null) {
                continue;
            }
            for (DrawingParseSupport support : unit.getSupports()) {
                if (support.getColumns() == null) {
                    continue;
                }
                for (DrawingParseColumn column : support.getColumns()) {
                    if (column.getHeightM() == null) {
                        continue;
                    }
                    String key = unit.getSeq() + "/" + support.getSeq() + "/" + column.getSeq();
                    out.put(key, column.getHeightM().stripTrailingZeros().toPlainString());
                }
            }
        }
        return out;
    }

    private List<LedgerConflictItem> detectParamConflicts(ProjectVO current, DrawingParseProposal proposal) {
        List<LedgerConflictItem> conflicts = new ArrayList<>();
        List<DrawingExtractItem> extracts = proposal.getExtracts();
        if (extracts == null || extracts.isEmpty() || current.getParams() == null) {
            return conflicts;
        }
        Map<String, ProjectParam> byKey = new HashMap<>();
        Map<String, ProjectParam> byLabel = new HashMap<>();
        for (ProjectParam row : current.getParams()) {
            byKey.put(row.getParamKey().toLowerCase(Locale.ROOT), row);
            byLabel.put(row.getLabel().trim(), row);
        }
        for (DrawingExtractItem extract : extracts) {
            String key = extract.getKey() == null ? "" : extract.getKey().trim();
            String label = extract.getLabel() == null ? "" : extract.getLabel().trim();
            if (LedgerParamRules.reservedKey(key) || LedgerParamRules.reservedLabel(label)) {
                continue;
            }
            String value = stringifyExtract(extract.getValue());
            if (!StringUtils.hasText(value)) {
                continue;
            }
            ProjectParam found = key.isEmpty() ? null : byKey.get(key.toLowerCase(Locale.ROOT));
            if (found == null && !label.isEmpty()) {
                found = byLabel.get(label);
            }
            if (found != null && !sameText(found.getValueText(), value)) {
                String name = label.isEmpty() ? key : label;
                conflicts.add(conflict(name, found.getValueText(), value));
            }
        }
        return conflicts;
    }

    private boolean applyExtracts(Long projectId, DrawingParseProposal proposal, boolean overwrite) {
        List<ProjectParamItemRequest> items = new ArrayList<>();
        if (proposal.getExtracts() == null) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (DrawingExtractItem extract : proposal.getExtracts()) {
            String key = extract.getKey() == null ? "" : extract.getKey().trim();
            String label = extract.getLabel() == null ? "" : extract.getLabel().trim();
            if (key.isEmpty() && label.isEmpty()) {
                continue;
            }
            if (LedgerParamRules.reservedKey(key) || LedgerParamRules.reservedLabel(label)) {
                continue;
            }
            String value = stringifyExtract(extract.getValue());
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String bagKey = LedgerParamRules.canonicalBagKey(
                    key.isEmpty() ? label.replaceAll("\\s+", "") : key, label);
            if (bagKey.isEmpty() || !seen.add(bagKey.toLowerCase(Locale.ROOT))) {
                continue;
            }
            ProjectParamItemRequest item = new ProjectParamItemRequest();
            item.setParamKey(bagKey.length() > 64 ? bagKey.substring(0, 64) : bagKey);
            item.setLabel(label.isEmpty() ? bagKey : label);
            item.setValueText(value);
            item.setUnit(extract.getUnit());
            item.setSource("drawing");
            items.add(item);
        }
        return projectService.upsertParamsFromDrawing(projectId, items, overwrite);
    }

    @SuppressWarnings("unchecked")
    private List<DrawingExtractItem> mergeExtracts(PythonParseResponse response) {
        if (response.getExtracts() != null && !response.getExtracts().isEmpty()) {
            return response.getExtracts();
        }
        if (response.getPageMap() == null) {
            return new ArrayList<>();
        }
        Object raw = response.getPageMap().get("extracts");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return new ArrayList<>();
        }
        List<DrawingExtractItem> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            DrawingExtractItem extract = new DrawingExtractItem();
            extract.setKey(map.get("key") == null ? null : String.valueOf(map.get("key")));
            extract.setLabel(map.get("label") == null ? null : String.valueOf(map.get("label")));
            extract.setValue(map.get("value"));
            extract.setUnit(map.get("unit") == null ? null : String.valueOf(map.get("unit")));
            extract.setNote(map.get("note") == null ? null : String.valueOf(map.get("note")));
            Object page = map.get("page");
            if (page instanceof Number number) {
                extract.setPage(number.intValue());
            }
            out.add(extract);
        }
        return out;
    }

    private String stringifyExtract(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros().toPlainString();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private boolean sameText(String left, String right) {
        String a = blankToNull(left);
        String b = blankToNull(right);
        if (Objects.equals(a, b)) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return normalizeMeasure(a).equalsIgnoreCase(normalizeMeasure(b));
    }

    /** 垫石「300×400×50cm」与「300×400×50」视为同一值，避免只差单位就整份卡住。 */
    private String normalizeMeasure(String raw) {
        String text = raw.trim().replace(" ", "").replace("×", "x").replace("*", "x");
        text = text.replace("厘米", "").replace("毫米", "");
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.endsWith("cm") || lower.endsWith("mm")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }

    private String formatSupports(List<ProjectUnit> units) {
        if (units == null || units.isEmpty()) {
            return "未给";
        }
        return units.stream()
                .sorted(Comparator.comparing(ProjectUnit::getSeq))
                .map(unit -> "第" + unit.getSeq() + "联[" + joinExistingSupports(unit) + "]")
                .collect(Collectors.joining(","));
    }

    private String formatProposalSupports(List<DrawingParseUnit> units) {
        if (units == null || units.isEmpty()) {
            return "未给";
        }
        boolean any = units.stream().anyMatch(unit -> unit.getSupports() != null && !unit.getSupports().isEmpty());
        if (!any) {
            return "未给";
        }
        return units.stream()
                .sorted(Comparator.comparing(DrawingParseUnit::getSeq))
                .map(unit -> "第" + unit.getSeq() + "联[" + joinProposalSupports(unit) + "]")
                .collect(Collectors.joining(","));
    }

    private String joinExistingSupports(ProjectUnit unit) {
        if (unit.getSupports() == null || unit.getSupports().isEmpty()) {
            return "无墩柱";
        }
        return unit.getSupports().stream()
                .sorted(Comparator.comparing(ProjectUnitSupport::getSeq))
                .map(support -> {
                    String code = StringUtils.hasText(support.getCode()) ? support.getCode() : ("#" + support.getSeq());
                    String cols = support.getColumns() == null ? "" : support.getColumns().stream()
                            .map(col -> (col.getHeightM() == null ? "?" : col.getHeightM().toPlainString()) + "m")
                            .collect(Collectors.joining("/"));
                    return code + (cols.isEmpty() ? "" : ":" + cols);
                })
                .collect(Collectors.joining(";"));
    }

    private String joinProposalSupports(DrawingParseUnit unit) {
        if (unit.getSupports() == null || unit.getSupports().isEmpty()) {
            return "无墩柱";
        }
        return unit.getSupports().stream()
                .sorted(Comparator.comparing(s -> s.getSeq() == null ? 0 : s.getSeq()))
                .map(support -> {
                    String code = StringUtils.hasText(support.getCode())
                            ? support.getCode()
                            : ("#" + support.getSeq());
                    String cols = support.getColumns() == null ? "" : support.getColumns().stream()
                            .map(col -> (col.getHeightM() == null ? "?" : col.getHeightM().toPlainString()) + "m")
                            .collect(Collectors.joining("/"));
                    return code + (cols.isEmpty() ? "" : ":" + cols);
                })
                .collect(Collectors.joining(";"));
    }
}
