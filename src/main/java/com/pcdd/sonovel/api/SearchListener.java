package com.pcdd.sonovel.api;

import com.pcdd.sonovel.model.SearchResult;
import java.util.List;

public interface SearchListener {
    /**
     * 当某一个书源的搜索结果返回时被调用
     * @param results 该书源的搜索结果
     */
    void onResult(List<SearchResult> results);

    /**
     * 当某一个书源搜索出错时被调用（可选实现）
     * @param e 异常信息
     */
    default void onError(Throwable e) {}

    /**
     * 当所有书源搜索结束（或全部异常）时被调用
     */
    default void onComplete() {}
}
