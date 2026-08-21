package com.ittools.platform.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Java 8 has no records; this is a plain immutable value class with fluent
 * getters (loginType(), yearCode(), ...) matching the brief's record shape,
 * plus Jackson annotations so it can be deserialized directly from JSON
 * request bodies (see Task 6).
 */
public final class LoginRequest {
    private final String loginType;
    private final String yearCode;
    private final String className;
    private final String name;
    private final String xjh;
    private final String loginName;
    private final String password;

    @JsonCreator
    public LoginRequest(
            @JsonProperty("loginType") String loginType,
            @JsonProperty("yearCode") String yearCode,
            @JsonProperty("className") String className,
            @JsonProperty("name") String name,
            @JsonProperty("xjh") String xjh,
            @JsonProperty("loginName") String loginName,
            @JsonProperty("password") String password) {
        this.loginType = loginType;
        this.yearCode = yearCode;
        this.className = className;
        this.name = name;
        this.xjh = xjh;
        this.loginName = loginName;
        this.password = password;
    }

    public String loginType() { return loginType; }
    public String yearCode() { return yearCode; }
    public String className() { return className; }
    public String name() { return name; }
    public String xjh() { return xjh; }
    public String loginName() { return loginName; }
    public String password() { return password; }
}
