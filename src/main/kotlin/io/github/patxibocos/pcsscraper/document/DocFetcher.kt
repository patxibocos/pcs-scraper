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
        private const val MAX_ATTEMPTS = 10
        private const val RETRY_DELAY = 1_000L
    }

    private val client = HttpClient(CIO)

    private suspend fun retry(docURL: URL, attempts: Int, init: (Doc.() -> Unit)?): Doc {
        delay(RETRY_DELAY)
        return getDoc(docURL, attempts - 1, init)
    }

    suspend fun getDoc(
        docURL: URL,
        attempts: Int = MAX_ATTEMPTS,
        init: (Doc.() -> Unit)? = null,
    ): Doc = coroutineScope {
        if (attempts == 0) {
            throw IllegalStateException("no more attempts remaining to fetch $docURL")
        }
        val path = docURL.file.dropWhile { it == '/' }
        val splits = path.split("/")
        val normalizeParentPath = if (splits.size > 1) splits.dropLast(1).joinToString(separator = "/", postfix = "/") {
            "_$it"
        } else ""
        val cacheKey = "$normalizeParentPath${splits.last()}"
        val cacheContent = if (!skipCache && cache != null) cache.get(cacheKey) else null
        if (cacheContent != null) {
            return@coroutineScope htmlDocument(cacheContent) {
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
            return@coroutineScope retry(docURL, attempts, init)
        }
        val byteArrayBody: ByteArray = httpResponse.body()
        val remoteContent = String(byteArrayBody)
        val fetchedDoc = htmlDocument(remoteContent) {
            init?.invoke(this)
            this
        }
        if (fetchedDoc.isEmpty()) {
            println("Empty document detected, fetching again")
            return@coroutineScope retry(docURL, attempts, init)
        }
        cache?.put(cacheKey to remoteContent)
        return@coroutineScope fetchedDoc
    }
}

private fun Doc.isEmpty() = findFirst(".page-content").text.trim().isEmpty()
