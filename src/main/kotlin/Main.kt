import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.io.File
import java.nio.file.Paths

const val pcsUrl = "https://www.procyclingstats.com"

fun main(args: Array<String>) {
    val (season, cachePath, output, _, skipCache) = getAppArgs(args)

    val cache = cachePath?.let { Cache(Paths.get(it)) }
    val docFetcher = DocFetcher(cache, skipCache)
    val pcsParser = PCSParser(docFetcher, pcsUrl)
    val serializer = Serializer()

    val teamsAndRiders = GetTeamsAndRiders(pcsParser = pcsParser)(season = season)

    val serialized = serializer.serialize(teamsAndRiders)
    with(File(Paths.get(output).toUri())) {
        parentFile.mkdirs()
        writeText(serialized)
    }
}

private data class AppArgs(
    val season: Int,
    val cachePath: String?,
    val output: String,
    val format: String,
    val skipCache: Boolean,
)

private fun getAppArgs(args: Array<String>): AppArgs {
    val parser = ArgParser("pcs-scrapper")
    val season by parser.option(ArgType.Int, shortName = "s", description = "Season").required()
    val cachePath by parser.option(ArgType.String, shortName = "c", description = "Cache path")
    val output by parser.option(ArgType.String, shortName = "o", description = "Output file path").required()
    val format by parser.option(
        ArgType.Choice(listOf("json"), { it }),
        shortName = "f",
        description = "Output file format"
    ).default("json")
    val skipCache by parser.option(ArgType.Boolean, shortName = "sc", description = "Skip cache").default(false)
    parser.parse(args)
    return AppArgs(
        season = season,
        cachePath = cachePath,
        output = output,
        format = format,
        skipCache = skipCache,
    )
}