# TVUI 📺✨

Ein eigener, minimalistischer Homescreen im Liquid-Glass-Look für den
**Philips 65PUS6412** (Android TV 6).

- Große Uhr und Datum
- Alle TV-Apps als Glas-Dock mit den offiziellen App-Bannern
- Hintergrund wählen: 5 eingebaute Verläufe oder ein eigenes Foto vom iPad (per QR-Code)

## Bauen

Jeder Push auf `main` baut automatisch eine APK (GitHub Actions) und
veröffentlicht sie als Release **latest**:

```
https://github.com/dna20717/Tvui/releases/latest/download/tvui.apk
```

## Auf dem Fernseher installieren

1. Einstellungen → Sicherheit → **Unbekannte Quellen** erlauben.
2. Aus dem Play Store die App **Downloader** (von AFTVnews) installieren.
3. In Downloader die APK-Adresse oben eingeben → installieren.
4. Home-Taste drücken → **TVUI** wählen → „Immer“.

Updates: einfach dieselbe Adresse nochmal in Downloader öffnen.

## Bedienung

| Taste | Aktion |
|---|---|
| OK auf einer Kachel | App starten |
| Zahnrad oben rechts / Menü-Taste | Hintergrund ändern |
| Zurück / Home | zurück zum Anfang |

**Eigenes Foto:** Hintergrund-Einstellungen öffnen, den QR-Code mit der
iPad-Kamera scannen, Foto auswählen. Fertig. (iPad und TV müssen im selben
WLAN sein.)
