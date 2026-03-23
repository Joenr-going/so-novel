package com.pcdd.sonovel.core;

import cn.hutool.core.io.FileUtil;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.BookFormat;
import com.pcdd.sonovel.model.Chapter;
import com.pcdd.sonovel.parse.TocParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class FormatFetcher {

    private final AppConfig config;

    public FormatFetcher(AppConfig config) {
        this.config = config;
    }

    public InputStream fetch(String bookUrl) {
        List<Chapter> toc = new TocParser(config).parseAll(bookUrl);
        return fetch(bookUrl, toc);
    }

    public InputStream fetch(String bookUrl, List<Chapter> toc) {
        if (toc == null || toc.isEmpty()) {
            return InputStream.nullInputStream();
        }
        BookFormat format = normalizeFormat(config.getExtName());
        File tempDir = createTempDir();
        try {
            new Crawler(config, null, tempDir.getAbsolutePath()).crawl(bookUrl, toc);
            File output = findOutputFile(tempDir, format);
            if (output == null || !output.exists()) {
                FileUtil.del(tempDir);
                return InputStream.nullInputStream();
            }
            try {
                return new AutoDeleteFileInputStream(output, tempDir);
            } catch (IOException e) {
                FileUtil.del(tempDir);
                throw new IllegalStateException("打开输出文件失败", e);
            }
        } catch (Exception e) {
            FileUtil.del(tempDir);
            throw e;
        }
    }

    private BookFormat normalizeFormat(BookFormat format) {
        return format == null ? Defaults.EXT_NAME : format;
    }

    private File createTempDir() {
        try {
            File f = File.createTempFile("so-novel-fetch-", "");
            if (!f.delete()) {
                throw new IOException("delete temp marker failed");
            }
            if (!f.mkdir()) {
                throw new IOException("mkdir temp dir failed");
            }
            return f;
        } catch (IOException e) {
            throw new IllegalStateException("创建临时目录失败", e);
        }
    }

    private File findOutputFile(File tempDir, BookFormat format) {
        String suffix = BookFormat.HTML.equals(format) ? ".zip" : "." + format.name().toLowerCase();
        File[] files = tempDir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(suffix)) {
                return file;
            }
        }
        return null;
    }

    private static class AutoDeleteFileInputStream extends FilterInputStream {
        private final File file;
        private final File dir;

        protected AutoDeleteFileInputStream(File file, File dir) throws IOException {
            super(new FileInputStream(file));
            this.file = file;
            this.dir = dir;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                FileUtil.del(file);
                FileUtil.del(dir);
            }
        }
    }
}
