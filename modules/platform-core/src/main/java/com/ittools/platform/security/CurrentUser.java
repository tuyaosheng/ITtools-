package com.ittools.platform.security;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 取当前登录用户的便捷入口，供各子项目模块的 service/controller 使用。
 */
public final class CurrentUser {
    private CurrentUser() {}

    public static AppUserDetails get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AppUserDetails)) {
            throw new InsufficientAuthenticationException("未登录");
        }
        return (AppUserDetails) a.getPrincipal();
    }

    public static Long id() {
        return get().userId();
    }
}
