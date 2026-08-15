package com.hidenobunagai.linkbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallRedirectionLogicTest {

    @Test
    fun `通常の電話番号は転送対象として返る`() {
        assertEquals("+819012345678", phoneNumberForRedirect("tel", "+819012345678"))
        assertEquals("09012345678", phoneNumberForRedirect("tel", "09012345678"))
    }

    @Test
    fun `アスタリスクを含むショートコードは転送しない`() {
        assertNull(phoneNumberForRedirect("tel", "*123#"))
        assertNull(phoneNumberForRedirect("tel", "*201#"))
    }

    @Test
    fun `シャープを含むショートコードは転送しない`() {
        assertNull(phoneNumberForRedirect("tel", "#123"))
    }

    @Test
    fun `空またはブランクの番号は転送しない`() {
        assertNull(phoneNumberForRedirect("tel", null))
        assertNull(phoneNumberForRedirect("tel", ""))
        assertNull(phoneNumberForRedirect("tel", " "))
    }

    @Test
    fun `tel 以外のスキームは転送しない`() {
        assertNull(phoneNumberForRedirect("sip", "user@example.com"))
        assertNull(phoneNumberForRedirect("voicemail", "vm1"))
    }
}
