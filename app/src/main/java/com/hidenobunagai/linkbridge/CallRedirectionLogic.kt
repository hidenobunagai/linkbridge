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
