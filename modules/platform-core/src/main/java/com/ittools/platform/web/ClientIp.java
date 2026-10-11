package com.ittools.platform.web;

import javax.servlet.http.HttpServletRequest;

/** 解析客户端 IP（机房 LAN 场景；若经反向代理则取 X-Forwarded-For 第一个）。 */
public final class ClientIp {
    private ClientIp() {}

    public static String of(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            return normalize(xff.split(",")[0].trim());
        }
        return normalize(req.getRemoteAddr());
    }

    static String normalize(String ip) {
        if (ip == null) return "";
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) return "127.0.0.1";
        if (ip.startsWith("::ffff:")) return ip.substring(7);
        return ip;
    }
}
