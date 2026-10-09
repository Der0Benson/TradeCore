# Tradecore – Minecraft Fabric Mod

Tradecore ist eine Fabric-Client-Mod für den **Block des Tages (BdT)**. Die frühere Preisverwaltung ist seit Version 2.2.0 nicht mehr Bestandteil der Mod.

## BdT-Funktionen

- aktuellen Block des Tages und den Gewinn anzeigen
- über eine schnelle oder langsame Laufzeit abstimmen
- Stimmen mit Anzahl und Prozentverteilung anzeigen
- neue BdT-Vorschläge erstellen
- Block- und Entity-Namen über die BdT-API auflösen
- BdT-Blöcke in der Welt hervorheben
- Highlight-Farbe, Reichweite, Scanintervall und Partikelmenge einstellen
- BdT-Daten beim Betreten einer Welt automatisch vorladen und manuell aktualisieren
- Ladezustände und Serverfehler direkt in der BdT-Oberfläche anzeigen
- bei fehlender Discord-Verknüpfung automatisch einen Verifizierungsdialog öffnen
- Highlight-Treffer auf nahe Blöcke begrenzen und Partikel FPS-schonend erzeugen

Zusätzlich bleiben der Chat-Rechner, der In-Game-Taschenrechner, der Update-Check sowie die Discord-Verknüpfung erhalten.

Der optionale, separat betriebene Discord-Dienst befindet sich unter [`discord-bot/`](discord-bot/). Discord-Token und Bot-Schlüssel werden nicht in die Mod gepackt.

## Anforderungen

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.5 oder neuer
- Fabric API

## Installation

1. Die JAR aus `build/libs/` in den Minecraft-Ordner `.minecraft/mods` kopieren.
2. Minecraft 26.2 mit Fabric starten.

## Tastenbelegung

| Taste | Aktion |
|---|---|
| Pfeil nach oben | BdT-Einstellungen öffnen |
| Pfeil nach rechts | Aktuellen BdT öffnen |
| Pfeil nach unten | BdT-Highlight umschalten |
| Umschalt + Z | Taschenrechner öffnen |

Die Tasten können in den Minecraft-Steuerungseinstellungen geändert werden.

## Chat-Rechner

Eine Rechnung in den Chat schreiben und Tab drücken, zum Beispiel `5+3`, `6*7` oder `100/5`.

## API-Endpunkte

Die Mod verwendet die Tradecore-API für:

- BdT abrufen
- BdT erstellen
- BdT-Abstimmungen
- Namen und IDs für BdT-Einträge auflösen
- Aktivitätsstatus und Discord-Verknüpfung
- Update-Prüfung

Anfragen, die Änderungen auslösen, werden mit HMAC-SHA256 signiert.

## Lizenz

Der Quellcode ist ausschließlich zur Einsicht und zur Sicherheits-/Transparenzprüfung öffentlich. Eine Erlaubnis zum Kopieren, Bearbeiten, Weiterverbreiten, Hosten oder Erstellen abgeleiteter Werke wird nicht erteilt. Siehe [LICENSE.txt](LICENSE.txt).
