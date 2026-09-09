package myz.bridge_agent_demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.auth.UserContext;
import myz.bridge_agent_demo.entity.SysAudit;
import myz.bridge_agent_demo.mapper.SysAuditMapper;
import myz.bridge_agent_demo.service.AuditService;
import myz.bridge_agent_demo.service.PermissionService;
import myz.bridge_agent_demo.vo.AuditPageVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.stream.Collectors;

/**
 * 审计写入与超管查询。controller 不直接碰表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final SysAuditMapper auditMapper;
    private final PermissionService permissionService;

    @Override
    public void record(String action, boolean success, Long actorUserId, String actorUsername,
                       String targetType, Long targetId, String targetLabel, Long projectId,
                       String reason, String beforeText, String afterText) {
        try {
            UserContext.User ctx = UserContext.get();
            Long uid = actorUserId;
            String uname = actorUsername;
            if (uid == null && ctx != null) {
                uid = ctx.id();
            }
            if (!StringUtils.hasText(uname) && ctx != null) {
                uname = ctx.username();
            }
            if (!StringUtils.hasText(uname)) {
                uname = "-";
            }
            SysAudit row = new SysAudit();
            row.setActorUserId(uid);
            row.setActorUsername(trim(uname, 32));
            row.setAction(action);
            row.setTargetType(targetType);
            row.setTargetId(targetId);
            row.setTargetLabel(trim(targetLabel, 128));
            row.setProjectId(projectId);
            row.setSuccess(success);
            row.setReason(trim(reason, 128));
            row.setBeforeText(trim(beforeText, 128));
            row.setAfterText(trim(afterText, 128));
            fillClient(row);
            auditMapper.insert(row);
        } catch (Exception e) {
            log.warn("写审计失败 action={}：{}", action, e.getMessage());
        }
    }

    @Override
    public AuditPageVO list(String action, String actorUsername, long page, long size) {
        permissionService.requireSuper();
        long p = page < 1 ? 1 : page;
        long s = size < 1 || size > 100 ? 20 : size;
        LambdaQueryWrapper<SysAudit> q = Wrappers.lambdaQuery(SysAudit.class)
                .eq(StringUtils.hasText(action), SysAudit::getAction, action)
                .like(StringUtils.hasText(actorUsername), SysAudit::getActorUsername, actorUsername)
                .orderByDesc(SysAudit::getId);
        Page<SysAudit> mp = auditMapper.selectPage(new Page<>(p, s), q);
        AuditPageVO vo = new AuditPageVO();
        vo.setTotal(mp.getTotal());
        vo.setPage(mp.getCurrent());
        vo.setSize(mp.getSize());
        vo.setRecords(mp.getRecords().stream().map(this::toRow).collect(Collectors.toList()));
        return vo;
    }

    private AuditPageVO.Row toRow(SysAudit row) {
        AuditPageVO.Row vo = new AuditPageVO.Row();
        vo.setId(row.getId());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setActorUserId(row.getActorUserId());
        vo.setActorUsername(row.getActorUsername());
        vo.setAction(row.getAction());
        vo.setTargetType(row.getTargetType());
        vo.setTargetId(row.getTargetId());
        vo.setTargetLabel(row.getTargetLabel());
        vo.setProjectId(row.getProjectId());
        vo.setSuccess(row.getSuccess());
        vo.setReason(row.getReason());
        vo.setBeforeText(row.getBeforeText());
        vo.setAfterText(row.getAfterText());
        vo.setIp(row.getIp());
        return vo;
    }

    private void fillClient(SysAudit row) {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servlet)) {
            return;
        }
        HttpServletRequest req = servlet.getRequest();
        row.setIp(trim(req.getRemoteAddr(), 64));
        row.setUserAgent(trim(req.getHeader("User-Agent"), 256));
    }

    private static String trim(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String t = value.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
