package com.ittools.grading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mind+ 程序（1.x 的 .mp、2.x 的 .mpcode）阅卷点。两种都是 zip，里面的 project.json 记着每个积木的名称（opcode）、参数和嵌套关系。
 * 只做静态检查（用了哪些积木、积木是否放在循环/条件里、填了什么数值和文字），不运行程序。
 */
final class MindPlusRules {
    private MindPlusRules() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static void register(Map<String, String[]> c) {
        c.put("MP_HAS_BLOCK", new String[]{"Mind+ 使用了积木", "积木名称（可写中文别名或 opcode 片段，多个用 | 分隔，全部用到才得分，如 getSoundIntensity|displayInLine）", "每个积木最少个数（默认 1）", "", "mp,mpcode"});
        c.put("MP_BLOCK_IN", new String[]{"Mind+ 积木放在指定积木里面", "里面的积木（如 getSoundIntensity）", "外层积木（如 control_loop、control_if）", "", "mp,mpcode"});
        c.put("MP_COMPARE_FILLED", new String[]{"Mind+ 比较积木已填入数值", "比较积木（默认 大于|小于）", "至少几个比较积木填了数值（默认 1）", "不算填写的默认值（如 50，Mind+ 2 的比较积木默认就是 50）", "mp,mpcode"});
        c.put("MP_VALUE", new String[]{"Mind+ 填写了数值或文字", "须出现的数值或文字（多个用 | 分隔，全部出现才得分，如 2000|警报）", "", "", "mp,mpcode"});
        c.put("MP_MQTT", new String[]{"Mind+ MQTT 设置正确（平台、服务器、主题）",
                "主题（可用 {{MQTT项目}} {{配对号}} {{组号}} {{角色}}，如 {{MQTT项目}}/Bell{{配对号}}；多个用 | 分隔，全部设置了才得分；留空不查主题）",
                "服务器地址（如 10.174.233.61；留空不查）", "平台（默认 siot；留空不查）", "mp,mpcode"});
    }

    /** 积木中文别名 → opcode 片段，便于老师在规则里直接写中文。 */
    private static final Map<String, String> ALIAS = new LinkedHashMap<>();
    static {
        ALIAS.put("声音强度", "getSoundIntensity");
        ALIAS.put("读取声音", "getSoundIntensity");
        ALIAS.put("光线强度", "readBrightness");
        ALIAS.put("读取光线", "readBrightness");
        ALIAS.put("屏幕显示", "displayInLine");
        ALIAS.put("设置灯颜色", "setLightsColor");
        ALIAS.put("关灯", "rgbDisable");
        ALIAS.put("循环", "control_loop|control_forever");   // Mind+ 1.x 是 control_loop，2.x 是 control_forever
        ALIAS.put("重复执行", "control_loop|control_forever");
        ALIAS.put("如果", "control_if");
        ALIAS.put("大于", "operator_gt");
        ALIAS.put("比较", "operator_");
        ALIAS.put("小于", "operator_lt");
        ALIAS.put("连接", "operator_join");
    }

    /** 解析后的程序：所有积木 + 所有填写过的文字/数值。 */
    static final class Program {
        final Map<String, JsonNode> blocks = new LinkedHashMap<>();
        final List<String> literals = new ArrayList<>();

        String opcode(String id) {
            JsonNode b = blocks.get(id);
            return b == null ? "" : b.path("opcode").asText("");
        }
    }

    static Program load(java.nio.file.Path path) throws Exception {
        Program p = new Program();
        try (ZipFile zf = new ZipFile(path.toFile())) {
            ZipEntry e = zf.getEntry("project.json");
            if (e == null) throw new IllegalStateException("不是 Mind+ 程序");
            JsonNode root;
            try (InputStream in = zf.getInputStream(e)) {
                root = MAPPER.readTree(in);
            }
            // Mind+ 1.x：{page0: {targets}, page1: {targets}, ...}；Mind+ 2.x：{targets}
            readTargets(root.path("targets"), p);
            Iterator<Map.Entry<String, JsonNode>> pages = root.fields();
            while (pages.hasNext()) {
                readTargets(pages.next().getValue().path("targets"), p);
            }
        }
        return p;
    }

    private static void readTargets(JsonNode targets, Program p) {
        if (!targets.isArray()) return;
        for (JsonNode t : targets) {
            Iterator<Map.Entry<String, JsonNode>> bs = t.path("blocks").fields();
            while (bs.hasNext()) {
                Map.Entry<String, JsonNode> en = bs.next();
                if (!en.getValue().isObject()) continue;
                p.blocks.put(en.getKey(), en.getValue());
                collectLiterals(en.getValue().path("inputs"), p.literals);
                Iterator<Map.Entry<String, JsonNode>> fs = en.getValue().path("fields").fields();
                while (fs.hasNext()) {
                    JsonNode f = fs.next().getValue();
                    if (f.isArray() && f.size() > 0 && f.get(0).isTextual()) p.literals.add(f.get(0).asText());
                }
            }
        }
    }

    /** 输入里形如 [类型码(4~10), "文字"] 的就是用户填的值；其余是对别的积木的引用。 */
    private static void collectLiterals(JsonNode n, List<String> out) {
        if (n == null) return;
        if (n.isArray()) {
            if (n.size() == 2 && n.get(0).isInt() && n.get(0).asInt() >= 4 && n.get(0).asInt() <= 10 && n.get(1).isValueNode()) {
                out.add(n.get(1).asText());
                return;
            }
            for (JsonNode c : n) collectLiterals(c, out);
        } else if (n.isObject()) {
            Iterator<JsonNode> it = n.elements();
            while (it.hasNext()) collectLiterals(it.next(), out);
        }
    }

    static String text(Program p) {
        StringBuilder b = new StringBuilder();
        for (JsonNode blk : p.blocks.values()) b.append(blk.path("opcode").asText("")).append('\n');
        for (String s : p.literals) b.append(s).append('\n');
        return b.toString();
    }

    private static String resolve(String key) {
        String k = key.trim();
        String a = ALIAS.get(k);
        return (a != null ? a : k).toLowerCase(Locale.ROOT);
    }

    private static boolean matches(String opcode, String frags) {
        String op = opcode.toLowerCase(Locale.ROOT);
        for (String f : frags.split("\\|")) if (op.contains(f)) return true;
        return false;
    }

    private static int count(Program p, String frag) {
        int n = 0;
        for (String id : p.blocks.keySet()) if (matches(p.opcode(id), frag)) n++;
        return n;
    }

    private static boolean hasAncestor(Program p, String id, String frag) {
        JsonNode b = p.blocks.get(id);
        int guard = 0;
        while (b != null && guard++ < 200) {
            String parent = b.path("parent").isTextual() ? b.path("parent").asText() : null;
            if (parent == null || parent.isEmpty()) return false;
            if (matches(p.opcode(parent), frag)) return true;
            b = p.blocks.get(parent);
        }
        return false;
    }

    static GradingEngine.Check check(Program p, GradingEngine.Rule r) {
        switch (r.type) {
            case "MP_HAS_BLOCK": {
                int min = 1;
                try { if (r.p2 != null && !r.p2.trim().isEmpty()) min = (int) Double.parseDouble(r.p2.trim()); } catch (NumberFormatException ignored) { /* 用默认值 */ }
                List<String> missing = new ArrayList<>();
                for (String k : r.p1.split("\\|")) {
                    if (k.trim().isEmpty()) continue;
                    if (count(p, resolve(k)) < min) missing.add(k.trim());
                }
                return new GradingEngine.Check(missing.isEmpty(), missing.isEmpty() ? "所需积木均已使用" : "缺少积木：" + String.join("、", missing));
            }
            case "MP_BLOCK_IN": {
                String child = resolve(r.p1);
                String outer = resolve(r.p2);
                boolean seen = false;
                for (String id : p.blocks.keySet()) {
                    if (!matches(p.opcode(id), child)) continue;
                    seen = true;
                    if (hasAncestor(p, id, outer)) return new GradingEngine.Check(true, r.p1.trim() + " 已放在 " + r.p2.trim() + " 里");
                }
                return new GradingEngine.Check(false, seen ? r.p1.trim() + " 没有放在 " + r.p2.trim() + " 里" : "没有用到 " + r.p1.trim());
            }
            case "MP_COMPARE_FILLED": {
                String spec = r.p1 == null || r.p1.trim().isEmpty() ? "大于|小于" : r.p1;
                int min = 1;
                try { if (r.p2 != null && !r.p2.trim().isEmpty()) min = (int) Double.parseDouble(r.p2.trim()); } catch (NumberFormatException ignored) { /* 用默认值 */ }
                List<String> frags = new ArrayList<>();
                for (String k : spec.split("\\|")) if (!k.trim().isEmpty()) frags.add(resolve(k));
                String ignored = r.p3 == null ? "" : r.p3.trim();
                int filled = 0, total = 0;
                for (Map.Entry<String, JsonNode> en : p.blocks.entrySet()) {
                    String op = p.opcode(en.getKey()).toLowerCase(Locale.ROOT);
                    boolean match = false;
                    for (String f : frags) if (op.contains(f)) { match = true; break; }
                    if (!match) continue;
                    total++;
                    List<String> lits = new ArrayList<>();
                    collectLiterals(en.getValue().path("inputs"), lits);
                    for (String l : lits) {
                        if (!ignored.isEmpty() && numEq(l, ignored)) continue;
                        try { Double.parseDouble(l.trim()); filled++; break; } catch (NumberFormatException e) { /* 不是数字 */ }
                    }
                }
                return new GradingEngine.Check(filled >= min, total == 0 ? "没有用到比较积木" : "共 " + total + " 个比较积木，" + filled + " 个填了数值");
            }
            case "MP_VALUE": {
                List<String> missing = new ArrayList<>();
                for (String k : r.p1.split("\\|")) {
                    String key = k.trim();
                    if (key.isEmpty()) continue;
                    boolean hit = false;
                    for (String lit : p.literals) {
                        if (lit.contains(key) || numEq(lit, key)) { hit = true; break; }
                    }
                    if (!hit) missing.add(key);
                }
                return new GradingEngine.Check(missing.isEmpty(), missing.isEmpty() ? "要求的数值/文字均已填写" : "没找到：" + String.join("、", missing));
            }
            case "MP_MQTT":
                return checkMqtt(p, r);
            default:
                return new GradingEngine.Check(false, "未知规则类型 " + r.type);
        }
    }

    /**
     * MQTT 设置：读“MQTT 初始化参数”里的 JSON（platform、params.siotwww、params.topic、topic_1~topic_4），
     * 主题也认“发送消息至 / 订阅 / 当接收到”积木里直接写的主题。占位符在规则保存的参数里已经按学生替换好了。
     */
    private static GradingEngine.Check checkMqtt(Program p, GradingEngine.Rule r) {
        java.util.Set<String> topics = new java.util.LinkedHashSet<>();
        java.util.Set<String> servers = new java.util.LinkedHashSet<>();
        java.util.Set<String> platforms = new java.util.LinkedHashSet<>();
        for (JsonNode b : p.blocks.values()) {
            String op = b.path("opcode").asText("");
            if ("mqtt_settings".equals(op)) {
                JsonNode f = b.path("fields").path("MQTT");
                String raw = f.isArray() && f.size() > 0 ? f.get(0).asText("") : "";
                try {
                    JsonNode s = MAPPER.readTree(raw);
                    if (s.hasNonNull("platform")) platforms.add(s.get("platform").asText());
                    JsonNode prm = s.path("params");
                    for (String k : new String[]{"topic", "topic_1", "topic_2", "topic_3", "topic_4", "topic1", "topic2", "topic3", "topic4"}) {
                        if (prm.hasNonNull(k) && !prm.get(k).asText().trim().isEmpty()) topics.add(prm.get(k).asText().trim());
                    }
                    if (prm.hasNonNull("siotwww")) servers.add(prm.get("siotwww").asText().trim());
                } catch (Exception ignored) { /* 设置框里不是 JSON：当没设置 */ }
            } else if (op.endsWith("mqtt_mqttSendToTopic") || op.endsWith("mqtt_mqttSendToTopic_save") || op.endsWith("mqtt_subscribe")
                    || op.endsWith("mqtt_whenReceivedTopicData") || op.endsWith("mqtt_acceptance_topic_meaage")) {
                List<String> lits = new ArrayList<>();
                collectLiterals(b.path("inputs").path("TOPIC"), lits);
                for (String l : lits) if (!l.trim().isEmpty()) topics.add(l.trim());
            }
        }
        if (platforms.isEmpty() && topics.isEmpty()) return new GradingEngine.Check(false, "没有用“MQTT 初始化参数”设置 MQTT");
        List<String> problems = new ArrayList<>();
        String wantPlat = r.p3 == null ? "" : r.p3.trim();
        if (!wantPlat.isEmpty() && platforms.stream().noneMatch(x -> x.equalsIgnoreCase(wantPlat))) {
            problems.add("平台应选 " + wantPlat + "（现在是 " + (platforms.isEmpty() ? "没设置" : String.join("、", platforms)) + "）");
        }
        String wantServer = r.p2 == null ? "" : r.p2.trim();
        if (!wantServer.isEmpty() && !servers.contains(wantServer)) {
            problems.add("服务器地址应为 " + wantServer + "（现在是 " + (servers.isEmpty() ? "空" : String.join("、", servers)) + "）");
        }
        if (r.p1 != null) {
            for (String t : r.p1.split("\\|")) {
                String want = t.trim();
                if (want.isEmpty()) continue;
                if (!topics.contains(want)) problems.add("主题应有 " + want + "（现在是 " + (topics.isEmpty() ? "没写" : String.join("、", topics)) + "）");
            }
        }
        return new GradingEngine.Check(problems.isEmpty(), problems.isEmpty() ? "MQTT 平台、服务器、主题都对" : String.join("；", problems));
    }

    private static boolean numEq(String a, String b) {
        try {
            return Math.abs(Double.parseDouble(a.trim()) - Double.parseDouble(b.trim())) < 1e-6;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
