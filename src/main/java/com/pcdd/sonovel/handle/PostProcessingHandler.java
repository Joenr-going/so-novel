package com.pcdd.sonovel.handle;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.pcdd.sonovel.model.Rule.Book;
import com.pcdd.sonovel.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


/**
 * @author pcdd
 * Created at 2024/12/4
 */
public interface PostProcessingHandler {

    Logger log = LoggerFactory.getLogger(PostProcessingHandler.class);

    void handle(Book book, File saveDir);

    /**
     * 下载封面失败会导致生成中断，必须捕获异常
     */
    default void downloadCover(Book book, File saveDir) {
        try {
            File coverFile = HttpUtil.downloadFileFromUrl(book.getCoverUrl(), FileUtils.toAbsolutePath(saveDir.toString()));
            FileUtil.rename(coverFile, "0_封面." + FileUtil.getType(coverFile), true);
        } catch (Exception e) {
            log.error("TXT/HTML 最新封面 {} 下载失败：{}", book.getCoverUrl(), e.getMessage());
        }
    }

}
