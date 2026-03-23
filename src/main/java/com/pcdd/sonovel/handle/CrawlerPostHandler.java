package com.pcdd.sonovel.handle;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import com.pcdd.sonovel.context.BookContext;
import com.pcdd.sonovel.core.Defaults;
import com.pcdd.sonovel.model.BookFormat;
import com.pcdd.sonovel.model.Rule.Book;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author pcdd
 * Created at 2024/3/17
 */
public class CrawlerPostHandler {

    private final AppConfig config;
    private static final Set<String> EXTENSIONS = Set.of("txt", "epub", "html", "pdf");

    @SneakyThrows
    public void handle(File saveDir, BookFormat format) {
        Book book = BookContext.get();
        StringBuilder s = new StringBuilder(StrUtil.format("<== 章节下载完毕《{}》({})，", book.getBookName(), book.getAuthor()));

        if (EXTENSIONS.contains(extName.toLowerCase())) {
            s.append("正在生成 ").append(extName.toUpperCase());
        }
        if ("txt".equals(extName)) {
            s.append(" (%s 编码)".formatted(CharsetUtil.parse(config.getTxtEncoding())));
        }
        Console.log(s.append("..."));

        // 等待文件系统更新索引
        int attempts = 10;
        while (FileUtil.isDirEmpty(saveDir) && attempts > 0) {
            Thread.sleep(100);
            attempts--;
        }

        PostHandlerFactory.getHandler(format).handle(book, saveDir);

        if (EXTENSIONS.contains(extName.toLowerCase()) && config.getPreserveChapterCache() == 0) {
            FileUtil.del(saveDir);
        }
    }

}
