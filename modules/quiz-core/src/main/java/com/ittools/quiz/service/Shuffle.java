package com.ittools.quiz.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 每人乱序：种子由 测验 + 学生（+ 题目）决定，同一学生每次打开顺序相同，不同学生不同。
 * 选项排列 perm[显示位置] = 原选项下标；答案在“显示字母”与“原字母”之间换算。
 */
public final class Shuffle {
    private Shuffle() {
    }

    static long seed(long testId, long studentId, long salt) {
        long h = 1125899906842597L;
        for (long v : new long[]{testId, studentId, salt}) h = 31 * h + (v ^ (v >>> 32));
        return h ^ 0x5DEECE66DL;
    }

    public static <T> List<T> order(List<T> items, long testId, long studentId) {
        List<T> l = new ArrayList<>(items);
        Collections.shuffle(l, new Random(seed(testId, studentId, -1)));
        return l;
    }

    /** 选项排列：perm[显示位置] = 原下标。 */
    public static int[] perm(int n, long testId, long studentId, long questionId) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        Collections.shuffle(idx, new Random(seed(testId, studentId, questionId)));
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = idx.get(i);
        return p;
    }

    /** 显示字母 → 原字母（如 perm=[2,0,1] 时 "A" → "C"）；多选按原字母排序。非字母/越界字符原样丢弃。 */
    public static String toOriginal(String shown, int[] perm) {
        if (shown == null) return null;
        StringBuilder b = new StringBuilder();
        for (char c : shown.toUpperCase().toCharArray()) {
            int d = c - 'A';
            if (d >= 0 && d < perm.length) b.append((char) ('A' + perm[d]));
        }
        char[] cs = b.toString().toCharArray();
        java.util.Arrays.sort(cs);
        return new String(cs);
    }

    /** 原字母 → 显示字母，多选按显示字母排序。 */
    public static String toShown(String original, int[] perm) {
        if (original == null) return null;
        int[] inv = new int[perm.length];
        for (int d = 0; d < perm.length; d++) inv[perm[d]] = d;
        StringBuilder b = new StringBuilder();
        for (char c : original.toUpperCase().toCharArray()) {
            int o = c - 'A';
            if (o >= 0 && o < inv.length) b.append((char) ('A' + inv[o]));
        }
        char[] cs = b.toString().toCharArray();
        java.util.Arrays.sort(cs);
        return new String(cs);
    }

    public static <T> List<T> apply(List<T> options, int[] perm) {
        List<T> out = new ArrayList<>();
        for (int d : perm) out.add(options.get(d));
        return out;
    }
}
