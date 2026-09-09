package myz.bridge_agent_demo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myz.bridge_agent_demo.auth.UserContext;
import myz.bridge_agent_demo.common.Result;
import myz.bridge_agent_demo.entity.SysRole;
import myz.bridge_agent_demo.entity.SysUser;
import myz.bridge_agent_demo.mapper.SysRoleMapper;
import myz.bridge_agent_demo.mapper.SysUserMapper;
import myz.bridge_agent_demo.service.JwtService;
import myz.bridge_agent_demo.service.PermissionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 拦浏览器调的 {@code /api}：验 JWT 签名与 exp，再比对用户表 {@code token_version}。
 * 通过后写入 {@link UserContext}；请求结束必须清掉。
 * Spring→Python 不走这里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PermissionService permissionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // 跨域预检没有票，必须放行，否则直打 8080 会被拦
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            reject(response, "未登录或登录已失效");
            return false;
        }
        JwtService.Claims claims = jwtService.parse(header.substring(BEARER.length()).trim());
        if (claims == null) {
            reject(response, "未登录或登录已失效");
            return false;
        }
        SysUser user = sysUserMapper.selectById(claims.getUserId());
        int currentVer = user == null || user.getTokenVersion() == null ? -1 : user.getTokenVersion();
        if (user == null) {
            reject(response, "未登录或登录已失效");
            return false;
        }
        if (currentVer != claims.getVer()) {
            reject(response, "账号已在其他设备登录");
            return false;
        }
        if (!"enabled".equals(user.getStatus())) {
            reject(response, "未登录或登录已失效");
            return false;
        }
        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        UserContext.set(permissionService.toContext(user, role));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        UserContext.clear();
    }

    /** 401 + 统一 Result。version 对不上视为被新登录顶掉。 */
    private void reject(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.writeValueAsString(Result.error(msg)));
    }
}
