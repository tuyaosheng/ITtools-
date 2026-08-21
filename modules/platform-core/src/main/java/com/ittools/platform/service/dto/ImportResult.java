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
    private final List<RowError> errors = new ArrayList<RowError>();

    public int getTotal() { return total; }
    public int getImported() { return imported; }
    public int getFailed() { return errors.size(); }
    public List<RowError> getErrors() { return errors; }

    public void incrementTotal() { this.total++; }
    public void incrementImported() { this.imported++; }
    public void addError(int row, String message) { this.errors.add(new RowError(row, message)); }
}
