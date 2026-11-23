package com.ittools.homework.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.classroom.service.LessonService;
import com.ittools.grading.service.GradingService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 平台图形化编程（掌控板）：作品页打开“用平台图形化编程交作品”后，学生在页面里拼积木。
 * 这里存学生拼到一半的程序（自动保存），并给编辑器课堂环境：本节课 MQTT 项目、服务器地址、组名和配对号、建议的主题。
 * 交作品走作品页原来的上传接口（.mpcode），自动评分用 Mind+ 规则。
 */
@Service
public class MpBlockService {
    private static final int MAX_PROJECT = 2 * 1024 * 1024;

    private final NamedParameterJdbcTemplate jdbc;
    private final LessonService lessons;
    private final HomeworkService homework;
    private final GradingService grading;
    private final ObjectMapper json = new ObjectMapper();

    public MpBlockService(NamedParameterJdbcTemplate jdbc, LessonService lessons, HomeworkService homework, GradingService grading) {
        this.jdbc = jdbc;
        this.lessons = lessons;
        this.homework = homework;
        this.grading = grading;
    }

    private Map<String, Object> requireBlockPage(Long lessonId, Long pageId) {
        Map<String, Object> pg = lessons.requirePageOfLesson(lessonId, pageId);
        if (!"HOMEWORK".equals(pg.get("pageType")) || !Boolean.TRUE.equals(homework.config(pageId).get("blockEditor"))) {
            throw new IllegalArgumentException("这个页面没有打开图形化编程");
        }
        return pg;
    }

    /** 学生打开编辑器：已保存的程序 + 课堂环境。 */
    @Transactional(readOnly = true)
    public Map<String, Object> studentLoad(Long studentId, Long lessonId, Long pageId, String serverHost) {
        lessons.studentPage(studentId, lessonId, pageId);   // 页面开放、本组角色能看
        Map<String, Object> pg = requireBlockPage(lessonId, pageId);
        Map<String, Object> out = new LinkedHashMap<>();
        Object own = project(lessonId, pageId, studentId);
        out.put("project", own);
        Map<String, Object> ctx = context(lessonId, studentId, pg, serverHost);
        ctx.put("allowHistory", !Boolean.FALSE.equals(homework.config(pageId).get("allowHistory")));
        out.put("context", ctx);
        out.put("canSubmit", true);
        // 本节课这一页还没存过：编辑器让学生选从哪里开始（老师给的起始程序 / 我以前的程序 / 空白）
        if (own == null) out.put("fresh", true);
        out.put("starter", starterJson(pageId));   // “打开”菜单里也能回到起始程序
        return out;
    }

    // ---------------- 起始程序（老师给作品页配的） ----------------
    private Object starterJson(Long pageId) {
        List<String> l = jdbc.queryForList("SELECT project FROM mpblock_starter WHERE page_id = :p", p("p", pageId), String.class);
        if (l.isEmpty()) return null;
        try {
            return json.readTree(l.get(0));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> requireTeacherHomeworkPage(Long teacherId, Long pageId) {
        grading.requirePageOwner(teacherId, pageId);
        List<Map<String, Object>> l = jdbc.queryForList("SELECT p.id, p.title, p.page_type AS \"pageType\", c.name AS \"courseName\" "
                + "FROM course_page p JOIN course c ON c.id = p.course_id WHERE p.id = :p", p("p", pageId));
        if (l.isEmpty() || !"HOMEWORK".equals(l.get(0).get("pageType"))) throw new IllegalArgumentException("只有作品页能设起始程序");
        return l.get(0);
    }

    /** 老师编辑起始程序：编辑器以“起始程序”模式打开，自动保存到这一页。 */
    @Transactional(readOnly = true)
    public Map<String, Object> starterLoad(Long teacherId, Long pageId, String serverHost) {
        Map<String, Object> pg = requireTeacherHomeworkPage(teacherId, pageId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("project", starterJson(pageId));
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("server", serverHost);
        ctx.put("label", pg.get("courseName") + " · " + pg.get("title") + " · 起始程序（自动保存）");
        ctx.put("fileName", pg.get("title") + "_起始程序");
        ctx.put("topicSuggestions", new ArrayList<String>());
        out.put("context", ctx);
        out.put("canSubmit", false);
        return out;
    }

    @Transactional
    public Map<String, Object> starterSave(Long teacherId, Long pageId, Object project) {
        requireTeacherHomeworkPage(teacherId, pageId);
        jdbc.update("INSERT INTO mpblock_starter(page_id, project) VALUES (:p, :j) "
                + "ON CONFLICT (page_id) DO UPDATE SET project = EXCLUDED.project, updated_at = now()", p("p", pageId, "j", projectText(project)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", true);
        return out;
    }

    @Transactional
    public void starterDelete(Long teacherId, Long pageId) {
        requireTeacherHomeworkPage(teacherId, pageId);
        jdbc.update("DELETE FROM mpblock_starter WHERE page_id = :p", p("p", pageId));
    }

    // ---------------- 我以前的程序（学生自己的） ----------------
    /**
     * 最近的程序：别的课的作品页存档、随时练习、交过的 .mpcode / .mp 作品（各最多 20 份，按时间倒排）。
     * 当前这一页（lessonId+pageId）不列。交过的作品文件由编辑器用作品下载接口自己取。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(Long studentId, Long lessonId, Long pageId) {
        List<Map<String, Object>> out = new ArrayList<>();
        out.addAll(jdbc.queryForList("SELECT 'work' AS kind, w.lesson_id AS \"lessonId\", w.page_id AS \"pageId\", "
                        + "c.name AS \"courseName\", cp.title AS \"pageTitle\", w.updated_at AS \"time\", w.project "
                        + "FROM mpblock_work w JOIN lesson l ON l.id = w.lesson_id JOIN course c ON c.id = l.course_id "
                        + "JOIN course_page cp ON cp.id = w.page_id "
                        + "WHERE w.student_id = :s AND NOT (w.lesson_id = :l AND w.page_id = :p) ORDER BY w.updated_at DESC LIMIT 20",
                p("s", studentId, "l", lessonId == null ? -1L : lessonId, "p", pageId == null ? -1L : pageId)));
        out.addAll(jdbc.queryForList("SELECT 'free' AS kind, updated_at AS \"time\", project FROM mpblock_free WHERE student_id = :s", p("s", studentId)));
        out.addAll(jdbc.queryForList("SELECT 'file' AS kind, s.id AS \"submissionId\", s.file_name AS \"fileName\", "
                        + "c.name AS \"courseName\", cp.title AS \"pageTitle\", s.submitted_at AS \"time\" "
                        + "FROM submission s JOIN lesson l ON l.id = s.lesson_id JOIN course c ON c.id = l.course_id "
                        + "JOIN course_page cp ON cp.id = s.page_id "
                        + "WHERE s.student_id = :s AND (lower(s.file_name) LIKE '%.mpcode' OR lower(s.file_name) LIKE '%.mp') "
                        + "ORDER BY s.submitted_at DESC LIMIT 20", p("s", studentId)));
        out.removeIf(h -> h.containsKey("project") && isBlankProgram((String) h.remove("project")));
        out.sort((a, b) -> ((java.util.Date) b.get("time")).compareTo((java.util.Date) a.get("time")));
        return out;
    }

    /** 只有“ESP32 主程序 + 循环执行”（或什么积木都没有）的存档不列进“我以前的程序”。 */
    private boolean isBlankProgram(String text) {
        try {
            com.fasterxml.jackson.databind.JsonNode targets = json.readTree(text).path("targets");
            for (com.fasterxml.jackson.databind.JsonNode t : targets) {
                java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = t.path("blocks").elements();
                while (it.hasNext()) {
                    com.fasterxml.jackson.databind.JsonNode b = it.next();
                    if (!b.isObject() || b.path("shadow").asBoolean(false)) continue;
                    String op = b.path("opcode").asText("");
                    if (!op.endsWith("_start") && !"control_forever".equals(op)) return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 取一份以前的作品页存档（只能取自己的）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> historyWork(Long studentId, Long lessonId, Long pageId) {
        List<String> l = jdbc.queryForList("SELECT project FROM mpblock_work WHERE lesson_id = :l AND page_id = :p AND student_id = :s",
                p("l", lessonId, "p", pageId, "s", studentId), String.class);
        if (l.isEmpty()) throw new IllegalArgumentException("没有找到这份程序");
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            out.put("project", json.readTree(l.get(0)));
        } catch (Exception e) {
            throw new IllegalArgumentException("这份程序坏了，打不开");
        }
        return out;
    }

    private String projectText(Object project) {
        String text;
        try {
            text = project instanceof String ? (String) project : json.writeValueAsString(project);
        } catch (Exception e) {
            throw new IllegalArgumentException("程序格式不对");
        }
        if (text == null || text.isEmpty() || "null".equals(text)) throw new IllegalArgumentException("程序是空的");
        if (text.length() > MAX_PROJECT) throw new IllegalArgumentException("程序太大了（超过 2MB），图片换小一点");
        return text;
    }

    @Transactional
    public Map<String, Object> studentSave(Long studentId, Long lessonId, Long pageId, Object project, String code) {
        lessons.studentPage(studentId, lessonId, pageId);
        requireBlockPage(lessonId, pageId);
        String text;
        try {
            text = project instanceof String ? (String) project : json.writeValueAsString(project);
        } catch (Exception e) {
            throw new IllegalArgumentException("程序格式不对");
        }
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("程序是空的");
        if (text.length() > MAX_PROJECT) throw new IllegalArgumentException("程序太大了（超过 2MB），图片换小一点");
        jdbc.update("INSERT INTO mpblock_work(lesson_id, page_id, student_id, project, code) VALUES (:l, :p, :s, :j, :c) "
                        + "ON CONFLICT (lesson_id, page_id, student_id) DO UPDATE SET project = EXCLUDED.project, code = EXCLUDED.code, updated_at = now()",
                p("l", lessonId, "p", pageId, "s", studentId, "j", text, "c", code));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", true);
        return out;
    }

    /** 老师看某个学生自动保存的积木（没交作品也能看）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> teacherLoad(Long teacherId, Long lessonId, Long pageId, Long studentId, String serverHost) {
        lessons.requireTeacher(teacherId, lessonId);
        Map<String, Object> pg = requireBlockPage(lessonId, pageId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("project", project(lessonId, pageId, studentId));
        Map<String, Object> ctx = context(lessonId, studentId, pg, serverHost);
        List<String> n = jdbc.queryForList("SELECT name FROM users WHERE id = :s", p("s", studentId), String.class);
        ctx.put("label", ctx.get("label") + (n.isEmpty() ? "" : " · " + n.get(0)) + "（老师查看）");
        out.put("context", ctx);
        out.put("canSubmit", false);
        return out;
    }

    /**
     * 作品页旁边的任务单：按标题找同一课程里的“课堂调查”或“图文”页。
     * open=false 表示老师在本节课还没打开那一页（学生能看到标题，不能填）。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> studentTask(Long studentId, Long lessonId, Long pageId) {
        lessons.studentPage(studentId, lessonId, pageId);
        Map<String, Object> pg = requireBlockPage(lessonId, pageId);
        Object title = homework.config(pageId).get("taskPageTitle");
        Map<String, Object> out = new LinkedHashMap<>();
        if (title == null || String.valueOf(title).isEmpty()) return out;
        List<Map<String, Object>> found = jdbc.queryForList("SELECT id, title, page_type AS \"pageType\" FROM course_page "
                        + "WHERE course_id = :c AND title = :t AND page_type IN ('SURVEY', 'TEXT') ORDER BY seq, id LIMIT 1",
                p("c", pg.get("courseId"), "t", String.valueOf(title)));
        out.put("title", String.valueOf(title));
        if (found.isEmpty()) {
            out.put("missing", true);
            return out;
        }
        Map<String, Object> t = found.get(0);
        out.put("pageId", t.get("id"));
        out.put("pageType", t.get("pageType"));
        boolean open = false;
        for (Map<String, Object> sp : lessons.studentPages(studentId, lessonId)) {
            if (((Number) sp.get("id")).longValue() == ((Number) t.get("id")).longValue()) open = true;
        }
        out.put("open", open);
        return out;
    }

    // ---------------- 随时练习（不在课里）：每个学生一份，自动保存 ----------------
    @Transactional(readOnly = true)
    public Map<String, Object> freeLoad(Long studentId, String serverHost) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> l = jdbc.queryForList("SELECT project FROM mpblock_free WHERE student_id = :s", p("s", studentId), String.class);
        Object project = null;
        if (!l.isEmpty()) {
            try {
                project = json.readTree(l.get(0));
            } catch (Exception ignored) {
                // 坏了就从默认程序开始
            }
        }
        out.put("project", project);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("server", serverHost);
        ctx.put("label", "随时练习（自动保存）");
        ctx.put("fileName", "我的掌控板程序");
        ctx.put("topicSuggestions", new ArrayList<String>());
        out.put("context", ctx);
        out.put("canSubmit", false);
        return out;
    }

    @Transactional
    public Map<String, Object> freeSave(Long studentId, Object project, String code) {
        String text;
        try {
            text = project instanceof String ? (String) project : json.writeValueAsString(project);
        } catch (Exception e) {
            throw new IllegalArgumentException("程序格式不对");
        }
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("程序是空的");
        if (text.length() > MAX_PROJECT) throw new IllegalArgumentException("程序太大了（超过 2MB），图片换小一点");
        jdbc.update("INSERT INTO mpblock_free(student_id, project, code) VALUES (:s, :j, :c) "
                + "ON CONFLICT (student_id) DO UPDATE SET project = EXCLUDED.project, code = EXCLUDED.code, updated_at = now()",
                p("s", studentId, "j", text, "c", code));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", true);
        return out;
    }

    private Object project(Long lessonId, Long pageId, Long studentId) {
        // 只取本节课这一页的存档；没有时编辑器让学生选：老师给的起始程序 / 自己以前的程序 / 空白
        List<String> l = jdbc.queryForList("SELECT project FROM mpblock_work WHERE lesson_id = :l AND page_id = :p AND student_id = :s",
                p("l", lessonId, "p", pageId, "s", studentId), String.class);
        if (l.isEmpty()) return null;
        try {
            return json.readTree(l.get(0));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> context(Long lessonId, Long studentId, Map<String, Object> page, String serverHost) {
        Map<String, Object> l = lessons.get(lessonId);
        Map<String, String> v = lessons.gradingVars(lessonId, studentId);
        Map<String, Object> ctx = new LinkedHashMap<>();
        String proj = v.get("MQTT项目");
        int g = Integer.parseInt(v.get("组号"));
        ctx.put("mqttProject", proj);
        ctx.put("server", serverHost);
        ctx.put("groupNo", g);
        ctx.put("groupLabel", v.get("组名"));
        ctx.put("pairNo", v.get("配对号"));
        ctx.put("role", v.get("角色"));
        ctx.put("device", g > 0 ? String.format("G%02d", g) : "G01");
        List<String> topics = new ArrayList<>();
        if (proj != null && !proj.isEmpty()) {
            if (g > 0) topics.add(proj + "/" + String.format("G%02d", g));
            if (!v.get("角色").isEmpty()) topics.add(proj + "/Bell" + v.get("配对号"));
        }
        ctx.put("topicSuggestions", topics);
        ctx.put("label", l.get("courseName") + " · " + page.get("title") + (v.get("组名").isEmpty() ? "" : " · " + v.get("组名")));
        ctx.put("fileName", String.valueOf(page.get("title")));
        return ctx;
    }
}
