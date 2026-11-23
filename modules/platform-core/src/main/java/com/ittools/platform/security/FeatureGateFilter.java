package com.ittools.platform.security;

import com.ittools.platform.settings.FeatureCatalog;
import com.ittools.platform.settings.FeatureService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 管理员关闭的功能：对应角色访问该功能独占的接口时返回 403（统一 ApiResponse 信封）。 */
@Component
public class FeatureGateFilter extends OncePerRequestFilter {
    private final FeatureService features;

    public FeatureGateFilter(FeatureService features) {
        this.features = features;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().startsWith(req.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.isAuthenticated()) {
            String role = a.getAuthorities().stream().map(x -> x.getAuthority())
                    .filter(x -> x.equals("ROLE_STUDENT") || x.equals("ROLE_TEACHER"))
                    .map(x -> x.substring(5)).findFirst().orElse(null);
            if (role != null) {
                FeatureCatalog.Feature f = features.blocked(role, req.getRequestURI().substring(req.getContextPath().length()));
                if (f != null) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.getWriter().write("{\"code\":403,\"message\":\"「" + f.label + "」功能已被管理员关闭\",\"data\":null}");
                    return;
                }
            }
        }
        chain.doFilter(req, resp);
    }
}
