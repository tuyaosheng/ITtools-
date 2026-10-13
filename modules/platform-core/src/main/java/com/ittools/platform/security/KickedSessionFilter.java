package com.ittools.platform.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 被“重复登录”踢下线的旧会话再次访问接口时，返回 401 并说明原因。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class KickedSessionFilter extends OncePerRequestFilter {
    private final SessionTracker tracker;

    public KickedSessionFilter(SessionTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws ServletException, IOException {
        String uri = req.getRequestURI();
        if (uri.startsWith("/api/") && !uri.startsWith("/api/auth/") && req.getRequestedSessionId() != null
                && !req.isRequestedSessionIdValid() && tracker.wasKicked(req.getRequestedSessionId())) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":401,\"message\":\"你的账号已在其他电脑登录，本机已下线\",\"data\":{\"kicked\":true}}");
            return;
        }
        chain.doFilter(req, resp);
    }
}
