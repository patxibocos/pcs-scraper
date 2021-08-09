package io.github.patxibocos.pcsscraper.document

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import it.skrape.core.htmlDocument
import it.skrape.selects.Doc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URL

class DocFetcher(
    private val cache: Cache?,
    private val skipCache: Boolean,
) {
    private val client = HttpClient(CIO)

    suspend fun getDoc(
        docURL: URL,
        init: (Doc.() -> Unit)? = null,
    ): Doc = coroutineScope {
        val cacheKey = docURL.file.dropWhile { it == '/' }
        val cacheContent = if (!skipCache && cache != null) cache.get(cacheKey) else null
        if (cacheContent != null) {
            return@coroutineScope htmlDocument(cacheContent) {
                init?.invoke(this)
                this
            }
        }
        val httpResponse: HttpResponse = try {
            withContext(Dispatchers.IO) {
                client.get(docURL)
            }
        } catch (t: Throwable) {
            return@coroutineScope getDoc(docURL, init)
        }
        val byteArrayBody: ByteArray = httpResponse.receive()
        val remoteContent = String(byteArrayBody)
        cache?.put(cacheKey to remoteContent)
        return@coroutineScope htmlDocument(remoteContent) {
            init?.invoke(this)
            this
        }
    }
}
