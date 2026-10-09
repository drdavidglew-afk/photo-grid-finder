package com.bookshelf.app

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val year: Int?,
    val read: Boolean,
    val commentCount: Int,
)

data class Comment(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val lat: Double?,
    val lon: Double?,
    val place: String?,
)

/** An author the user may have meant; [detail] helps tell namesakes apart. */
data class AuthorCandidate(val key: String, val name: String, val detail: String)

/** One book as returned by the lookup, before it is saved. */
data class Work(val id: String, val title: String, val year: Int?)
