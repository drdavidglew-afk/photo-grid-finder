package com.bookshelf.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val book = Book("OL1W", "Wuthering Heights", "Emily Brontë", 1847, read = false, commentCount = 0)

    @Test fun emptyQueryMatchesEverything() = assertTrue(matches(book, "  "))

    @Test fun matchesTitleOrAuthorKeywords() {
        assertTrue(matches(book, "wuthering"))
        assertTrue(matches(book, "emily"))
        assertTrue(matches(book, "heights emily"))
    }

    @Test fun allKeywordsMustMatch() = assertFalse(matches(book, "emily jane"))

    @Test fun ignoresCaseAndAccents() {
        assertTrue(matches(book, "BRONTE"))
        assertTrue(matches(book, "brontë"))
    }

    @Test fun titleKeyCollapsesPunctuationAndCase() {
        assertEquals(titleKey("Small Gods"), titleKey("small  gods!"))
    }
}
