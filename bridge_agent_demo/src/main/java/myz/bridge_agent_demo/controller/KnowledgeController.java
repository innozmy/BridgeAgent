package myz.bridge_agent_demo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.common.Result;
import myz.bridge_agent_demo.entity.KnowledgeDocument;
import myz.bridge_agent_demo.service.KnowledgeService;
import myz.bridge_agent_demo.service.PermissionService;
import myz.bridge_agent_demo.vo.KnowledgeParseStartVO;
import myz.bridge_agent_demo.vo.KnowledgeUploadVO;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 公司知识总库。项目启用集在 {@link ProjectController} 的 /knowledge 接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge/documents")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final PermissionService permissionService;

    @GetMapping
    public Result<List<KnowledgeDocument>> list() {
        log.info("查询知识总库");
        return Result.success(knowledgeService.listDocuments());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeUploadVO> upload(@RequestParam String name,
                                            @RequestParam String category,
                                            @RequestParam(required = false) String familyCode,
                                            @RequestParam(required = false) String region,
                                            @RequestParam(required = false) String specialty,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveTo,
                                            @RequestParam("file") MultipartFile file) {
        log.info("上传知识文献 {}", name);
        permissionService.requireKnowledgeWrite();
        return Result.success(knowledgeService.upload(
                name, category, familyCode, region, specialty, effectiveFrom, effectiveTo, file));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        log.info("下载知识文件 {}", id);
        return knowledgeService.download(id);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除知识文献 {}", id);
        permissionService.requireKnowledgeWrite();
        knowledgeService.deleteDocument(id);
        return Result.success();
    }

    /**
     * 解析一本已上传的 PDF：后台 Unstructured + 附图千问，立刻返回。
     *
     * @param force 已解析完成时须确认后为 true
     */
    @PostMapping("/{id}/parse")
    public Result<KnowledgeParseStartVO> parse(@PathVariable Long id,
                                              @RequestParam(defaultValue = "false") boolean force) {
        log.info("解析知识文献 {} force={}", id, force);
        permissionService.requireKnowledgeWrite();
        return Result.success(knowledgeService.startParse(id, force));
    }

    /**
     * 合并已解析文献：后台只写 merge/，立刻返回。
     *
     * @param force 已合并完成时须确认后为 true
     */
    @PostMapping("/{id}/merge")
    public Result<KnowledgeParseStartVO> merge(@PathVariable Long id,
                                               @RequestParam(defaultValue = "false") boolean force) {
        log.info("合并知识文献 {} force={}", id, force);
        permissionService.requireKnowledgeWrite();
        return Result.success(knowledgeService.startMerge(id, force));
    }

    /**
     * 分割已合并文献：后台只写 split/，立刻返回。
     *
     * @param force 已分割完成时须确认后为 true
     */
    @PostMapping("/{id}/split")
    public Result<KnowledgeParseStartVO> split(@PathVariable Long id,
                                               @RequestParam(defaultValue = "false") boolean force) {
        log.info("分割知识文献 {} force={}", id, force);
        permissionService.requireKnowledgeWrite();
        return Result.success(knowledgeService.startSplit(id, force));
    }

    /**
     * 嵌入已分割文献：后台只写本机 Milvus，立刻返回。
     *
     * @param force 已嵌入完成时须确认后为 true
     */
    @PostMapping("/{id}/embed")
    public Result<KnowledgeParseStartVO> embed(@PathVariable Long id,
                                               @RequestParam(defaultValue = "false") boolean force) {
        log.info("嵌入知识文献 {} force={}", id, force);
        permissionService.requireKnowledgeWrite();
        return Result.success(knowledgeService.startEmbed(id, force));
    }
}
