package com.ittools.platform.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
     *
     * 例外：{@link PolicyViolationException}（如"请在指定机位登录"）是凭据已正确之后的业务策略拒绝，
     * 不泄露账号是否存在，因此原样返回其提示。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> auth(AuthenticationException e) {
        String msg = e instanceof PolicyViolationException ? e.getMessage() : "用户名或密码错误";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<Void>error(401, msg));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.<Void>error(403, "无权访问"));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> bad(RuntimeException e) {
        return ResponseEntity.badRequest().body(ApiResponse.<Void>error(400, e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(ApiResponse.<Void>error(400, "上传文件过大"));
    }
}
