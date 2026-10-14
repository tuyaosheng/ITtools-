package com.ittools.grading.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFHyperlink;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 阅卷参数读取（原“阅卷参数辅助读取工具”）：教师上传样例/标准答案文件，列出各段落、单元格、幻灯片的格式，
 * 便于填写阅卷点参数。
 */
public final class OfficeInspector {
    static final int MAX_PARAS = 200;
    static final int MAX_CELLS = 400;

    private OfficeInspector() {}

    public static Map<String, Object> inspect(Path file, String fileName) throws Exception {
        String ext = GradingEngine.ext(fileName);
        switch (ext) {
            case "docx":
                return docx(DocxModel.load(file));
            case "xlsx":
            case "xls":
                return xlsx(file);
            case "pptx":
                return pptx(file);
            default:
                throw new IllegalArgumentException("只支持 docx、xlsx、pptx 文件");
        }
    }

    private static Map<String, Object> docx(DocxModel m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "docx");
        List<Map<String, Object>> paras = new ArrayList<>();
        int i = 0;
        for (DocxModel.Para p : m.bodyParagraphs()) {
            if (++i > MAX_PARAS) break;
            DocxModel.Run first = null;
            for (DocxModel.Run r : p.runs) if (!r.text.trim().isEmpty()) {
                first = r;
                break;
            }
            Set<String> fonts = new LinkedHashSet<>(), sizes = new LinkedHashSet<>(), colors = new LinkedHashSet<>();
            boolean bold = false, italic = false, underline = false;
            for (DocxModel.Run r : p.runs) {
                if (r.text.trim().isEmpty()) continue;
                String ea = m.font(p, r, true), as = m.font(p, r, false);
                if (ea != null) fonts.add(ea);
                if (as != null && !as.equals(ea)) fonts.add(as);
                sizes.add(OfficeRules.sizeName(m.size(p, r)));
                colors.add(m.color(p, r));
                bold |= m.flag(p, r, "b");
                italic |= m.flag(p, r, "i");
                underline |= m.flag(p, r, "u");
            }
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("index", i);
            x.put("text", cut(p.text, 50));
            x.put("font", String.join(" / ", fonts));
            x.put("size", String.join(" / ", sizes));
            x.put("bold", bold);
            x.put("italic", italic);
            x.put("underline", underline);
            x.put("color", String.join(" / ", colors));
            x.put("align", OfficeRules.alignLabel(m.align(p)));
            x.put("indent", OfficeRules.fmt(m.firstLineChars(p)) + " 字符");
            Object[] ls = m.lineSpacing(p);
            x.put("lineSpacing", "auto".equals(ls[0]) ? OfficeRules.fmt((Double) ls[1]) + "倍" : ("exact".equals(ls[0]) ? "固定值" : "最小值") + OfficeRules.fmt((Double) ls[1]) + "磅");
            Object[] sb = m.paraSpace(p, true), sa = m.paraSpace(p, false);
            x.put("spaceBefore", OfficeRules.fmt((Double) sb[1]) + sb[0]);
            x.put("spaceAfter", OfficeRules.fmt((Double) sa[1]) + sa[0]);
            if (first == null) x.put("empty", true);
            paras.add(x);
        }
        out.put("paragraphs", paras);
        Map<String, Object> page = new LinkedHashMap<>();
        Object[] ps = m.pageSize();
        page.put("orient", (Boolean) ps[2] ? "横向" : "纵向");
        page.put("size", OfficeRules.fmt((Double) ps[0] / 566.93) + " × " + OfficeRules.fmt((Double) ps[1] / 566.93) + " 厘米");
        page.put("margins", "上 " + OfficeRules.fmt(m.margin("top")) + " / 下 " + OfficeRules.fmt(m.margin("bottom"))
                + " / 左 " + OfficeRules.fmt(m.margin("left")) + " / 右 " + OfficeRules.fmt(m.margin("right")) + " 厘米");
        page.put("columns", m.columns());
        page.put("header", String.join(" / ", m.headerTexts));
        page.put("footer", String.join(" / ", m.footerTexts));
        page.put("pageNumber", m.pageNumber);
        page.put("images", m.images);
        List<String> tables = new ArrayList<>();
        for (int t = 0; t < m.tables.size(); t++) {
            int[] sz = m.tableSize(m.tables.get(t));
            tables.add("第" + (t + 1) + "个：" + sz[0] + "行×" + sz[1] + "列");
        }
        page.put("tables", tables);
        out.put("page", page);
        return out;
    }

    private static Map<String, Object> xlsx(Path file) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "xlsx");
        List<Map<String, Object>> sheets = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file); Workbook wb = WorkbookFactory.create(in)) {
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            DataFormatter df = new DataFormatter();
            int count = 0;
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sh = wb.getSheetAt(s);
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("name", sh.getSheetName());
                List<Map<String, Object>> cells = new ArrayList<>();
                for (Row row : sh) {
                    for (Cell c : row) {
                        if (count >= MAX_CELLS) break;
                        String v;
                        try {
                            v = df.formatCellValue(c, ev);
                        } catch (RuntimeException e) {
                            v = df.formatCellValue(c);
                        }
                        CellStyle st = c.getCellStyle();
                        boolean styled = st != null && st.getIndex() != 0;
                        if (v.trim().isEmpty() && !styled) continue;
                        count++;
                        Font f = wb.getFontAt(st.getFontIndex());
                        Map<String, Object> cx = new LinkedHashMap<>();
                        cx.put("ref", c.getAddress().formatAsString());
                        cx.put("value", cut(v, 40));
                        if (c.getCellType() == CellType.FORMULA) cx.put("formula", "=" + c.getCellFormula());
                        cx.put("font", f.getFontName() + " " + OfficeRules.fmt(f.getFontHeightInPoints()) + "磅" + (f.getBold() ? " 加粗" : ""));
                        if (f instanceof XSSFFont) cx.put("color", OfficeRules.hex(((XSSFFont) f).getXSSFColor()));
                        if (st.getFillPattern() != null && st.getFillPattern().getCode() != 0 && st instanceof XSSFCellStyle) {
                            cx.put("fill", OfficeRules.hex(((XSSFCellStyle) st).getFillForegroundXSSFColor()));
                        }
                        cx.put("align", st.getAlignment().name());
                        cx.put("format", st.getDataFormatString());
                        cells.add(cx);
                    }
                }
                x.put("cells", cells);
                List<String> merged = new ArrayList<>();
                for (CellRangeAddress a : sh.getMergedRegions()) merged.add(a.formatAsString());
                x.put("merged", merged);
                List<String> charts = new ArrayList<>();
                if (sh instanceof XSSFSheet) {
                    x.put("autoFilter", ((XSSFSheet) sh).getCTWorksheet().isSetAutoFilter());
                    XSSFDrawing d = ((XSSFSheet) sh).getDrawingPatriarch();
                    if (d != null) for (XSSFChart ch : d.getCharts()) for (XDDFChartData data : ch.getChartSeries()) charts.add(OfficeRules.chartName(data));
                }
                x.put("charts", charts);
                x.put("conditionalFormats", sh.getSheetConditionalFormatting().getNumConditionalFormattings());
                sheets.add(x);
            }
        }
        out.put("sheets", sheets);
        return out;
    }

    private static Map<String, Object> pptx(Path file) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "pptx");
        List<Map<String, Object>> slides = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file); XMLSlideShow ppt = new XMLSlideShow(in)) {
            int i = 0;
            for (XSLFSlide s : ppt.getSlides()) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("index", ++i);
                x.put("layout", OfficeRules.layoutName(s.getSlideLayout()));
                String tr = OfficeRules.transitionOf(s);
                x.put("transition", tr == null ? "" : tr);
                x.put("animation", s.getXmlObject().isSetTiming() && s.getXmlObject().getTiming().xmlText().contains("presetClass"));
                x.put("background", s.getXmlObject().getCSld().isSetBg());
                int pics = 0;
                List<Map<String, Object>> texts = new ArrayList<>();
                List<String> links = new ArrayList<>();
                for (XSLFShape sh : s.getShapes()) {
                    if (sh instanceof XSLFPictureShape) pics++;
                    if (!(sh instanceof XSLFTextShape)) continue;
                    for (XSLFTextParagraph p : ((XSLFTextShape) sh).getTextParagraphs()) {
                        for (XSLFTextRun r : p.getTextRuns()) {
                            String t = r.getRawText();
                            if (t == null || t.trim().isEmpty()) continue;
                            XSLFHyperlink h = r.getHyperlink();
                            if (h != null && h.getAddress() != null) links.add(h.getAddress());
                            if (texts.size() >= 30) continue;
                            Map<String, Object> tx = new LinkedHashMap<>();
                            tx.put("text", cut(t, 40));
                            tx.put("font", r.getFontFamily());
                            tx.put("size", r.getFontSize() == null ? "" : OfficeRules.fmt(r.getFontSize()) + "磅");
                            tx.put("bold", r.isBold());
                            tx.put("color", OfficeRules.pptColor(r));
                            texts.add(tx);
                        }
                    }
                }
                x.put("images", pics);
                x.put("links", links);
                x.put("texts", texts);
                slides.add(x);
            }
        }
        out.put("slides", slides);
        return out;
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }
}
