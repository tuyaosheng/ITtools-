package com.ittools.platform.security;

/**
 * 登录后置策略扩展点：其他子项目模块（如机房模块的"固定机位登录"）实现此接口，
 * 在凭据校验通过后、会话建立前做额外检查；不通过时抛出 AuthenticationException 子类。
 * 平台基座不依赖任何实现模块。
 */
public interface LoginPolicy {
    void check(AppUserDetails user, String clientIp);
}
