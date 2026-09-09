package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.entity.KnowledgeDocument;
import myz.bridge_agent_demo.entity.ModelTask;
import myz.bridge_agent_demo.entity.ProjectFile;
import myz.bridge_agent_demo.mapper.KnowledgeDocumentMapper;
import myz.bridge_agent_demo.mapper.ModelTaskMapper;
import myz.bridge_agent_demo.mapper.ProjectFileMapper;
import myz.bridge_agent_demo.service.JobDispatchService;
import myz.bridge_agent_demo.service.KnowledgeDocumentWrites;
import myz.bridge_agent_demo.service.ModelTaskService;
import myz.bridge_agent_demo.service.TaskKinds;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Service：JVM 起来后回收上一进程留下的 running。
 * 线程池是内存账，库行仍进行中时调度只捞 queued，会一直卡在页面上。
 * 不自动重跑识图/建模/知识四步，避免对半截 Python 再打一枪。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanJobReaper {

    private static final String INTERRUPT = "服务重启，本枪中断。请重新同意任务卡或再点知识四步。";

    private final ModelTaskMapper taskMapper;
    private final ModelTaskService modelTaskService;
    private final ProjectFileMapper projectFileMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeDocumentWrites documentWrites;
    private final JobDispatchService jobDispatchService;

    /**
     * Spring 已可接请求后再收。{@code queued} / {@code waiting} / {@code proposed} 不动。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        int tasks = reapRunningTasks();
        int files = releaseParsingFiles();
        int docs = reapKnowledge();
        log.warn("启动回收孤儿作业：任务失败 {} 张，图纸 parsing 回退 {} 份，知识四步中断 {} 步",
                tasks, files, docs);
        jobDispatchService.pumpAll();
    }

    /** 识图与建模 running（含父+子同时 running）一律失败并写时间线。 */
    private int reapRunningTasks() {
        List<ModelTask> running = taskMapper.selectList(
                Wrappers.<ModelTask>lambdaQuery()
                        .eq(ModelTask::getStatus, "running")
                        .in(ModelTask::getKind,
                                TaskKinds.DRAWING_FULL,
                                TaskKinds.DRAWING_SUPPLEMENT,
                                "图纸识别",
                                TaskKinds.MODELING));
        int n = 0;
        for (ModelTask task : running) {
            if (modelTaskService.casStatus(task.getProjectId(), task.getId(), "running", "failed")) {
                modelTaskService.appendEvent(task.getId(), INTERRUPT);
                n++;
            }
        }
        return n;
    }

    /** 识图中途把图纸标成 parsing；进程没了要回到可再识。 */
    private int releaseParsingFiles() {
        return projectFileMapper.update(null, Wrappers.<ProjectFile>lambdaUpdate()
                .set(ProjectFile::getParseStatus, "uploaded")
                .eq(ProjectFile::getParseStatus, "parsing"));
    }

    private int reapKnowledge() {
        int n = 0;
        for (KnowledgeDocument doc : documentMapper.selectList(null)) {
            if ("parsing".equals(doc.getParseStatus())) {
                documentWrites.interruptParse(doc.getId());
                n++;
            }
            if ("merging".equals(doc.getMergeStatus())) {
                documentWrites.interruptMerge(doc.getId());
                n++;
            }
            if ("splitting".equals(doc.getSplitStatus())) {
                documentWrites.interruptSplit(doc.getId());
                n++;
            }
            if ("embedding".equals(doc.getEmbedStatus())) {
                documentWrites.interruptEmbed(doc.getId());
                n++;
            }
        }
        return n;
    }
}
