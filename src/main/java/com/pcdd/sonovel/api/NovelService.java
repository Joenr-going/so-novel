package com.pcdd.sonovel.api;

import com.pcdd.sonovel.model.BookFormat;
import com.pcdd.sonovel.model.Chapter;
import com.pcdd.sonovel.model.SearchResult;

import java.io.InputStream;
import java.util.List;

public interface NovelService {
    List<SearchResult> search(String keyword);

    List<Chapter> fetchToc(String bookUrl);

    default int fetchTotalChapters(String bookUrl) {
        List<Chapter> toc = fetchToc(bookUrl);
        return toc == null ? 0 : toc.size();
    }

    InputStream fetch(String bookUrl, Chapter... chapters);

    double download(String bookUrl, Chapter... chapters);

    InputStream fetch(String bookUrl, BookFormat format, Chapter... chapters);

    double download(String bookUrl, BookFormat format, Chapter... chapters);
}
