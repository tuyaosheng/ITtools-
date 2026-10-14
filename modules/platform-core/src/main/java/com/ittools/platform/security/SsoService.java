package com.ittools.platform.security;

import com.ittools.platform.settings.SystemSettingService;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 第三方程序登录签名校验：HMAC-SHA256(密钥, "学籍号|Unix秒")，5 分钟有效，一次性。 */
@Service
public class SsoService {
    static final long WINDOW_SECONDS = 300;

    private final SystemSettingService settings;
    private final Map<String, Long> used = new ConcurrentHashMap<>();

    public SsoService(SystemSettingService settings) {
        this.settings = settings;
    }

    /** 返回 null 表示通过，否则为失败原因。 */
    public String verify(String user, long t, String sig) {
        if (!settings.getBool(SystemSettingService.SSO_ENABLED)) return "系统未开启第三方程序登录";
        String secret = settings.get(SystemSettingService.SSO_SECRET);
        if (secret == null || secret.length() < 16) return "系统未设置第三方登录密钥";
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - t) > WINDOW_SECONDS) return "登录链接已过期";
        String expect = sign(secret, user, t);
        if (sig == null || !MessageDigest.isEqual(expect.getBytes(StandardCharsets.US_ASCII), sig.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            return "登录链接签名无效";
        }
        used.values().removeIf(exp -> exp < now);
        if (used.putIfAbsent(expect, now + 2 * WINDOW_SECONDS) != null) return "登录链接已使用过";
        return null;
    }

    public static String sign(String secret, String user, long t) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal((user + "|" + t).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String newSecret() {
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }
}
