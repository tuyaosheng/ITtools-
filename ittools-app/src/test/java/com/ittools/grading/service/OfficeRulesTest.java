package com.ittools.grading.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 细粒度 Office 阅卷点：Word（含样式继承）、Excel、PPT。 */
class OfficeRulesTest {
    @TempDir
    static Path dir;
    static Path docx, xlsx, pptx;

    private static final String W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

    @BeforeAll
    static void build() throws Exception {
        docx = dir.resolve("a.docx");
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>");
        parts.put("word/styles.xml", "<w:styles " + W + ">"
                + "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:eastAsia=\"宋体\"/><w:sz w:val=\"21\"/></w:rPr></w:rPrDefault></w:docDefaults>"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"Title1\"><w:name w:val=\"标题样式\"/><w:basedOn w:val=\"Normal\"/>"
                + "<w:pPr><w:jc w:val=\"center\"/></w:pPr><w:rPr><w:rFonts w:eastAsia=\"黑体\"/><w:b/><w:sz w:val=\"32\"/></w:rPr></w:style>"
                + "</w:styles>");
        parts.put("word/document.xml", "<w:document " + W + "><w:body>"
                // 第1段：标题样式（字体/字号/加粗/居中都来自样式）
                + "<w:p><w:pPr><w:pStyle w:val=\"Title1\"/></w:pPr><w:r><w:t>信息技术</w:t></w:r></w:p>"
                // 第2段：首行缩进 2 字符、1.5 倍行距、段前 0.5 行；其中“重点”二字红色倾斜下划线
                + "<w:p><w:pPr><w:ind w:firstLineChars=\"200\" w:firstLine=\"420\"/><w:spacing w:line=\"360\" w:lineRule=\"auto\" w:beforeLines=\"50\"/><w:jc w:val=\"both\"/></w:pPr>"
                + "<w:r><w:t>这是正文，</w:t></w:r><w:r><w:rPr><w:i/><w:u w:val=\"single\"/><w:color w:val=\"FF0000\"/></w:rPr><w:t>重点</w:t></w:r><w:r><w:t>内容。</w:t></w:r></w:p>"
                // 第3段：固定值 20 磅，楷体_GB2312 小四
                + "<w:p><w:pPr><w:spacing w:line=\"400\" w:lineRule=\"exact\"/></w:pPr><w:r><w:rPr><w:rFonts w:eastAsia=\"楷体_GB2312\"/><w:sz w:val=\"24\"/></w:rPr><w:t>第三段</w:t></w:r></w:p>"
                + "<w:tbl><w:tblGrid><w:gridCol/><w:gridCol/><w:gridCol/></w:tblGrid>"
                + "<w:tr><w:tc><w:p><w:r><w:t>表格</w:t></w:r></w:p></w:tc><w:tc><w:p/></w:tc><w:tc><w:p/></w:tc></w:tr>"
                + "<w:tr><w:tc><w:p/></w:tc><w:tc><w:p/></w:tc><w:tc><w:p/></w:tc></w:tr></w:tbl>"
                + "<w:sectPr><w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/><w:pgMar w:top=\"1418\" w:bottom=\"1418\" w:left=\"1701\" w:right=\"1701\"/>"
                + "<w:cols w:num=\"2\"/></w:sectPr></w:body></w:document>");
        parts.put("word/header1.xml", "<w:hdr " + W + "><w:p><w:r><w:t>温岭中学信息技术</w:t></w:r></w:p></w:hdr>");
        parts.put("word/footer1.xml", "<w:ftr " + W + "><w:p><w:fldSimple w:instr=\" PAGE \"><w:r><w:t>1</w:t></w:r></w:fldSimple></w:p></w:ftr>");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(docx))) {
            for (Map.Entry<String, String> e : parts.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }

        xlsx = dir.resolve("b.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(xlsx)) {
            XSSFSheet sh = wb.createSheet("成绩表");
            XSSFFont f = wb.createFont();
            f.setFontName("黑体");
            f.setFontHeightInPoints((short) 16);
            f.setBold(true);
            f.setColor(new XSSFColor(new byte[]{(byte) 0xFF, 0, 0}, null));
            XSSFCellStyle title = wb.createCellStyle();
            title.setFont(f);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, 0}, null));
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFRow r0 = sh.createRow(0);
            for (int c = 0; c < 3; c++) r0.createCell(c).setCellStyle(title);
            r0.getCell(0).setCellValue("成绩表");
            sh.addMergedRegion(CellRangeAddress.valueOf("A1:C1"));
            XSSFCellStyle bordered = wb.createCellStyle();
            bordered.setBorderTop(BorderStyle.THIN);
            bordered.setBorderBottom(BorderStyle.THIN);
            bordered.setBorderLeft(BorderStyle.THIN);
            bordered.setBorderRight(BorderStyle.THIN);
            XSSFCellStyle pct = wb.createCellStyle();
            pct.cloneStyleFrom(bordered);
            pct.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
            double[] scores = {95, 88, 70};
            for (int i = 0; i < 3; i++) {
                XSSFRow r = sh.createRow(i + 1);
                XSSFCell a = r.createCell(0);
                a.setCellValue("学生" + (i + 1));
                a.setCellStyle(bordered);
                XSSFCell b = r.createCell(1);
                b.setCellValue(scores[i]);
                b.setCellStyle(bordered);
                XSSFCell c = r.createCell(2);
                c.setCellValue(scores[i] / 100);
                c.setCellStyle(pct);
            }
            sh.setAutoFilter(CellRangeAddress.valueOf("A2:C4"));
            sh.getSheetConditionalFormatting().addConditionalFormatting(new CellRangeAddress[]{CellRangeAddress.valueOf("B2:B4")},
                    sh.getSheetConditionalFormatting().createConditionalFormattingRule(org.apache.poi.ss.usermodel.ComparisonOperator.GT, "80"));
            XSSFDrawing d = sh.createDrawingPatriarch();
            XSSFClientAnchor an = d.createAnchor(0, 0, 0, 0, 4, 1, 10, 15);
            XSSFChart chart = d.createChart(an);
            XDDFCategoryDataSource cat = XDDFDataSourcesFactory.fromStringCellRange(sh, CellRangeAddress.valueOf("A2:A4"));
            XDDFNumericalDataSource<Double> val = XDDFDataSourcesFactory.fromNumericCellRange(sh, CellRangeAddress.valueOf("B2:B4"));
            XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
            data.addSeries(cat, val);
            chart.plot(data);
            wb.write(out);
        }

        pptx = dir.resolve("c.pptx");
        try (XMLSlideShow ppt = new XMLSlideShow(); OutputStream out = Files.newOutputStream(pptx)) {
            XSLFSlideMaster master = ppt.getSlideMasters().get(0);
            XSLFSlideLayout titleLayout = master.getLayout(SlideLayout.TITLE);
            XSLFSlide s1 = ppt.createSlide(titleLayout);
            s1.getPlaceholder(0).setText("我的家乡");
            XSLFSlide s2 = ppt.createSlide(master.getLayout(SlideLayout.BLANK));
            XSLFTextBox box = s2.createTextBox();
            XSLFTextRun run = box.addNewTextParagraph().addNewTextRun();
            run.setText("美丽温岭");
            run.setFontFamily("微软雅黑");
            run.setFontSize(40.0);
            run.setBold(true);
            run.setFontColor(new java.awt.Color(0, 112, 192));
            run.createHyperlink().setAddress("https://www.example.com");
            s2.getXmlObject().getCSld().addNewBg().addNewBgPr().addNewSolidFill().addNewSrgbClr().setVal(new byte[]{0, 0, 0});
            ppt.write(out);
        }
    }

    private static Map<String, Object> grade(Path f, String type, String p1, String p2, String p3) {
        GradingEngine.Result r = GradingEngine.grade(f, f.getFileName().toString(), Arrays.asList(new GradingEngine.Rule(type, p1, p2, p3, 1, null)));
        return r.details.get(0);
    }

    private static void pass(Path f, String type, String p1, String p2, String p3) {
        Map<String, Object> d = grade(f, type, p1, p2, p3);
        assertThat(d.get("passed")).as(type + " " + p1 + "/" + p2 + "/" + p3 + " → " + d.get("message")).isEqualTo(true);
    }

    private static void fail(Path f, String type, String p1, String p2, String p3) {
        Map<String, Object> d = grade(f, type, p1, p2, p3);
        assertThat(d.get("passed")).as(type + " " + p1 + "/" + p2 + "/" + p3 + " → " + d.get("message")).isEqualTo(false);
    }

    @Test
    void wordParagraphFormatsWithStyleInheritance() {
        pass(docx, "DOCX_PARA_FONT", "1", "黑体", null);
        pass(docx, "DOCX_PARA_FONT", "1", "SimHei", null);
        fail(docx, "DOCX_PARA_FONT", "2", "黑体", null);
        pass(docx, "DOCX_PARA_FONT", "2", "宋体", null);              // 文档默认
        pass(docx, "DOCX_PARA_FONT", "第三段", "楷体", null);          // 楷体_GB2312 视为楷体
        pass(docx, "DOCX_PARA_SIZE", "1", "三号", null);
        pass(docx, "DOCX_PARA_SIZE", "1", "16", null);
        pass(docx, "DOCX_PARA_SIZE", "2", "五号", null);
        pass(docx, "DOCX_PARA_SIZE", "3", "小四", null);
        pass(docx, "DOCX_PARA_BOLD", "1", "", null);
        pass(docx, "DOCX_PARA_BOLD", "2", "否", null);
        pass(docx, "DOCX_PARA_ALIGN", "1", "居中", null);
        pass(docx, "DOCX_PARA_ALIGN", "2", "两端对齐", null);
        fail(docx, "DOCX_PARA_ALIGN", "3", "居中", null);
        // 只检查定位文字本身
        pass(docx, "DOCX_PARA_COLOR", "重点", "红色", null);
        pass(docx, "DOCX_PARA_COLOR", "重点", "FF0000", null);
        fail(docx, "DOCX_PARA_COLOR", "2", "红色", null);
        pass(docx, "DOCX_PARA_ITALIC", "重点", null, null);
        pass(docx, "DOCX_PARA_UNDERLINE", "重点", null, null);
        fail(docx, "DOCX_PARA_UNDERLINE", "这是正文", null, null);
        pass(docx, "DOCX_PARA_INDENT", "2", "2", null);
        fail(docx, "DOCX_PARA_INDENT", "1", "2", null);
        pass(docx, "DOCX_PARA_LINE_SPACING", "2", "1.5倍", null);
        pass(docx, "DOCX_PARA_LINE_SPACING", "3", "固定值20磅", null);
        pass(docx, "DOCX_PARA_LINE_SPACING", "1", "单倍", null);
        pass(docx, "DOCX_PARA_SPACE_BEFORE", "2", "0.5行", null);
        fail(docx, "DOCX_PARA_SPACE_BEFORE", "3", "0.5行", null);
        fail(docx, "DOCX_PARA_FONT", "9", "黑体", null);              // 没有第 9 段
    }

    @Test
    void wordPageAndObjects() {
        pass(docx, "DOCX_PAGE_MARGIN", "上", "2.5", null);
        pass(docx, "DOCX_PAGE_MARGIN", "左", "3", null);
        fail(docx, "DOCX_PAGE_MARGIN", "左", "2.5", null);
        pass(docx, "DOCX_PAGE_ORIENT", "横向", null, null);
        pass(docx, "DOCX_PAPER_SIZE", "A4", null, null);
        pass(docx, "DOCX_HEADER_TEXT", "温岭中学", null, null);
        fail(docx, "DOCX_FOOTER_TEXT", "温岭中学", null, null);
        pass(docx, "DOCX_PAGE_NUMBER", null, null, null);
        pass(docx, "DOCX_COLUMNS", "2", null, null);
        pass(docx, "DOCX_TABLE_SIZE", "", "2", "3");
        fail(docx, "DOCX_TABLE_SIZE", "1", "3", "3");
    }

    @Test
    void excelCellFormats() {
        pass(xlsx, "XLSX_CELL_FONT", "A1", "黑体", null);
        pass(xlsx, "XLSX_CELL_FONT_SIZE", "A1", "16", null);
        pass(xlsx, "XLSX_CELL_BOLD", "A1", null, null);
        fail(xlsx, "XLSX_CELL_BOLD", "A2", null, null);
        pass(xlsx, "XLSX_CELL_FONT_COLOR", "A1", "红色", null);
        pass(xlsx, "XLSX_CELL_FILL", "A1:C1", "黄色", null);
        fail(xlsx, "XLSX_CELL_FILL", "A2", "", null);
        pass(xlsx, "XLSX_CELL_ALIGN", "A1", "居中", null);
        pass(xlsx, "XLSX_CELL_NUMFMT", "C2:C4", "百分比", null);
        pass(xlsx, "XLSX_CELL_NUMFMT", "成绩表!C2:C4", "2位小数", null);
        fail(xlsx, "XLSX_CELL_NUMFMT", "B2:B4", "百分比", null);
        pass(xlsx, "XLSX_MERGED", "A1:C1", null, null);
        fail(xlsx, "XLSX_MERGED", "A1:B1", null, null);
        pass(xlsx, "XLSX_BORDER", "A2:C4", null, null);
        fail(xlsx, "XLSX_BORDER", "A1:C4", null, null);
        pass(xlsx, "XLSX_SORTED", "B2:B4", "降序", null);
        fail(xlsx, "XLSX_SORTED", "B2:B4", "升序", null);
        pass(xlsx, "XLSX_AUTOFILTER", "", null, null);
        pass(xlsx, "XLSX_CHART_TYPE", "饼图", null, null);
        fail(xlsx, "XLSX_CHART_TYPE", "柱形图", null, null);
        pass(xlsx, "XLSX_SHEET_NAME", "成绩表", null, null);
        pass(xlsx, "XLSX_COND_FORMAT", "B3", null, null);
        fail(xlsx, "XLSX_COND_FORMAT", "C2:C4", null, null);
    }

    @Test
    void pptSlides() {
        pass(pptx, "PPTX_SLIDE_TEXT", "1", "我的家乡", null);
        fail(pptx, "PPTX_SLIDE_TEXT", "2", "我的家乡", null);
        pass(pptx, "PPTX_LAYOUT", "1", "标题幻灯片", null);
        pass(pptx, "PPTX_LAYOUT", "2", "空白", null);
        pass(pptx, "PPTX_TEXT_FONT", "2", "温岭", "微软雅黑");
        pass(pptx, "PPTX_TEXT_SIZE", "", "美丽", "40");
        pass(pptx, "PPTX_TEXT_BOLD", "2", "美丽温岭", null);
        pass(pptx, "PPTX_TEXT_COLOR", "2", "美丽温岭", "蓝色");
        pass(pptx, "PPTX_HYPERLINK", "", "example.com", null);
        fail(pptx, "PPTX_HYPERLINK", "1", "", null);
        pass(pptx, "PPTX_BACKGROUND", "2", null, null);
        fail(pptx, "PPTX_BACKGROUND", "1", null, null);
        fail(pptx, "PPTX_TRANSITION_TYPE", "", "淡出", null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void inspectorReadsFormats() throws Exception {
        Map<String, Object> d = OfficeInspector.inspect(docx, "a.docx");
        List<Map<String, Object>> paras = (List<Map<String, Object>>) d.get("paragraphs");
        assertThat(paras).hasSize(3);
        assertThat(paras.get(0).get("font") + " " + paras.get(0).get("size") + " " + paras.get(0).get("align")).isEqualTo("黑体 / Calibri 三号（16磅） 居中");
        assertThat(paras.get(1).get("indent") + " " + paras.get(1).get("lineSpacing") + " " + paras.get(1).get("spaceBefore")).isEqualTo("2 字符 1.5倍 0.5行");
        Map<String, Object> page = (Map<String, Object>) d.get("page");
        assertThat(page.get("orient")).isEqualTo("横向");
        assertThat(page.get("tables").toString()).contains("2行×3列");

        Map<String, Object> x = OfficeInspector.inspect(xlsx, "b.xlsx");
        Map<String, Object> sheet = ((List<Map<String, Object>>) x.get("sheets")).get(0);
        Map<String, Object> a1 = ((List<Map<String, Object>>) sheet.get("cells")).get(0);
        assertThat(a1.get("ref") + " " + a1.get("font") + " " + a1.get("fill")).isEqualTo("A1 黑体 16磅 加粗 FFFF00");
        assertThat(sheet.get("merged").toString()).contains("A1:C1");
        assertThat(sheet.get("charts").toString()).contains("饼");

        Map<String, Object> p = OfficeInspector.inspect(pptx, "c.pptx");
        List<Map<String, Object>> slides = (List<Map<String, Object>>) p.get("slides");
        assertThat(slides.get(0).get("layout")).isEqualTo("标题幻灯片");
        assertThat(slides.get(1).get("background")).isEqualTo(true);
        assertThat(slides.get(1).get("texts").toString()).contains("微软雅黑").contains("40磅");
    }

    @Test
    void transitionNamesAndHelpers() {
        assertThat(OfficeRules.colorName("0070C0")).isEqualTo("蓝");
        assertThat(OfficeRules.colorName("00B050")).isEqualTo("绿");
        assertThat(OfficeRules.fontSize("小二")).isEqualTo(18.0);
        // PowerPoint 2010 新效果写在 mc:AlternateContent 中，取 Choice 中的效果名
        assertThat(OfficeRules.transitionOfXml("<p:sld><mc:AlternateContent><mc:Choice Requires=\"p14\"><p:transition spd=\"slow\" p14:dur=\"2000\">"
                + "<p14:vortex dir=\"r\"/></p:transition></mc:Choice><mc:Fallback><p:transition spd=\"slow\"><p:fade/></p:transition></mc:Fallback></mc:AlternateContent></p:sld>"))
                .isEqualTo("vortex");
        assertThat(OfficeRules.transitionOfXml("<p:sld><p:transition spd=\"med\"><p:blinds dir=\"vert\"/></p:transition></p:sld>")).isEqualTo("blinds");
        assertThat(OfficeRules.transitionOfXml("<p:sld><p:transition/><p:timing><p:tnLst/></p:timing></p:sld>")).isEqualTo("cut");
        assertThat(OfficeRules.transitionOfXml("<p:sld><p:cSld/></p:sld>")).isNull();
    }
}
