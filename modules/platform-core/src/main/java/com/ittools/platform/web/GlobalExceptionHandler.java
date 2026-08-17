package com.ittools.platform.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * RULING 9 (binding, uniform auth-failure message): every
     * AuthenticationException subtype - UsernameNotFoundException
     * ("user not found"), DisabledException ("account disabled"),
     * BadCredentialsException ("wrong password"), or the
     * InsufficientAuthenticationException thrown by AuthController#me for an
     * anonymous caller - MUST collapse to the SAME generic 401 message.
     * Leaking which specific reason caused the failure lets an attacker
     * enumerate valid usernames/accounts. The concrete exception type and
     * its original message are still available server-side (e.getClass(),
     * e.getMessage()) for logging; only the HTTP response is genericized.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> auth(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<Void>error(401, "用户名或密码错误"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> bad(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.<Void>error(400, e.getMessage()));
    }
}
