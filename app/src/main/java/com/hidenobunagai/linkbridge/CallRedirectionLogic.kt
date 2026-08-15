package com.hidenobunagai.linkbridge

/**
 * 楽天リンクへ転送してよい通常の電話番号を返す。転送すべきでない場合は null を返す。
 *
 * ショートコード (*123# など)・USSD・非 tel スキーム (sip: など) は
 * 楽天リンクでは扱えないため、通常発信のまま通す対象とする。
 */
internal fun phoneNumberForRedirect(scheme: String?, number: String?): String? {
    if (scheme != "tel") return null
    if (number.isNullOrBlank()) return null
    if (number.any { it == '*' || it == '#' }) return null
    return number
}

/**
 * Rakuten Link は国内番号形式 (080... など) でないと発信できないため、
 * Telecom が渡す E.164 形式 (+81...) を国内形式 (0...) に変換する。
 * 日本以外の番号や変換不要な番号はそのまま返す。
 */
internal fun toNationalFormat(number: String): String {
    if (number.startsWith("+81") && number.length in 12..13) {
        return "0" + number.removePrefix("+81")
    }
    return number
}
