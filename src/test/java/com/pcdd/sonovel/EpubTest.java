package com.pcdd.sonovel;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import io.documentnode.epub4j.domain.*;
import io.documentnode.epub4j.epub.EpubReader;
import io.documentnode.epub4j.epub.EpubWriter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EpubTest {

    private static final Logger log = LoggerFactory.getLogger(EpubTest.class);

    @Test
    @Order(1)
    @SneakyThrows
    void createEpub() {
        Book book = new Book();
        book.getMetadata().addTitle("武神主宰");
        book.getMetadata().addAuthor(new Author("暗魔师"));
        book.getMetadata().addDescription("天武大陆一代传奇秦尘，因好友背叛意外陨落武域。三百年后，他转生在一个受尽欺凌的王府私生子身上，利用前世造诣，凝神功、炼神丹，逆天而上，强势崛起，从此踏上一段震惊大陆的惊世之旅。");
        book.setCoverImage(new Resource(ResourceUtil.readBytes("cover.jpg"), "cover.jpg"));

        // 循环添加章节
        for (int i = 1; i <= 10; i++) {
            String chapterTitle = String.format("第%03d章", i);
            // 第二个参数必须确保唯一
            Resource resource = new Resource(ResourceUtil.readBytes("chapter.html"), i + ".html");
            book.addSection(chapterTitle, resource);
        }

        EpubWriter epubWriter = new EpubWriter();
        epubWriter.write(book, FileUtil.getOutputStream("test.epub"));
    }

    @Test
    @Order(2)
    @DisplayName("读取 EPUB")
    @SneakyThrows
    void readEpub() {
        EpubReader epubReader = new EpubReader();
        Book book = epubReader.readEpub(FileUtil.getInputStream("test.epub"));
        Metadata metadata = book.getMetadata();

        // 书名
        log.info("{}", metadata.getTitles().get(0));
        // 作者
        log.info("{}", metadata.getAuthors().get(0));
        // 简介
        log.info("{}", metadata.getDescriptions().get(0));
        // 章节数
        log.info("{}", book.getContents().size());
        // 正文
        log.info("{}", new String(book.getContents().get(0).getData()));

        int i = 0;
        // 遍历目录
        for (TOCReference tocReference : book.getTableOfContents().getTocReferences()) {
            if (i++ == 10) break;
            // 章节名
            log.info("{}", tocReference.getTitle());
        }
    }

}
