package com.ittools.grading.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 直接读取 docx 的 XML 得到段落、文字格式（按 文字 → 字符样式 → 段落样式 → 文档默认 的顺序解析继承）、
 * 页面设置、页眉页脚与表格。用于细粒度阅卷与“阅卷参数读取”。
 */
public final class DocxModel {
    static final long MAX_PART = 30L * 1024 * 1024;

    public static final class Run {
        public final Element r;
        public final String text;
        public final int start;

        Run(Element r, String text, int start) {
            this.r = r;
            this.text = text;
            this.start = start;
        }
    }

    public static final class Para {
        public final Element p;
        public final String text;
        public final boolean inTable;
        public final List<Run> runs = new ArrayList<>();

        Para(Element p, boolean inTable) {
            this.p = p;
            this.inTable = inTable;
            StringBuilder b = new StringBuilder();
            for (Element r : children(p, "r")) addRun(r, b);
            for (Element h : children(p, "hyperlink")) for (Element r : children(h, "r")) addRun(r, b);
            this.text = b.toString();
        }

        private void addRun(Element r, StringBuilder b) {
            StringBuilder t = new StringBuilder();
            for (Node n = r.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element)) continue;
                String ln = n.getLocalName();
                if ("t".equals(ln)) t.append(n.getTextContent());
                else if ("tab".equals(ln)) t.append('\t');
            }
            runs.add(new Run(r, t.toString(), b.length()));
            b.append(t);
        }
    }

    private final Map<String, Element> styles = new HashMap<>();
    private Element defaultRPr;
    private Element defaultPPr;
    private String defaultParaStyle;
    public final List<Para> paragraphs = new ArrayList<>();
    public final List<Element> tables = new ArrayList<>();
    public Element sectPr;
    public final List<String> headerTexts = new ArrayList<>();
    public final List<String> footerTexts = new ArrayList<>();
    public boolean pageNumber;
    public int images;

    public static DocxModel load(Path file) throws Exception {
        DocxModel m = new DocxModel();
        try (ZipFile zf = new ZipFile(file.toFile())) {
            Document doc = parse(read(zf, "word/document.xml"));
            if (doc == null) throw new IllegalStateException("不是有效的 Word 文档");
            Document st = parse(read(zf, "word/styles.xml"));
            if (st != null) m.loadStyles(st.getDocumentElement());
            Element body = first(doc.getDocumentElement(), "body");
            if (body != null) m.walk(body, false);
            m.images = doc.getElementsByTagNameNS("*", "blip").getLength() + doc.getElementsByTagNameNS("*", "imagedata").getLength();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                boolean header = n.matches("word/header\\d*\\.xml");
                boolean footer = n.matches("word/footer\\d*\\.xml");
                if (!header && !footer) continue;
                Document hd = parse(read(zf, n));
                if (hd == null) continue;
                String text = hd.getDocumentElement().getTextContent();
                (header ? m.headerTexts : m.footerTexts).add(text == null ? "" : text.trim());
                String xml = textOfInstr(hd);
                if (xml.toUpperCase().contains("PAGE")) m.pageNumber = true;
            }
        }
        return m;
    }

    private static String textOfInstr(Document d) {
        StringBuilder b = new StringBuilder();
        NodeList l = d.getElementsByTagNameNS("*", "instrText");
        for (int i = 0; i < l.getLength(); i++) b.append(l.item(i).getTextContent()).append(' ');
        NodeList f = d.getElementsByTagNameNS("*", "fldSimple");
        for (int i = 0; i < f.getLength(); i++) b.append(attr((Element) f.item(i), "instr")).append(' ');
        return b.toString();
    }

    private void walk(Element parent, boolean inTable) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element)) continue;
            Element e = (Element) n;
            switch (e.getLocalName()) {
                case "p":
                    paragraphs.add(new Para(e, inTable));
                    break;
                case "tbl":
                    if (!inTable) tables.add(e);
                    for (Element tr : children(e, "tr")) for (Element tc : children(tr, "tc")) walk(tc, true);
                    break;
                case "sdt":
                    Element c = first(e, "sdtContent");
                    if (c != null) walk(c, inTable);
                    break;
                case "sectPr":
                    sectPr = e;
                    break;
                default:
                    break;
            }
        }
        if (!inTable && sectPr == null) {
            // 最后一节的 sectPr 也可能在最后一个段落的 pPr 中
            for (int i = paragraphs.size() - 1; i >= 0 && sectPr == null; i--) {
                Element ppr = first(paragraphs.get(i).p, "pPr");
                if (ppr != null) sectPr = first(ppr, "sectPr");
            }
        }
    }

    private void loadStyles(Element root) {
        Element dd = first(root, "docDefaults");
        if (dd != null) {
            Element rd = first(dd, "rPrDefault");
            if (rd != null) defaultRPr = first(rd, "rPr");
            Element pd = first(dd, "pPrDefault");
            if (pd != null) defaultPPr = first(pd, "pPr");
        }
        for (Element s : children(root, "style")) {
            String id = attr(s, "styleId");
            if (id == null) continue;
            styles.put(id, s);
            if ("paragraph".equals(attr(s, "type")) && ("1".equals(attr(s, "default")) || "true".equals(attr(s, "default")))) defaultParaStyle = id;
        }
    }

    /** 非空的正文段落（不含表格中的段落），按出现顺序。 */
    public List<Para> bodyParagraphs() {
        List<Para> l = new ArrayList<>();
        for (Para p : paragraphs) if (!p.inTable && !p.text.trim().isEmpty()) l.add(p);
        return l;
    }

    // ================= 格式解析 =================

    /** 依次查找的属性容器链：文字 rPr → 字符样式链 → 段落样式链（rPr）→ 文档默认 rPr。 */
    private List<Element> rPrChain(Para p, Run run) {
        List<Element> chain = new ArrayList<>();
        Element rpr = run == null ? null : first(run.r, "rPr");
        if (rpr != null) {
            chain.add(rpr);
            Element rs = first(rpr, "rStyle");
            if (rs != null) addStyleChain(chain, attr(rs, "val"), "rPr");
        }
        addStyleChain(chain, paraStyleId(p), "rPr");
        if (defaultRPr != null) chain.add(defaultRPr);
        return chain;
    }

    private List<Element> pPrChain(Para p) {
        List<Element> chain = new ArrayList<>();
        Element ppr = first(p.p, "pPr");
        if (ppr != null) chain.add(ppr);
        addStyleChain(chain, paraStyleId(p), "pPr");
        if (defaultPPr != null) chain.add(defaultPPr);
        return chain;
    }

    private String paraStyleId(Para p) {
        Element ppr = first(p.p, "pPr");
        Element ps = ppr == null ? null : first(ppr, "pStyle");
        return ps != null ? attr(ps, "val") : defaultParaStyle;
    }

    private void addStyleChain(List<Element> chain, String styleId, String part) {
        Set<String> seen = new HashSet<>();
        while (styleId != null && seen.add(styleId)) {
            Element s = styles.get(styleId);
            if (s == null) return;
            Element e = first(s, part);
            if (e != null) chain.add(e);
            Element based = first(s, "basedOn");
            styleId = based == null ? null : attr(based, "val");
        }
    }

    private static String findAttr(List<Element> chain, String el, String at) {
        for (Element c : chain) {
            Element e = first(c, el);
            if (e != null && attr(e, at) != null) return attr(e, at);
        }
        return null;
    }

    private static Element findEl(List<Element> chain, String el) {
        for (Element c : chain) {
            Element e = first(c, el);
            if (e != null) return e;
        }
        return null;
    }

    /** 中文字体优先（eastAsia），其次西文字体。主题字体返回“主题字体”。 */
    public String font(Para p, Run run, boolean eastAsia) {
        List<Element> ch = rPrChain(p, run);
        String f = findAttr(ch, "rFonts", eastAsia ? "eastAsia" : "ascii");
        if (f == null && !eastAsia) f = findAttr(ch, "rFonts", "hAnsi");
        if (f == null) {
            String theme = findAttr(ch, "rFonts", eastAsia ? "eastAsiaTheme" : "asciiTheme");
            if (theme != null) return "主题字体";
        }
        return f;
    }

    public double size(Para p, Run run) {
        String v = findAttr(rPrChain(p, run), "sz", "val");
        try {
            return v == null ? 10.0 : Double.parseDouble(v) / 2.0;
        } catch (NumberFormatException e) {
            return 10.0;
        }
    }

    public boolean flag(Para p, Run run, String el) {
        Element e = findEl(rPrChain(p, run), el);
        if (e == null) return false;
        String v = attr(e, "val");
        if ("u".equals(el)) return v != null && !"none".equals(v);
        return v == null || !("0".equals(v) || "false".equals(v) || "off".equals(v));
    }

    public String color(Para p, Run run) {
        String v = findAttr(rPrChain(p, run), "color", "val");
        return v == null ? "auto" : v;
    }

    public String align(Para p) {
        String v = findAttr(pPrChain(p), "jc", "val");
        return v == null ? "left" : v;
    }

    /** 首行缩进字符数（按 firstLineChars；只有 firstLine 时按首个文字字号换算）。负数表示悬挂缩进。 */
    public double firstLineChars(Para p) {
        List<Element> ch = pPrChain(p);
        Element ind = findEl(ch, "ind");
        if (ind == null) return 0;
        String fc = attr(ind, "firstLineChars");
        if (fc != null && !"0".equals(fc)) return Double.parseDouble(fc) / 100.0;
        String hc = attr(ind, "hangingChars");
        if (hc != null && !"0".equals(hc)) return -Double.parseDouble(hc) / 100.0;
        double pt = size(p, p.runs.isEmpty() ? null : p.runs.get(0));
        String fl = attr(ind, "firstLine");
        if (fl != null) return Double.parseDouble(fl) / 20.0 / pt;
        String h = attr(ind, "hanging");
        if (h != null) return -Double.parseDouble(h) / 20.0 / pt;
        return 0;
    }

    /** 行距：[规则 auto/exact/atLeast, 值]（auto 为倍数，其余为磅）。 */
    public Object[] lineSpacing(Para p) {
        List<Element> ch = pPrChain(p);
        String line = findAttr(ch, "spacing", "line");
        String rule = findAttr(ch, "spacing", "lineRule");
        if (line == null) return new Object[]{"auto", 1.0};
        double v = Double.parseDouble(line);
        if (rule == null || "auto".equals(rule)) return new Object[]{"auto", v / 240.0};
        return new Object[]{rule, v / 20.0};
    }

    /** 段前/段后：[单位 "行"/"磅", 值]。 */
    public Object[] paraSpace(Para p, boolean before) {
        List<Element> ch = pPrChain(p);
        String lines = findAttr(ch, "spacing", before ? "beforeLines" : "afterLines");
        if (lines != null && !"0".equals(lines)) return new Object[]{"行", Double.parseDouble(lines) / 100.0};
        String tw = findAttr(ch, "spacing", before ? "before" : "after");
        return new Object[]{"磅", tw == null ? 0.0 : Double.parseDouble(tw) / 20.0};
    }

    /** 页边距（厘米）：top/bottom/left/right。 */
    public double margin(String side) {
        Element pm = sectPr == null ? null : first(sectPr, "pgMar");
        String v = pm == null ? null : attr(pm, side);
        if (v == null) {
            switch (side) {
                case "top":
                case "bottom":
                    return 2.54;
                default:
                    return 3.17;
            }
        }
        return Double.parseDouble(v) / 566.93;
    }

    /** 纸张：[宽 twips, 高 twips, 是否横向]。 */
    public Object[] pageSize() {
        Element ps = sectPr == null ? null : first(sectPr, "pgSz");
        double w = ps == null || attr(ps, "w") == null ? 11906 : Double.parseDouble(attr(ps, "w"));
        double h = ps == null || attr(ps, "h") == null ? 16838 : Double.parseDouble(attr(ps, "h"));
        boolean landscape = (ps != null && "landscape".equals(attr(ps, "orient"))) || w > h;
        return new Object[]{w, h, landscape};
    }

    public int columns() {
        Element c = sectPr == null ? null : first(sectPr, "cols");
        String n = c == null ? null : attr(c, "num");
        return n == null ? 1 : Integer.parseInt(n);
    }

    /** 表格行数、列数（列数取表格网格列数，没有网格时取首行单元格数）。 */
    public int[] tableSize(Element tbl) {
        List<Element> rows = children(tbl, "tr");
        Element grid = first(tbl, "tblGrid");
        int cols = grid == null ? 0 : children(grid, "gridCol").size();
        if (cols == 0 && !rows.isEmpty()) cols = children(rows.get(0), "tc").size();
        return new int[]{rows.size(), cols};
    }

    // ================= XML 工具 =================

    private static byte[] read(ZipFile zf, String name) throws IOException {
        ZipEntry e = zf.getEntry(name);
        if (e == null) return null;
        try (InputStream in = zf.getInputStream(e)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) {
                bos.write(buf, 0, r);
                if (bos.size() > MAX_PART) throw new IOException("文档过大");
            }
            return bos.toByteArray();
        }
    }

    private static Document parse(byte[] xml) throws Exception {
        if (xml == null) return null;
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setExpandEntityReferences(false);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(xml));
    }

    static List<Element> children(Element e, String local) {
        List<Element> l = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && local.equals(n.getLocalName())) l.add((Element) n);
        }
        return l;
    }

    static Element first(Element e, String local) {
        if (e == null) return null;
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && local.equals(n.getLocalName())) return (Element) n;
        }
        return null;
    }

    /** 读取 w: 命名空间（或无前缀）的属性。 */
    static String attr(Element e, String local) {
        if (e == null) return null;
        String v = e.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", local);
        if (v == null || v.isEmpty()) v = e.getAttribute(local);
        return v == null || v.isEmpty() ? null : v;
    }
}
