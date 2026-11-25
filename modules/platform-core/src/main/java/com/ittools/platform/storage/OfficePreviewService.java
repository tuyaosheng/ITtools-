package com.ittools.platform.storage;

import org.apache.poi.common.usermodel.fonts.FontInfo;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.sl.draw.DrawFontManager;
import org.apache.poi.sl.draw.DrawFontManagerDefault;
import org.apache.poi.sl.draw.Drawable;
import org.apache.poi.hwpf.converter.WordToHtmlConverter;
import org.apache.poi.sl.usermodel.GroupShape;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.SlideShowFactory;
import org.apache.poi.sl.usermodel.TableCell;
import org.apache.poi.sl.usermodel.TableShape;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.TextRun;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.util.XMLHelper;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 课件在线预览（机房内网、服务器不一定装 Office/LibreOffice，所以用 POI 在服务端渲染）：
 * - ppt / pptx：每张幻灯片渲染成 JPG，前端按张翻页播放；
 * - doc：转成网页（docx / xlsx 由前端直接在浏览器里显示）。
 * 结果缓存在存储目录旁边的 preview-cache 下，按文件路径 + 大小 + 修改时间区分，文件替换后自动重新生成。
 */
@Service
public class OfficePreviewService {
    private static final int SLIDE_WIDTH = 1600;
    private final FileStorageService storage;
    private final Path cacheRoot;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private static final DrawFontManager CJK_FONTS = new CjkFontManager();

    /**
     * 课件常用 Calibri、Arial 等西文字体，里面没有汉字，Java 画出来是方框。
     * 中文字体选本机有的（微软雅黑 / 黑体 / 宋体 / Linux 下的 Noto、文泉驿）；配合 fixCjkRuns 使用。
     */
    static final class CjkFontManager extends DrawFontManagerDefault {
        static final String CJK_FAMILY = pickCjkFamily();

        private static String pickCjkFamily() {
            java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(
                    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String f : new String[]{"Microsoft YaHei", "微软雅黑", "SimHei", "黑体", "SimSun", "宋体", "DengXian", "等线",
                    "PingFang SC", "Noto Sans CJK SC", "Noto Sans SC", "Source Han Sans SC", "WenQuanYi Micro Hei", "WenQuanYi Zen Hei"}) {
                if (have.contains(f)) return f;
            }
            return Font.DIALOG;   // 逻辑字体，JRE 自带的字体映射一般能显示中文
        }

        private static final Map<String, Boolean> CJK_OK = new ConcurrentHashMap<>();

        /** 文字段里个别字符当前字体画不出来时，POI 用这里的备用字体补上。 */
        @Override
        public FontInfo getFallbackFont(Graphics2D graphics, FontInfo fontInfo) {
            return cjkInfo();
        }

        static boolean canShowCjk(String face) {
            return CJK_OK.computeIfAbsent(face, n -> new Font(n, Font.PLAIN, 12).canDisplay(0x4e2d));
        }

        private static FontInfo cjkInfo() {
            return new FontInfo() {
                @Override
                public String getTypeface() {
                    return CJK_FAMILY;
                }
            };
        }
    }

    private final LibreOfficeService libreOffice;

    public OfficePreviewService(FileStorageService storage, LibreOfficeService libreOffice) {
        this.storage = storage;
        this.libreOffice = libreOffice;
        Path parent = storage.root().getParent();
        this.cacheRoot = (parent == null ? storage.root() : parent).resolve("preview-cache");
    }

    public static boolean isSlides(String ext) {
        return "ppt".equals(ext) || "pptx".equals(ext) || "pps".equals(ext) || "ppsx".equals(ext);
    }

    /** 预览信息：幻灯片返回 {kind: slides, count, width, height}；doc 返回 {kind: html}；其余 {kind: none}。 */
    public Map<String, Object> info(String relPath, String fileName) {
        return info(relPath, fileName, null);
    }

    /**
     * @param mode "images" 时 PPT 强制按图片显示；否则服务器装了 LibreOffice 就转成带动画的演示（kind = show），
     *             转换失败自动退回图片。
     */
    public Map<String, Object> info(String relPath, String fileName, String mode) {
        String ext = FileStorageService.extensionOf(fileName);
        Map<String, Object> m = new LinkedHashMap<>();
        // 缓存版本：前端拼到图片/演示地址上，课件换了文件或转换方式升级后浏览器不会用旧缓存
        m.put("ver", cacheDir(existing(relPath), relPath).getFileName().toString().substring(0, 12));
        if (isSlides(ext) && !"images".equals(mode) && libreOffice.available()) {
            try {
                Path show = ensureShow(relPath);
                m.put("kind", "show");
                m.put("ratio", showRatio(show));
                return m;
            } catch (IllegalArgumentException e) {
                m.put("showError", e.getMessage());   // 退回图片，前端可提示
            }
        }
        if (isSlides(ext)) {
            Path dir = ensureSlides(relPath);
            Map<String, String> meta = readMeta(dir);
            m.put("kind", "slides");
            m.put("count", Integer.parseInt(meta.get("count")));
            m.put("width", Integer.parseInt(meta.get("width")));
            m.put("height", Integer.parseInt(meta.get("height")));
        } else if ("doc".equals(ext)) {
            ensureDocHtml(relPath);
            m.put("kind", "html");
        } else {
            m.put("kind", "none");
        }
        return m;
    }

    /** 第 n 张幻灯片（从 1 开始）的图片文件。 */
    public Path slide(String relPath, int n) {
        Path dir = ensureSlides(relPath);
        int count = Integer.parseInt(readMeta(dir).get("count"));
        if (n < 1 || n > count) throw new IllegalArgumentException("没有第 " + n + " 张幻灯片");
        return dir.resolve(n + ".jpg");
    }

    /** LibreOffice 转出的交互式演示（SVG + 自带播放脚本，点击/方向键逐步播放动画和切换）。 */
    public Path show(String relPath) {
        return ensureShow(relPath);
    }

    private Path ensureShow(String relPath) {
        Path src = existing(relPath);
        Path dir = cacheDir(src, relPath);
        Path out = dir.resolve("show.xhtml");
        Path failed = dir.resolve("show.failed");
        if (Files.exists(out)) return out;
        if (Files.exists(failed)) throw new IllegalArgumentException("动画演示生成失败，已改用图片显示");
        synchronized (locks.computeIfAbsent(dir.toString() + "#show", k -> new Object())) {
            if (Files.exists(out)) return out;
            try {
                Files.createDirectories(dir);
                Path work = cacheRoot.resolve("work").resolve(dir.getFileName().toString());
                Path svg = libreOffice.convert(src, "svg:impress_svg_Export", "svg", work);
                Path tmp = dir.resolve("show.xhtml.tmp");
                Files.write(tmp, wrapSvg(new String(Files.readAllBytes(svg), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
                Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(svg);
            } catch (Exception e) {
                try {
                    Files.write(failed, String.valueOf(e.getMessage()).getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                    // 记录失败只是为了不反复转换
                }
                throw new IllegalArgumentException("动画演示生成失败（" + e.getMessage() + "），已改用图片显示");
            }
        }
        return out;
    }

    /** 演示的宽高比（取 SVG 的 viewBox），前端按这个比例固定渲染再整体缩放。 */
    private static double showRatio(Path show) {
        try (java.io.Reader r = Files.newBufferedReader(show, StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n = r.read(buf);
            java.util.regex.Matcher mt = java.util.regex.Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"")
                    .matcher(new String(buf, 0, Math.max(n, 0)));
            if (mt.find()) return Double.parseDouble(mt.group(1)) / Double.parseDouble(mt.group(2));
        } catch (Exception ignored) {
            // 取不到就按 16:9
        }
        return 16.0 / 9;
    }

    /**
     * 把 LibreOffice 的 SVG 演示包进 XHTML 页面：浏览器都按普通网页加载（有的浏览器/内嵌窗口不肯在框里打开纯 SVG 文档），
     * XML 解析保留 ooo:/anim: 等命名空间；播放脚本里假定 SVG 是整个文档的几处调用改成指向 SVG 根元素。
     */
    static String wrapSvg(String svg) {
        int start = svg.indexOf("<svg");
        if (start < 0) throw new IllegalStateException("不是 SVG");
        String body = svg.substring(start).replace("document.documentElement.createSVG", "ROOT_NODE.createSVG");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                +"<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/><title>slides</title>"
                + "<style>html,body{margin:0;width:100%;height:100%;background:#000;overflow:hidden}"
                + "body>svg{display:block;width:100%;height:100%}</style>"
                + "<script><![CDATA[window.addEventListener('error',function(e){try{window.parent.postMessage("
                + "{ittoolsSlidesError:String(e.message)+' @'+e.lineno},'*');}catch(x){}});]]></script></head><body>"
                + body
                // 外层平台页面的“上一步/下一步”按钮和键盘通过 postMessage 控制播放（焦点不在框里时也能翻）
                + "<script><![CDATA[window.addEventListener('message',function(e){"
                + "if(e.source!==window.parent||!e.data||e.data.ittoolsSlides===undefined)return;"
                + "try{var c=e.data.ittoolsSlides;if(c==='next')dispatchEffects(1);else if(c==='prev')aSlideShow.rewindEffect();"
                + "else if(c==='nextSlide')switchSlide(1,false);else if(c==='prevSlide')switchSlide(-1,false);"
                + "else if(c==='first')aSlideShow.displaySlide(0,true);}catch(err){}"
                + "setTimeout(ittoolsNow,150);setTimeout(ittoolsNow,1500);});"
                + "function ittoolsNow(){try{if(typeof nCurSlide==='number')ittoolsReport(nCurSlide);}catch(err){}}"
                // 每翻到一页把“第几页 / 共几页”告诉外层页面（工具栏显示进度）
                + "function ittoolsReport(i){try{window.parent.postMessage({ittoolsSlidesState:{slide:i+1,count:theMetaDoc.nNumberOfSlides}},'*');}catch(err){}}"
                + "window.addEventListener('load',function(){setTimeout(function(){try{var o=aSlideShow.notifySlideStart;"
                + "aSlideShow.notifySlideStart=function(n){ittoolsReport(n);return o.apply(this,arguments);};"
                + "ittoolsReport(theMetaDoc.nStartSlideNumber||0);}catch(err){}},0);});]]></script>"
                + "</body></html>";
    }

    public Path docHtml(String relPath) {
        return ensureDocHtml(relPath);
    }

    // ---------------------------------------------------------------------------------------------

    private Path ensureSlides(String relPath) {
        Path src = existing(relPath);
        Path dir = cacheDir(src, relPath);
        if (Files.exists(dir.resolve("meta.txt"))) return dir;
        synchronized (locks.computeIfAbsent(dir.toString(), k -> new Object())) {
            if (Files.exists(dir.resolve("meta.txt"))) return dir;
            try {
                Files.createDirectories(dir);
                try (SlideShow<?, ?> ss = SlideShowFactory.create(src.toFile(), null, true)) {
                    Dimension pg = ss.getPageSize();
                    double scale = (double) SLIDE_WIDTH / pg.width;
                    int w = SLIDE_WIDTH;
                    int h = (int) Math.round(pg.height * scale);
                    List<? extends Slide<?, ?>> slides = ss.getSlides();
                    int i = 0;
                    for (Slide<?, ?> s : slides) {
                        i++;
                        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = img.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                        g.setRenderingHint(Drawable.FONT_HANDLER, CJK_FONTS);
                        g.setPaint(Color.WHITE);
                        g.fillRect(0, 0, w, h);
                        g.scale(scale, scale);
                        try {
                            fixCjkRuns(s.getShapes());
                            s.draw(g);
                        } catch (RuntimeException e) {
                            // 个别形状画不出来时保留已画好的部分，不让整份课件预览失败
                        }
                        g.dispose();
                        writeJpg(img, dir.resolve(i + ".jpg"));
                    }
                    String meta = "count=" + i + "\nwidth=" + w + "\nheight=" + h + "\n";
                    Files.write(dir.resolve("meta.txt"), meta.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("这个课件无法在线预览（" + e.getClass().getSimpleName() + "），请下载后查看");
            }
        }
        return dir;
    }

    /**
     * POI 画字时按文字段自己的字体（如 Calibri）建 AWT 字体，字体映射管不到；所以渲染前在内存里
     * 把“含汉字、但字体显示不了汉字”的文字段改用中文字体（只改内存，不写回文件）。
     */
    private static void fixCjkRuns(List<? extends Shape<?, ?>> shapes) {
        for (Shape<?, ?> sh : shapes) {
            if (sh instanceof GroupShape) {
                fixCjkRuns(((GroupShape<?, ?>) sh).getShapes());
            } else if (sh instanceof TableShape) {
                TableShape<?, ?> t = (TableShape<?, ?>) sh;
                for (int r = 0; r < t.getNumberOfRows(); r++) {
                    for (int c = 0; c < t.getNumberOfColumns(); c++) {
                        TableCell<?, ?> cell = t.getCell(r, c);
                        if (cell != null) fixCjkText(cell);
                    }
                }
            } else if (sh instanceof TextShape) {
                fixCjkText((TextShape<?, ?>) sh);
            }
        }
    }

    private static void fixCjkText(TextShape<?, ?> ts) {
        for (TextParagraph<?, ?, ?> p : ts.getTextParagraphs()) {
            for (TextRun run : p.getTextRuns()) {
                String text = run.getRawText();
                if (text == null || !hasCjk(text)) continue;
                String face = run.getFontFamily();
                if (face == null || !CjkFontManager.canShowCjk(face)) {
                    run.setFontFamily(CjkFontManager.CJK_FAMILY);
                }
            }
        }
    }

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            Character.UnicodeScript sc = Character.UnicodeScript.of(s.charAt(i));
            if (sc == Character.UnicodeScript.HAN || sc == Character.UnicodeScript.HIRAGANA
                    || sc == Character.UnicodeScript.KATAKANA || sc == Character.UnicodeScript.HANGUL) return true;
            char ch = s.charAt(i);
            if (ch >= '　' && ch <= '〿' || ch >= '＀' && ch <= '￯') return true;   // 中文标点、全角符号
        }
        return false;
    }

    private Path ensureDocHtml(String relPath) {
        Path src = existing(relPath);
        Path dir = cacheDir(src, relPath);
        Path out = dir.resolve("doc.html");
        if (Files.exists(out)) return out;
        synchronized (locks.computeIfAbsent(dir.toString(), k -> new Object())) {
            if (Files.exists(out)) return out;
            try (InputStream in = Files.newInputStream(src); HWPFDocument doc = new HWPFDocument(in)) {
                Files.createDirectories(dir);
                WordToHtmlConverter conv = new WordToHtmlConverter(XMLHelper.newDocumentBuilder().newDocument());
                conv.processDocument(doc);
                Transformer t = TransformerFactory.newInstance().newTransformer();
                t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                t.setOutputProperty(OutputKeys.METHOD, "html");
                Path tmp = dir.resolve("doc.html.tmp");
                try (OutputStream os = Files.newOutputStream(tmp)) {
                    t.transform(new DOMSource(conv.getDocument()), new StreamResult(os));
                }
                Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                throw new IllegalArgumentException("这个文档无法在线预览（" + e.getClass().getSimpleName() + "），请下载后查看");
            }
        }
        return out;
    }

    private Path existing(String relPath) {
        Path src = storage.resolve(relPath);
        if (!Files.exists(src)) throw new IllegalArgumentException("文件不存在");
        return src;
    }

    private Path cacheDir(Path src, String relPath) {
        try {
            String key = "v9|" + relPath + "|" + Files.size(src) + "|" + Files.getLastModifiedTime(src).toMillis();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(key.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", b));
            return cacheRoot.resolve(sb.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> readMeta(Path dir) {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(dir.resolve("meta.txt"), StandardCharsets.UTF_8)) {
                int i = line.indexOf('=');
                if (i > 0) m.put(line.substring(0, i), line.substring(i + 1));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return m;
    }

    private static void writeJpg(BufferedImage img, Path out) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam p = writer.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.88f);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out.toFile())) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), p);
        } finally {
            writer.dispose();
        }
    }
}
