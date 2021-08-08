package io.github.patxibocos.pcsscraper.document

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.BrowserUserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import it.skrape.core.htmlDocument
import it.skrape.selects.Doc
import java.net.URL

class DocFetcher(
    private val cache: Cache?,
    private val skipCache: Boolean,
) {
    private val client = HttpClient {
        BrowserUserAgent()
    }

    suspend fun getDoc(
        docURL: URL,
        init: (Doc.() -> Unit)? = null,
    ): Doc {
        val cacheKey = docURL.file.dropWhile { it == '/' }
        val cacheContent = if (!skipCache && cache != null) cache.get(cacheKey) else null
        if (cacheContent != null) {
            return htmlDocument(cacheContent) {
                init?.invoke(this)
                this
            }
        }
        val httpResponse: HttpResponse = client.get(docURL)
        val byteArrayBody: ByteArray = httpResponse.receive()
        val remoteContent = String(byteArrayBody)
        cache?.put(cacheKey to remoteContent)
        return htmlDocument(remoteContent) {
            init?.invoke(this)
            this
        }
    }
}
