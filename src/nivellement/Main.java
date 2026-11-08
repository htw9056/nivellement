package nivellement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Startpunkt. Ohne Argumente startet die grafische Oberfläche, mit Argumenten die Konsole.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            NivellementGui.starte();
            return;
        }
        System.exit(konsole(args));
    }

    static int konsole(String[] args) {
        System.out.println(Version.titel());
        Einstellungen e = Konfiguration.fuerProjekt(projektArgument(args), new StringBuilder());
        Path messdatei = null;
        Path rohdaten = null;
        Path importMdb = null;
        Path importAsc = null;
        Path exportAsc = null;
        boolean pruefen = false;
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "-f":
                        e.festpunktDatei = args[++i];
                        break;
                    case "-hd":
                        e.hoehenDatei = args[++i];
                        break;
                    case "-v":
                        e.verteilung = args[++i].toLowerCase().startsWith("a") ? Einstellungen.Verteilung.AUFSTELLUNGEN
                                : Einstellungen.Verteilung.ZIELWEITEN;
                        break;
                    case "-k":
                        e.fehlerklasse = Math.max(1, Math.min(4, Integer.parseInt(args[++i])));
                        break;
                    case "-m":
                        e.mittelbildung = Math.max(0, Math.min(3, Integer.parseInt(args[++i])));
                        break;
                    case "-n":
                        e.nachkommastellen = Math.max(2, Math.min(5, Integer.parseInt(args[++i])));
                        break;
                    case "-ha":
                        e.manuelleAnfangshoehe = zahl(args[++i]);
                        break;
                    case "-he":
                        e.manuelleEndhoehe = zahl(args[++i]);
                        break;
                    case "-neu":
                        e.neupunkteLoeschen = true;
                        break;
                    case "-mdb":
                        importMdb = Path.of(args[++i]);
                        break;
                    case "-asc":
                        importAsc = Path.of(args[++i]);
                        break;
                    case "-export":
                        exportAsc = Path.of(args[++i]);
                        break;
                    case "-u":
                        rohdaten = Path.of(args[++i]);
                        break;
                    case "-von":
                        e.berNrVon = Integer.parseInt(args[++i]);
                        break;
                    case "-bis":
                        e.berNrBis = Integer.parseInt(args[++i]);
                        break;
                    case "-a":
                        e.alteBerechnungLoeschen = false;
                        break;
                    case "-p":
                        pruefen = true;
                        break;
                    case "-x":
                        e.erweitertesProtokoll = true;
                        break;
                    case "-h":
                    case "--hilfe":
                        hilfe();
                        return 0;
                    default:
                        messdatei = Path.of(a);
                }
            }
            if (messdatei == null) {
                hilfe();
                return 1;
            }
            if (importMdb != null || importAsc != null || exportAsc != null) {
                Path hd = hoehendateiPfad(messdatei, e);
                Hoehendatei h = Hoehendatei.lies(hd);
                if (importMdb != null) {
                    int n = h.uebernehme(MdbLeser.lies(importMdb));
                    System.out.println(n + " Punkte aus " + importMdb.getFileName() + " übernommen.");
                }
                if (importAsc != null) {
                    int n = h.uebernehme(Hoehendatei.liesAsc(importAsc));
                    System.out.println(n + " Punkte aus " + importAsc.getFileName() + " übernommen.");
                }
                if (importMdb != null || importAsc != null) {
                    h.schreibe(hd);
                    System.out.println("Höhendatei: " + hd + " (" + h.anzahl() + " Punkte)");
                }
                if (exportAsc != null) {
                    java.nio.file.Files.write(exportAsc,
                            Hoehendatei.ascText(h.eintraege(), e.nachkommastellen).getBytes(Projekt.ANSI));
                    System.out.println(h.anzahl() + " Punkte nach " + exportAsc + " exportiert.");
                }
                return 0;
            }
            if (rohdaten != null) {
                if (!Projekt.istStapeldatei(messdatei)) {
                    throw new Exception("Mit -u muss als Projekt eine Stapeldatei (.nig) angegeben werden.");
                }
                int n = Projekt.uebernehmeRohdaten(rohdaten, messdatei, e);
                System.out.println(n + " Linie(n) aus " + rohdaten.getFileName() + " in " + messdatei.getFileName()
                        + " übernommen.");
                return 0;
            }
            if (pruefen) {
                System.out.println(Pruefung.pruefe(Einleser.liesMessdatei(messdatei)));
                return 0;
            }
            Ergebnis r = fuehreAus(messdatei, e);
            System.out.println(r.protokoll);
            System.out.println("Protokoll  : " + r.protokollDatei);
            System.out.println("Höhendatei: " + r.hoehenDatei);
            return 0;
        } catch (Exception ex) {
            System.err.println("Fehler: " + ex.getMessage());
            return 2;
        }
    }

    /** Sucht die Mess-/Stapeldatei in den Argumenten (erstes Argument, das kein Optionswert ist). */
    static Path projektArgument(String[] args) {
        java.util.Set<String> mitWert = java.util.Set.of("-f", "-hd", "-v", "-k", "-m", "-n", "-ha", "-he", "-u",
                "-von", "-bis", "-mdb", "-asc", "-export");
        for (int i = 0; i < args.length; i++) {
            if (mitWert.contains(args[i])) {
                i++;
            } else if (!args[i].startsWith("-")) {
                return Path.of(args[i]);
            }
        }
        return null;
    }

    /** Höhendatei: angegebene Datei oder "<Projekt>_Hoehen.csv" im Ordner der Messdatei. */
    static Path hoehendateiPfad(Path messdatei, Einstellungen e) {
        if (e.hoehenDatei != null && !e.hoehenDatei.isBlank()) {
            return Path.of(e.hoehenDatei);
        }
        return Projekt.hoehendatei(messdatei);
    }

    /** Gemeinsamer Ablauf für Konsole und GUI. */
    static Ergebnis fuehreAus(Path messdatei, Einstellungen e) throws Exception {
        for (int k = 0; k < 4; k++) {
            String fehler = Formel.pruefe(e.fehlerformeln[k]);
            if (fehler != null) {
                throw new Exception("Fehlerklasse " + (k + 1) + ": " + fehler);
            }
        }
        Path hdPfad = hoehendateiPfad(messdatei, e);
        Hoehendatei hd = Hoehendatei.lies(hdPfad);
        if (e.neupunkteLoeschen) {
            hd.neupunkteLoeschen();
        }
        if (e.festpunktDatei != null && !e.festpunktDatei.isBlank()) {
            Path f = Path.of(e.festpunktDatei);
            if (!Files.exists(f)) {
                throw new Exception("Festpunktdatei nicht gefunden: " + f);
            }
            Map<String, Double> fp = Einleser.liesFestpunkte(f);
            hd.setzeFestpunkte(fp);
        }
        List<Linie> linien = Einleser.liesMessdatei(messdatei);
        if (linien.isEmpty()) {
            throw new Exception("Keine Nivellementmessungen in der Datei gefunden.");
        }
        Auswertung.Gesamt g = Auswertung.werteAus(linien, e, hd);
        Ergebnis r = new Ergebnis();
        r.gesamt = g;
        r.protokoll = Protokoll.erzeuge(g, e, messdatei);
        r.protokollDatei = Protokoll.speichere(messdatei, r.protokoll, !e.alteBerechnungLoeschen);
        hd.schreibe(hdPfad);
        r.hoehenDatei = hdPfad;
        return r;
    }

    static class Ergebnis {
        Auswertung.Gesamt gesamt;
        String protokoll;
        Path protokollDatei;
        Path hoehenDatei;
    }

    static double zahl(String s) {
        return Double.parseDouble(s.trim().replace(',', '.'));
    }

    private static void hilfe() {
        System.out.println("Aufruf: java -jar Nivellement.jar <Messdatei .gsi|.dat|.nig> [Optionen]\n"
                + "  -f <Datei>   Festpunktdatei (je Zeile: Punkt Höhe)\n"
                + "  -hd <Datei>  Höhendatei (Standard: <Projekt>_Hoehen.csv)\n"
                + "  -v z|a       Verteilung streckenproportional (z) oder nach Anzahl der Blicke (a)\n"
                + "  -k 1..4      Fehlerklasse (Formeln aus der Programmkonfiguration)\n"
                + "  -m 0..3      Mittelbildung 0=mitteln 1=neu 2=vergleichen 3=ohne\n"
                + "  -n 2..5      Dezimalstellen der Höhen\n"
                + "  -ha <m>      Anfangshöhe manuell (erste Linie)\n"
                + "  -he <m>      Endhöhe manuell (erste Linie)\n"
                + "  -neu         Neupunkte vorher aus der Höhendatei löschen\n"
                + "  -x           erweitertes Protokoll (dh, Verbesserung, Probe)\n"
                + "  -von <n> -bis <m>  nur Berechnungsnummern n bis m berechnen\n"
                + "  -a           Protokoll anhängen statt alte Berechnung löschen\n"
                + "  -p           Messdaten nur prüfen\n"
                + "  -u <Roh>     GSI/DAT umformatieren und an die Stapeldatei (.nig) anhängen\n"
                + "  -mdb <Datei> NIGRA-Höhendatei (.MDB) in die Höhendatei des Projekts übernehmen\n"
                + "  -asc <Datei> Höhenverzeichnis (ASCII, NIGRA-Format) übernehmen\n"
                + "  -export <Datei>  Höhenverzeichnis als ASCII (NIGRA-Format) ausgeben\n"
                + "Bei .NIG-Dateien gelten die Parameter aus der Stapeldatei.\n"
                + "Ohne Argumente startet die grafische Oberfläche.");
    }
}
