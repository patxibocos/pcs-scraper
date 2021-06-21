import it.skrape.core.htmlDocument
import it.skrape.selects.Doc
import java.net.URI

class DocFetcher(
    private val remoteUrl: String,
    private val cache: Cache? = null,
    private val forceRemoteFetching: Boolean = false,
) {

    fun getDoc(
        docUrl: String,
        init: Doc.() -> Unit = {},
    ): Doc {
        val cacheContent = if (!forceRemoteFetching && cache != null) cache.get(docUrl) else null
        if (cacheContent != null) {
            return htmlDocument(cacheContent) {
                init(this)
                this
            }
        }
        val remoteContent = URI(remoteUrl).resolve(docUrl).toURL().readText()
        cache?.put(docUrl to remoteContent)
        return htmlDocument(remoteContent) {
            init(this)
            this
        }
    }

}