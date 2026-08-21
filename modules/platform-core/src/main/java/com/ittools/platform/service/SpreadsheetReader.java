package com.ittools.platform.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a spreadsheet uploaded for batch import into rows of trimmed string
 * cells (row 0 is the header). Supports .xlsx / .xls (Apache POI) and .csv
 * (commons-csv, UTF-8). Never returns null cells — missing cells are "".
 */
final class SpreadsheetReader {

    List<List<String>> read(InputStream in, String filename) throws Exception {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".csv")) {
            return readCsv(in);
        }
        return readExcel(in);
    }

    private List<List<String>> readExcel(InputStream in) throws Exception {
        List<List<String>> rows = new ArrayList<List<String>>();
        Workbook wb = WorkbookFactory.create(in);
        try {
            DataFormatter fmt = new DataFormatter();
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<String>();
                if (row != null) {
                    int lastCell = row.getLastCellNum();
                    for (int c = 0; c < lastCell; c++) {
                        Cell cell = row.getCell(c);
                        cells.add(cell == null ? "" : fmt.formatCellValue(cell).trim());
                    }
                }
                rows.add(cells);
            }
        } finally {
            wb.close();
        }
        return rows;
    }

    private List<List<String>> readCsv(InputStream in) throws Exception {
        List<List<String>> rows = new ArrayList<List<String>>();
        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        CSVParser parser = CSVParser.parse(reader, CSVFormat.DEFAULT);
        try {
            boolean first = true;
            for (CSVRecord rec : parser) {
                List<String> cells = new ArrayList<String>();
                for (int c = 0; c < rec.size(); c++) {
                    String v = rec.get(c);
                    if (first && c == 0 && v != null) {
                        v = v.replace("﻿", ""); // strip UTF-8 BOM on the first cell
                    }
                    cells.add(v == null ? "" : v.trim());
                }
                rows.add(cells);
                first = false;
            }
        } finally {
            parser.close();
        }
        return rows;
    }
}
