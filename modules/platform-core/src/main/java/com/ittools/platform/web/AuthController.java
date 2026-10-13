package com.ittools.platform.web;

import com.ittools.platform.security.AppUserDetails;
import com.ittools.platform.security.LoginRequest;
import com.ittools.platform.security.MultiLoginAuthenticationProvider;
import com.ittools.platform.service.LookupService;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ittools.platform.security.LoginPolicy;

/**
 * BINDING Java 8 / Spring Boot 2.7 / Spring Security 5.x translation of the
 * task brief: javax.servlet.http.* (not jakarta.servlet.http.*) since Spring
 * Boot 2.7 runs on the Servlet 4 / javax.servlet API.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final MultiLoginAuthenticationProvider provider;
    private final LookupService lookups;
    private final List<LoginPolicy> policies;
    private final com.ittools.platform.service.LoginLogService loginLogs;
    private static final String LOG_ATTR = "ittools.loginLogId";
    private final com.ittools.platform.security.SessionTracker sessions;
    private final com.ittools.platform.settings.SystemSettingService settings;
    private final SecurityContextRepository repo = new HttpSessionSecurityContextRepository();

    public AuthController(MultiLoginAuthenticationProvider provider, LookupService lookups,
                          java.util.Optional<List<LoginPolicy>> policies, com.ittools.platform.service.LoginLogService loginLogs,
                          com.ittools.platform.security.SessionTracker sessions, com.ittools.platform.settings.SystemSettingService settings) {
        this.loginLogs = loginLogs;
        this.sessions = sessions;
        this.settings = settings;
        this.provider = provider;
        this.lookups = lookups;
        this.policies = policies.orElse(Collections.<LoginPolicy>emptyList());
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest req,
                                                    HttpServletRequest request, HttpServletResponse response) {
        AppUserDetails ud = provider.authenticate(req);
        String ip = ClientIp.of(request);
        for (LoginPolicy p : policies) {
            p.check(ud, ip);
        }
        // Session fixation: rotate the session id right after authentication
        // succeeds and before the SecurityContext is persisted, so a login
        // never reuses a pre-existing (potentially attacker-seeded) session.
        request.getSession(true);      // ensure a session exists
        request.changeSessionId();     // rotate id to prevent session fixation
        Authentication auth = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        repo.saveContext(ctx, request, response);
        request.getSession().setAttribute(LOG_ATTR, loginLogs.recordLogin(ud.userId(), ip));
        // 学生账号同一时间只允许一处登录：新登录让旧电脑下线（系统参数可关闭）
        if (ud.role() == com.ittools.platform.domain.Role.STUDENT && settings.getBool(com.ittools.platform.settings.SystemSettingService.SINGLE_LOGIN)) {
            for (HttpSession old : sessions.others(ud.userId(), request.getSession())) {
                try {
                    Object logId = old.getAttribute(LOG_ATTR);
                    if (logId instanceof Long) loginLogs.recordLogout((Long) logId);
                } catch (IllegalStateException ignored) {
                    // 旧会话已失效
                }
                sessions.kick(old);
            }
        }
        sessions.register(ud.userId(), request.getSession());
        return ApiResponse.ok(principalPayload(ud));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s != null) {
            Object logId = s.getAttribute(LOG_ATTR);
            if (logId instanceof Long) loginLogs.recordLogout((Long) logId);
            s.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal AppUserDetails ud) {
        // /api/auth/** is permitAll in SecurityConfig (so /login and /logout
        // are reachable anonymously), which means anonymous requests reach
        // this method too instead of being stopped by the entry point -
        // @AuthenticationPrincipal resolves to null for the anonymous
        // principal rather than throwing. Without this explicit check an
        // anonymous /me call would NPE (500) instead of the required 401, so
        // we raise the same AuthenticationException family the login path
        // uses; GlobalExceptionHandler turns it into a uniform 401 response.
        if (ud == null) {
            throw new InsufficientAuthenticationException("未登录");
        }
        return ApiResponse.ok(principalPayload(ud));
    }

    private Map<String, Object> principalPayload(AppUserDetails ud) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", ud.userId());
        m.put("name", ud.getUsername());
        m.put("displayName", ud.displayName());
        m.put("classId", ud.classId());
        m.put("role", ud.role().name());
        m.put("permissions", lookups.permissionCodesForRole(ud.role()));
        return m;
    }
}
