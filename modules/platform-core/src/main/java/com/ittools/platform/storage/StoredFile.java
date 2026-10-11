package com.ittools.platform.storage;

/** 已保存文件的元数据（relPath 为相对存储根目录的路径，入库保存它）。 */
public class StoredFile {
    private final String relPath;
    private final String originalName;
    private final long size;
    private final String extension;

    public StoredFile(String relPath, String originalName, long size, String extension) {
        this.relPath = relPath;
        this.originalName = originalName;
        this.size = size;
        this.extension = extension;
    }

    public String getRelPath() { return relPath; }
    public String getOriginalName() { return originalName; }
    public long getSize() { return size; }
    public String getExtension() { return extension; }
}
