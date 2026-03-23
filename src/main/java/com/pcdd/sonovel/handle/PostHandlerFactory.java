package com.pcdd.sonovel.handle;

import com.pcdd.sonovel.model.BookFormat;
import lombok.experimental.UtilityClass;

/**
 * @author pcdd
 * Created at 2024/12/4
 */
@UtilityClass
public class PostHandlerFactory {

    public PostProcessingHandler getHandler(BookFormat format) {
        return switch (format) {
            case TXT -> new TxtMergeHandler();
            case EPUB -> new EpubMergeHandler();
            case HTML -> new HtmlTocHandler();
            case PDF -> new PdfMergeHandler();
        };
    }
}
