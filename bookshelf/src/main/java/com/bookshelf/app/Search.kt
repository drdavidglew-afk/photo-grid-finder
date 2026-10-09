package com.bookshelf.app

import java.text.Normalizer

private val accents = Regex("\\p{Mn}+")
private val notWord = Regex("[^\\p{L}\\p{N}]+")
private val spaces = Regex("\\s+")

/** Lower-case and strip accents so "Brontë" matches "bronte". */
fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(accents, "").lowercase()

/** Key used to collapse duplicate editions of the same title. */
fun titleKey(title: String): String = fold(title).replace(notWord, " ").trim()

/** True when every keyword in [query] appears in the book's title or author. */
fun matches(book: Book, query: String): Boolean {
    val words = fold(query).split(spaces).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = fold(book.title + " " + book.author)
    return words.all { it in haystack }
}
