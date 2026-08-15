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
 * 国番号 81 形式 (+81... または プラス記号が落ちた 81...) を国内形式 (0...) に変換する。
 * 日本以外の番号や変換不要な番号はそのまま返す。
 *
 * プラス記号なしの 81... で届くケースがあるため、両方の形を扱う。
 * (例) +818068811852 / 818068811852 → 08068811852
 */
internal fun toNationalFormat(number: String): String {
    val body = number.removePrefix("+")
    // 81 + 国内番号 (3〜9 で始まる 9〜10 桁)。"+81" のみ長さ 12〜13、プラスなしで 11〜12。
    if (body.startsWith("81") && body.length in 11..12) {
        val rest = body.drop(2)
        if (rest.firstOrNull()?.let { it in '3'..'9' } == true) {
            return "0$rest"
        }
    }
    return number
}
