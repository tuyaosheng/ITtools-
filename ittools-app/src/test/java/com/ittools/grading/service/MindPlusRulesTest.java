package com.ittools.grading.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** .mp 的两种结构：Mind+ 1.x（page1.targets、control_loop）和 Mind+ 2.x（targets、control_forever、比较积木默认 50）。 */
class MindPlusRulesTest {
    @TempDir
    Path dir;

    private static final String BLOCKS = "{\"f\":{\"opcode\":\"%s\",\"parent\":null,\"inputs\":{\"SUBSTACK\":[2,\"d\"]},\"fields\":{}},"
            + "\"d\":{\"opcode\":\"%sesp32_displayInLine\",\"parent\":\"f\",\"inputs\":{\"TEXT\":[3,\"r\",[10,\"x\"]]},\"fields\":{}},"
            + "\"r\":{\"opcode\":\"%sesp32_readBrightness\",\"parent\":\"d\",\"inputs\":{},\"fields\":{}},"
            + "\"i\":{\"opcode\":\"control_if_else\",\"parent\":\"d\",\"inputs\":{\"CONDITION\":[2,\"c\"],\"SUBSTACK\":[2,\"l\"]},\"fields\":{}},"
            + "\"c\":{\"opcode\":\"operator_lt\",\"parent\":\"i\",\"inputs\":{\"OPERAND2\":[1,[10,\"%s\"]]},\"fields\":{}},"
            + "\"l\":{\"opcode\":\"%sesp32_setLightsColor\",\"parent\":\"i\",\"inputs\":{},\"fields\":{}}}";

    private Path mp(String name, String projectJson) throws Exception {
        Path f = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(f); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("project.json"));
            z.write(projectJson.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return f;
    }

    private static String v2(String threshold) {
        String p = "dev-DFRobot-handpyEsp32@0.0.6_";
        return "{\"targets\":[{\"isStage\":true,\"blocks\":" + String.format(BLOCKS, "control_forever", p, p, threshold, p) + "}]}";
    }

    private static String v1(String threshold) {
        String p = "esp32.";
        return "{\"page1\":{\"targets\":[{\"blocks\":" + String.format(BLOCKS, "control_loop", p, p, threshold, p) + "}]},\"pageIndex\":1}";
    }

    private static double score(Path f, GradingEngine.Rule... rules) {
        return GradingEngine.grade(f, f.getFileName().toString(), Arrays.asList(rules)).score;
    }

    private static GradingEngine.Rule rule(String type, String a, String b, String c) {
        return new GradingEngine.Rule(type, a, b, c, 10, null);
    }

    @Test
    void readsBothFormats() throws Exception {
        for (String json : new String[]{v1("500"), v2("500")}) {
            Path f = mp("a.mp", json);
            assertThat(score(f, rule("MP_HAS_BLOCK", "光线强度|屏幕显示|设置灯颜色", null, null))).as(json).isEqualTo(10);
            assertThat(score(f, rule("MP_BLOCK_IN", "光线强度", "循环", null))).as(json).isEqualTo(10);
            assertThat(score(f, rule("MP_BLOCK_IN", "屏幕显示", "循环", null))).as(json).isEqualTo(10);
            assertThat(score(f, rule("MP_BLOCK_IN", "设置灯颜色", "control_if", null))).as(json).isEqualTo(10);
            assertThat(score(f, rule("MP_HAS_BLOCK", "声音强度", null, null))).as(json).isEqualTo(0);
        }
    }

    @Test
    void readsMpcodeExtension() throws Exception {
        Path f = mp("a.mpcode", v2("500"));
        assertThat(score(f, rule("MP_HAS_BLOCK", "光线强度", null, null))).isEqualTo(10);
        assertThat(score(f, rule("TEXT_CONTAINS", "readBrightness", null, null))).isEqualTo(10);
        assertThat(GradingEngine.CATALOG.get("MP_HAS_BLOCK")[4]).contains("mpcode");
    }

    @Test
    void acceptsLessOrGreaterThan() throws Exception {
        GradingEngine.Rule filled = rule("MP_COMPARE_FILLED", "大于|小于", "1", "50");
        assertThat(score(mp("a.mp", v2("500").replace("operator_lt", "operator_gt")), filled)).isEqualTo(10);
    }

    @Test
    void defaultThresholdIsNotFilled() throws Exception {
        GradingEngine.Rule filled = rule("MP_COMPARE_FILLED", "大于|小于", "1", "50");
        assertThat(score(mp("a.mp", v2("500")), filled)).isEqualTo(10);
        assertThat(score(mp("b.mp", v2("50")), filled)).as("默认 50 不算").isEqualTo(0);
        assertThat(score(mp("c.mp", v1("")), filled)).as("1.x 空白").isEqualTo(0);
        assertThat(score(mp("d.mp", v2("50")), rule("MP_COMPARE_FILLED", "大于|小于", "1", null))).as("不设默认值时 50 也算").isEqualTo(10);
    }
}
