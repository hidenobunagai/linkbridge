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
        assertEquals("08068811852", toDialableNumber("+818068811852"))
        assertEquals("0312345678", toDialableNumber("+81312345678"))
    }

    @Test
    fun `プラス記号なしの81形式でも変換する`() {
        assertEquals("08068811852", toDialableNumber("818068811852"))
        assertEquals("0312345678", toDialableNumber("81312345678"))
    }

    @Test
    fun `海外番号は010プレフィックス形式に変換する`() {
        assertEquals("01033123456789", toDialableNumber("+33123456789"))
        assertEquals("01014155550100", toDialableNumber("+14155550100"))
        assertEquals("010442012345678", toDialableNumber("+442012345678"))
    }

    @Test
    fun `変換不要な番号はそのまま返る`() {
        assertEquals("08068811852", toDialableNumber("08068811852"))
        assertEquals("01033123456789", toDialableNumber("01033123456789"))
        assertEquals("*123#", toDialableNumber("*123#"))
        assertEquals("", toDialableNumber(""))
    }

    @Test
    fun `不正な81番号は変換しない`() {
        assertEquals("+81012345678", toDialableNumber("+81012345678"))
        assertEquals("+818012345", toDialableNumber("+818012345"))
    }
}
