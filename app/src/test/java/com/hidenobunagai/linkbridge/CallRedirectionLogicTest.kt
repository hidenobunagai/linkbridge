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

    @Test
    fun `E164の国内番号は0から始まる形式に変換する`() {
        assertEquals("08068811852", toNationalFormat("+818068811852"))
        assertEquals("0312345678", toNationalFormat("+81312345678"))
    }

    @Test
    fun `変換不要な番号はそのまま返る`() {
        assertEquals("08068811852", toNationalFormat("08068811852"))
        assertEquals("+14155550100", toNationalFormat("+14155550100"))
        assertEquals("*123#", toNationalFormat("*123#"))
        assertEquals("", toNationalFormat(""))
    }
}
