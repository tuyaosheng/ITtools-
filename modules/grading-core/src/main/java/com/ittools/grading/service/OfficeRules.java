package com.ittools.grading.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.XDDFAreaChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDoughnutChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFRadarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFScatterChartData;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.sl.usermodel.PaintStyle;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XSLFHyperlink;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 细粒度的 Office 阅卷点（原 Word / Excel / PPT 操作题“检测项 + 参数 + 答案”）：
 * 针对某个段落、某个单元格（区域）、某张幻灯片检查格式与设置。
 * 段落定位：数字 = 第 N 个非空正文段落；文字 = 包含该文字的段落（只检查这段文字本身）；空或“全部” = 所有正文段落。
 */
final class OfficeRules {
    private OfficeRules() {}

    static void register(Map<String, String[]> c) {
        String loc = "段落（第几段，或段中文字，空=全部）";
        c.put("DOCX_PARA_FONT", new String[]{"Word 段落字体", loc, "字体（如 黑体、Times New Roman）", "", "docx"});
        c.put("DOCX_PARA_SIZE", new String[]{"Word 段落字号", loc, "字号（如 三号、小四、16）", "", "docx"});
        c.put("DOCX_PARA_BOLD", new String[]{"Word 段落加粗", loc, "是/否（默认 是）", "", "docx"});
        c.put("DOCX_PARA_ITALIC", new String[]{"Word 段落倾斜", loc, "是/否（默认 是）", "", "docx"});
        c.put("DOCX_PARA_UNDERLINE", new String[]{"Word 段落下划线", loc, "是/否（默认 是）", "", "docx"});
        c.put("DOCX_PARA_COLOR", new String[]{"Word 文字颜色", loc, "颜色（如 红色、FF0000）", "", "docx"});
        c.put("DOCX_PARA_ALIGN", new String[]{"Word 段落对齐", loc, "左对齐/居中/右对齐/两端对齐/分散对齐", "", "docx"});
        c.put("DOCX_PARA_INDENT", new String[]{"Word 首行缩进", loc, "字符数（如 2）", "", "docx"});
        c.put("DOCX_PARA_LINE_SPACING", new String[]{"Word 行距", loc, "行距（如 1.5倍、单倍、固定值20磅）", "", "docx"});
        c.put("DOCX_PARA_SPACE_BEFORE", new String[]{"Word 段前间距", loc, "间距（如 0.5行、6磅）", "", "docx"});
        c.put("DOCX_PARA_SPACE_AFTER", new String[]{"Word 段后间距", loc, "间距（如 0.5行、6磅）", "", "docx"});
        c.put("DOCX_PAGE_MARGIN", new String[]{"Word 页边距", "上/下/左/右", "厘米（如 2.5）", "", "docx"});
        c.put("DOCX_PAGE_ORIENT", new String[]{"Word 纸张方向", "横向/纵向", "", "", "docx"});
        c.put("DOCX_PAPER_SIZE", new String[]{"Word 纸张大小", "A4/A3/A5/B5/16开/Letter", "", "", "docx"});
        c.put("DOCX_HEADER_TEXT", new String[]{"Word 页眉文字", "页眉须包含的文字", "", "", "docx"});
        c.put("DOCX_FOOTER_TEXT", new String[]{"Word 页脚文字", "页脚须包含的文字", "", "", "docx"});
        c.put("DOCX_PAGE_NUMBER", new String[]{"Word 插入页码", "", "", "", "docx"});
        c.put("DOCX_COLUMNS", new String[]{"Word 分栏", "栏数（如 2）", "", "", "docx"});
        c.put("DOCX_TABLE_SIZE", new String[]{"Word 表格行列数", "第几个表格（空=第1个）", "行数", "列数", "docx"});

        String cell = "单元格或区域（如 A1、A1:E1、Sheet1!B2:B9）";
        c.put("XLSX_CELL_FONT", new String[]{"Excel 单元格字体", cell, "字体（如 黑体）", "", "xlsx"});
        c.put("XLSX_CELL_FONT_SIZE", new String[]{"Excel 单元格字号", cell, "字号（磅，如 16）", "", "xlsx"});
        c.put("XLSX_CELL_BOLD", new String[]{"Excel 单元格加粗", cell, "是/否（默认 是）", "", "xlsx"});
        c.put("XLSX_CELL_FONT_COLOR", new String[]{"Excel 文字颜色", cell, "颜色（如 红色、FF0000）", "", "xlsx"});
        c.put("XLSX_CELL_FILL", new String[]{"Excel 单元格底纹", cell, "颜色（如 黄色、FFFF00；空=有填充即可）", "", "xlsx"});
        c.put("XLSX_CELL_ALIGN", new String[]{"Excel 水平对齐", cell, "左对齐/居中/右对齐", "", "xlsx"});
        c.put("XLSX_CELL_NUMFMT", new String[]{"Excel 数字格式", cell, "格式（如 0.00、百分比、货币、日期）", "", "xlsx"});
        c.put("XLSX_MERGED", new String[]{"Excel 合并单元格", "区域（如 A1:E1）", "", "", "xlsx"});
        c.put("XLSX_BORDER", new String[]{"Excel 区域加边框", "区域（如 A2:E10）", "", "", "xlsx"});
        c.put("XLSX_SORTED", new String[]{"Excel 数据已排序", "列区域（如 E2:E20）", "升序/降序", "", "xlsx"});
        c.put("XLSX_AUTOFILTER", new String[]{"Excel 自动筛选", "工作表（空=第一个）", "", "", "xlsx"});
        c.put("XLSX_CHART_TYPE", new String[]{"Excel 图表类型", "类型（柱形/条形/折线/饼/散点/面积/圆环/雷达）", "工作表（空=任意）", "", "xlsx"});
        c.put("XLSX_SHEET_NAME", new String[]{"Excel 工作表名称", "名称（如 成绩表）", "", "", "xlsx"});
        c.put("XLSX_COND_FORMAT", new String[]{"Excel 条件格式", "区域（如 C2:C20）", "", "", "xlsx"});

        String slide = "第几张幻灯片（空/全部=任意一张）";
        c.put("PPTX_SLIDE_TEXT", new String[]{"PPT 幻灯片包含文字", slide, "文字", "", "pptx"});
        c.put("PPTX_TEXT_FONT", new String[]{"PPT 文字字体", slide, "定位文字", "字体（如 黑体）", "pptx"});
        c.put("PPTX_TEXT_SIZE", new String[]{"PPT 文字字号", slide, "定位文字", "字号（磅，如 40）", "pptx"});
        c.put("PPTX_TEXT_BOLD", new String[]{"PPT 文字加粗", slide, "定位文字", "是/否（默认 是）", "pptx"});
        c.put("PPTX_TEXT_COLOR", new String[]{"PPT 文字颜色", slide, "定位文字", "颜色（如 红色、FF0000）", "pptx"});
        c.put("PPTX_LAYOUT", new String[]{"PPT 幻灯片版式", "第几张幻灯片", "版式（如 标题幻灯片、标题和内容、空白）", "", "pptx"});
        c.put("PPTX_BACKGROUND", new String[]{"PPT 设置背景", slide, "", "", "pptx"});
        c.put("PPTX_TRANSITION_TYPE", new String[]{"PPT 切换效果类型", slide, "效果（如 淡出、推进、擦除、百叶窗）", "", "pptx"});
        c.put("PPTX_SLIDE_ANIMATION", new String[]{"PPT 幻灯片有动画", slide, "", "", "pptx"});
        c.put("PPTX_HYPERLINK", new String[]{"PPT 插入超链接", slide, "链接地址须包含（可空）", "", "pptx"});
        c.put("PPTX_SLIDE_IMAGES", new String[]{"PPT 幻灯片图片数", slide, "最少图片数", "", "pptx"});
        c.put("TEXT_NOT_CONTAINS", new String[]{"文本不包含（查找替换）", "不应出现的文字（多个用 | 分隔）", "", "", "docx,pptx,xlsx,txt,html,htm,py,csv,md"});
    }

    static GradingEngine.Check check(GradingEngine.Doc doc, GradingEngine.Rule r) throws Exception {
        String t = r.type;
        if (t.startsWith("DOCX_")) return docx(doc.docx(), r);
        if (t.startsWith("XLSX_")) return xlsx(doc, r);
        if (t.startsWith("PPTX_")) return pptx(doc, r);
        if ("TEXT_NOT_CONTAINS".equals(t)) {
            String text = doc.text();
            List<String> found = new ArrayList<>();
            for (String k : nz(r.p1).split("\\|")) if (!k.trim().isEmpty() && text.contains(k.trim())) found.add(k.trim());
            return new GradingEngine.Check(found.isEmpty(), found.isEmpty() ? "未出现" : "仍有：" + String.join("、", found));
        }
        return new GradingEngine.Check(false, "未知规则类型 " + t);
    }

    // ================= Word =================

    /** 定位到的段落及要检查的文字片段。 */
    private static final class Target {
        final DocxModel.Para para;
        final List<DocxModel.Run> runs;

        Target(DocxModel.Para para, List<DocxModel.Run> runs) {
            this.para = para;
            this.runs = runs;
        }
    }

    static List<Target> targets(DocxModel m, String loc) {
        List<Target> out = new ArrayList<>();
        String l = nz(loc).trim();
        List<DocxModel.Para> body = m.bodyParagraphs();
        if (l.isEmpty() || "全部".equals(l) || "*".equals(l)) {
            for (DocxModel.Para p : body) out.add(new Target(p, textRuns(p.runs)));
        } else if (l.matches("\\d+")) {
            int n = Integer.parseInt(l);
            if (n >= 1 && n <= body.size()) out.add(new Target(body.get(n - 1), textRuns(body.get(n - 1).runs)));
        } else {
            for (DocxModel.Para p : m.paragraphs) {
                int i = p.text.indexOf(l);
                if (i < 0) continue;
                List<DocxModel.Run> rs = new ArrayList<>();
                for (DocxModel.Run run : p.runs) {
                    int a = run.start, b = run.start + run.text.length();
                    if (b > i && a < i + l.length() && !run.text.trim().isEmpty()) rs.add(run);
                }
                out.add(new Target(p, rs));
                break;
            }
        }
        return out;
    }

    private static List<DocxModel.Run> textRuns(List<DocxModel.Run> runs) {
        List<DocxModel.Run> l = new ArrayList<>();
        for (DocxModel.Run r : runs) if (!r.text.trim().isEmpty()) l.add(r);
        return l;
    }

    private static String where(String loc) {
        String l = nz(loc).trim();
        if (l.isEmpty() || "全部".equals(l)) return "正文";
        return l.matches("\\d+") ? "第 " + l + " 段" : "「" + l + "」";
    }

    private interface RunTest {
        Object value(DocxModel m, DocxModel.Para p, DocxModel.Run r);
    }

    /** 所有目标文字都满足才通过；消息列出实际值。 */
    private static GradingEngine.Check eachRun(DocxModel m, String loc, RunTest get, java.util.function.Predicate<Object> ok, String label) {
        List<Target> ts = targets(m, loc);
        if (ts.isEmpty()) return new GradingEngine.Check(false, "找不到" + where(loc));
        Set<String> actual = new LinkedHashSet<>();
        boolean all = true;
        int n = 0;
        for (Target t : ts) {
            for (DocxModel.Run r : t.runs) {
                Object v = get.value(m, t.para, r);
                actual.add(String.valueOf(v));
                n++;
                if (!ok.test(v)) all = false;
            }
        }
        if (n == 0) return new GradingEngine.Check(false, where(loc) + "没有文字");
        return new GradingEngine.Check(all, where(loc) + label + "：" + String.join("、", limit(actual)));
    }

    private static GradingEngine.Check eachPara(DocxModel m, String loc, java.util.function.Function<DocxModel.Para, Object> get,
                                                java.util.function.Predicate<Object> ok, String label) {
        List<Target> ts = targets(m, loc);
        if (ts.isEmpty()) return new GradingEngine.Check(false, "找不到" + where(loc));
        Set<String> actual = new LinkedHashSet<>();
        boolean all = true;
        for (Target t : ts) {
            Object v = get.apply(t.para);
            actual.add(String.valueOf(v));
            if (!ok.test(v)) all = false;
        }
        return new GradingEngine.Check(all, where(loc) + label + "：" + String.join("、", limit(actual)));
    }

    private static GradingEngine.Check docx(DocxModel m, GradingEngine.Rule r) {
        switch (r.type) {
            case "DOCX_PARA_FONT": {
                String want = nz(r.p2).trim();
                return eachRun(m, r.p1, (mm, p, run) -> {
                    String ea = mm.font(p, run, true), as = mm.font(p, run, false);
                    String f = containsChinese(run.text) ? (ea != null ? ea : as) : (as != null ? as : ea);
                    return f == null ? "默认字体（未设置）" : f;
                }, v -> fontEquals(String.valueOf(v), want), "字体");
            }
            case "DOCX_PARA_SIZE": {
                double want = fontSize(r.p2);
                return eachRun(m, r.p1, (mm, p, run) -> fmt(mm.size(p, run)) + "磅", v -> Math.abs(parseNum(v) - want) < 0.26, "字号");
            }
            case "DOCX_PARA_BOLD":
                return flagRule(m, r, "b", "加粗");
            case "DOCX_PARA_ITALIC":
                return flagRule(m, r, "i", "倾斜");
            case "DOCX_PARA_UNDERLINE":
                return flagRule(m, r, "u", "下划线");
            case "DOCX_PARA_COLOR": {
                String want = nz(r.p2).trim();
                return eachRun(m, r.p1, (mm, p, run) -> mm.color(p, run), v -> colorMatches(String.valueOf(v), want), "颜色");
            }
            case "DOCX_PARA_ALIGN": {
                String want = alignLabel(wordAlign(r.p2).iterator().next());
                return eachPara(m, r.p1, p -> alignLabel(m.align(p)), v -> want.equals(v), "对齐方式");
            }
            case "DOCX_PARA_INDENT": {
                double want = parseNum(r.p2);
                return eachPara(m, r.p1, p -> fmt(m.firstLineChars(p)) + "字符", v -> Math.abs(parseNum(v) - want) < 0.3, "首行缩进");
            }
            case "DOCX_PARA_LINE_SPACING": {
                Object[] want = parseLineSpacing(r.p2);
                return eachPara(m, r.p1, p -> {
                    Object[] ls = m.lineSpacing(p);
                    return "auto".equals(ls[0]) ? fmt((Double) ls[1]) + "倍" : ("exact".equals(ls[0]) ? "固定值" : "最小值") + fmt((Double) ls[1]) + "磅";
                }, v -> {
                    String s = String.valueOf(v);
                    if ("auto".equals(want[0])) return s.endsWith("倍") && Math.abs(parseNum(s) - (Double) want[1]) < 0.06;
                    String pre = "exact".equals(want[0]) ? "固定值" : "最小值";
                    return s.startsWith(pre) && Math.abs(parseNum(s) - (Double) want[1]) < 0.6;
                }, "行距");
            }
            case "DOCX_PARA_SPACE_BEFORE":
            case "DOCX_PARA_SPACE_AFTER": {
                boolean before = r.type.endsWith("BEFORE");
                String w = nz(r.p2).trim();
                String unit = w.contains("行") ? "行" : "磅";
                double want = parseNum(w);
                return eachPara(m, r.p1, p -> {
                    Object[] sp = m.paraSpace(p, before);
                    return fmt((Double) sp[1]) + sp[0];
                }, v -> {
                    String s = String.valueOf(v);
                    if (s.endsWith(unit)) return Math.abs(parseNum(s) - want) < ("行".equals(unit) ? 0.06 : 0.6);
                    // 行与磅互换（1 行按 12 磅估算）
                    double pt = s.endsWith("行") ? parseNum(s) * 12 : parseNum(s);
                    double wantPt = "行".equals(unit) ? want * 12 : want;
                    return Math.abs(pt - wantPt) < 1.0;
                }, before ? "段前" : "段后");
            }
            case "DOCX_PAGE_MARGIN": {
                String side = side(r.p1);
                if (side == null) return new GradingEngine.Check(false, "参数1 应为 上/下/左/右");
                double v = m.margin(side);
                return new GradingEngine.Check(Math.abs(v - parseNum(r.p2)) < 0.06, nz(r.p1).trim() + "边距 " + fmt(v) + " 厘米");
            }
            case "DOCX_PAGE_ORIENT": {
                boolean land = (Boolean) m.pageSize()[2];
                boolean want = nz(r.p1).contains("横");
                return new GradingEngine.Check(land == want, "纸张方向：" + (land ? "横向" : "纵向"));
            }
            case "DOCX_PAPER_SIZE": {
                Object[] ps = m.pageSize();
                double w = Math.min((Double) ps[0], (Double) ps[1]), h = Math.max((Double) ps[0], (Double) ps[1]);
                String name = paperName(w, h);
                String want = nz(r.p1).trim().toUpperCase(Locale.ROOT).replace("纸", "");
                return new GradingEngine.Check(name.equalsIgnoreCase(want), "纸张：" + name);
            }
            case "DOCX_HEADER_TEXT":
            case "DOCX_FOOTER_TEXT": {
                List<String> texts = r.type.contains("HEADER") ? m.headerTexts : m.footerTexts;
                String want = nz(r.p1).trim();
                boolean ok = false;
                for (String s : texts) if (!s.isEmpty() && (want.isEmpty() || s.contains(want))) ok = true;
                String all = String.join(" / ", texts).trim();
                return new GradingEngine.Check(ok, (r.type.contains("HEADER") ? "页眉" : "页脚") + "：" + (all.isEmpty() ? "（无）" : cut(all, 40)));
            }
            case "DOCX_PAGE_NUMBER":
                return new GradingEngine.Check(m.pageNumber, m.pageNumber ? "已插入页码" : "未插入页码");
            case "DOCX_COLUMNS": {
                int n = m.columns();
                return new GradingEngine.Check(n == (int) parseNum(r.p1), "分栏 " + n + " 栏");
            }
            case "DOCX_TABLE_SIZE": {
                int idx = nz(r.p1).trim().isEmpty() ? 1 : (int) parseNum(r.p1);
                if (idx < 1 || idx > m.tables.size()) return new GradingEngine.Check(false, "没有第 " + idx + " 个表格");
                int[] sz = m.tableSize(m.tables.get(idx - 1));
                boolean ok = (nz(r.p2).trim().isEmpty() || sz[0] == (int) parseNum(r.p2)) && (nz(r.p3).trim().isEmpty() || sz[1] == (int) parseNum(r.p3));
                return new GradingEngine.Check(ok, "表格 " + sz[0] + " 行 " + sz[1] + " 列");
            }
            default:
                return new GradingEngine.Check(false, "未知规则类型 " + r.type);
        }
    }

    private static GradingEngine.Check flagRule(DocxModel m, GradingEngine.Rule r, String el, String label) {
        boolean want = yes(r.p2);
        return eachRun(m, r.p1, (mm, p, run) -> mm.flag(p, run, el) ? "是" : "否", v -> "是".equals(v) == want, label);
    }

    // ================= Excel =================

    private static final class Range {
        final Sheet sheet;
        final CellRangeAddress addr;

        Range(Sheet sheet, CellRangeAddress addr) {
            this.sheet = sheet;
            this.addr = addr;
        }
    }

    private static Range range(Workbook wb, String spec) {
        String s = nz(spec).trim().replace("$", "");
        Sheet sh = wb.getSheetAt(0);
        int bang = s.lastIndexOf('!');
        if (bang > 0) {
            String name = s.substring(0, bang).replace("'", "");
            sh = wb.getSheet(name);
            if (sh == null) throw new IllegalArgumentException("找不到工作表 " + name);
            s = s.substring(bang + 1);
        }
        return new Range(sh, CellRangeAddress.valueOf(s.contains(":") ? s : s + ":" + s));
    }

    private interface CellTest {
        Object value(Workbook wb, Cell c);
    }

    private static GradingEngine.Check eachCell(Workbook wb, String spec, CellTest get, java.util.function.Predicate<Object> ok, String label) {
        Range rg = range(wb, spec);
        Set<String> actual = new LinkedHashSet<>();
        boolean all = true;
        for (int r = rg.addr.getFirstRow(); r <= rg.addr.getLastRow(); r++) {
            Row row = rg.sheet.getRow(r);
            for (int c = rg.addr.getFirstColumn(); c <= rg.addr.getLastColumn(); c++) {
                Cell cell = row == null ? null : row.getCell(c);
                Object v = cell == null ? null : get.value(wb, cell);
                actual.add(String.valueOf(v == null ? "（无）" : v));
                if (v == null || !ok.test(v)) all = false;
            }
        }
        return new GradingEngine.Check(all, nz(spec).trim() + " " + label + "：" + String.join("、", limit(actual)));
    }

    private static GradingEngine.Check xlsx(GradingEngine.Doc doc, GradingEngine.Rule r) throws Exception {
        Workbook wb = doc.excel();
        switch (r.type) {
            case "XLSX_CELL_FONT": {
                String want = nz(r.p2).trim();
                return eachCell(wb, r.p1, (w, c) -> w.getFontAt(c.getCellStyle().getFontIndex()).getFontName(), v -> fontEquals(String.valueOf(v), want), "字体");
            }
            case "XLSX_CELL_FONT_SIZE": {
                double want = fontSize(r.p2);
                return eachCell(wb, r.p1, (w, c) -> fmt(w.getFontAt(c.getCellStyle().getFontIndex()).getFontHeightInPoints()) + "磅",
                        v -> Math.abs(parseNum(v) - want) < 0.26, "字号");
            }
            case "XLSX_CELL_BOLD": {
                boolean want = yes(r.p2);
                return eachCell(wb, r.p1, (w, c) -> w.getFontAt(c.getCellStyle().getFontIndex()).getBold() ? "是" : "否", v -> "是".equals(v) == want, "加粗");
            }
            case "XLSX_CELL_FONT_COLOR": {
                String want = nz(r.p2).trim();
                return eachCell(wb, r.p1, (w, c) -> {
                    Font f = w.getFontAt(c.getCellStyle().getFontIndex());
                    return f instanceof XSSFFont ? hex(((XSSFFont) f).getXSSFColor()) : "auto";
                }, v -> colorMatches(String.valueOf(v), want), "文字颜色");
            }
            case "XLSX_CELL_FILL": {
                String want = nz(r.p2).trim();
                return eachCell(wb, r.p1, (w, c) -> {
                    CellStyle st = c.getCellStyle();
                    if (st.getFillPattern() == null || st.getFillPattern().getCode() == 0) return "无";
                    return st instanceof XSSFCellStyle ? hex(((XSSFCellStyle) st).getFillForegroundXSSFColor()) : "有";
                }, v -> !"无".equals(v) && (want.isEmpty() || colorMatches(String.valueOf(v), want)), "底纹");
            }
            case "XLSX_CELL_ALIGN": {
                Set<HorizontalAlignment> want = cellAlign(r.p2);
                return eachCell(wb, r.p1, (w, c) -> c.getCellStyle().getAlignment(), v -> want.contains(v), "对齐");
            }
            case "XLSX_CELL_NUMFMT": {
                String want = nz(r.p2).trim();
                return eachCell(wb, r.p1, (w, c) -> c.getCellStyle().getDataFormatString(), v -> numFmtMatches(String.valueOf(v), want), "数字格式");
            }
            case "XLSX_MERGED": {
                Range rg = range(wb, r.p1);
                for (CellRangeAddress a : rg.sheet.getMergedRegions()) {
                    if (a.formatAsString().equals(rg.addr.formatAsString())) return new GradingEngine.Check(true, "已合并 " + a.formatAsString());
                }
                List<String> ms = new ArrayList<>();
                for (CellRangeAddress a : rg.sheet.getMergedRegions()) ms.add(a.formatAsString());
                return new GradingEngine.Check(false, "未合并 " + rg.addr.formatAsString() + (ms.isEmpty() ? "" : "（已合并：" + String.join("、", limit(ms)) + "）"));
            }
            case "XLSX_BORDER": {
                Range rg = range(wb, r.p1);
                int missing = 0, total = 0;
                for (int i = rg.addr.getFirstRow(); i <= rg.addr.getLastRow(); i++) {
                    Row row = rg.sheet.getRow(i);
                    for (int j = rg.addr.getFirstColumn(); j <= rg.addr.getLastColumn(); j++) {
                        total++;
                        Cell c = row == null ? null : row.getCell(j);
                        CellStyle st = c == null ? null : c.getCellStyle();
                        if (st == null || st.getBorderTop() == BorderStyle.NONE || st.getBorderBottom() == BorderStyle.NONE
                                || st.getBorderLeft() == BorderStyle.NONE || st.getBorderRight() == BorderStyle.NONE) missing++;
                    }
                }
                return new GradingEngine.Check(missing == 0, missing == 0 ? "区域全部有边框" : total + " 个单元格中 " + missing + " 个缺少边框");
            }
            case "XLSX_SORTED": {
                Range rg = range(wb, r.p1);
                boolean desc = nz(r.p2).contains("降");
                List<Object> vals = new ArrayList<>();
                int col = rg.addr.getFirstColumn();
                for (int i = rg.addr.getFirstRow(); i <= rg.addr.getLastRow(); i++) {
                    Row row = rg.sheet.getRow(i);
                    Cell c = row == null ? null : row.getCell(col);
                    if (c == null) continue;
                    CellType ct = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
                    if (ct == CellType.NUMERIC) vals.add(c.getNumericCellValue());
                    else if (ct == CellType.STRING && !c.getStringCellValue().trim().isEmpty()) vals.add(c.getStringCellValue());
                }
                if (vals.size() < 2) return new GradingEngine.Check(false, "区域数据不足");
                boolean ok = true;
                for (int i = 1; i < vals.size(); i++) {
                    int cmp = compare(vals.get(i - 1), vals.get(i));
                    if (desc ? cmp < 0 : cmp > 0) ok = false;
                }
                return new GradingEngine.Check(ok, ok ? "已按" + (desc ? "降序" : "升序") + "排列" : "未按" + (desc ? "降序" : "升序") + "排列");
            }
            case "XLSX_AUTOFILTER": {
                Sheet sh = nz(r.p1).trim().isEmpty() ? wb.getSheetAt(0) : wb.getSheet(r.p1.trim());
                if (sh == null) return new GradingEngine.Check(false, "找不到工作表 " + r.p1);
                boolean ok = sh instanceof XSSFSheet && ((XSSFSheet) sh).getCTWorksheet().isSetAutoFilter();
                return new GradingEngine.Check(ok, ok ? "已设置自动筛选" : "未设置自动筛选");
            }
            case "XLSX_CHART_TYPE": {
                String want = chartKind(r.p1);
                List<String> found = new ArrayList<>();
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    Sheet sh = wb.getSheetAt(i);
                    if (!nz(r.p2).trim().isEmpty() && !sh.getSheetName().equals(r.p2.trim())) continue;
                    if (!(sh instanceof XSSFSheet)) continue;
                    XSSFDrawing d = ((XSSFSheet) sh).getDrawingPatriarch();
                    if (d == null) continue;
                    for (XSSFChart ch : d.getCharts()) for (XDDFChartData data : ch.getChartSeries()) found.add(chartName(data));
                }
                boolean ok = found.contains(want);
                return new GradingEngine.Check(ok, found.isEmpty() ? "没有图表" : "图表类型：" + String.join("、", limit(new ArrayList<>(new LinkedHashSet<>(found)))));
            }
            case "XLSX_SHEET_NAME": {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < wb.getNumberOfSheets(); i++) names.add(wb.getSheetName(i));
                return new GradingEngine.Check(names.contains(nz(r.p1).trim()), "工作表：" + String.join("、", names));
            }
            case "XLSX_COND_FORMAT": {
                Range rg = range(wb, r.p1);
                org.apache.poi.ss.usermodel.SheetConditionalFormatting cf = rg.sheet.getSheetConditionalFormatting();
                for (int i = 0; i < cf.getNumConditionalFormattings(); i++) {
                    for (CellRangeAddress a : cf.getConditionalFormattingAt(i).getFormattingRanges()) {
                        if (a.intersects(rg.addr)) return new GradingEngine.Check(true, "已设置条件格式 " + a.formatAsString());
                    }
                }
                return new GradingEngine.Check(false, rg.addr.formatAsString() + " 未设置条件格式");
            }
            default:
                return new GradingEngine.Check(false, "未知规则类型 " + r.type);
        }
    }

    @SuppressWarnings("unchecked")
    private static int compare(Object a, Object b) {
        if (a instanceof Double && b instanceof Double) return Double.compare((Double) a, (Double) b);
        return java.text.Collator.getInstance(Locale.CHINA).compare(String.valueOf(a), String.valueOf(b));
    }

    static String chartName(XDDFChartData d) {
        if (d instanceof XDDFBarChartData) return ((XDDFBarChartData) d).getBarDirection() == BarDirection.BAR ? "条形" : "柱形";
        if (d instanceof XDDFLineChartData) return "折线";
        if (d instanceof XDDFPieChartData) return "饼";
        if (d instanceof XDDFDoughnutChartData) return "圆环";
        if (d instanceof XDDFScatterChartData) return "散点";
        if (d instanceof XDDFAreaChartData) return "面积";
        if (d instanceof XDDFRadarChartData) return "雷达";
        return "其他";
    }

    private static String chartKind(String s) {
        String v = nz(s).trim().toLowerCase(Locale.ROOT);
        if (v.contains("柱") || v.contains("column")) return "柱形";
        if (v.contains("条") || v.equals("bar")) return "条形";
        if (v.contains("折") || v.contains("line")) return "折线";
        if (v.contains("环") || v.contains("doughnut")) return "圆环";
        if (v.contains("饼") || v.contains("pie")) return "饼";
        if (v.contains("散") || v.contains("scatter") || v.contains("xy")) return "散点";
        if (v.contains("面积") || v.contains("area")) return "面积";
        if (v.contains("雷达") || v.contains("radar")) return "雷达";
        return v;
    }

    static String hex(XSSFColor c) {
        if (c == null) return "auto";
        String argb = c.getARGBHex();
        if (argb == null) return c.isThemed() ? "主题色" + c.getTheme() : "auto";
        return argb.length() == 8 ? argb.substring(2) : argb;
    }

    private static boolean numFmtMatches(String fmt, String want) {
        String f = fmt.toLowerCase(Locale.ROOT);
        String w = want.toLowerCase(Locale.ROOT);
        if (w.isEmpty()) return !"general".equals(f);
        if (w.contains("百分")) return f.contains("%");
        if (w.contains("货币") || w.contains("¥") || w.contains("￥")) return f.contains("¥") || f.contains("￥") || f.contains("$") || f.contains("[$");
        if (w.contains("日期")) return f.contains("y") || f.contains("m/d") || f.contains("d");
        if (w.contains("文本")) return "@".equals(f);
        if (w.matches("\\d+位小数")) return f.matches(".*0\\.0{" + w.replaceAll("\\D", "") + "}(?!0).*");
        return f.contains(w);
    }

    private static Set<HorizontalAlignment> cellAlign(String s) {
        String v = nz(s).trim();
        Set<HorizontalAlignment> set = new LinkedHashSet<>();
        if (v.contains("中")) {
            set.add(HorizontalAlignment.CENTER);
            set.add(HorizontalAlignment.CENTER_SELECTION);
        } else if (v.contains("右")) set.add(HorizontalAlignment.RIGHT);
        else if (v.contains("左")) set.add(HorizontalAlignment.LEFT);
        else if (v.contains("两端")) set.add(HorizontalAlignment.JUSTIFY);
        else if (v.contains("分散")) set.add(HorizontalAlignment.DISTRIBUTED);
        else if (v.contains("填充")) set.add(HorizontalAlignment.FILL);
        else set.add(HorizontalAlignment.GENERAL);
        return set;
    }

    // ================= PPT =================

    private static List<XSLFSlide> slides(GradingEngine.Doc doc, String spec) throws Exception {
        List<XSLFSlide> all = doc.ppt().getSlides();
        String s = nz(spec).trim();
        if (s.isEmpty() || "全部".equals(s) || "*".equals(s)) return all;
        int n = (int) parseNum(s);
        List<XSLFSlide> l = new ArrayList<>();
        if (n >= 1 && n <= all.size()) l.add(all.get(n - 1));
        return l;
    }

    private static String slideWhere(String spec) {
        String s = nz(spec).trim();
        return s.isEmpty() || "全部".equals(s) ? "各幻灯片" : "第 " + s + " 张";
    }

    private static boolean any(String spec) {
        String s = nz(spec).trim();
        return s.isEmpty() || "全部".equals(s) || "*".equals(s);
    }

    private static List<XSLFTextRun> runs(XSLFSlide slide, String locate) {
        List<XSLFTextRun> out = new ArrayList<>();
        String loc = nz(locate).trim();
        for (XSLFShape sh : slide.getShapes()) {
            if (!(sh instanceof XSLFTextShape)) continue;
            for (XSLFTextParagraph p : ((XSLFTextShape) sh).getTextParagraphs()) {
                String pt = p.getText();
                if (!loc.isEmpty() && (pt == null || !pt.contains(loc))) continue;
                int idx = loc.isEmpty() ? 0 : pt.indexOf(loc);
                int pos = 0;
                for (XSLFTextRun r : p.getTextRuns()) {
                    String t = r.getRawText() == null ? "" : r.getRawText();
                    int a = pos, b = pos + t.length();
                    pos = b;
                    if (t.trim().isEmpty()) continue;
                    if (loc.isEmpty() || (b > idx && a < idx + loc.length())) out.add(r);
                }
            }
        }
        return out;
    }

    private interface PptTest {
        Object value(XSLFTextRun r);
    }

    private static GradingEngine.Check eachText(GradingEngine.Doc doc, GradingEngine.Rule r, PptTest get, java.util.function.Predicate<Object> ok, String label) throws Exception {
        List<XSLFSlide> ss = slides(doc, r.p1);
        if (ss.isEmpty()) return new GradingEngine.Check(false, "没有" + slideWhere(r.p1) + "幻灯片");
        Set<String> actual = new LinkedHashSet<>();
        boolean anyOk = false;
        for (XSLFSlide s : ss) {
            List<XSLFTextRun> rs = runs(s, r.p2);
            if (rs.isEmpty()) continue;
            boolean all = true;
            for (XSLFTextRun run : rs) {
                Object v = get.value(run);
                actual.add(String.valueOf(v));
                if (!ok.test(v)) all = false;
            }
            if (all) anyOk = true;
        }
        if (actual.isEmpty()) return new GradingEngine.Check(false, slideWhere(r.p1) + "找不到文字「" + nz(r.p2) + "」");
        return new GradingEngine.Check(anyOk, "「" + nz(r.p2) + "」" + label + "：" + String.join("、", limit(actual)));
    }

    static String pptColor(XSLFTextRun r) {
        PaintStyle ps = r.getFontColor();
        if (ps instanceof PaintStyle.SolidPaint) {
            Color c = ((PaintStyle.SolidPaint) ps).getSolidColor().getColor();
            if (c != null) return String.format("%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
        }
        return "auto";
    }

    private static GradingEngine.Check pptx(GradingEngine.Doc doc, GradingEngine.Rule r) throws Exception {
        switch (r.type) {
            case "PPTX_SLIDE_TEXT": {
                String want = nz(r.p2).trim();
                for (XSLFSlide s : slides(doc, r.p1)) if (slideText(s).contains(want)) return new GradingEngine.Check(true, slideWhere(r.p1) + "包含「" + want + "」");
                return new GradingEngine.Check(false, slideWhere(r.p1) + "没有「" + want + "」");
            }
            case "PPTX_TEXT_FONT": {
                String want = nz(r.p3).trim();
                return eachText(doc, r, run -> run.getFontFamily(), v -> fontEquals(String.valueOf(v), want), "字体");
            }
            case "PPTX_TEXT_SIZE": {
                double want = fontSize(r.p3);
                return eachText(doc, r, run -> run.getFontSize() == null ? "18磅" : fmt(run.getFontSize()) + "磅", v -> Math.abs(parseNum(v) - want) < 0.6, "字号");
            }
            case "PPTX_TEXT_BOLD": {
                boolean want = yes(r.p3);
                return eachText(doc, r, run -> run.isBold() ? "是" : "否", v -> "是".equals(v) == want, "加粗");
            }
            case "PPTX_TEXT_COLOR": {
                String want = nz(r.p3).trim();
                return eachText(doc, r, OfficeRules::pptColor, v -> colorMatches(String.valueOf(v), want), "颜色");
            }
            case "PPTX_LAYOUT": {
                List<XSLFSlide> ss = slides(doc, r.p1);
                if (ss.isEmpty() || any(r.p1)) return new GradingEngine.Check(false, "请指定第几张幻灯片");
                XSLFSlideLayout lay = ss.get(0).getSlideLayout();
                String name = layoutName(lay);
                return new GradingEngine.Check(layoutMatches(lay, nz(r.p2).trim()), "版式：" + name);
            }
            case "PPTX_BACKGROUND": {
                int n = 0;
                List<XSLFSlide> ss = slides(doc, r.p1);
                for (XSLFSlide s : ss) if (s.getXmlObject().getCSld().isSetBg()) n++;
                boolean ok = any(r.p1) ? n > 0 : n == ss.size() && n > 0;
                return new GradingEngine.Check(ok, n + " 张幻灯片设置了背景");
            }
            case "PPTX_TRANSITION_TYPE": {
                Set<String> want = transitionKeys(nz(r.p2).trim());
                List<String> found = new ArrayList<>();
                boolean ok = false;
                for (XSLFSlide s : slides(doc, r.p1)) {
                    String t = transitionOf(s);
                    if (t == null) continue;
                    found.add(transitionLabel(t));
                    if (want.contains(t.toLowerCase(Locale.ROOT))) ok = true;
                }
                return new GradingEngine.Check(ok, found.isEmpty() ? "未设置切换效果" : "切换效果：" + String.join("、", limit(new ArrayList<>(new LinkedHashSet<>(found)))));
            }
            case "PPTX_SLIDE_ANIMATION": {
                int n = 0;
                for (XSLFSlide s : slides(doc, r.p1)) if (s.getXmlObject().isSetTiming() && s.getXmlObject().getTiming().xmlText().contains("presetClass")) n++;
                return new GradingEngine.Check(n > 0, slideWhere(r.p1) + (n > 0 ? "有动画" : "没有动画"));
            }
            case "PPTX_HYPERLINK": {
                String want = nz(r.p2).trim();
                List<String> links = new ArrayList<>();
                for (XSLFSlide s : slides(doc, r.p1)) {
                    for (XSLFTextRun run : runs(s, "")) {
                        XSLFHyperlink h = run.getHyperlink();
                        if (h != null) links.add(h.getAddress() == null ? "（幻灯片内跳转）" : h.getAddress());
                    }
                    for (XSLFShape sh : s.getShapes()) {
                        if (sh instanceof org.apache.poi.xslf.usermodel.XSLFSimpleShape) {
                            XSLFHyperlink h = ((org.apache.poi.xslf.usermodel.XSLFSimpleShape) sh).getHyperlink();
                            if (h != null) links.add(h.getAddress() == null ? "（幻灯片内跳转）" : h.getAddress());
                        }
                    }
                }
                boolean ok = false;
                for (String l : links) if (want.isEmpty() || l.contains(want)) ok = true;
                return new GradingEngine.Check(ok, links.isEmpty() ? "没有超链接" : "超链接：" + String.join("、", limit(links)));
            }
            case "PPTX_SLIDE_IMAGES": {
                int need = nz(r.p2).trim().isEmpty() ? 1 : (int) parseNum(r.p2);
                int best = 0;
                for (XSLFSlide s : slides(doc, r.p1)) {
                    int n = 0;
                    for (XSLFShape sh : s.getShapes()) if (sh instanceof XSLFPictureShape) n++;
                    best = Math.max(best, n);
                }
                return new GradingEngine.Check(best >= need, slideWhere(r.p1) + "图片最多 " + best + " 张");
            }
            default:
                return new GradingEngine.Check(false, "未知规则类型 " + r.type);
        }
    }

    static String slideText(XSLFSlide s) {
        StringBuilder b = new StringBuilder();
        for (XSLFShape sh : s.getShapes()) if (sh instanceof XSLFTextShape) b.append(((XSLFTextShape) sh).getText()).append('\n');
        return b.toString();
    }

    private static final Map<SlideLayout, String> LAYOUT_NAMES = new HashMap<>();
    static {
        LAYOUT_NAMES.put(SlideLayout.TITLE, "标题幻灯片");
        LAYOUT_NAMES.put(SlideLayout.TITLE_AND_CONTENT, "标题和内容");
        LAYOUT_NAMES.put(SlideLayout.SECTION_HEADER, "节标题");
        LAYOUT_NAMES.put(SlideLayout.TWO_OBJ, "两栏内容");
        LAYOUT_NAMES.put(SlideLayout.TWO_TX_TWO_OBJ, "比较");
        LAYOUT_NAMES.put(SlideLayout.TITLE_ONLY, "仅标题");
        LAYOUT_NAMES.put(SlideLayout.BLANK, "空白");
        LAYOUT_NAMES.put(SlideLayout.OBJ_TX, "内容与标题");
        LAYOUT_NAMES.put(SlideLayout.PIC_TX, "图片与标题");
        LAYOUT_NAMES.put(SlideLayout.VERT_TX, "标题和竖排文字");
    }

    static String layoutName(XSLFSlideLayout lay) {
        if (lay == null) return "（无）";
        String std = lay.getType() == null ? null : LAYOUT_NAMES.get(lay.getType());
        return std != null ? std : lay.getName();
    }

    private static boolean layoutMatches(XSLFSlideLayout lay, String want) {
        if (lay == null || want.isEmpty()) return false;
        String std = lay.getType() == null ? null : LAYOUT_NAMES.get(lay.getType());
        String name = lay.getName() == null ? "" : lay.getName();
        return want.equals(std) || name.contains(want) || name.equalsIgnoreCase(want);
    }

    private static final Pattern TRANSITION_CHILD = Pattern.compile("<(?:\\w+:)?(\\w+)[\\s/>]");

    /** 切换效果：取 transition 下第一个效果元素名（如 fade、push、wipe、blinds，2010 版效果如 vortex）。 */
    static String transitionOf(XSLFSlide s) {
        // 2010 版新效果写在 mc:AlternateContent 中，直接从幻灯片 XML 中查找 transition 元素
        return transitionOfXml(s.getXmlObject().xmlText());
    }

    static String transitionOfXml(String xml) {
        Matcher m = Pattern.compile("<(?:\\w+:)?transition[\\s>/]").matcher(xml);
        if (!m.find()) return null;
        int gt = xml.indexOf('>', m.start());
        if (gt < 0 || xml.charAt(gt - 1) == '/') return "cut";          // <p:transition/> 无具体效果
        int end = xml.indexOf("transition>", gt);
        return firstEffect(xml.substring(gt + 1, end < 0 ? xml.length() : end));
    }

    private static String firstEffect(String xml) {
        Matcher m = TRANSITION_CHILD.matcher(xml);
        while (m.find()) {
            String n = m.group(1);
            if (!Arrays.asList("transition", "xml-fragment", "sndAc", "stSnd", "endSnd", "snd", "extLst", "ext").contains(n)) return n;
        }
        return "cut";
    }

    private static final String[][] TRANSITIONS = {
            {"fade", "淡出"}, {"push", "推进"}, {"wipe", "擦除"}, {"split", "分割"}, {"cut", "切出"}, {"zoom", "缩放"},
            {"cover", "覆盖"}, {"pull", "揭开"}, {"randomBar", "随机线条"}, {"circle", "形状"}, {"dissolve", "溶解"},
            {"blinds", "百叶窗"}, {"checker", "棋盘"}, {"comb", "梳理"}, {"strips", "条纹"}, {"wheel", "时钟"},
            {"wedge", "楔入"}, {"plus", "十字"}, {"diamond", "菱形"}, {"newsflash", "旋转"}, {"random", "随机"},
            {"vortex", "涡流"}, {"ripple", "涟漪"}, {"glitter", "闪耀"}, {"honeycomb", "蜂巢"}, {"shred", "粉碎"},
            {"flip", "翻转"}, {"gallery", "库"}, {"cube", "立方体"}, {"doors", "门"}, {"box", "框"}, {"switch", "切换"},
            {"window", "窗口"}, {"ferris", "摩天轮"}, {"conveyor", "传送带"}, {"pan", "平移"}, {"flash", "闪光"},
            {"reveal", "显示"}, {"warp", "飞过"}, {"flythrough", "飞过"}, {"prism", "棱镜"}, {"vortex", "涡流"}, {"morph", "平滑"}
    };

    private static Set<String> transitionKeys(String want) {
        Set<String> s = new LinkedHashSet<>();
        String w = want.toLowerCase(Locale.ROOT);
        for (String[] t : TRANSITIONS) if (t[0].equalsIgnoreCase(w) || t[1].equals(want)) s.add(t[0].toLowerCase(Locale.ROOT));
        if (s.isEmpty()) s.add(w);
        return s;
    }

    private static String transitionLabel(String key) {
        for (String[] t : TRANSITIONS) if (t[0].equalsIgnoreCase(key)) return t[1];
        return key;
    }

    // ================= 通用 =================

    private static final Map<String, Double> CN_SIZES = new LinkedHashMap<>();
    static {
        String[] n = {"初号", "小初", "一号", "小一", "二号", "小二", "三号", "小三", "四号", "小四", "五号", "小五", "六号", "小六", "七号", "八号"};
        double[] v = {42, 36, 26, 24, 22, 18, 16, 15, 14, 12, 10.5, 9, 7.5, 6.5, 5.5, 5};
        for (int i = 0; i < n.length; i++) CN_SIZES.put(n[i], v[i]);
        CN_SIZES.put("大一号", 28.0);
        CN_SIZES.put("小一号", 24.0);
        CN_SIZES.put("小二号", 18.0);
        CN_SIZES.put("小三号", 15.0);
        CN_SIZES.put("小四号", 12.0);
        CN_SIZES.put("小五号", 9.0);
        CN_SIZES.put("小六号", 6.5);
    }

    static double fontSize(String s) {
        String v = nz(s).trim().replace("磅", "").replace("pt", "");
        Double cn = CN_SIZES.get(v);
        return cn != null ? cn : parseNum(v);
    }

    static String sizeName(double pt) {
        for (Map.Entry<String, Double> e : CN_SIZES.entrySet()) if (Math.abs(e.getValue() - pt) < 0.01 && e.getKey().length() == 2) return e.getKey() + "（" + fmt(pt) + "磅）";
        return fmt(pt) + "磅";
    }

    private static final Map<String, String> FONT_ALIASES = new HashMap<>();
    static {
        String[][] a = {{"宋体", "simsun"}, {"黑体", "simhei"}, {"楷体", "kaiti", "楷体_gb2312", "kaiti_gb2312"}, {"仿宋", "fangsong", "仿宋_gb2312", "fangsong_gb2312"},
                {"微软雅黑", "microsoft yahei"}, {"隶书", "lisu"}, {"幼圆", "youyuan"}, {"华文行楷", "stxingkai"}, {"华文彩云", "stcaiyun"}, {"新宋体", "nsimsun"}};
        for (String[] g : a) for (String x : g) FONT_ALIASES.put(x.toLowerCase(Locale.ROOT), g[0]);
    }

    static boolean fontEquals(String actual, String want) {
        if (actual == null || "null".equals(actual) || want.isEmpty()) return false;
        String a = actual.trim().toLowerCase(Locale.ROOT), w = want.trim().toLowerCase(Locale.ROOT);
        if (a.equals(w)) return true;
        String ca = FONT_ALIASES.getOrDefault(a, a), cw = FONT_ALIASES.getOrDefault(w, w);
        return ca.equalsIgnoreCase(cw);
    }

    /** 颜色比较：期望为 6 位十六进制时精确比较；为颜色名称时按色相归类（红/橙/黄/绿/青/蓝/紫/黑/白/灰）。 */
    static boolean colorMatches(String actualHex, String want) {
        if (want == null || want.isEmpty()) return false;
        String w = want.trim().replace("#", "");
        String a = actualHex == null ? "" : actualHex.trim().replace("#", "");
        if (w.matches("[0-9a-fA-F]{6}")) return a.equalsIgnoreCase(w);
        if ("auto".equalsIgnoreCase(a) || a.startsWith("主题色")) return w.contains("黑") || w.contains("自动");
        if (!a.matches("[0-9a-fA-F]{6}")) return false;
        return colorName(a).equals(w.replace("色", ""));
    }

    static String colorName(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        float h = hsb[0] * 360, s = hsb[1], b = hsb[2];
        if (b < 0.2f) return "黑";
        if (s < 0.15f) return b > 0.9f ? "白" : "灰";
        if (h < 15 || h >= 340) return "红";
        if (h < 45) return "橙";
        if (h < 70) return "黄";
        if (h < 165) return "绿";
        if (h < 195) return "青";
        if (h < 260) return "蓝";
        return "紫";
    }

    static String alignLabel(String jc) {
        switch (jc) {
            case "center":
                return "居中";
            case "right":
            case "end":
                return "右对齐";
            case "both":
                return "两端对齐";
            case "distribute":
                return "分散对齐";
            default:
                return "左对齐";
        }
    }

    private static Set<String> wordAlign(String s) {
        String v = nz(s).trim();
        if (v.contains("中")) return set("center");
        if (v.contains("右")) return set("right", "end");
        if (v.contains("两端")) return set("both");
        if (v.contains("分散")) return set("distribute");
        return set("left", "start");
    }

    private static Set<String> set(String... a) {
        return new LinkedHashSet<>(Arrays.asList(a));
    }

    private static Object[] parseLineSpacing(String s) {
        String v = nz(s).trim();
        if (v.contains("单倍")) return new Object[]{"auto", 1.0};
        if (v.contains("双倍")) return new Object[]{"auto", 2.0};
        if (v.contains("倍")) return new Object[]{"auto", parseNum(v)};
        if (v.contains("最小")) return new Object[]{"atLeast", parseNum(v)};
        if (v.contains("磅") || v.contains("固定")) return new Object[]{"exact", parseNum(v)};
        return new Object[]{"auto", parseNum(v)};
    }

    private static String side(String s) {
        String v = nz(s).trim();
        if (v.startsWith("上")) return "top";
        if (v.startsWith("下")) return "bottom";
        if (v.startsWith("左")) return "left";
        if (v.startsWith("右")) return "right";
        return null;
    }

    private static String paperName(double w, double h) {
        String[][] sizes = {{"A4", "11906", "16838"}, {"A3", "16838", "23811"}, {"A5", "8391", "11906"}, {"B5", "10319", "14571"},
                {"B5", "9979", "14175"}, {"16开", "10433", "14742"}, {"LETTER", "12240", "15840"}};
        for (String[] s : sizes) {
            if (Math.abs(w - Double.parseDouble(s[1])) < 80 && Math.abs(h - Double.parseDouble(s[2])) < 80) return s[0];
        }
        return fmt(w / 567) + "×" + fmt(h / 567) + "厘米";
    }

    static boolean yes(String s) {
        String v = nz(s).trim();
        return !(v.equals("否") || v.equals("不") || v.equalsIgnoreCase("false") || v.equalsIgnoreCase("no") || v.equals("0"));
    }

    static double parseNum(Object o) {
        Matcher m = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(String.valueOf(o));
        return m.find() ? Double.parseDouble(m.group()) : 0;
    }

    static String fmt(double d) {
        double r = Math.round(d * 100) / 100.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    private static boolean containsChinese(String s) {
        for (char c : s.toCharArray()) if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) return true;
        return false;
    }

    private static List<String> limit(java.util.Collection<String> c) {
        List<String> l = new ArrayList<>();
        for (String s : c) {
            if (l.size() >= 5) {
                l.add("…");
                break;
            }
            l.add(s);
        }
        return l;
    }

    private static String cut(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
