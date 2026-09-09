package myz.bridge_agent_demo.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.service.AgentJobLauncher;
import myz.bridge_agent_demo.service.DrawingParseService;
import myz.bridge_agent_demo.service.JobDispatchService;
import myz.bridge_agent_demo.service.KnowledgeEmbedRunner;
import myz.bridge_agent_demo.service.KnowledgeMergeRunner;
import myz.bridge_agent_demo.service.KnowledgeParseRunner;
import myz.bridge_agent_demo.service.KnowledgeSplitRunner;
import myz.bridge_agent_demo.service.ModelingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 后台跑识图 / 建模 / 知识作业。必须独立 Bean，否则 {@code @Async} 自调用不生效。
 * 跑完再泵对应车道，把 queued 顶上来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentJobLauncherImpl implements AgentJobLauncher {

    private final DrawingParseService drawingParseService;
    private final ModelingService modelingService;
    private final KnowledgeParseRunner knowledgeParseRunner;
    private final KnowledgeMergeRunner knowledgeMergeRunner;
    private final KnowledgeSplitRunner knowledgeSplitRunner;
    private final KnowledgeEmbedRunner knowledgeEmbedRunner;
    private final ObjectProvider<JobDispatchService> jobDispatchService;

    @Override
    @Async("drawingJobExecutor")
    public void runDrawing(Long projectId, Long taskId) {
        log.info("识图线程已接手 projectId={} taskId={}", projectId, taskId);
        try {
            drawingParseService.executeJob(projectId, taskId);
        } catch (Exception e) {
            log.warn("后台识图失败 projectId={} taskId={}: {}", projectId, taskId, e.getMessage());
        } finally {
            jobDispatchService.getObject().pumpDrawing();
        }
    }

    @Override
    @Async("sapJobExecutor")
    public void runModeling(Long projectId, Long taskId) {
        log.info("建模线程已接手 projectId={} taskId={}", projectId, taskId);
        try {
            modelingService.executeJob(projectId, taskId);
        } catch (Exception e) {
            log.warn("后台建模失败 projectId={} taskId={}: {}", projectId, taskId, e.getMessage());
        } finally {
            jobDispatchService.getObject().pumpModeling();
        }
    }

    @Override
    @Async("knowledgeJobExecutor")
    public void runKnowledgeParse(Long documentId, boolean force) {
        try {
            knowledgeParseRunner.execute(documentId, force);
        } catch (Exception e) {
            log.warn("后台知识解析失败 documentId={}: {}", documentId, e.getMessage());
        } finally {
            jobDispatchService.getObject().pumpKnowledge();
        }
    }

    @Override
    @Async("knowledgeJobExecutor")
    public void runKnowledgeMerge(Long documentId) {
        try {
            knowledgeMergeRunner.execute(documentId);
        } catch (Exception e) {
            log.warn("后台知识合并失败 documentId={}: {}", documentId, e.getMessage());
        } finally {
            jobDispatchService.getObject().pumpKnowledge();
        }
    }

    @Override
    @Async("knowledgeJobExecutor")
    public void runKnowledgeSplit(Long documentId) {
        try {
            knowledgeSplitRunner.execute(documentId);
        } catch (Exception e) {
            log.warn("后台知识分割失败 documentId={}: {}", documentId, e.getMessage());
        } finally {
            jobDispatchService.getObject().pumpKnowledge();
        }
    }

    @Override
    @Async("knowledgeJobExecutor")
    public void runKnowledgeEmbed(Long documentId) {
        try {
            knowledgeEmbedRunner.execute(documentId);
        } catch (Exception e) {
            log.warn("后台知识嵌入失败 documentId={}: {}", documentId, e.getMessage());
        } finally {
            jobDispatchService.getObject().pumpKnowledge();
        }
    }
}
