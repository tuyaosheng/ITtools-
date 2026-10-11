package com.ittools.platform.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 请求体（JSON 对象 -> Map）的类型化读取助手，供子项目 1-7 的 controller 使用，
 * 避免为每个简单表单单独写 Command 类。缺失/格式错误统一抛 IllegalArgumentException（-> 400）。
 */
public final class Body {
    private final Map<String, Object> m;

    private Body(Map<String, Object> m) {
        this.m = m == null ? Collections.<String, Object>emptyMap() : m;
    }

    public static Body of(Map<String, Object> m) {
        return new Body(m);
    }

    public boolean has(String k) {
        return m.containsKey(k) && m.get(k) != null;
    }

    public String str(String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v).trim();
    }

    public String reqStr(String k, String label) {
        String v = str(k);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return v;
    }

    public Long lng(String k) {
        Object v = m.get(k);
        if (v == null || "".equals(v)) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(k + " 必须是整数");
        }
    }

    public Long reqLng(String k, String label) {
        Long v = lng(k);
        if (v == null) throw new IllegalArgumentException(label + "不能为空");
        return v;
    }

    public Integer intv(String k) {
        Long v = lng(k);
        return v == null ? null : v.intValue();
    }

    public int intv(String k, int def) {
        Integer v = intv(k);
        return v == null ? def : v;
    }

    public Double dbl(String k) {
        Object v = m.get(k);
        if (v == null || "".equals(v)) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(k + " 必须是数字");
        }
    }

    public boolean bool(String k, boolean def) {
        Object v = m.get(k);
        if (v == null) return def;
        if (v instanceof Boolean) return (Boolean) v;
        return "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v));
    }

    public List<Long> longs(String k) {
        Object v = m.get(k);
        List<Long> out = new ArrayList<>();
        if (v instanceof Collection) {
            for (Object o : (Collection<?>) v) {
                if (o instanceof Number) out.add(((Number) o).longValue());
                else if (o != null) out.add(Long.parseLong(String.valueOf(o)));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> maps(String k) {
        Object v = m.get(k);
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof Collection) {
            for (Object o : (Collection<?>) v) {
                if (o instanceof Map) out.add((Map<String, Object>) o);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public List<String> strs(String k) {
        Object v = m.get(k);
        List<String> out = new ArrayList<>();
        if (v instanceof Collection) {
            for (Object o : (Collection<?>) v) {
                if (o != null) out.add(String.valueOf(o));
            }
        }
        return out;
    }

    public Map<String, Object> raw() {
        return m;
    }
}
