package my.noveldokusha.scraper.helpers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.interceptors.GLOBAL_USER_AGENT
import my.noveldokusha.network.postPayload
import my.noveldokusha.network.postRequest
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import my.noveldokusha.scraper.configs.*
import my.noveldokusha.scraper.configs.CatalogSelectors
import my.noveldokusha.scraper.configs.SearchSelectors
import my.noveldokusha.scraper.configs.BookSelectors
import my.noveldokusha.scraper.configs.ChapterSelectors
import my.noveldokusha.scraper.utils.*
import okhttp3.Headers
import org.jsoup.nodes.Document
import timber.log.Timber

/**
 * HTML-based catalog operations
 */

/**
 * New declarative version using HtmlSelectors
 */
suspend fun getCatalogList(
    config: HtmlSelectors,
    index: Int,
    networkClient: NetworkClient
): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        val url = config.buildCatalogUrl(index)
        val doc = GET(url, networkClient = networkClient, charset = config.charset ?: "UTF-8")

        // Extract books using declarative selectors
        Timber.d("ScraperHelpers: Extracting catalog items using HtmlSelectors")
        val items = try {
            val extractedItems = doc.extractElements(config.catalog.item)
            Timber.d("ScraperHelpers: Found ${extractedItems.size} catalog items")
            if (extractedItems.isEmpty()) {
                Timber.w("ScraperHelpers: Selector '${config.catalog.item.selectors.joinToString(", ")}' returned no elements")
            }
            extractedItems
        } catch (e: Exception) {
            Timber.w("ScraperHelpers: Failed to extract catalog items: ${e.message}")
            emptyList()
        }

        val books = items.map { item ->
            Timber.d("ScraperHelpers: Processing catalog item: ${item.text().take(100)}...")
            val title = item.extractValue(config.catalog.title) ?: ""
            val itemUrl = item.extractValue(config.catalog.url) ?: ""
            val cover = item.extractValue(config.catalog.cover) ?: ""

            Timber.d("ScraperHelpers: Extracted title: '$title', url: '$itemUrl', cover: '$cover'")
            BookResult(title = title, url = itemUrl, coverImageUrl = cover)
        }

        Timber.d("ScraperHelpers: Final catalog items count: ${books.size}")

        // Determine if last page (simplified - can be enhanced later)
        val isLastPage = books.isEmpty()

        val pagedList = PagedList(list = books, index = index, isLastPage = isLastPage)

        // Convert relative URLs to absolute URLs
        val booksWithAbsoluteUrls = pagedList.list.map { book ->
            val absoluteBookUrl = config.transformBookUrl(book.url)
            val transformedCover = config.transformCoverUrl(book.coverImageUrl.takeIf { it.isNotBlank() } ?: "", absoluteBookUrl)
            book.copy(
                url = absoluteBookUrl,
                coverImageUrl = transformedCover
            )
        }

        pagedList.copy(list = booksWithAbsoluteUrls)
    }
}

/**
 * Legacy version using HtmlScraperConfig
 */


suspend fun getCatalogList(
    config: HtmlScraperConfig,
    index: Int,
    networkClient: NetworkClient
): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        val url = config.buildCatalogUrl(index)
        val doc = GET(url, networkClient = networkClient, charset = config.charset ?: "UTF-8")
        val pagedList = parseHtmlCatalogPage(
            doc, index,
            config.catalogItemSelector, config.catalogTitleSelector,
            config.catalogUrlSelector, config.catalogCoverSelector,
            config.catalogLastPageSelector, config.baseUrl
        )

        // Convert relative URLs to absolute URLs
        val booksWithAbsoluteUrls = pagedList.list.map { book ->
            val absoluteBookUrl = config.transformBookUrl(book.url)
            val transformedCover = when (book.coverImageUrl) {
                "transform" -> config.transformCoverUrl("", absoluteBookUrl) // Generate URL from book URL
                else -> book.coverImageUrl.takeIf { it.isNotBlank() }?.let { config.transformCoverUrl(it, absoluteBookUrl) } ?: book.coverImageUrl
            }
            book.copy(
                url = absoluteBookUrl,
                coverImageUrl = transformedCover
            )
        }

        pagedList.copy(list = booksWithAbsoluteUrls)
    }
}

/**
 * New declarative version using HtmlSelectors
 */
suspend fun getCatalogSearch(
    config: HtmlSelectors,
    index: Int,
    input: String,
    networkClient: NetworkClient
): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        if (input.isBlank()) return@tryConnect PagedList.createEmpty(index)

        // Use custom search provider if available (e.g., FanMTL with searchid pagination)
        if (config.customSearchProvider != null) {
            Timber.d("ScraperHelpers: Using custom search provider")
            return@tryConnect config.customSearchProvider.invoke(input, index, networkClient)
        }

        // Handle POST search if enabled
        val doc = if (config.postSearchEnabled && config.postSearchUrl != null && config.postSearchDataBuilder != null) {
            Timber.d("ScraperHelpers: Using POST search for '$input'")
            Timber.d("ScraperHelpers: POST URL: ${config.postSearchUrl}")
            // Use Jsoup for raw body POST with proper charset encoding
            if (config.postSearchUseRawBody) {
                val data = config.postSearchDataBuilder.invoke(input)
                val charset = config.charset ?: "UTF-8"
                
                Timber.d("ScraperHelpers: POST data: $data")
                Timber.d("ScraperHelpers: Charset: $charset")
                
                // Build form data with proper charset encoding
                val formData = data.entries.joinToString("&") { (key, value) ->
                    val encodedKey = java.net.URLEncoder.encode(key, charset)
                    val encodedValue = java.net.URLEncoder.encode(value, charset)
                    "$encodedKey=$encodedValue"
                }
                
                Timber.d("ScraperHelpers: Encoded form data: $formData")
                Timber.d("ScraperHelpers: Search headers: ${config.searchHeaders}")
                
                val conn = org.jsoup.Jsoup.connect(config.postSearchUrl)
                    .userAgent(GLOBAL_USER_AGENT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .requestBody(formData)
                    .timeout(10000)
                
                // Add search headers
                config.searchHeaders.forEach { (key, value) -> conn.header(key, value) }
                
                Timber.d("ScraperHelpers: Executing POST request...")
                val resultDoc = conn.post()
                Timber.d("ScraperHelpers: POST response URL: ${resultDoc.location()}")
                Timber.d("ScraperHelpers: POST response title: ${resultDoc.title()}")
                Timber.d("ScraperHelpers: POST response body preview: ${resultDoc.body().text().take(500)}")
                resultDoc
            } else {
                // Standard POST request
                val data = config.postSearchDataBuilder.invoke(input)
                val headers = config.searchHeaders.toMutableMap()
                POST(config.postSearchUrl, data, headers = headers, networkClient = networkClient)
            }
        } else {
            // GET search fallback
            val url = config.buildSearchUrl(index, input)
            if (url.isBlank()) return@tryConnect PagedList.createEmpty(index)
            Timber.d("ScraperHelpers: Using GET search for '$input' -> $url")
            GET(url, networkClient = networkClient, charset = config.charset ?: "UTF-8")
        }

        // Use search selectors with fallback to catalog
        Timber.d("ScraperHelpers: Extracting search items using HtmlSelectors")
        val selectors: ItemSelectors = config.search ?: config.catalog
        Timber.d("ScraperHelpers: Using ${if (config.search != null) "search" else "catalog"} selectors for search")

        val items = try {
            val extractedItems = doc.extractElements(selectors.item)
            Timber.d("ScraperHelpers: Found ${extractedItems.size} search items")
            if (extractedItems.isEmpty()) {
                Timber.w("ScraperHelpers: Search selector '${selectors.item.selectors.joinToString(", ")}' returned no elements")
            }
            extractedItems
        } catch (e: Exception) {
            Timber.w("ScraperHelpers: Failed to extract search items: ${e.message}")
            emptyList()
        }

        val books = items.map { item ->
            Timber.d("ScraperHelpers: Processing search item: ${item.text().take(100)}...")
            val title = item.extractValue(selectors.title) ?: ""
            val url = item.extractValue(selectors.url) ?: ""
            val cover = item.extractValue(selectors.cover) ?: ""

            Timber.d("ScraperHelpers: Extracted title: '$title', url: '$url', cover: '$cover'")
            BookResult(title = title, url = url, coverImageUrl = cover)
        }

        Timber.d("ScraperHelpers: Final search items count: ${books.size}")

        val isLastPage = books.isEmpty() || config.searchNoPagination
        val pagedList = PagedList(list = books, index = index, isLastPage = isLastPage)

        // Convert relative URLs to absolute URLs
        val booksWithAbsoluteUrls = pagedList.list.map { book ->
            val absoluteBookUrl = config.transformBookUrl(book.url)
            val transformedCover = config.transformCoverUrl(book.coverImageUrl.takeIf { it.isNotBlank() } ?: "", absoluteBookUrl)
            book.copy(
                url = absoluteBookUrl,
                coverImageUrl = transformedCover
            )
        }

        pagedList.copy(list = booksWithAbsoluteUrls)
    }
}

/**
 * Legacy version using HtmlScraperConfig
 */
suspend fun getCatalogSearch(
    config: HtmlScraperConfig,
    index: Int,
    input: String,
    networkClient: NetworkClient
): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        if (input.isBlank()) return@tryConnect PagedList.createEmpty(index)
        val url = config.buildSearchUrl(index, input)
        if (url.isBlank()) return@tryConnect PagedList.createEmpty(index)
        val doc = GET(url, networkClient = networkClient, charset = config.charset ?: "UTF-8")
        val pagedList = parseHtmlCatalogPage(
            doc, index,
            config.searchItemSelector ?: config.catalogItemSelector,
            config.searchTitleSelector ?: config.catalogTitleSelector,
            config.searchUrlSelector ?: config.catalogUrlSelector,
            config.searchCoverSelector ?: config.catalogCoverSelector,
            config.catalogLastPageSelector, config.baseUrl
        )

        // Convert relative URLs to absolute URLs
        val booksWithAbsoluteUrls = pagedList.list.map { book ->
            val absoluteBookUrl = config.transformBookUrl(book.url)
            book.copy(
                url = absoluteBookUrl,
                coverImageUrl = book.coverImageUrl.takeIf { it.isNotBlank() }?.let { config.transformCoverUrl(it, absoluteBookUrl) } ?: book.coverImageUrl
            )
        }

        pagedList.copy(list = booksWithAbsoluteUrls)
    }
}

suspend fun getCatalogSearchPost(
    config: HtmlScraperConfig,
    index: Int,
    input: String,
    networkClient: NetworkClient
): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        if (input.isBlank()) return@tryConnect PagedList.createEmpty(index)

        val doc = if (config.postSearchEnabled && config.postSearchUrl != null && config.postSearchDataBuilder != null) {
            // Special handling for FreeWebNovel POST search using Jsoup
            if (config.postSearchUrl.contains("freewebnovel.com")) {
                val data = config.postSearchDataBuilder.invoke(input)
                org.jsoup.Jsoup.connect(config.postSearchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    .data(data)
                    .timeout(10000)
                    .post()
            } else {
                // Standard POST request
                val data = config.postSearchDataBuilder.invoke(input)
                val headers = config.searchHeaders.toMutableMap()

                // Add Cloudflare config to headers if present
                config.cloudflareConfig?.let { cfConfig ->
                    headers["X-Cloudflare-Config"] = cfConfig.toJson()
                }

                POST(config.postSearchUrl, data, headers = headers, networkClient = networkClient)
            }
        } else {
            // Fallback to GET search
            val url = config.buildSearchUrl(index, input)
            GET(url, networkClient = networkClient, charset = config.charset ?: "UTF-8")
        }

        val pagedList = parseHtmlCatalogPage(
            doc, index,
            config.searchItemSelector ?: config.catalogItemSelector,
            config.searchTitleSelector ?: config.catalogTitleSelector,
            config.searchUrlSelector ?: config.catalogUrlSelector,
            config.searchCoverSelector ?: config.catalogCoverSelector,
            config.catalogLastPageSelector, config.baseUrl
        )

        // Convert relative URLs to absolute URLs
        val booksWithAbsoluteUrls = pagedList.list.map { book ->
            val absoluteBookUrl = config.transformBookUrl(book.url)
            book.copy(
                url = absoluteBookUrl,
                coverImageUrl = book.coverImageUrl.takeIf { it.isNotBlank() }?.let { config.transformCoverUrl(it, absoluteBookUrl) } ?: book.coverImageUrl
            )
        }

        pagedList.copy(list = booksWithAbsoluteUrls)
    }
}

/**
 * HTML-based book operations
 */
/**
 * New declarative version using HtmlSelectors
 */
suspend fun getBookCover(
    config: HtmlSelectors,
    bookUrl: String,
    networkClient: NetworkClient
): Response<String?> = withContext(Dispatchers.Default) {
    tryConnect {
        val doc = GET(bookUrl, networkClient = networkClient, charset = config.charset ?: "UTF-8")
        val coverUrl = doc.extractValue(config.book.cover)

        if (coverUrl == null) {
            Timber.w("Book cover: No cover found for $bookUrl")
        }

        config.transformCoverUrl(coverUrl ?: "", bookUrl)
    }
}

/**
 * Legacy version using HtmlScraperConfig
 */
suspend fun getBookCover(
    config: HtmlScraperConfig,
    bookUrl: String,
    networkClient: NetworkClient
): Response<String?> = withContext(Dispatchers.Default) {
    tryConnect {
        val doc = GET(bookUrl, networkClient = networkClient, charset = config.charset ?: "UTF-8")

        // Handle comma-separated selectors with fallback logic
        val selectors = config.bookCoverSelector.split(",").map { it.trim() }
        var coverUrl: String? = null

        for (selector in selectors) {
            val element = doc.selectFirst(selector)
            if (element != null) {
                // Determine which attribute to use based on element type
                coverUrl = when {
                    element.tagName() == "meta" -> element.attr("content")
                    element.tagName() == "img" -> element.attr("src")
                    element.tagName() == "link" -> element.attr("href")
                    else -> element.attr("src") ?: element.attr("content") ?: element.attr("href")
                }
                if (!coverUrl.isNullOrBlank()) break
            }
        }

        if (coverUrl == null) {
            Timber.w("Book cover: No cover found with selectors '${config.bookCoverSelector}' for $bookUrl")
        }

        coverUrl?.let { config.transformCoverUrl(it, bookUrl) }
    }
}

/**
 * New declarative version using HtmlSelectors
 */
suspend fun getBookTitle(
    config: HtmlSelectors,
    bookUrl: String,
    networkClient: NetworkClient
): Response<String?> = withContext(Dispatchers.Default) {
    tryConnect {
        val doc = GET(bookUrl, networkClient = networkClient, charset = config.charset ?: "UTF-8")
        val title = if (config.book.title != null) {
            doc.extractValue(config.book.title)
        } else {
            null
        }

        if (title == null) {
            Timber.w("Book title: No title found for $bookUrl")
        }

        title
    }
}

/**
 * New declarative version using HtmlSelectors
 */
suspend fun getBookDescription(
    config: HtmlSelectors,
    bookUrl: String,
    networkClient: NetworkClient
): Response<String?> = withContext(Dispatchers.Default) {
    tryConnect {
        val doc = GET(bookUrl, networkClient = networkClient, charset = config.charset ?: "UTF-8")
        val description = doc.extractValue(config.book.description)

        if (description == null) {
            Timber.w("Book description: No description found for $bookUrl")
        }

        description
    }
}

/**
 * Get chapter list hash for quick change detection.
 * Returns hash of the value extracted by book.latestChapter selector (e.g., last chapter number, total count, etc.)
 * Returns null if selector is not configured or value cannot be extracted.
 */
suspend fun getChapterListHash(
    config: HtmlSelectors,
    bookUrl: String,
    networkClient: NetworkClient
): Response<String?> = withContext(Dispatchers.Default) {
    tryConnect {
        val latestChapterRule = config.book.latestChapterHash
        if (latestChapterRule == null) {
            return@tryConnect null
        }
        
        val doc = GET(bookUrl, networkClient = networkClient, charset = config.charset ?: "UTF-8")
        
        // Extract value using SelectorRule
        val value = doc.extractValue(latestChapterRule)
        
        if (value == null || value.isBlank()) {
            Timber.w("Chapter list hash: No value found for $bookUrl")
            return@tryConnect null
        }
        
        // Compute hash
        val hash = java.security.MessageDigest.getInstance("MD5")
            .digest(value.trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        Timber.d("Chapter list hash for $bookUrl: $hash (from: $value)")
        hash
    }
}

/**
 * Legacy version using HtmlScraperConfig
 */
suspend fun getBookDescription(
    config: HtmlScraperConfig,
    bookUrl: String,
    networkClient: NetworkClient
): Response<String?> = withContext(Dispatchers.Default) {
    tryConnect {
        val doc = GET(bookUrl, networkClient = networkClient, charset = config.charset ?: "UTF-8")
        val description = doc.selectFirst(config.bookDescriptionSelector)?.let { element ->
            when {
                element.tagName() == "meta" -> element.attr("content")
                else -> TextExtractor.get(element)
            }
        }

        if (description == null) {
            Timber.w("Book description: No description found with selector '${config.bookDescriptionSelector}' for $bookUrl")
        }

        description
    }
}

/**
 * HTML-based chapter operations
 */
suspend fun getChapterList(
    config: HtmlScraperConfig,
    bookUrl: String,
    networkClient: NetworkClient
): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        Timber.d("ScraperHelpers: getChapterList called for $bookUrl")
        val doc = GET(bookUrl, networkClient = networkClient, charset = config.charset ?: "UTF-8")
        Timber.d("ScraperHelpers: Loaded document from $bookUrl, selecting chapters with ${config.chapterListSelector}")
        val chapters = doc.selectList(config.chapterListSelector).map {
            ChapterResult(
                title = it.text(),
                url = config.transformChapterUrl(it.attr("href"))
            )
        }

        if (chapters.isEmpty()) {
            Timber.w("Chapter list: No chapters found with selector '${config.chapterListSelector}' for $bookUrl")
        }

                Timber.d("ScraperHelpers: Found ${chapters.size} chapters for $bookUrl")
                chapters
    }
}

suspend fun getChapterListPaginated(
    config: HtmlScraperConfig,
    bookUrl: String,
    networkClient: NetworkClient
): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        when (config.chapterPaginationType) {
            ChapterPaginationType.NONE -> {
                // Simple single page
                val doc = GET(bookUrl, networkClient = networkClient, charset = config.charset ?: "UTF-8")
                val chapters = doc.selectList(config.chapterListSelector).map {
                    ChapterResult(
                        title = it.text(),
                        url = config.transformChapterUrl(it.attr("href"))
                    )
                }
                chapters
            }
            ChapterPaginationType.PAGE_BASED -> {
                // AllNovel/NovelFull style pagination
                if (config.chapterPaginationConfig == null) return@tryConnect emptyList()

                loadChaptersPaginated(
                    bookUrl = config.chapterPaginationConfig.pageUrlBuilder(bookUrl, 1),
                    chapterSelector = config.chapterPaginationConfig.chapterSelector,
                    maxPageExtractor = config.chapterPaginationConfig.maxPageExtractor,
                    urlBuilder = { page -> config.chapterPaginationConfig.pageUrlBuilder(bookUrl, page) },
                    networkClient = networkClient,
                    transformUrl = config.transformChapterUrl
                )
            }
            ChapterPaginationType.AJAX_BASED -> {
                // Simple AJAX provider - just call the function from config
                config.ajaxChapterListProvider?.invoke(bookUrl, networkClient) ?: emptyList()
            }
            ChapterPaginationType.CUSTOM -> {
                // Custom logic - would need additional config
                emptyList()
            }
        }
    }
}

/**
 * Legacy version - will be removed after migration
 */
/*
suspend fun getChapterText(
    config: HtmlScraperConfig,
    doc: Document
): String {
    val content = doc.selectFirst(config.chapterContentSelector)
        ?.cleanChapterContent(config.removeSelectors) ?: ""

    // Remove duplicate title from start of content
    val cleanedContent = if (config.chapterTitleSelector != null && content.isNotBlank()) {
        val title = doc.selectText(config.chapterTitleSelector)
        if (title != null && content.trimStart().startsWith(title.trim())) {
            content.trimStart().removePrefix(title.trim()).trimStart()
        } else {
            content
        }
    } else {
        content
    }

    if (cleanedContent.isBlank()) {
        Timber.w("Chapter text: No content found with selector '${config.chapterContentSelector}'")
    }

    return cleanedContent
}
*/

/**
 * New declarative version using HtmlSelectors
 */
suspend fun getChapterText(
    config: HtmlSelectors,
    doc: Document
): String {
    Timber.d("ScraperHelpers: getChapterText called with HtmlSelectors")

    // Extract chapter content directly using text selector
    Timber.d("ScraperHelpers: Extracting chapter content with selector '${config.chapters.content.selectors.joinToString(", ")}'")
    val rawContent = try {
        doc.extractValue(config.chapters.content).also { text ->
            Timber.d("ScraperHelpers: Raw content length: ${text?.length ?: 0}")
        }
    } catch (e: Exception) {
        Timber.w("ScraperHelpers: Failed to extract content text: ${e.message}")
        null
    }

    val content = rawContent ?: ""
    Timber.d("ScraperHelpers: Final content length: ${content.length}")

    // Remove duplicate title from start of content - DISABLED to avoid issues with attr() selectors
    val cleanedContent = content

    Timber.d("ScraperHelpers: Final content length: ${cleanedContent.length}")
    if (cleanedContent.isBlank()) {
        Timber.w("ScraperHelpers: Chapter text: No content found")
    }

    return cleanedContent
}

/**
 * New declarative version using HtmlSelectors
 */
suspend fun getChapterList(
    config: HtmlSelectors,
    bookUrl: String,
    networkClient: NetworkClient
): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        when (config.chapterPaginationType) {
            ChapterPaginationType.NONE -> {
                // Simple single page
                Timber.d("ScraperHelpers: getChapterList called for $bookUrl")
                val doc = GET(bookUrl, networkClient = networkClient, charset = config.charset ?: "UTF-8")
                Timber.d("ScraperHelpers: Loaded document from $bookUrl")
                val chapters = doc.extractElements(config.chapters.list).map {
                    ChapterResult(
                        title = it.extractValue(config.chapters.title ?: text("a")) ?: "",
                        url = config.transformChapterUrl(it.extractValue(attr("href", "a")) ?: "")
                    )
                }

                if (chapters.isEmpty()) {
                    Timber.w("Chapter list: No chapters found for $bookUrl")
                }

                Timber.d("ScraperHelpers: Found ${chapters.size} chapters for $bookUrl")
                if (config.reverseChapters) chapters.reversed() else chapters
            }
            ChapterPaginationType.PAGE_BASED -> {
                // AllNovel/NovelFull style pagination
                if (config.chapterPaginationConfig == null) return@tryConnect emptyList()

                loadChaptersPaginated(
                    bookUrl = config.chapterPaginationConfig.pageUrlBuilder(bookUrl, 1),
                    chapterSelector = config.chapterPaginationConfig.chapterSelector,
                    maxPageExtractor = config.chapterPaginationConfig.maxPageExtractor,
                    urlBuilder = { page -> config.chapterPaginationConfig.pageUrlBuilder(bookUrl, page) },
                    networkClient = networkClient,
                    transformUrl = config.transformChapterUrl
                )
            }
            ChapterPaginationType.AJAX_BASED -> {
                // Simple AJAX provider - just call the function from config
                val chapters = config.ajaxChapterListProvider?.invoke(bookUrl, networkClient) ?: emptyList()
                if (config.reverseChapters) chapters.reversed() else chapters
            }
            ChapterPaginationType.CUSTOM -> {
                // Custom logic - would need additional config
                emptyList()
            }
        }
    }
}

/**
 * Legacy version using HtmlScraperConfig
 */
suspend fun getChapterText(
    config: HtmlScraperConfig,
    doc: Document
): String {
    val content = doc.selectFirst(config.chapterContentSelector)
        ?.cleanChapterContent(config.removeSelectors) ?: ""

    // Remove duplicate title from start of content
    val cleanedContent = if (config.chapterTitleSelector != null && content.isNotBlank()) {
        val title = doc.selectText(config.chapterTitleSelector)
        if (title != null && content.trimStart().startsWith(title.trim())) {
            content.trimStart().removePrefix(title.trim()).trimStart()
        } else {
            content
        }
    } else {
        content
    }

    if (cleanedContent.isBlank()) {
        Timber.w("Chapter text: No content found with selector '${config.chapterContentSelector}'")
    }

    return cleanedContent
}



/**
 * Legacy version using HtmlScraperConfig
 */
suspend fun getChapterTitle(
    config: HtmlScraperConfig,
    doc: Document
): String? = config.chapterTitleSelector?.let { doc.selectText(it) }



/**
 * JSON API catalog operations
 */
suspend fun getCatalogListJson(
    config: JsonApiScraperConfig,
    index: Int,
    networkClient: NetworkClient
): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        val url = config.buildCatalogUrl(index + 1)
        val json = fetchJson(url, config.headers, networkClient).asJsonObject
        parseJsonCatalogPage(
            json, index,
            config.catalogDataKey, config.catalogTitleKeys,
            config.catalogUrlKey, config.catalogCoverKey,
            config.catalogHasNextKey, config.baseUrl
        )
    }
}

suspend fun getCatalogSearchJson(
    config: JsonApiScraperConfig,
    index: Int,
    input: String,
    networkClient: NetworkClient
): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        if (input.isBlank()) return@tryConnect PagedList.createEmpty(index)
        val url = config.buildSearchUrl(index + 1, input)
        val json = fetchJson(url, config.headers, networkClient).asJsonObject

        // Use search keys if defined, otherwise catalog keys
        val dataKey = config.searchDataKey ?: config.catalogDataKey
        val titleKeys = config.searchTitleKeys ?: config.catalogTitleKeys
        val urlKey = config.searchUrlKey ?: config.catalogUrlKey
        val coverKey = config.searchCoverKey ?: config.catalogCoverKey
        val hasNextKey = config.searchHasNextKey ?: config.catalogHasNextKey

        parseJsonCatalogPage(
            json, index, dataKey, titleKeys, urlKey, coverKey, hasNextKey, config.baseUrl
        )
    }
}

suspend fun getCatalogSearchPostJson(
    config: JsonApiScraperConfig,
    index: Int,
    input: String,
    networkClient: NetworkClient
): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        if (input.isBlank()) return@tryConnect PagedList.createEmpty(index)

        val json = if (config.postSearchEnabled && config.postSearchUrl != null && config.postSearchDataBuilder != null) {
            val data = config.postSearchDataBuilder.invoke(input)
            val doc = POST(config.postSearchUrl, data, networkClient = networkClient)
            // Assume the response is JSON, not HTML - would need adjustment based on actual API
            doc.selectFirst("body")?.text()?.let { com.google.gson.JsonParser.parseString(it).asJsonObject }
                ?: throw IllegalStateException("Expected JSON response from POST search")
        } else {
            // Fallback to GET search
            val url = config.buildSearchUrl(index + 1, input)
            fetchJson(url, config.headers, networkClient).asJsonObject
        }

        // Use search keys if defined, otherwise catalog keys
        val dataKey = config.searchDataKey ?: config.catalogDataKey
        val titleKeys = config.searchTitleKeys ?: config.catalogTitleKeys
        val urlKey = config.searchUrlKey ?: config.catalogUrlKey
        val coverKey = config.searchCoverKey ?: config.catalogCoverKey
        val hasNextKey = config.searchHasNextKey ?: config.catalogHasNextKey

        parseJsonCatalogPage(
            json, index, dataKey, titleKeys, urlKey, coverKey, hasNextKey, config.baseUrl
        )
    }
}

/**
 * JSON API book operations
 */
suspend fun getBookCoverJson(
    config: JsonApiScraperConfig,
    bookUrl: String,
    networkClient: NetworkClient
): Response<String?> = withContext(Dispatchers.Default) {
    tryConnect {
        val slug = extractSlug(bookUrl) ?: return@tryConnect null
        val url = config.buildBookUrl(slug)
        val json = fetchJson(url, config.headers, networkClient).asJsonObject
        val bookData = config.parseBookData(json)
        bookData.cover
    }
}

suspend fun getBookDescriptionJson(
    config: JsonApiScraperConfig,
    bookUrl: String,
    networkClient: NetworkClient
): Response<String?> = withContext(Dispatchers.Default) {
    tryConnect {
        val slug = extractSlug(bookUrl) ?: return@tryConnect null
        val url = config.buildBookUrl(slug)
        val json = fetchJson(url, config.headers, networkClient).asJsonObject
        val bookData = config.parseBookData(json)
        bookData.description
    }
}

/**
 * JSON API chapter operations
 */
suspend fun getChapterListJson(
    config: JsonApiScraperConfig,
    bookUrl: String,
    networkClient: NetworkClient
): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
    tryConnect {
        val slug = extractSlug(bookUrl) ?: return@tryConnect emptyList()
        val url = config.buildChapterListUrl(slug)
        val json = fetchJson(url, config.headers, networkClient).asJsonObject
        config.parseChapterData(json, slug)
    }
}

suspend fun getChapterTextJson(
    config: JsonApiScraperConfig,
    chapterUrl: String,
    networkClient: NetworkClient
): Response<String> = withContext(Dispatchers.Default) {
    tryConnect {
        // Parse chapter URL to extract parameters
        val chapterPath = parseChapterPath(chapterUrl, config.baseUrl)
            ?: return@tryConnect ""

        val (slug, volume, number, branchId) = chapterPath

        // Build API URL for chapter content
        val apiUrl = buildString {
            append(config.apiBaseUrl)
            append(slug)
            append("/chapter?number=")
            append(number)
            append("&volume=")
            append(volume)
            branchId?.let {
                append("&branch_id=")
                append(it)
            }
        }

        // Fetch chapter JSON
        val json = fetchJson(apiUrl, config.headers, networkClient).asJsonObject

        // Parse content using config
        config.parseChapterContent(json)
    }
}

// Helper function to parse chapter URL
private fun parseChapterPath(chapterUrl: String, baseUrl: String): ChapterPath? {
    val clean = chapterUrl.removePrefix(baseUrl).trim('/').split("/")
    if (clean.size >= 5 && clean[0] == "ru" && clean[2] == "read") {
        val slug = clean[1]
        val volumePart = clean[3] // v1
        val chapterPart = clean[4].split("?")[0] // c1
        val volume = volumePart.removePrefix("v")
        val number = chapterPart.removePrefix("c")

        val queryParams = chapterUrl.substringAfter("?", "").split("&")
        val branchId = queryParams.find { it.startsWith("bid=") }?.substringAfter("bid=")

        return ChapterPath(slug, volume, number, branchId)
    }
    return null
}

private data class ChapterPath(
    val slug: String,
    val volume: String,
    val number: String,
    val branchId: String?
)

suspend fun getChapterTitleJson(
    @Suppress("UNUSED_PARAMETER") config: JsonApiScraperConfig,
    @Suppress("UNUSED_PARAMETER") doc: Document
): String? = null // Usually comes from API

/**
 * Utility functions for cover URL transformations
 */
