package com.mdmoney.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategoryTest {

    @Test
    fun derives_a_readable_title_from_a_slug() {
        assertEquals("Casa", prettifySlug("casa"))
        assertEquals("Adam Shopping", prettifySlug("adam shopping"))
        assertEquals("Educação", prettifySlug("educação"), "accents are left alone")
        assertEquals("Salário", prettifySlug("Salário"), "an already-capitalised slug is untouched")
    }

    private val known = listOf(
        Category("casa", "Casa"),
        Category("adam shopping", "Adam Shopping"),
    )

    /** Typing the title of a category that exists must land on it, not fork a second note. */
    @Test
    fun typing_a_known_title_resolves_to_its_slug() {
        assertEquals("casa", resolveCategorySlug("Casa", known))
        assertEquals("casa", resolveCategorySlug("  casa  ", known))
        assertEquals("casa", resolveCategorySlug("CASA", known), "matching ignores case")
        assertEquals("adam shopping", resolveCategorySlug("Adam Shopping", known))
    }

    @Test
    fun typing_something_new_becomes_a_new_slug() {
        assertEquals("viagem", resolveCategorySlug("viagem", known))
        assertEquals("Seguro do carro", resolveCategorySlug("  Seguro do carro ", known))
    }

    @Test
    fun a_new_slug_is_always_a_usable_file_name() {
        // The slug becomes `categories/<slug>.md`, and `[[...]]` inside a link would break it.
        assertEquals("a-b", resolveCategorySlug("a/b", known))
        assertEquals("casa-carro", resolveCategorySlug("casa:carro", known))
        assertEquals("link", resolveCategorySlug("[[link]]", known))
    }

    @Test
    fun nothing_typed_is_no_category() {
        assertNull(resolveCategorySlug("", known))
        assertNull(resolveCategorySlug("   ", known))
        assertNull(resolveCategorySlug("///", known), "nothing left once it is made a file name")
    }
}
