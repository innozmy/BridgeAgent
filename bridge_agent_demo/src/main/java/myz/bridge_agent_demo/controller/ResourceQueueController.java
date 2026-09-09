package myz.bridge_agent_demo.controller;

import lombok.RequiredArgsConstructor;
import myz.bridge_agent_demo.common.Result;
import myz.bridge_agent_demo.service.JobDispatchService;
import myz.bridge_agent_demo.service.PermissionService;
import myz.bridge_agent_demo.vo.ResourceQueueVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 资源队列：三车道占用。须登录；项目行按权限过滤。 */
@RestController
@RequestMapping("/api/resource-queue")
@RequiredArgsConstructor
public class ResourceQueueController {

    private final JobDispatchService jobDispatchService;
    private final PermissionService permissionService;

    @GetMapping
    public Result<ResourceQueueVO> snapshot() {
        permissionService.requireUser();
        return Result.success(jobDispatchService.snapshot());
    }
}
