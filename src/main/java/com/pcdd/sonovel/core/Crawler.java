package com.pcdd.sonovel.core;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.pcdd.sonovel.context.BookContext;
import com.pcdd.sonovel.context.DownloadContext;
import com.pcdd.sonovel.handle.CrawlerPostHandler;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.BookFormat;
import com.pcdd.sonovel.model.Chapter;
import com.pcdd.sonovel.model.Rule.Book;
import com.pcdd.sonovel.parse.BookParser;
import com.pcdd.sonovel.parse.ChapterParser;
import com.pcdd.sonovel.parse.TocParser;
import com.pcdd.sonovel.util.FileUtils;
import com.pcdd.sonovel.util.VirtualThreadLimiter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author pcdd
 * Created at 2021/6/10
 */
public class Crawler {

    private static final Logger log = LoggerFactory.getLogger(Crawler.class);

    private final AppConfig config;
    private final DownloadProgressListener progressListener;
    private final String downloadPath;
    private int digitCount;
    private String bookDir;
    private BookFormat format;

    public Crawler(AppConfig config) {
        this(config, null, Defaults.DOWNLOAD_PATH);
    }

    public Crawler(AppConfig config, DownloadProgressListener progressListener) {
        this(config, progressListener, Defaults.DOWNLOAD_PATH);
    }

    public Crawler(AppConfig config, DownloadProgressListener progressListener, String downloadPath) {
        this.config = config;
        this.progressListener = progressListener;
        this.downloadPath = downloadPath == null || downloadPath.isBlank() ? Defaults.DOWNLOAD_PATH : downloadPath;
    }

    public double crawl(String bookUrl) {
        TocParser tocParser = new TocParser(config);
        List<Chapter> toc = tocParser.parseAll(bookUrl);
        if (toc.isEmpty()) {
            log.warn("<== 目录为空，中止下载");
            return 0;
        }
        log.info("<== 共计 {} 章", toc.size());
        return crawl(bookUrl, toc);
    }

    /**
     * 爬取小说
     *
     * @param bookUrl 详情页链接
     * @param toc     章节目录
     */
    @SneakyThrows
    public double crawl(String bookUrl, List<Chapter> toc) {
        digitCount = String.valueOf(toc.size()).length();
        Book book = new BookParser(config).parse(bookUrl);
        BookContext.set(book);
        DownloadContext.set(downloadPath);
        format = normalizeFormat(config.getExtName());

        try {
            // 下载临时目录名格式：书名 (作者) EXT
            bookDir = FileUtils.sanitizeFileName(
                    "%s (%s) %s".formatted(book.getBookName(), book.getAuthor(), format.name()));
            File dir = FileUtil.mkdir(new File(downloadPath + File.separator + bookDir));
            if (!dir.exists()) {
                log.error("创建下载目录失败：{}\n1. 检查 config.ini 下载路径是否合法\n2. 尝试以管理员身份运行（部分目录需要管理员权限）", dir);
                return 0;
            }

            // IO 密集型任务，不要和 CPU 核数绑定
            int maxConcurrent = config.getConcurrency() == -1
                    ? Math.min(50, toc.size())
                    : Math.min(config.getConcurrency(), toc.size());

            log.info("<== 开始下载《{}》({}) 共计 {} 章 | 最大并发：{}",
                    book.getBookName(), book.getAuthor(), toc.size(), maxConcurrent);

            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            ChapterParser chapterParser = new ChapterParser(config);

            AtomicInteger completed = new AtomicInteger(0);

            // IO 密集任务，瓶颈在网络和磁盘而不是 CPU
            try (var limiter = new VirtualThreadLimiter(maxConcurrent)) {
                toc.forEach(item -> limiter.submit(() -> {
                    createChapterFile(chapterParser.parse(item));

                    long currentIndex = completed.incrementAndGet();
                    if (progressListener != null) {
                        progressListener.onProgress(currentIndex, toc.size());
                    }
                }));
            }
            log.info("-".repeat(100));

            new CrawlerPostHandler().handle(dir, format);
            stopWatch.stop();

            double totalTimeSeconds = stopWatch.getTotalTimeSeconds();
            log.info("<== 完成！总耗时 {} s\n", NumberUtil.round(totalTimeSeconds, 2));
            return totalTimeSeconds;
        } finally {
            BookContext.clear();
            DownloadContext.clear();
        }
    }

    /**
     * 保存章节
     */
    private void createChapterFile(Chapter chapter) {
        if (chapter == null) return;

        try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(generateChapterPath(chapter)))) {
            fos.write(chapter.getContent().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("保存章节失败: {}", chapter.getTitle(), e);
        }
    }

    private String generateChapterPath(Chapter chapter) {
        String parentPath = downloadPath + File.separator + bookDir + File.separator;
        // 文件名下划线前的数字前补零
        String order = digitCount >= String.valueOf(chapter.getOrder()).length()
                ? StrUtil.padPre(chapter.getOrder() + "", digitCount, '0') // 全本下载
                : String.valueOf(chapter.getOrder()); // 非全本下载

        return parentPath + order + switch (format) {
            // 下划线用于兼容，不要删除，见 com/pcdd/sonovel/handle/HtmlTocHandler.java:28
            case HTML -> "_.html";
            case TXT -> "_" + FileUtils.sanitizeFileName(chapter.getTitle()) + ".txt";
            // 转换前的格式为 html
            case EPUB, PDF -> "_" + FileUtils.sanitizeFileName(chapter.getTitle()) + ".html";
        };
    }

    private BookFormat normalizeFormat(BookFormat format) {
        return format == null ? Defaults.EXT_NAME : format;
    }

}
