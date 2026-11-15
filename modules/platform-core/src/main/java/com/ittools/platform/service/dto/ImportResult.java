package com.ittools.platform.service.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of a batch import: how many rows were read, how many imported,
 * and a per-row reason for each failure. Serialized to JSON (bean getters).
 */
public final class ImportResult {

    public static final class RowError {
        private final int row;
        private final String message;

        public RowError(int row, String message) {
            this.row = row;
            this.message = message;
        }

        /** 1-based data row number (spreadsheet row minus the header row). */
        public int getRow() { return row; }
        public String getMessage() { return message; }
    }

    private int total;
    private int imported;
    private int skipped;
    private final List<RowError> errors = new ArrayList<RowError>();

    public int getTotal() { return total; }
    public int getImported() { return imported; }
    /** Rows skipped as duplicates of existing records (not counted as failures). */
    public int getSkipped() { return skipped; }
    public int getFailed() { return errors.size(); }
    public List<RowError> getErrors() { return errors; }

    public void incrementTotal() { this.total++; }
    public void incrementImported() { this.imported++; }
    public void incrementSkipped() { this.skipped++; }
    public void addError(int row, String message) { this.errors.add(new RowError(row, message)); }
}
