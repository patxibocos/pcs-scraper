package io.github.patxibocos.pcsscraper.entity

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.net.URL
import java.time.LocalDate
import java.util.Locale

@Serializable
data class Rider(
    val id: String,
    val firstName: String,
    val lastName: String,
    val country: String,
    val website: String?,
    @Contextual val birthDate: LocalDate?,
    val birthPlace: String?,
    val weight: Int?,
    val height: Int?,
    @Contextual val photo: URL,
    val uciRankingPosition: Int?,
) {
    init {
        require(country.uppercase() in Locale.getISOCountries()) {
            "$country is not a valid ISO country code"
        }
    }
}
