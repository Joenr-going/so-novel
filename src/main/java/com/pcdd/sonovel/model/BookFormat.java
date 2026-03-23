package com.pcdd.sonovel.model;

/**
 * 抓取与下载支持的文件格式枚举
 */
public enum BookFormat {
    TXT("txt"),
    EPUB("epub"),
    HTML("html"),
    PDF("pdf");

    private final String extName;

    BookFormat(String extName) {
        this.extName = extName;
    }

    public String getExtName() {
        return extName;
    }

    public static BookFormat fromString(String format) {
        if (format == null || format.isBlank()) {
            return EPUB;
        }
        for (BookFormat bf : values()) {
            if (bf.name().equalsIgnoreCase(format)) {
                return bf;
            }
        }
        throw new IllegalArgumentException("Unsupported format: " + format);
    }
}
