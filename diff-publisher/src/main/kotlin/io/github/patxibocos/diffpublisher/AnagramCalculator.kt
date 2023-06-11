package io.github.patxibocos.diffpublisher

import java.io.File

fun main() {
    val dictionary = File("/Users/patxibocos/Desktop/words.csv").readLines()
    val anagramCalculator = AnagramCalculator(dictionary)
    println(anagramCalculator.listAnagrams("hydroxydeoxycorticosterones"))
}

class AnagramCalculator(private val wordsDictionary: Collection<String>) {

    private fun findAnagrams(
        word: String,
        dictionary: Collection<String>,
        acc: String = "",
    ): Collection<String> {
        if (word.isEmpty() || dictionary.isEmpty()) {
            return dictionary
        }
        val wordsByLetter = dictionary.groupBy { it[acc.length] }
        return word.toSet().flatMap { c ->
            val filteredDict = wordsByLetter[c] ?: return@flatMap emptyList()
            val index = word.indexOf(c)
            findAnagrams(word.removeRange(index, index + 1), filteredDict, acc + c)
        }
    }


    fun listAnagrams(word: String): Collection<String> {
        val sameLengthWords = wordsDictionary.filter { it.length == word.length }
        if (sameLengthWords.isEmpty()) {
            return emptyList()
        }
        return findAnagrams(word.lowercase(), sameLengthWords) - word
    }
}
