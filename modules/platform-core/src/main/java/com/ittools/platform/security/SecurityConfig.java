package com.ittools.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                    // The SPA shell + its static assets are public at the HTTP layer -
                    // actual access control happens against the /api/** endpoints the
                    // SPA calls once loaded, and client-side router guards redirect
                    // unauthenticated users to /login. Without this, anyRequest()
                    // .authenticated() below would 401 "/", "/admin/**" etc. before
                    // SpaForwardController/static resource resolution ever runs.
                    .antMatchers("/", "/index.html", "/login", "/admin/**", "/teacher/**", "/student/**",
                            "/assets/**", "/favicon.ico").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable());
        return http.build();
    }
}
