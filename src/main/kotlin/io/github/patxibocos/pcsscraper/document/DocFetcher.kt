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
import java.net.URL

class DocFetcher(
    private val cache: Cache?,
    private val skipCache: Boolean,
) {
    companion object {
        private const val RETRY_DELAY = 1_000L
    }

    private val client = HttpClient(CIO)

    private suspend fun fetchDoc(
        docURL: URL,
        init: (Doc.() -> Unit)?,
    ): Doc? {
        val path = docURL.file.dropWhile { it == '/' }
        val splits = path.split("/")
        val normalizeParentPath = if (splits.size > 1) splits.dropLast(1).joinToString(separator = "/", postfix = "/") {
            "_$it"
        } else ""
        val cacheKey = "$normalizeParentPath${splits.last()}"
        val cacheContent = if (!skipCache && cache != null) cache.get(cacheKey) else null
        if (cacheContent != null) {
            return htmlDocument(cacheContent) {
                init?.invoke(this)
                this
            }
        }
        println("Fetching document $docURL")
        val httpResponse: HttpResponse = try {
            withContext(Dispatchers.IO) {
                client.get(docURL)
            }
        } catch (t: Throwable) {
            println("Exception occurred when fetching: ${t.message}")
            return null
        }
        val byteArrayBody: ByteArray = httpResponse.body()
        val remoteContent = String(byteArrayBody)
        val fetchedDoc = htmlDocument(remoteContent) {
            init?.invoke(this)
            this
        }
        if (fetchedDoc.isEmpty()) {
            println("Empty document detected, fetching again")
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
                delay(RETRY_DELAY)
            }
        }
        return@coroutineScope fetchedDoc
    }
}

private fun Doc.isEmpty() = findFirst(".page-content").text.trim().isEmpty()
