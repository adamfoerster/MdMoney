package com.mdmoney.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VaultLinkTest {

    @Test
    fun reads_the_link_the_app_writes() {
        assertEquals(VaultLink("casa", "Casa"), parseVaultLink("\"[[casa|Casa]]\""))
        assertEquals(VaultLink("nubank", "Nubank"), parseVaultLink("\"[[nubank|Nubank]]\""))
    }

    /** A vault is hand-edited in Obsidian, so every shape it can end up in must still resolve. */
    @Test
    fun reads_the_shapes_a_hand_edited_vault_holds() {
        assertEquals(VaultLink("casa", "Casa"), parseVaultLink("[[casa|Casa]]"), "unquoted")
        assertEquals(VaultLink("casa", null), parseVaultLink("[[casa]]"), "no display title")
        assertEquals(VaultLink("casa", null), parseVaultLink("'[[casa]]'"), "single quotes")
        assertEquals(VaultLink("casa", "Casa"), parseVaultLink("  \"[[ casa | Casa ]]\"  "), "loose spacing")
        // Obsidian writes a path when the note name isn't unique; the name is still the identity.
        assertEquals(VaultLink("casa", "Casa"), parseVaultLink("\"[[categories/casa|Casa]]\""))
    }

    /** The whole point of tolerance: a vault that was never migrated keeps working. */
    @Test
    fun reads_a_plain_value_from_before_links_existed() {
        assertEquals(VaultLink("casa", null), parseVaultLink("casa"))
        assertEquals(VaultLink("adam shopping", null), parseVaultLink("adam shopping"))
    }

    @Test
    fun nothing_is_null_rather_than_an_empty_category() {
        assertNull(parseVaultLink(null))
        assertNull(parseVaultLink(""))
        assertNull(parseVaultLink("   "))
        assertNull(parseVaultLink("\"\""), "a quoted blank is still blank")
        assertNull(parseVaultLink("[[]]"), "a link pointing nowhere")
        assertNull(parseVaultLink("\"[[|Casa]]\""), "a title with no target")
    }

    @Test
    fun writes_a_quoted_link_that_survives_a_round_trip() {
        assertEquals("\"[[casa|Casa]]\"", formatVaultLink("casa", "Casa"))
        // Quoted because bare `[[x]]` is a YAML flow sequence, not a link, to Obsidian.
        assertEquals(VaultLink("casa", "Casa"), parseVaultLink(formatVaultLink("casa", "Casa")))
        assertEquals(
            VaultLink("adam shopping", "Adam Shopping"),
            parseVaultLink(formatVaultLink("adam shopping", "Adam Shopping")),
        )
    }
}
