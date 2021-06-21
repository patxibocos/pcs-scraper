import it.skrape.core.htmlDocument
import it.skrape.selects.Doc
import java.net.URL

class DocFetcher(
    private val cache: Cache? = null,
    private val forceRemoteFetching: Boolean = false,
) {

    fun getDoc(
        docURL: URL,
        init: (Doc.() -> Unit)? = null,
    ): Doc {
        val cacheKey = docURL.file.dropWhile { it == '/' }
        val cacheContent = if (!forceRemoteFetching && cache != null) cache.get(cacheKey) else null
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