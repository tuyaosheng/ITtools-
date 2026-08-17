package com.ittools.platform.web;

/**
 * Uniform HTTP response envelope: {"code":.., "message":.., "data":..}.
 *
 * The task brief specifies this as a Java 17 {@code record}, but this
 * project targets Java 8 (no records) AND the payload is Jackson-serialized
 * to JSON. Jackson's default serializer introspects STANDARD BEAN GETTERS
 * (getCode()/getMessage()/getData()), not record-style fluent accessors
 * (code()/message()/data()) - fluent accessors would serialize to "{}". So
 * this is a plain immutable final class with bean getters; construction is
 * restricted to the two static factories, matching the brief's call-site
 * contract (ApiResponse.ok(data) / ApiResponse.error(code, message)).
 */
public final class ApiResponse<T> {
    private final int code;
    private final String message;
    private final T data;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
