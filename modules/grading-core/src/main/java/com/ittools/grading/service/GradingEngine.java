package com.ittools.grading.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自动评分引擎（子项目 4）。按设计"评分规则现代化重新设计，不逆向旧无源码引擎"：
 * 旧系统用本地 exe + Office COM 检测作品；这里改为在服务端用 Apache POI 解析 OOXML（docx/xlsx/pptx），
 * 对每条规则独立判定得分，结果逐条可解释。不执行任何学生上传的代码。
 */
public final class GradingEngine {

    /** 规则类型目录：type -> [名称, 参数1说明, 参数2说明, 参数3说明, 适用扩展名]。 */
    public static final Map<String, String[]> CATALOG = new LinkedHashMap<>();
    static {
        CATALOG.put("FILE_EXT", new String[]{"文件类型符合", "允许的扩展名（逗号分隔，如 docx,doc）", "", "", "*"});
        CATALOG.put("FILE_SIZE_MIN", new String[]{"文件不小于", "最小大小 KB", "", "", "*"});
        CATALOG.put("FILE_SIZE_MAX", new String[]{"文件不大于", "最大大小 KB", "", "", "*"});
        CATALOG.put("TEXT_CONTAINS", new String[]{"文本包含关键字", "关键字（多个用 | 分隔，全部出现才得分）", "", "", "docx,pptx,xlsx,txt,html,htm,py,csv,md"});
        CATALOG.put("DOCX_MIN_CHARS", new String[]{"Word 字数不少于", "最少字数", "", "", "docx"});
        CATALOG.put("DOCX_MIN_IMAGES", new String[]{"Word 插入图片", "最少图片数", "", "", "docx"});
        CATALOG.put("DOCX_HAS_TABLE", new String[]{"Word 插入表格", "最少表格数", "", "", "docx"});
        CATALOG.put("DOCX_MIN_HEADINGS", new String[]{"Word 使用标题样式", "最少标题段落数", "", "", "docx"});
        CATALOG.put("DOCX_FONT_USED", new String[]{"Word 使用指定字体", "字体名称（如 黑体）", "", "", "docx"});
        CATALOG.put("DOCX_BOLD_USED", new String[]{"Word 使用加粗", "", "", "", "docx"});
        CATALOG.put("XLSX_CELL_EQUALS", new String[]{"Excel 单元格值等于", "单元格（如 B2 或 Sheet1!B2）", "期望值", "", "xlsx"});
        CATALOG.put("XLSX_CELL_FORMULA", new String[]{"Excel 单元格使用公式", "单元格（如 D10）", "公式须包含的函数（可空，如 SUM）", "", "xlsx"});
        CATALOG.put("XLSX_HAS_CHART", new String[]{"Excel 插入图表", "最少图表数", "", "", "xlsx"});
        CATALOG.put("XLSX_MIN_ROWS", new String[]{"Excel 数据行数不少于", "工作表（空=第一个）", "最少非空行数", "", "xlsx"});
        CATALOG.put("PPTX_MIN_SLIDES", new String[]{"PPT 幻灯片不少于", "最少张数", "", "", "pptx"});
        CATALOG.put("PPTX_MIN_IMAGES", new String[]{"PPT 插入图片", "最少图片数", "", "", "pptx"});
        CATALOG.put("PPTX_HAS_ANIMATION", new String[]{"PPT 设置动画", "至少几张幻灯片有动画", "", "", "pptx"});
        CATALOG.put("PPTX_HAS_TRANSITION", new String[]{"PPT 设置切换效果", "至少几张幻灯片有切换", "", "", "pptx"});
        CATALOG.put("HTML_HAS_TAG", new String[]{"网页包含标签", "标签名（如 img、table、a）", "最少个数", "", "html,htm"});
        OfficeRules.register(CATALOG);
    }

    public static class Rule {
        public final String type;
        public final String p1;
        public final String p2;
        public final String p3;
        public final double score;
        public final String note;

        public Rule(String type, String p1, String p2, String p3, double score, String note) {
            this.type = type;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.score = score;
            this.note = note;
        }
    }

    public static class Result {
        public double score;
        public double max;
        public final List<Map<String, Object>> details = new ArrayList<>();
    }

    private GradingEngine() {}

    public static Result grade(Path file, String fileName, List<Rule> rules) {
        Result r = new Result();
        String ext = ext(fileName);
        Doc doc = new Doc(file, ext);
        try {
            for (Rule rule : rules) {
                r.max += rule.score;
                Map<String, Object> d = new LinkedHashMap<>();
                String[] meta = CATALOG.get(rule.type);
                d.put("type", rule.type);
                d.put("name", meta == null ? rule.type : meta[0]);
                d.put("note", rule.note);
                d.put("max", rule.score);
                boolean ok;
                String msg;
                try {
                    Check c = check(doc, rule);
                    ok = c.ok;
                    msg = c.msg;
                } catch (Exception e) {
                    ok = false;
                    msg = "无法检测：" + e.getClass().getSimpleName();
                }
                d.put("passed", ok);
                d.put("message", msg);
                d.put("score", ok ? rule.score : 0);
                if (ok) r.score += rule.score;
                r.details.add(d);
            }
        } finally {
            doc.close();
        }
        return r;
    }

    static class Check {
        final boolean ok;
        final String msg;

        Check(boolean ok, String msg) {
            this.ok = ok;
            this.msg = msg;
        }
    }

    private static Check check(Doc doc, Rule rule) throws Exception {
        switch (rule.type) {
            case "FILE_EXT": {
                List<String> allowed = Arrays.asList(lower(rule.p1).replace(".", "").split("\\s*[,，|]\\s*"));
                return new Check(allowed.contains(doc.ext), "文件类型：." + doc.ext);
            }
            case "FILE_SIZE_MIN": {
                long kb = Files.size(doc.path) / 1024;
                return new Check(kb >= num(rule.p1), "文件大小 " + kb + " KB");
            }
            case "FILE_SIZE_MAX": {
                long kb = Files.size(doc.path) / 1024;
                return new Check(kb <= num(rule.p1), "文件大小 " + kb + " KB");
            }
            case "TEXT_CONTAINS": {
                String text = doc.text();
                List<String> missing = new ArrayList<>();
                for (String k : rule.p1.split("\\|")) {
                    if (!k.trim().isEmpty() && !text.contains(k.trim())) missing.add(k.trim());
                }
                return new Check(missing.isEmpty(), missing.isEmpty() ? "关键字均已出现" : "缺少：" + String.join("、", missing));
            }
            case "DOCX_MIN_CHARS": {
                int n = doc.text().replaceAll("\\s", "").length();
                return new Check(n >= num(rule.p1), "共 " + n + " 字");
            }
            case "DOCX_MIN_IMAGES": {
                int n = doc.word().getAllPictures().size();
                return new Check(n >= num(rule.p1, 1), "图片 " + n + " 张");
            }
            case "DOCX_HAS_TABLE": {
                int n = doc.word().getTables().size();
                return new Check(n >= num(rule.p1, 1), "表格 " + n + " 个");
            }
            case "DOCX_MIN_HEADINGS": {
                int n = 0;
                XWPFDocument w = doc.word();
                for (XWPFParagraph p : w.getParagraphs()) {
                    String sid = p.getStyleID();
                    if (sid == null) continue;
                    String name = sid;
                    XWPFStyle st = w.getStyles() == null ? null : w.getStyles().getStyle(sid);
                    if (st != null && st.getName() != null) name = st.getName();
                    String l = name.toLowerCase(Locale.ROOT);
                    if ((l.startsWith("heading") || l.contains("标题")) && !p.getText().trim().isEmpty()) n++;
                }
                return new Check(n >= num(rule.p1, 1), "标题段落 " + n + " 个");
            }
            case "DOCX_FONT_USED": {
                String font = rule.p1.trim();
                for (XWPFParagraph p : doc.word().getParagraphs()) {
                    for (XWPFRun run : p.getRuns()) {
                        String f = run.getFontFamily();
                        String ea = run.getFontFamily(XWPFRun.FontCharRange.eastAsia);
                        if (font.equalsIgnoreCase(f) || font.equalsIgnoreCase(ea)) return new Check(true, "已使用字体 " + font);
                    }
                }
                return new Check(false, "未找到字体 " + font);
            }
            case "DOCX_BOLD_USED": {
                for (XWPFParagraph p : doc.word().getParagraphs()) {
                    for (XWPFRun run : p.getRuns()) {
                        if (run.isBold() && run.text() != null && !run.text().trim().isEmpty()) return new Check(true, "已使用加粗");
                    }
                }
                return new Check(false, "未使用加粗");
            }
            case "XLSX_CELL_EQUALS": {
                Cell c = doc.cell(rule.p1);
                String v = c == null ? "" : doc.cellText(c);
                String expect = rule.p2 == null ? "" : rule.p2.trim();
                boolean ok = v.trim().equals(expect) || numericEquals(v, expect);
                return new Check(ok, rule.p1 + " = " + (v.isEmpty() ? "（空）" : v));
            }
            case "XLSX_CELL_FORMULA": {
                Cell c = doc.cell(rule.p1);
                if (c == null || c.getCellType() != CellType.FORMULA) return new Check(false, rule.p1 + " 未使用公式");
                String f = c.getCellFormula();
                boolean ok = rule.p2 == null || rule.p2.trim().isEmpty() || f.toUpperCase(Locale.ROOT).contains(rule.p2.trim().toUpperCase(Locale.ROOT));
                return new Check(ok, rule.p1 + " 公式：=" + f);
            }
            case "XLSX_HAS_CHART": {
                int n = 0;
                Workbook wb = doc.excel();
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    Sheet s = wb.getSheetAt(i);
                    if (s instanceof XSSFSheet) {
                        XSSFDrawing d = ((XSSFSheet) s).getDrawingPatriarch();
                        if (d != null) n += d.getCharts().size();
                    }
                }
                return new Check(n >= num(rule.p1, 1), "图表 " + n + " 个");
            }
            case "XLSX_MIN_ROWS": {
                Workbook wb = doc.excel();
                Sheet s = rule.p1 == null || rule.p1.trim().isEmpty() ? wb.getSheetAt(0) : wb.getSheet(rule.p1.trim());
                if (s == null) return new Check(false, "找不到工作表 " + rule.p1);
                int n = 0;
                for (Row row : s) {
                    for (Cell c : row) {
                        if (!doc.cellText(c).trim().isEmpty()) {
                            n++;
                            break;
                        }
                    }
                }
                return new Check(n >= num(rule.p2), "非空行 " + n + " 行");
            }
            case "PPTX_MIN_SLIDES": {
                int n = doc.ppt().getSlides().size();
                return new Check(n >= num(rule.p1), "幻灯片 " + n + " 张");
            }
            case "PPTX_MIN_IMAGES": {
                int n = 0;
                for (XSLFSlide s : doc.ppt().getSlides()) {
                    for (XSLFShape sh : s.getShapes()) {
                        if (sh instanceof XSLFPictureShape) n++;
                    }
                }
                return new Check(n >= num(rule.p1, 1), "图片 " + n + " 张");
            }
            case "PPTX_HAS_ANIMATION": {
                int n = 0;
                for (XSLFSlide s : doc.ppt().getSlides()) {
                    if (s.getXmlObject().isSetTiming()) n++;
                }
                return new Check(n >= num(rule.p1, 1), n + " 张幻灯片设置了动画");
            }
            case "PPTX_HAS_TRANSITION": {
                int n = 0;
                for (XSLFSlide s : doc.ppt().getSlides()) {
                    if (s.getXmlObject().isSetTransition()) n++;
                }
                return new Check(n >= num(rule.p1, 1), n + " 张幻灯片设置了切换");
            }
            case "HTML_HAS_TAG": {
                String html = doc.text().toLowerCase(Locale.ROOT);
                String tag = lower(rule.p1).replace("<", "").replace(">", "");
                int n = 0;
                int i = 0;
                while ((i = html.indexOf("<" + tag, i)) >= 0) {
                    int next = i + tag.length() + 1;
                    if (next >= html.length() || !Character.isLetterOrDigit(html.charAt(next))) n++;
                    i = next;
                }
                return new Check(n >= num(rule.p2, 1), "<" + tag + "> 出现 " + n + " 次");
            }
            default:
                return OfficeRules.check(doc, rule);
        }
    }

    // ---- helpers ----

    private static boolean numericEquals(String a, String b) {
        try {
            return Math.abs(Double.parseDouble(a.trim()) - Double.parseDouble(b.trim())) < 1e-6;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int num(String s) {
        return num(s, 0);
    }

    private static int num(String s, int def) {
        if (s == null || s.trim().isEmpty()) return def;
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    public static String ext(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /** 延迟打开的文档句柄，同一次评分内复用。 */
    static final class Doc {
        final Path path;
        final String ext;
        private XWPFDocument word;
        private Workbook excel;
        private FormulaEvaluator eval;
        private XMLSlideShow ppt;
        private DocxModel docx;
        private String text;

        Doc(Path path, String ext) {
            this.path = path;
            this.ext = ext;
        }

        XWPFDocument word() throws Exception {
            if (!"docx".equals(ext)) throw new IllegalStateException("不是 docx 文件");
            if (word == null) {
                try (InputStream in = Files.newInputStream(path)) {
                    word = new XWPFDocument(in);
                }
            }
            return word;
        }

        DocxModel docx() throws Exception {
            if (!"docx".equals(ext)) throw new IllegalStateException("不是 docx 文件");
            if (docx == null) docx = DocxModel.load(path);
            return docx;
        }

        Workbook excel() throws Exception {
            if (!"xlsx".equals(ext) && !"xls".equals(ext)) throw new IllegalStateException("不是 Excel 文件");
            if (excel == null) {
                try (InputStream in = Files.newInputStream(path)) {
                    excel = WorkbookFactory.create(in);
                }
                eval = excel.getCreationHelper().createFormulaEvaluator();
            }
            return excel;
        }

        XMLSlideShow ppt() throws Exception {
            if (!"pptx".equals(ext)) throw new IllegalStateException("不是 pptx 文件");
            if (ppt == null) {
                try (InputStream in = Files.newInputStream(path)) {
                    ppt = new XMLSlideShow(in);
                }
            }
            return ppt;
        }

        Cell cell(String ref) throws Exception {
            Workbook wb = excel();
            CellReference cr = new CellReference(ref.trim());
            Sheet s = cr.getSheetName() == null ? wb.getSheetAt(0) : wb.getSheet(cr.getSheetName());
            if (s == null) return null;
            Row row = s.getRow(cr.getRow());
            return row == null ? null : row.getCell(cr.getCol());
        }

        String cellText(Cell c) {
            try {
                return new DataFormatter().formatCellValue(c, eval);
            } catch (RuntimeException e) {
                return new DataFormatter().formatCellValue(c);
            }
        }

        String text() throws Exception {
            if (text != null) return text;
            StringBuilder b = new StringBuilder();
            switch (ext) {
                case "docx":
                    for (XWPFParagraph p : word().getParagraphs()) b.append(p.getText()).append('\n');
                    word().getTables().forEach(t -> b.append(t.getText()).append('\n'));
                    break;
                case "pptx":
                    for (XSLFSlide s : ppt().getSlides()) {
                        for (XSLFShape sh : s.getShapes()) {
                            if (sh instanceof XSLFTextShape) b.append(((XSLFTextShape) sh).getText()).append('\n');
                        }
                    }
                    break;
                case "xlsx":
                case "xls":
                    Workbook wb = excel();
                    for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                        for (Row row : wb.getSheetAt(i)) {
                            for (Cell c : row) b.append(cellText(c)).append('\t');
                            b.append('\n');
                        }
                    }
                    break;
                default:
                    byte[] bytes = Files.readAllBytes(path);
                    b.append(new String(bytes, StandardCharsets.UTF_8));
            }
            text = b.toString();
            return text;
        }

        void close() {
            try {
                if (word != null) word.close();
                if (excel != null) excel.close();
                if (ppt != null) ppt.close();
            } catch (Exception ignored) {
                // 仅释放资源
            }
        }
    }
}
