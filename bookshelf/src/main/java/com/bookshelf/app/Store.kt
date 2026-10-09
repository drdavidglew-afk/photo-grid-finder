package com.bookshelf.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local storage for the book list, read flags and comments. */
class Store(context: Context) : SQLiteOpenHelper(context.applicationContext, "bookshelf.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE books (" +
                "id TEXT PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, " +
                "year INTEGER, read INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE comments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, book_id TEXT NOT NULL, text TEXT NOT NULL, " +
                "created INTEGER NOT NULL, lat REAL, lon REAL, place TEXT)"
        )
        db.execSQL("CREATE INDEX comments_book ON comments (book_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Saves new books as unread. Books already on the list are left untouched. Returns how many were new. */
    fun addBooks(author: String, works: List<Work>): Int {
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            for (w in works) {
                val values = ContentValues().apply {
                    put("id", w.id)
                    put("title", w.title)
                    put("author", author)
                    if (w.year != null) put("year", w.year) else putNull("year")
                    put("read", 0)
                }
                if (db.insertWithOnConflict("books", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    fun allBooks(): List<Book> =
        readableDatabase.rawQuery(
            "SELECT b.id, b.title, b.author, b.year, b.read, " +
                "(SELECT COUNT(*) FROM comments c WHERE c.book_id = b.id) " +
                "FROM books b " +
                "ORDER BY b.author COLLATE NOCASE, b.year IS NULL, b.year, b.title COLLATE NOCASE",
            null,
        ).use { c ->
            val out = ArrayList<Book>(c.count)
            while (c.moveToNext()) out.add(bookFrom(c))
            out
        }

    fun book(id: String): Book? =
        readableDatabase.rawQuery(
            "SELECT b.id, b.title, b.author, b.year, b.read, " +
                "(SELECT COUNT(*) FROM comments c WHERE c.book_id = b.id) " +
                "FROM books b WHERE b.id = ?",
            arrayOf(id),
        ).use { c -> if (c.moveToFirst()) bookFrom(c) else null }

    fun setRead(id: String, read: Boolean) {
        val values = ContentValues().apply { put("read", if (read) 1 else 0) }
        writableDatabase.update("books", values, "id = ?", arrayOf(id))
    }

    fun comments(bookId: String): List<Comment> =
        readableDatabase.rawQuery(
            "SELECT id, text, created, lat, lon, place FROM comments WHERE book_id = ? ORDER BY created DESC, id DESC",
            arrayOf(bookId),
        ).use { c ->
            val out = ArrayList<Comment>(c.count)
            while (c.moveToNext()) {
                out.add(
                    Comment(
                        id = c.getLong(0),
                        text = c.getString(1),
                        createdAt = c.getLong(2),
                        lat = if (c.isNull(3)) null else c.getDouble(3),
                        lon = if (c.isNull(4)) null else c.getDouble(4),
                        place = c.getString(5),
                    )
                )
            }
            out
        }

    fun addComment(bookId: String, text: String, createdAt: Long, lat: Double?, lon: Double?, place: String?) {
        val values = ContentValues().apply {
            put("book_id", bookId)
            put("text", text)
            put("created", createdAt)
            if (lat != null && lon != null) {
                put("lat", lat)
                put("lon", lon)
            }
            if (place != null) put("place", place)
        }
        writableDatabase.insert("comments", null, values)
    }

    fun deleteComment(id: Long) {
        writableDatabase.delete("comments", "id = ?", arrayOf(id.toString()))
    }

    private fun bookFrom(c: android.database.Cursor) = Book(
        id = c.getString(0),
        title = c.getString(1),
        author = c.getString(2),
        year = if (c.isNull(3)) null else c.getInt(3),
        read = c.getInt(4) != 0,
        commentCount = c.getInt(5),
    )
}
