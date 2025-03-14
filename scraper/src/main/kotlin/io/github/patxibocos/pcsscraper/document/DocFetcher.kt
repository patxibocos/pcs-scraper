package io.github.patxibocos.pcsscraper.document

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import it.skrape.core.htmlDocument
import it.skrape.selects.Doc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.slf4j.Logger
import java.net.URL
import kotlin.time.Duration

class DocFetcher(
    private val cache: Cache?,
    private val skipCache: Boolean,
    private val retryDelay: Duration,
    private val logger: Logger = KotlinLogging.logger {},
) {
    private val client = HttpClient(CIO)

    fun invalidateDoc(docURL: URL) {
        if (skipCache || cache == null) {
            return
        }
        val cacheKey = cacheKeyForURL(docURL)
        cache.delete(cacheKey)
    }

    private fun cacheKeyForURL(url: URL): String {
        val path = url.file.dropWhile { it == '/' }
        val splits = path.split("/")
        val normalizeParentPath = if (splits.size > 1) {
            splits.dropLast(1).joinToString(separator = "/", postfix = "/") {
                "_$it"
            }
        } else {
            ""
        }
        return "$normalizeParentPath${splits.last()}"
    }

    private suspend fun fetchDoc(
        docURL: URL,
        init: (Doc.() -> Unit)?,
    ): Doc? {
        val cacheKey = cacheKeyForURL(docURL)
        val cacheContent = if (!skipCache && cache != null) cache.get(cacheKey) else null
        if (cacheContent != null) {
            return htmlDocument(cacheContent) {
                init?.invoke(this)
                this
            }
        }
        logger.info("Fetching document $docURL")
        val httpResponse: HttpResponse = try {
            withContext(Dispatchers.IO) {
                client.get(docURL)
            }
        } catch (t: Throwable) {
            logger.error("Failed fetching document $docURL", t)
            return null
        }
        val byteArrayBody: ByteArray = httpResponse.body()
        val remoteContent = String(byteArrayBody)
        val fetchedDoc = htmlDocument(remoteContent) {
            init?.invoke(this)
            this
        }
        if (fetchedDoc.isEmpty()) {
            logger.warn("Empty document detected $docURL")
            return null
        }
        cache?.put(cacheKey to remoteContent)
        return fetchedDoc
    }

    suspend fun getDoc(
        docURL: URL,
        init: (Doc.() -> Unit)? = null,
    ): Doc = coroutineScope {
        var fetchedDoc: Doc? = null
        while (fetchedDoc == null) {
            fetchedDoc = fetchDoc(docURL, init)
            if (fetchedDoc == null) {
                delay(retryDelay)
            }
        }
        return@coroutineScope fetchedDoc
    }
}

private fun Doc.isEmpty(): Boolean {
    val pageContent = findFirst(".page-content")
    return pageContent.text.trim().isEmpty() || pageContent.text.contains("temporarily unavailable")
}
