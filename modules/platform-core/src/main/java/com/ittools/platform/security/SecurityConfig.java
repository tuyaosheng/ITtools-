package com.ittools.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * BINDING Java 8 / Spring Boot 2.7 / Spring Security 5.x translation of the
 * task brief (written against Java 17 + Spring Security 6):
 *  - @EnableGlobalMethodSecurity(prePostEnabled = true), not the SecSec6
 *    @EnableMethodSecurity.
 *  - .antMatchers(...), not the SecSec6 .requestMatchers(...).
 *  - CsrfConfigurer#ignoringAntMatchers(...), not #ignoringRequestMatchers(...).
 *  - The SecurityFilterChain @Bean style itself is unchanged - supported
 *    since Spring Security 5.7, which is what Spring Boot 2.7.18 ships.
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringAntMatchers("/api/auth/login"))
            .authorizeRequests(a -> a
                    .antMatchers("/api/auth/**", "/api/public/**").permitAll()
                    .antMatchers("/api/admin/**").hasRole("ADMIN")
                    .antMatchers("/api/teacher/**").hasRole("TEACHER")
                    .antMatchers("/api/student/**").hasRole("STUDENT")
                    // The SPA shell + its static assets are public at the HTTP layer -
                    // actual access control happens against the /api/** endpoints the
                    // SPA calls once loaded, and client-side router guards redirect
                    // unauthenticated users to /login. Without this, anyRequest()
                    // .authenticated() below would 401 "/", "/admin/**" etc. before
                    // SpaForwardController/static resource resolution ever runs.
                    .antMatchers("/", "/index.html", "/login", "/admin/**", "/teacher/**", "/student/**",
                            "/assets/**", "/pyrunner/**", "/scratch/**", "/favicon.ico").permitAll()
                    // 课件包（Storyline 等）：按不可猜测的包编号公开访问，响应带 CSP sandbox，与平台会话隔离
                    .antMatchers(HttpMethod.GET, "/courseware/**").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler((req, resp, ex) -> {
                        // 403 也使用统一 ApiResponse 信封
                        resp.setStatus(HttpStatus.FORBIDDEN.value());
                        resp.setContentType("application/json;charset=UTF-8");
                        resp.getWriter().write("{\"code\":403,\"message\":\"无权访问\",\"data\":null}");
                    }))
            // 课件包需要在课堂页面的 iframe 中显示：只允许同源框架嵌入
            .headers(h -> h.frameOptions().sameOrigin())
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable());
        return http.build();
    }
}
