package com.pcdd.sonovel.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.http.HttpUtil;
import com.pcdd.sonovel.handle.EpubMergeHandler;
import com.pcdd.sonovel.model.AppConfig;
import com.pcdd.sonovel.model.Chapter;
import com.pcdd.sonovel.model.Rule.Book;
import com.pcdd.sonovel.parse.BookParser;
import com.pcdd.sonovel.parse.ChapterParser;
import com.pcdd.sonovel.parse.TocParser;
import com.pcdd.sonovel.util.VirtualThreadLimiter;
import io.documentnode.epub4j.domain.Author;
import io.documentnode.epub4j.domain.MediaTypes;
import io.documentnode.epub4j.domain.Metadata;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubWriter;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class EpubFetcher {

    private final AppConfig config;

    public EpubFetcher(AppConfig config) {
        this.config = config;
    }

    public InputStream fetch(String bookUrl) {
        List<Chapter> toc = new TocParser(config).parseAll(bookUrl);
        return fetch(bookUrl, toc);
    }

    @SneakyThrows
    public InputStream fetch(String bookUrl, List<Chapter> toc) {
        if (toc == null || toc.isEmpty()) {
            return InputStream.nullInputStream();
        }
        Book book = new BookParser(config).parse(bookUrl);
        List<Chapter> chapters = parseChapters(toc);
        return new ByteArrayInputStream(buildEpub(book, chapters));
    }

    private List<Chapter> parseChapters(List<Chapter> toc) {
        int maxConcurrent = config.getConcurrency() == -1
                ? Math.min(50, toc.size())
                : Math.min(config.getConcurrency(), toc.size());
        List<Chapter> chapters = new ArrayList<>(toc.size());
        for (int i = 0; i < toc.size(); i++) {
            chapters.add(null);
        }
        ChapterParser chapterParser = new ChapterParser(config);
        try (var limiter = new VirtualThreadLimiter(maxConcurrent)) {
            for (int i = 0; i < toc.size(); i++) {
                int index = i;
                Chapter item = toc.get(i);
                limiter.submit(() -> {
                    Chapter parsed = chapterParser.parse(item);
                    if (parsed != null && StrUtil.isNotEmpty(parsed.getContent())) {
                        chapters.set(index, parsed);
                    }
                });
            }
        }
        return chapters.stream().filter(c -> c != null && StrUtil.isNotEmpty(c.getContent())).toList();
    }

    @SneakyThrows
    private byte[] buildEpub(Book b, List<Chapter> chapters) {
        io.documentnode.epub4j.domain.Book book = new io.documentnode.epub4j.domain.Book();
        Metadata meta = book.getMetadata();
        meta.addTitle(b.getBookName());
        meta.setAuthors(List.of(new Author(b.getAuthor())));
        meta.addDescription(b.getIntro());
        try {
            byte[] bytes = HttpUtil.downloadBytes(b.getCoverUrl());
            book.setCoverImage(new Resource(bytes, "cover.jpg"));
            book.addSection("封面", new Resource(ResourceUtil.readBytes("templates/chapter_cover.html"), EpubMergeHandler.COVER_NAME));
        } catch (Exception e) {
        }
        meta.setLanguage("zh");
        meta.setDates(List.of(new io.documentnode.epub4j.domain.Date(new Date())));
        meta.addPublisher("so-novel");
        meta.setRights(List.of("本电子书由 so-novel(https://github.com/freeok/so-novel) 制作生成。仅供交流使用，不得用于商业用途。"));
        int len = String.valueOf(chapters.size()).length();
        for (int i = 0; i < chapters.size(); i++) {
            Chapter ch = chapters.get(i);
            String title = StrUtil.emptyToDefault(ch.getTitle(), "第" + (i + 1) + "章");
            String id = StrUtil.padPre(String.valueOf(i + 1), len, '0');
            String content = StrUtil.emptyToDefault(ch.getContent(), "");
            book.addSection(title, new Resource(id, content.getBytes(StandardCharsets.UTF_8), id + ".html", MediaTypes.XHTML));
        }
        EpubWriter epubWriter = new EpubWriter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        epubWriter.write(book, out);
        return out.toByteArray();
    }
}
