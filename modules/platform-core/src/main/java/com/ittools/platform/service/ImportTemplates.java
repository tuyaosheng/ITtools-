package com.ittools.platform.service;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 生成批量导入模板（.xlsx）：第 1 张表是表头 + 示例行（改好后可直接上传导入，导入只读第 1 张表），
 * 第 2 张表“填写说明”逐条列出规则。
 */
public final class ImportTemplates {
    private ImportTemplates() {
    }

    public static byte[] build(String sheetName, String[] header, String[][] examples, int[] widths, String... notes) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            Sheet s = wb.createSheet(sheetName);
            Row h = s.createRow(0);
            for (int c = 0; c < header.length; c++) {
                h.createCell(c).setCellValue(header[c]);
                h.getCell(c).setCellStyle(bold);
                s.setColumnWidth(c, (widths != null && c < widths.length ? widths[c] : 14) * 256);
            }
            for (int r = 0; r < examples.length; r++) {
                Row row = s.createRow(r + 1);
                for (int c = 0; c < examples[r].length; c++) row.createCell(c).setCellValue(examples[r][c]);
            }
            s.createFreezePane(0, 1);
            Sheet n = wb.createSheet("填写说明");
            n.setColumnWidth(0, 100 * 256);
            String[] all = new String[notes.length + 2];
            all[0] = "在“" + sheetName + "”表中按表头填写，一行一条；示例行是可以直接导入的真实数据，不需要就改掉或删掉。";
            System.arraycopy(notes, 0, all, 1, notes.length);
            all[all.length - 1] = "保存后在页面上点“导入”选择本文件即可；导入只读第一张表，本说明表不用删除。也支持另存为 CSV（UTF-8）。";
            for (int i = 0; i < all.length; i++) n.createRow(i).createCell(0).setCellValue((i + 1) + ". " + all[i]);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成模板失败：" + e.getMessage());
        }
    }
}
