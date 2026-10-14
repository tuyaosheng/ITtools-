package com.ittools.platform.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统参数（对应原 admin/setsystem.aspx：学校名称、工作模式、文件过滤、网盘容量、作业补交、敏感词过滤等）。
 * 以键值对存储，未设置的键使用 DEFAULTS 中的默认值。
 */
@Service
public class SystemSettingService {
    public static final String SCHOOL_NAME = "school.name";
    /** NORMAL 正常教学 / OPEN_CLASS 公开课 / XJH 学籍+密码 / FIXED_SEAT 班级+姓名+固定机位 */
    public static final String LOGIN_MODE = "login.mode";
    public static final String BLOCKED_EXT = "upload.blockedExtensions";
    public static final String DISK_QUOTA_MB = "disk.quotaMb";
    public static final String HOMEWORK_ALLOW_LATE = "homework.allowLate";
    public static final String UPLOAD_MAX_MB = "upload.maxMb";
    public static final String SENSITIVE_WORDS = "interact.sensitiveWords";
    /** 学生账号只允许一处登录（再次登录时旧电脑下线） */
    public static final String SINGLE_LOGIN = "login.singleSession";
    /** 登录页公告 */
    public static final String SITE_NOTICE = "site.notice";
    /** 学生可自行修改密码 / 上传头像 */
    public static final String STUDENT_PASSWORD = "student.allowPasswordChange";
    public static final String STUDENT_AVATAR = "student.allowAvatarChange";
    /** 第三方程序登录（原 weblogin.aspx）：开关与签名密钥 */
    public static final String SSO_ENABLED = "sso.enabled";
    public static final String SSO_SECRET = "sso.secret";

    public static final List<String> LOGIN_MODES = Arrays.asList("NORMAL", "OPEN_CLASS", "XJH", "FIXED_SEAT");

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put(SCHOOL_NAME, "信息技术教学辅助平台");
        DEFAULTS.put(LOGIN_MODE, "NORMAL");
        DEFAULTS.put(BLOCKED_EXT, "exe,bat,cmd,com,vbs,msi,scr,ps1,jsp,asp,aspx,php");
        DEFAULTS.put(DISK_QUOTA_MB, "50");
        DEFAULTS.put(HOMEWORK_ALLOW_LATE, "true");
        DEFAULTS.put(UPLOAD_MAX_MB, "50");
        DEFAULTS.put(SENSITIVE_WORDS, "");
        DEFAULTS.put(SINGLE_LOGIN, "true");
        DEFAULTS.put(SITE_NOTICE, "");
        DEFAULTS.put(STUDENT_PASSWORD, "true");
        DEFAULTS.put(STUDENT_AVATAR, "false");
        DEFAULTS.put(SSO_ENABLED, "false");
        DEFAULTS.put(SSO_SECRET, "");
    }

    private final SystemSettingRepository repo;

    public SystemSettingService(SystemSettingRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> m = new LinkedHashMap<>(DEFAULTS);
        for (SystemSetting s : repo.findAll()) {
            m.put(s.getKey(), s.getValue());
        }
        return m;
    }

    @Transactional(readOnly = true)
    public String get(String key) {
        return repo.findById(key).map(SystemSetting::getValue).orElse(DEFAULTS.get(key));
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (RuntimeException e) {
            return Integer.parseInt(DEFAULTS.get(key));
        }
    }

    public boolean getBool(String key) {
        return "true".equalsIgnoreCase(get(key));
    }

    @Transactional
    public Map<String, String> update(Map<String, String> changes) {
        for (Map.Entry<String, String> e : changes.entrySet()) {
            if (!DEFAULTS.containsKey(e.getKey())) {
                throw new IllegalArgumentException("未知参数：" + e.getKey());
            }
            String v = e.getValue() == null ? "" : e.getValue().trim();
            if (LOGIN_MODE.equals(e.getKey()) && !LOGIN_MODES.contains(v)) {
                throw new IllegalArgumentException("无效的工作模式：" + v);
            }
            if (SITE_NOTICE.equals(e.getKey()) && v.length() > 1000) throw new IllegalArgumentException("公告不超过 1000 字");
            if (SSO_SECRET.equals(e.getKey()) && !v.isEmpty() && v.length() < 16) throw new IllegalArgumentException("密钥至少 16 位");
            if ((DISK_QUOTA_MB.equals(e.getKey()) || UPLOAD_MAX_MB.equals(e.getKey())) && !v.matches("\\d{1,6}")) {
                throw new IllegalArgumentException("容量必须是正整数");
            }
            SystemSetting s = repo.findById(e.getKey()).orElse(new SystemSetting(e.getKey(), v));
            s.setValue(v);
            repo.save(s);
        }
        return all();
    }

    public boolean isBlockedExtension(String ext) {
        if (ext == null || ext.isEmpty()) return false;
        return blockedExtensions().contains(ext.toLowerCase(Locale.ROOT));
    }

    public List<String> blockedExtensions() {
        String v = get(BLOCKED_EXT);
        if (v == null || v.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(v.split("[,，\\s]+"))
                .map(x -> x.trim().toLowerCase(Locale.ROOT).replace(".", ""))
                .filter(x -> !x.isEmpty())
                .collect(Collectors.toList());
    }

    /** 敏感词过滤（原系统"敏感词过滤"设置），用于讨论/论坛内容。 */
    public String filterSensitive(String text) {
        if (text == null) return null;
        String words = get(SENSITIVE_WORDS);
        if (words == null || words.trim().isEmpty()) return text;
        String out = text;
        for (String w : words.split("[,，\\s]+")) {
            if (!w.isEmpty()) {
                out = out.replace(w, repeat('*', w.length()));
            }
        }
        return out;
    }

    private static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }
}
