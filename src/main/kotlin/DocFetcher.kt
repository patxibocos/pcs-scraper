import it.skrape.core.htmlDocument
import it.skrape.selects.Doc
import java.net.URL

class DocFetcher(private val cache: Cache? = null) {

    fun getDoc(
        docUrl: String,
        init: Doc.() -> Unit = {},
    ): Doc {
        val cacheContent = cache?.get(docUrl)
        if (cacheContent != null) {
            return htmlDocument(cacheContent) {
                init(this)
                this
            }
        }
        val remoteContent = URL("https://www.procyclingstats.com/$docUrl").readText()
        cache?.put(docUrl to remoteContent)
        return htmlDocument(remoteContent) {
            init(this)
            this
        }
    }

}