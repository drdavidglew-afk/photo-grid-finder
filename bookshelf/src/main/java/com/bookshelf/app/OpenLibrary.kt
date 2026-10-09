package com.bookshelf.app

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Book lookups against openlibrary.org (free, no key needed). Call from a background thread. */
object OpenLibrary {

    private const val BASE = "https://openlibrary.org"
    private const val PAGE = 100
    private const val MAX_PAGES = 5

    private fun get(path: String): JSONObject {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "BookshelfAndroid/1.0 (personal reading list)")
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode != 200) throw IOException("Open Library returned HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /**
     * Everyone the text could refer to, whether it was an author's name or a book title.
     * One result means no ambiguity; more means the user should be asked.
     */
    fun findAuthors(query: String): List<AuthorCandidate> {
        val found = LinkedHashMap<String, AuthorCandidate>()

        val authors = get("/search/authors.json?q=${enc(query)}&limit=8").optJSONArray("docs")
        for (i in 0 until (authors?.length() ?: 0)) {
            val d = authors!!.getJSONObject(i)
            val key = d.optString("key")
            val name = d.optString("name")
            val count = d.optInt("work_count", 0)
            if (key.isEmpty() || name.isEmpty() || count == 0) continue
            val detail = listOfNotNull(
                d.optString("birth_date").takeIf { it.isNotEmpty() }?.let { "born $it" },
                d.optString("top_work").takeIf { it.isNotEmpty() }?.let { "known for “$it”" },
                "$count ${if (count == 1) "work" else "works"}",
            ).joinToString(" · ")
            found.putIfAbsent(key, AuthorCandidate(key, name, detail))
        }

        val titles = get(
            "/search.json?title=${enc(query)}&limit=10&fields=title,author_name,author_key"
        ).optJSONArray("docs")
        for (i in 0 until (titles?.length() ?: 0)) {
            val d = titles!!.getJSONObject(i)
            val keys = d.optJSONArray("author_key") ?: continue
            val names = d.optJSONArray("author_name") ?: continue
            val title = d.optString("title")
            for (j in 0 until minOf(keys.length(), names.length(), 3)) {
                found.putIfAbsent(
                    keys.getString(j),
                    AuthorCandidate(keys.getString(j), names.getString(j), "author of “$title”"),
                )
            }
        }
        return found.values.toList()
    }

    /** Every book by the author, with duplicate editions of a title collapsed to one. */
    fun worksBy(authorKey: String): List<Work> {
        val byTitle = LinkedHashMap<String, Work>()
        for (page in 1..MAX_PAGES) {
            val json = get(
                "/search.json?q=${enc("author_key:$authorKey")}&fields=key,title,first_publish_year" +
                    "&limit=$PAGE&page=$page"
            )
            val docs = json.optJSONArray("docs") ?: break
            if (docs.length() == 0) break
            for (i in 0 until docs.length()) {
                val d = docs.getJSONObject(i)
                val id = d.optString("key").removePrefix("/works/")
                val title = d.optString("title")
                if (id.isEmpty() || title.isEmpty()) continue
                val year = if (d.has("first_publish_year")) d.optInt("first_publish_year") else null
                val k = titleKey(title).ifEmpty { id }
                val prev = byTitle[k]
                if (prev == null || (year != null && (prev.year == null || year < prev.year))) {
                    byTitle[k] = Work(id, title, year)
                }
            }
            if (page * PAGE >= json.optInt("numFound", 0)) break
        }
        return byTitle.values.toList()
    }
}
