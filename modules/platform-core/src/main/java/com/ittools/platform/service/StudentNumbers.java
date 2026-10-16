package com.ittools.platform.service;

import java.util.Map;

/**
 * 学生的两个编号（字段名沿用旧称，界面上的叫法已改）：
 * <ul>
 *   <li>学号（users.xjh，原“学籍号”）：学生必填，全系统唯一，可用于登录、照片命名</li>
 *   <li>班内学号（users.student_no，原“学号”）：可空，填则为 1~50 的整数，存成两位“01”~“50”，名单按它排序</li>
 * </ul>
 */
public final class StudentNumbers {
    public static final int MAX_CLASS_NO = 50;

    private StudentNumbers() {}

    /** 学号：去空格后不能为空、不超过 32 字。 */
    public static String requireXjh(String v) {
        String s = v == null ? "" : v.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("请填写学号");
        if (s.length() > 32) throw new IllegalArgumentException("学号过长（不超过 32 字）");
        return s;
    }

    /** 班内学号：空返回 null；否则须为 1~50 的整数，统一存成两位（“5”“05”都存为“05”），名单按文本排序也是对的。 */
    public static String classNo(String v) {
        String s = v == null ? "" : v.trim();
        if (s.isEmpty()) return null;
        // 表格里的数字常被读成“5.0”
        if (s.matches("\\d{1,3}\\.0+")) s = s.substring(0, s.indexOf('.'));
        if (!s.matches("\\d{1,3}")) throw new IllegalArgumentException("班内学号应为 1~" + MAX_CLASS_NO + " 的整数：" + v.trim());
        int n = Integer.parseInt(s);
        if (n < 1 || n > MAX_CLASS_NO) throw new IllegalArgumentException("班内学号应为 1~" + MAX_CLASS_NO + " 的整数：" + v.trim());
        return String.format("%02d", n);
    }

    /**
     * 表格表头兼容：新表头是“学号（=原学籍号）”“班内学号”；旧表头同时有“学籍号”“学号”时，
     * 旧“学号”是班内学号。统一换成新叫法，后面只认“学号”“班内学号”。
     */
    public static void normalizeHeaders(Map<String, Integer> col) {
        if (!col.containsKey("学籍号")) return;
        Integer xjh = col.remove("学籍号");
        Integer no = col.remove("学号");
        col.put("学号", xjh);
        if (no != null && !col.containsKey("班内学号")) col.put("班内学号", no);
    }
}
