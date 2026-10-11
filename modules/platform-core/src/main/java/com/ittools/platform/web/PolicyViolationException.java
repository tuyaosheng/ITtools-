package com.ittools.platform.web;

import org.springframework.security.core.AuthenticationException;

/** 登录策略拒绝（凭据正确，但违反如"固定机位"等业务规则），提示信息会原样返回给客户端。 */
public class PolicyViolationException extends AuthenticationException {
    public PolicyViolationException(String msg) {
        super(msg);
    }
}
