package io.github.patxibocos.pcsscraper.entity

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.net.URL
import java.util.Locale

@Serializable
data class Team(
    val id: String,
    val name: String,
    val status: Status,
    val abbreviation: String,
    val country: String,
    val bike: String,
    @Contextual val jersey: URL,
    val website: String?,
    val year: Int,
    val riders: List<String>,
) {
    enum class Status {
        WT, PRT
    }

    init {
        require(country.uppercase() in Locale.getISOCountries()) {
            "$country is not a valid ISO country code"
        }
    }
}
