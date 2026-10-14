package com.ittools.platform.service;

import com.ittools.platform.settings.SystemSettingService;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.storage.StoredFile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.ittools.platform.db.Sql.p;

/**
 * 个人资料与头像（原 student/info、teacher/info、admin/stucamera 头像采集、teacher/stuphoto 头像查询）。
 * 头像统一解码后缩放为不超过 240 像素的 JPEG 保存，既节省空间又去除图片中的非图像内容。
 */
@Service
public class ProfileService {
    static final int AVATAR_SIZE = 240;
    static final int MAX_PIXELS_SIDE = 8000;
    static final long MAX_UPLOAD = 5L * 1024 * 1024;
    static final long MAX_ZIP = 300L * 1024 * 1024;

    private final NamedParameterJdbcTemplate jdbc;
    private final FileStorageService storage;
    private final SystemSettingService settings;

    public ProfileService(NamedParameterJdbcTemplate jdbc, FileStorageService storage, SystemSettingService settings) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> profile(Long userId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT u.id, u.role, u.name, u.login_name AS \"loginName\", u.student_no AS \"studentNo\", "
                + "u.xjh, u.gender, u.birthday, u.bio, u.avatar_path IS NOT NULL AS \"hasAvatar\", k.name AS \"className\", "
                + "y.label AS \"yearLabel\" FROM users u LEFT JOIN klass k ON k.id = u.class_id LEFT JOIN school_year y ON y.id = u.enroll_year_id "
                + "WHERE u.id = :id", p("id", userId));
        if (l.isEmpty()) throw new IllegalArgumentException("用户不存在");
        Map<String, Object> m = new LinkedHashMap<>(l.get(0));
        boolean student = "STUDENT".equals(m.get("role"));
        m.put("canChangeName", !student);
        m.put("canChangeAvatar", !student || settings.getBool(SystemSettingService.STUDENT_AVATAR));
        m.put("canChangePassword", !student || settings.getBool(SystemSettingService.STUDENT_PASSWORD));
        return m;
    }

    @Transactional
    public Map<String, Object> update(Long userId, Map<String, Object> body) {
        Map<String, Object> cur = profile(userId);
        Object g = body.get("gender");
        String gender = g == null || String.valueOf(g).isEmpty() ? null : String.valueOf(g);
        if (gender != null && !"男".equals(gender) && !"女".equals(gender)) throw new IllegalArgumentException("性别只能是 男 或 女");
        LocalDate birthday = null;
        Object b = body.get("birthday");
        if (b != null && !String.valueOf(b).isEmpty()) {
            try {
                birthday = LocalDate.parse(String.valueOf(b).substring(0, 10));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("生日格式应为 yyyy-MM-dd");
            }
            if (birthday.isAfter(LocalDate.now()) || birthday.getYear() < 1900) throw new IllegalArgumentException("生日不正确");
        }
        String bio = body.get("bio") == null ? null : String.valueOf(body.get("bio")).trim();
        if (bio != null && bio.length() > 200) throw new IllegalArgumentException("个人简介不超过 200 字");
        jdbc.update("UPDATE users SET gender = :g, birthday = :b, bio = :bio, updated_at = now() WHERE id = :id",
                p("g", gender, "b", birthday == null ? null : java.sql.Date.valueOf(birthday), "bio", bio == null || bio.isEmpty() ? null : bio, "id", userId));
        if (Boolean.TRUE.equals(cur.get("canChangeName")) && body.get("name") != null) {
            String name = String.valueOf(body.get("name")).trim();
            if (name.isEmpty() || name.length() > 64) throw new IllegalArgumentException("姓名为 1~64 字");
            jdbc.update("UPDATE users SET name = :n WHERE id = :id", p("n", name, "id", userId));
        }
        return profile(userId);
    }

    // ================= 头像 =================

    /** 本人上传头像（学生需系统开放）。 */
    @Transactional
    public Map<String, Object> uploadOwn(Long userId, MultipartFile file) throws IOException {
        if (!Boolean.TRUE.equals(profile(userId).get("canChangeAvatar"))) throw new IllegalArgumentException("头像由老师统一采集，系统未开放自行上传");
        if (file.getSize() > MAX_UPLOAD) throw new IllegalArgumentException("图片不能超过 5MB");
        setAvatar(userId, file.getBytes());
        return profile(userId);
    }

    @Transactional
    public void setAvatar(Long userId, byte[] image) throws IOException {
        byte[] jpg = toAvatar(image);
        StoredFile sf = storage.storeBytes(jpg, "avatar", "avatar.jpg");
        List<String> old = jdbc.queryForList("SELECT avatar_path FROM users WHERE id = :id", p("id", userId), String.class);
        if (old.isEmpty()) throw new IllegalArgumentException("用户不存在");
        jdbc.update("UPDATE users SET avatar_path = :a WHERE id = :id", p("a", sf.getRelPath(), "id", userId));
        if (old.get(0) != null) storage.delete(old.get(0));
    }

    @Transactional
    public void removeAvatar(Long userId) {
        List<String> old = jdbc.queryForList("SELECT avatar_path FROM users WHERE id = :id", p("id", userId), String.class);
        jdbc.update("UPDATE users SET avatar_path = NULL WHERE id = :id", p("id", userId));
        if (!old.isEmpty() && old.get(0) != null) storage.delete(old.get(0));
    }

    /** 头像文件（无头像返回 null）。 */
    @Transactional(readOnly = true)
    public Path avatarFile(Long userId) {
        List<String> l = jdbc.queryForList("SELECT avatar_path FROM users WHERE id = :id", p("id", userId), String.class);
        if (l.isEmpty() || l.get(0) == null) return null;
        Path f = storage.resolve(l.get(0));
        return Files.isRegularFile(f) ? f : null;
    }

    /** 解码图片（JPG/PNG/GIF/BMP），按比例缩放到不超过 240 像素，重新编码为 JPEG。 */
    static byte[] toAvatar(byte[] data) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> it = iis == null ? null : ImageIO.getImageReaders(iis);
            if (it == null || !it.hasNext()) throw new IllegalArgumentException("只支持 JPG、PNG、GIF、BMP 图片");
            ImageReader r = it.next();
            try {
                r.setInput(iis);
                if (r.getWidth(0) > MAX_PIXELS_SIDE || r.getHeight(0) > MAX_PIXELS_SIDE) throw new IllegalArgumentException("图片尺寸过大");
                BufferedImage src = r.read(0);
                int w = src.getWidth(), h = src.getHeight();
                double scale = Math.min(1.0, (double) AVATAR_SIZE / Math.max(w, h));
                int nw = Math.max(1, (int) Math.round(w * scale)), nh = Math.max(1, (int) Math.round(h * scale));
                BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = dst.createGraphics();
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, nw, nh);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(src, 0, 0, nw, nh, null);
                g.dispose();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(dst, "jpg", out);
                return out.toByteArray();
            } finally {
                r.dispose();
            }
        }
    }

    /**
     * 批量导入头像（原“学生头像采集”后复制照片到服务器）：zip 中的图片以 学籍号 或 教师登录名 命名，如 G330100200801010011.jpg。
     */
    @Transactional
    public Map<String, Object> importZip(MultipartFile file) throws IOException {
        if (file.getSize() > MAX_ZIP) throw new IllegalArgumentException("压缩包不能超过 300MB");
        File tmp = File.createTempFile("avatars", ".zip");
        List<String> unmatched = new ArrayList<>();
        int ok = 0, bad = 0;
        try {
            file.transferTo(tmp);
            ZipFile zf;
            try {
                zf = new ZipFile(tmp, java.nio.charset.StandardCharsets.UTF_8);
                zf.entries().hasMoreElements();
            } catch (IllegalArgumentException e) {
                zf = new ZipFile(tmp, java.nio.charset.Charset.forName("GBK"));
            }
            try {
                Enumeration<? extends ZipEntry> en = zf.entries();
                int n = 0;
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory()) continue;
                    if (++n > 10000) throw new IllegalArgumentException("压缩包中的文件过多");
                    String name = e.getName().replace('\\', '/');
                    name = name.substring(name.lastIndexOf('/') + 1);
                    int dot = name.lastIndexOf('.');
                    if (dot <= 0 || name.startsWith(".")) continue;
                    String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
                    if (!"jpg".equals(ext) && !"jpeg".equals(ext) && !"png".equals(ext) && !"gif".equals(ext) && !"bmp".equals(ext)) continue;
                    String key = name.substring(0, dot).trim();
                    List<Long> ids = jdbc.queryForList("SELECT id FROM users WHERE (role = 'STUDENT' AND xjh = :k) OR (role <> 'STUDENT' AND login_name = :k)",
                            p("k", key), Long.class);
                    if (ids.size() != 1) {
                        if (unmatched.size() < 200) unmatched.add(name);
                        continue;
                    }
                    try (InputStream in = zf.getInputStream(e)) {
                        if (e.getSize() > MAX_UPLOAD) {
                            bad++;
                            continue;
                        }
                        setAvatar(ids.get(0), readAll(in));
                        ok++;
                    } catch (IllegalArgumentException ex) {
                        bad++;
                    }
                }
            } finally {
                zf.close();
            }
        } finally {
            if (!tmp.delete()) tmp.deleteOnExit();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("imported", ok);
        out.put("invalid", bad);
        out.put("unmatched", unmatched);
        return out;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) > 0) {
            bos.write(buf, 0, r);
            if (bos.size() > MAX_UPLOAD) throw new IllegalArgumentException("图片过大");
        }
        return bos.toByteArray();
    }
}
