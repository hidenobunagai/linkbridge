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
 * Rakuten Link が発信できる形式に変換する。Rakuten Link は "+" 付き番号を扱えないため:
 * - 日本の国内番号 (+81... / 81...) → 0 始まり (080... など)
 * - 海外番号 (+XX...) → 国際プレフィックス 010 形式 (010XX...)
 * - それ以外はそのまま返す
 */
internal fun toDialableNumber(number: String): String {
    val body = number.removePrefix("+")
    // 日本の国内番号: 81 + (3〜9 で始まる 9〜10 桁)
    if (body.startsWith("81") && body.length in 11..12) {
        val rest = body.drop(2)
        if (rest.firstOrNull()?.let { it in '3'..'9' } == true) {
            return "0$rest"
        }
    }
    // 海外番号: E.164 (+XX...) → 010 プレフィックス形式
    if (number.startsWith("+") && !body.startsWith("81")) {
        return "010$body"
    }
    return number
}
