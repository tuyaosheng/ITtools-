package com.ittools.platform.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员可以按角色开关的功能清单。key 与前端菜单 / 路由 meta.feature 对应；
 * api 为该功能独占的后端接口前缀（Ant 风格），关闭后这些接口对该角色返回 403。
 * 课堂里也会用到的接口（如在课堂里交作业、做测验）不列入 api，只隐藏菜单入口。
 */
public final class FeatureCatalog {
    public static final String STUDENT = "STUDENT";
    public static final String TEACHER = "TEACHER";

    public static final class Feature {
        public final String role;
        public final String key;
        public final String label;
        public final String group;
        public final List<String> api;

        Feature(String role, String key, String label, String group, String... api) {
            this.role = role;
            this.key = key;
            this.label = label;
            this.group = group;
            this.api = Arrays.asList(api);
        }
    }

    public static final List<Feature> ALL;
    static {
        List<Feature> l = new ArrayList<>();
        // ---- 学生 ----
        l.add(new Feature(STUDENT, "works", "我的作品", "学习"));
        l.add(new Feature(STUDENT, "showcase", "精华作品", "学习", "/api/homework/showcase/**", "/api/homework/essence-ranking/**"));
        l.add(new Feature(STUDENT, "tests", "作业与测验", "学习"));
        l.add(new Feature(STUDENT, "practice", "刷题练习", "学习", "/api/student/quiz/practice/**", "/api/student/quiz/practice-sets/**", "/api/student/quiz/practice-sessions/**"));
        l.add(new Feature(STUDENT, "python", "Python 练习", "学习", "/api/student/python/**"));
        l.add(new Feature(STUDENT, "blocks", "图形化编程", "学习", "/api/student/mpblocks/free/**", "/api/student/mpblocks/free"));
        l.add(new Feature(STUDENT, "op-practice", "课后操作题", "学习", "/api/student/op-practice/**"));
        l.add(new Feature(STUDENT, "typing", "指法练习", "学习", "/api/student/typing/**", "/api/typing/**"));
        l.add(new Feature(STUDENT, "resources", "学习资源", "资源与空间", "/api/student/resources/**"));
        l.add(new Feature(STUDENT, "disk", "我的网盘", "资源与空间", "/api/disk/**"));
        l.add(new Feature(STUDENT, "site", "个人网站", "资源与空间", "/api/student/site/**"));
        l.add(new Feature(STUDENT, "notes", "学习笔记", "资源与空间", "/api/notes/**"));
        l.add(new Feature(STUDENT, "electives", "走班选课", "其他", "/api/student/electives/**"));
        l.add(new Feature(STUDENT, "qa", "师生答疑", "其他", "/api/student/qa/**", "/api/qa/public/**"));
        l.add(new Feature(STUDENT, "report", "我的学情", "其他", "/api/student/report/**"));
        // ---- 教师 ----
        l.add(new Feature(TEACHER, "homework", "作品批阅", "教学"));
        l.add(new Feature(TEACHER, "quiz", "题库与试卷", "教学"));
        l.add(new Feature(TEACHER, "tests", "作业与测验", "教学"));
        l.add(new Feature(TEACHER, "python", "Python 题库", "教学", "/api/teacher/python/**"));
        l.add(new Feature(TEACHER, "rubrics", "评价量规", "教学", "/api/teacher/grading/rubrics/**"));
        l.add(new Feature(TEACHER, "resources", "资源库", "资源与备课", "/api/teacher/resources/**"));
        l.add(new Feature(TEACHER, "lesson-plans", "教案", "资源与备课", "/api/teacher/lesson-plans/**"));
        l.add(new Feature(TEACHER, "reflections", "学习心得", "资源与备课", "/api/teacher/reflections/**"));
        l.add(new Feature(TEACHER, "disk", "教师网盘", "资源与备课", "/api/disk/**"));
        l.add(new Feature(TEACHER, "student-space", "学生空间", "学生管理", "/api/teacher/disks/**"));
        l.add(new Feature(TEACHER, "reports", "学习情况", "学生管理", "/api/teacher/reports/**"));
        l.add(new Feature(TEACHER, "login-logs", "登入记录", "学生管理", "/api/teacher/login-logs/**"));
        l.add(new Feature(TEACHER, "timetable", "课表", "其他", "/api/teacher/timetable/**"));
        l.add(new Feature(TEACHER, "qa", "师生答疑", "其他", "/api/teacher/qa/**"));
        ALL = Collections.unmodifiableList(l);
    }

    private FeatureCatalog() {
    }

    public static List<Feature> of(String role) {
        return ALL.stream().filter(f -> f.role.equals(role)).collect(Collectors.toList());
    }

    public static boolean exists(String role, String key) {
        return ALL.stream().anyMatch(f -> f.role.equals(role) && f.key.equals(key));
    }
}
