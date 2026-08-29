package com.hidenobunagai.linkbridge

/**
 * 電話番号からハイフン、空白、括弧などの装飾記号を除去する。
 * '+'、'*'、'#'、および数字は保持する。
 */
internal fun normalizePhoneNumber(number: String): String {
    return buildString(number.length) {
        for (ch in number) {
            if (ch.isDigit() || ch == '+' || ch == '*' || ch == '#') {
                append(ch)
            }
        }
    }
}

/**
 * 楽天リンクへ転送してよい通常の電話番号を返す。転送すべきでない場合は null を返す。
 *
 * ショートコード (*123# など)・USSD・非 tel スキーム (sip: など)・
 * 特番 (171/188/147/148/1417 など)・ナビダイヤル (0570) は
 * 楽天リンクでは発信できないため、通常発信のまま通す対象とする。
 */
internal fun phoneNumberForRedirect(scheme: String?, number: String?): String? {
    if (scheme != "tel") return null
    if (number.isNullOrBlank()) return null
    val normalized = normalizePhoneNumber(number)
    if (normalized.isBlank()) return null
    if (normalized.any { it == '*' || it == '#' }) return null
    // 特番・サービス番号 (171/188/1417 など) は桁数が少ないため、8 桁未満は通常発信のまま通す
    if (normalized.count { it.isDigit() } < 8) return null
    // ナビダイヤル (0570, +81570, 81570 など) は楽天リンクで発信できないため通常発信のまま通す
    val digits = normalized.removePrefix("+")
    if (digits.startsWith("0570") || digits.startsWith("81570")) return null
    return normalized
}

/**
 * Rakuten Link が発信できる形式に変換する。Rakuten Link は "+" 付き番号を扱えないため:
 * - 日本の国内番号 (+81... / 81...) → 0 始まり (080...、011... など)
 * - 海外番号 (+XX...) → 国際プレフィックス 010 形式 (010XX...)
 * - それ以外はそのまま返す
 */
internal fun toDialableNumber(number: String): String {
    val normalized = normalizePhoneNumber(number)
    val body = normalized.removePrefix("+")
    // 日本の国内番号: 81 + (1〜9 で始まる 9〜10 桁)
    // 01x (北海道・東北), 02x (関東・信越・北陸), 03 (東京), 04x, 05x, 06 (大阪), 07x, 08x, 09x, 050, 070/080/090 等すべてカバー
    if (body.startsWith("81") && body.length in 11..12) {
        val rest = body.drop(2)
        if (rest.firstOrNull()?.let { it in '1'..'9' } == true) {
            return "0$rest"
        }
    }
    // 海外番号: E.164 (+XX...) → 010 プレフィックス形式
    if (normalized.startsWith("+") && !body.startsWith("81")) {
        return "010$body"
    }
    return normalized
}

/**
 * 保留中の転送情報が、まだ有効な新しいものかどうかを判定する。
 * 転送から時間が経ちすぎている場合は、別の通話 (着信など) と誤マッチさせない。
 */
internal fun isPendingRedirectFresh(redirectTimeMs: Long, nowMs: Long, windowMs: Long): Boolean {
    val age = nowMs - redirectTimeMs
    return age in 0..windowMs
}
