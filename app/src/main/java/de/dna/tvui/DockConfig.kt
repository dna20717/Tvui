package de.dna.tvui

/**
 * Was im Dock steht – in genau dieser Reihenfolge.
 *
 * AppItem:  eine installierte TV-App. Gefunden wird sie über den Paketnamen
 *           oder, falls der anders ist, über ihren angezeigten Namen.
 * LinkItem: eine Website, die im Browser des Fernsehers geöffnet wird.
 */
sealed class DockItem(val name: String)

class AppItem(
    name: String,
    val packages: List<String>,
    val labelMatch: Regex,
) : DockItem(name)

class LinkItem(
    name: String,
    val url: String,
    val color: Int = 0xFF8A5CFF.toInt(),
) : DockItem(name)

object DockConfig {
    val items: List<DockItem> = listOf(
        AppItem(
            "YouTube",
            listOf("com.google.android.youtube.tv"),
            Regex("^YouTube$", RegexOption.IGNORE_CASE),
        ),
        AppItem(
            "YouTube Music",
            listOf("com.google.android.youtube.tvmusic"),
            Regex("YouTube Music", RegexOption.IGNORE_CASE),
        ),
        AppItem(
            "ARD Mediathek",
            listOf("de.swr.avp.ard.tv"),
            Regex("\\bARD\\b"),
        ),
        AppItem(
            "ZDFmediathek",
            listOf("com.zdf.android.mediathek"),
            Regex("ZDF"),
        ),

        // Websites, z. B.:
        // LinkItem("Arte", "https://www.arte.tv", 0xFFE8521F.toInt()),
    )
}
