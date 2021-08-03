package io.github.patxibocos.pcsscraper.document

import it.skrape.core.htmlDocument
import it.skrape.selects.Doc
import java.net.URL

class DocFetcher(
    private val cache: Cache?,
    private val skipCache: Boolean,
) {
    fun getDoc(
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
        val remoteContent = docURL.readText()
        cache?.put(cacheKey to remoteContent)
        return htmlDocument(remoteContent) {
            init?.invoke(this)
            this
        }
    }
}
