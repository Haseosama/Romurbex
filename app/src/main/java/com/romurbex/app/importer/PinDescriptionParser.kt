package com.romurbex.app.importer

data class ParsedPin(val name: String, val description: String, val latitude: Double, val longitude: Double)

/**
 * Parse le texte d'une description d'épingle Pinterest contenant des coordonnées GPS au format
 * DMS (ex. 45°20'51.3"N 0°11'41.3"W) suivies du nom/adresse du lieu — format observé sur le
 * tableau urbex de l'utilisateur. Aucune IA, aucun réseau : uniquement du texte que l'utilisateur
 * copie-colle lui-même depuis Pinterest.
 */
object PinDescriptionParser {
    private val DMS_REGEX = Regex(
        """(\d{1,3})[°º]\s*(\d{1,2})['’]\s*([\d.]+)"?\s*([NSns])\s*[,;]?\s*(\d{1,3})[°º]\s*(\d{1,2})['’]\s*([\d.]+)"?\s*([EWOew])""",
    )

    fun parse(text: String): ParsedPin? {
        val raw = text.trim()
        if (raw.isBlank()) return null
        val match = DMS_REGEX.find(raw) ?: return null
        val (dLat, mLat, sLat, hemiLat, dLon, mLon, sLon, hemiLon) = match.destructured

        val lat = toDecimal(dLat, mLat, sLat, hemiLat)
        val lon = toDecimal(dLon, mLon, sLon, hemiLon)

        val remainder = raw.removeRange(match.range).trim(' ', '\n', '\t', '-', '—', ':')
        val name = remainder.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            .takeUnless { it.isNullOrBlank() } ?: "Lieu importé de Pinterest"

        return ParsedPin(name = name, description = raw, latitude = lat, longitude = lon)
    }

    // "O" couvre la notation française "Ouest" en plus de "W".
    private fun toDecimal(deg: String, min: String, sec: String, hemisphere: String): Double {
        val value = deg.toDouble() + min.toDouble() / 60.0 + sec.toDouble() / 3600.0
        return if (hemisphere.equals("S", true) || hemisphere.equals("W", true) || hemisphere.equals("O", true)) -value else value
    }
}
