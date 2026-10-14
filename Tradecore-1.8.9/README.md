# Tradecore fuer Minecraft 1.8.9

Eigenstaendige Forge-Portierung der Tradecore-Client-Mod fuer Minecraft 1.8.9.

## Voraussetzungen

- Minecraft 1.8.9
- Forge 11.15.1.2318
- Java 8

## Funktionen

- Block des Tages anzeigen, aktualisieren und lokal zwischenspeichern
- fuer schnelle oder langsame Laufzeit abstimmen
- neue BdT-Vorschlaege mit Block-/Mob-Auswahl erstellen
- Discord-Verknuepfung und Aktivitaetsstatus
- BdT-Bloecke in der Welt farbig hervorheben
- Highlight-Farbe, Reichweite, Scanintervall und Intensitaet konfigurieren
- Chat-Rechner mit Tab und eigener Rechner-Bildschirm
- optionale Filterung der GrieferGames-Server-News
- Updatepruefung

## API

Die neuen 1.8.9-Endpunkte werden in
`src/main/java/de/tradecore/legacy/config/ApiEndpoints.java` eingetragen.
Solange `BASE_URL` leer ist, bleiben Online-Aktionen sicher deaktiviert und zeigen einen klaren Hinweis.

Alle Requests senden zusaetzlich `minecraftVersion=1.8.9`, damit die neue API nur Inhalte liefern kann,
die in dieser Spielversion existieren.

Blockvarianten mit Metadaten werden als `minecraft:block#meta` uebertragen, zum Beispiel
`minecraft:wool#14` fuer rote Wolle. Die neue API sollte dieses Format beim Aufloesen beruecksichtigen.

## Entwicklung

Das Projekt basiert auf dem offiziellen Forge-1.8.9-MDK. ForgeGradle 2.1 und Gradle 2.7 benoetigen ein Java-8-JDK.

```powershell
.\gradlew.bat setupDecompWorkspace
.\gradlew.bat build
```

Die fertige JAR liegt anschliessend in `build/libs/`.
