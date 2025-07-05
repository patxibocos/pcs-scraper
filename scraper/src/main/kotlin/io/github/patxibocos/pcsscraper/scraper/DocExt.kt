package io.github.patxibocos.pcsscraper.scraper

import it.skrape.selects.Doc
import it.skrape.selects.DocElement

internal fun Doc.findNextSiblingElements(elements: Int = 1, condition: DocElement.() -> Boolean): List<DocElement> {
    val element = findAll { filter { condition(it) } }.firstOrNull()
    if (element == null) {
        return emptyList()
    }
    var found = false
    var remaining = elements
    val result = mutableListOf<DocElement>()
    element.parent.children.forEach {
        if (found) {
            result.add(it)
            remaining--
        }
        if (remaining == 0) {
            return result
        }
        if (!found && condition(it)) {
            found = true
        }
    }
    return emptyList()
}