package com.ittools.platform.settings;

import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 功能开关：以“已关闭功能”列表的形式存在系统参数里（空 = 全部开放）。 */
@Service
public class FeatureService {
    private final SystemSettingService settings;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public FeatureService(SystemSettingService settings) {
        this.settings = settings;
    }

    private static String settingKey(String role) {
        return "features.disabled." + role.toLowerCase();
    }

    public Set<String> disabled(String role) {
        String v = settings.get(settingKey(role));
        Set<String> out = new LinkedHashSet<>();
        if (v != null) {
            Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(out::add);
        }
        return out;
    }

    /** 管理页：每个角色的功能清单 + 已关闭列表。 */
    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String role : new String[]{FeatureCatalog.STUDENT, FeatureCatalog.TEACHER}) {
            Map<String, Object> r = new LinkedHashMap<>();
            List<Map<String, Object>> items = new ArrayList<>();
            for (FeatureCatalog.Feature f : FeatureCatalog.of(role)) {
                Map<String, Object> i = new LinkedHashMap<>();
                i.put("key", f.key);
                i.put("label", f.label);
                i.put("group", f.group);
                i.put("enforced", !f.api.isEmpty());
                items.add(i);
            }
            r.put("features", items);
            r.put("disabled", new ArrayList<>(disabled(role)));
            m.put(role.toLowerCase(), r);
        }
        return m;
    }

    public Map<String, Object> save(Map<String, List<String>> body) {
        Map<String, String> changes = new LinkedHashMap<>();
        for (String role : new String[]{FeatureCatalog.STUDENT, FeatureCatalog.TEACHER}) {
            List<String> l = body.get(role.toLowerCase());
            if (l == null) continue;
            for (String k : l) {
                if (!FeatureCatalog.exists(role, k)) throw new IllegalArgumentException("未知功能：" + k);
            }
            changes.put(settingKey(role), l.stream().distinct().collect(Collectors.joining(",")));
        }
        settings.update(changes);
        return overview();
    }

    /** 这个角色访问该接口路径是否被关闭的功能挡住；没挡住返回 null。 */
    public FeatureCatalog.Feature blocked(String role, String path) {
        Set<String> off = null;
        for (FeatureCatalog.Feature f : FeatureCatalog.of(role)) {
            for (String p : f.api) {
                if (matcher.match(p, path)) {
                    if (off == null) off = disabled(role);
                    if (off.contains(f.key)) return f;
                    break;
                }
            }
        }
        return null;
    }
}
