## 概述

**So Novel Core** 是一个纯 JVM 的小说抓取核心库（JDK 17），可被其他项目作为 Maven 依赖直接集成调用。

本仓库来源于 https://github.com/freeok/so-novel，如需直接阅读小说的 UI 界面与完整应用体验，请前往该源项目。

本项目目的：将原工程拆分为可复用的核心库，便于在 Android/服务端等场景以依赖方式集成。
移除 CLI/TUI/Web 交互层与打包脚本，保留核心抓取与解析能力；统一 JDK 17 与 Rhino JS 引擎。

## Maven 依赖

```xml
<dependency>
  <groupId>com.pcdd</groupId>
  <artifactId>so-novel-core</artifactId>
  <version>latest</version>
</dependency>
```

## 快速开始

```java
AppConfig config = AppConfigLoader.APP_CONFIG;
NovelService service = new NovelServiceImpl(config);

List<SearchResult> results = service.search("玄鉴仙族");
if (results.isEmpty()) {
    return;
}

String bookUrl = results.get(0).getUrl();
double seconds = service.download(bookUrl);
```

## API 用法

### 读取目录

```java
AppConfig config = AppConfigLoader.APP_CONFIG;
NovelService service = new NovelServiceImpl(config);

List<SearchResult> results = service.search("玄鉴仙族");
List<Chapter> toc = service.fetchToc(results.get(0).getUrl());
```

### 下载全本

```java
double totalSeconds = service.download(results.get(0).getUrl());
```

### 下载指定章节

```java
List<Chapter> toc = service.fetchToc(results.get(0).getUrl());
Chapter[] part = toc.subList(0, Math.min(20, toc.size())).toArray(new Chapter[0]);
double selectedSeconds = service.download(results.get(0).getUrl(), part);
```

### 指定格式下载

```java
double seconds = service.download(results.get(0).getUrl(), BookFormat.PDF);
```

### 以内存流获取 EPUB

```java
InputStream epub = service.fetch(results.get(0).getUrl());
```

### 指定格式以内存流获取

```java
InputStream stream = service.fetch(results.get(0).getUrl(), BookFormat.TXT);
```

## API 说明

### search(String keyword)

- 参数
  - keyword：书名或作者关键词
- 返回值
  - List<SearchResult>：聚合搜索结果列表
- 说明
  - 自动按已启用的书源并发搜索
  - 当配置 searchFilter=1 时会做相似度过滤与排序

### fetchToc(String bookUrl)

- 参数
  - bookUrl：书籍详情页 URL
- 返回值
  - List<Chapter>：目录章节列表
- 说明
  - 仅抓取目录，不下载正文

### fetch(String bookUrl, Chapter... chapters)

- 参数
  - bookUrl：书籍详情页 URL
  - chapters：可选章节列表，不传表示整本
- 返回值
  - InputStream：EPUB 二进制输入流

### fetch(String bookUrl, BookFormat format, Chapter... chapters)

- 参数
  - bookUrl：书籍详情页 URL
  - format：目标格式（BookFormat.EPUB/TXT/HTML/PDF）
  - chapters：可选章节列表，不传表示整本
- 返回值
  - InputStream：目标格式二进制输入流

### download(String bookUrl, Chapter... chapters)

- 参数
  - bookUrl：书籍详情页 URL
  - chapters：可选章节列表，不传表示整本
- 返回值
  - double：总耗时（秒）

### download(String bookUrl, BookFormat format, Chapter... chapters)

- 参数
  - bookUrl：书籍详情页 URL
  - format：目标格式（BookFormat.EPUB/TXT/HTML/PDF）
  - chapters：可选章节列表，不传表示整本
- 返回值
  - double：总耗时（秒）

## 配置示例

```java
AppConfig config = AppConfigLoader.APP_CONFIG;
config.setLanguage("zh_cn");
config.setActiveRules("main.json");
config.setSearchLimit(20);
config.setSearchFilter(1);
config.setConcurrency(16);
config.setMinInterval(200);
config.setMaxInterval(400);
config.setEnableRetry(1);
config.setMaxRetries(5);
config.setRetryMinInterval(2000);
config.setRetryMaxInterval(4000);
config.setProxyEnabled(0);
config.setProxyHost("127.0.0.1");
config.setProxyPort(7890);
config.setSourceId(-1);
```

## 配置说明

### 全局

- ghProxy：GitHub 下载代理前缀（保留字段）
- cfBypass：Cloudflare 绕过服务地址

### 书源

- language：目标语言（zh_cn/zh_tw/zh_hant）
- activeRules：激活的规则文件名
- sourceId：指定书源 ID（-1 表示不限定）
- searchLimit：搜索结果数量上限（-1 表示不限制）
- searchFilter：是否对搜索结果做相似度过滤与排序（1 开启）

### 抓取

- concurrency：下载并发数（-1 表示自动）
- minInterval：请求最小间隔（毫秒）
- maxInterval：请求最大间隔（毫秒）
- enableRetry：是否启用重试（1 开启）
- maxRetries：最大重试次数
- retryMinInterval：重试最小间隔（毫秒）
- retryMaxInterval：重试最大间隔（毫秒）

### Cookie

- qidianCookie：起点 Cookie，用于部分站点请求

### 代理

- proxyEnabled：是否启用代理（1 开启）
- proxyHost：代理主机
- proxyPort：代理端口
