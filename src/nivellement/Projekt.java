package nivellement;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ein Projekt besteht aus der Stapeldatei {@code <Projekt>.NIG}, der Höhendatei
 * {@code <Projekt>_Hoehen.csv} und dem Protokoll {@code <Projekt>_Protokoll.txt} im selben Ordner.
 */
public final class Projekt {

    /** Stapeldateien werden wie bei NIGRA in ANSI (Windows-1252) geschrieben. */
    public static final Charset ANSI = Charset.forName("windows-1252");

    private Projekt() {
    }

    public static String name(Path datei) {
        String basis = datei.getFileName().toString();
        int p = basis.lastIndexOf('.');
        return p > 0 ? basis.substring(0, p) : basis;
    }

    public static Path ordner(Path datei) {
        return datei.toAbsolutePath().getParent();
    }

    public static Path hoehendatei(Path datei) {
        return ordner(datei).resolve(name(datei) + "_Hoehen.csv");
    }

    public static Path protokoll(Path datei) {
        return ordner(datei).resolve(name(datei) + "_Protokoll.txt");
    }

    public static boolean istStapeldatei(Path datei) {
        return datei.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nig");
    }

    /** Nächste freie Berechnungsnummer einer Stapeldatei (höchste B-Nummer + 1). */
    public static int naechsteBerechnungsnummer(Path nig) throws IOException {
        int max = 0;
        if (Files.exists(nig)) {
            for (String z : Einleser.zeilen(nig)) {
                if (z.length() > 1 && Character.toUpperCase(z.charAt(0)) == 'B') {
                    try {
                        max = Math.max(max, Integer.parseInt(z.substring(1).trim()));
                    } catch (NumberFormatException ex) {
                        // keine Berechnungsnummer
                    }
                }
            }
        }
        return max + 1;
    }

    /**
     * Formatiert Rohdaten (GSI/DAT) um und hängt sie an die Stapeldatei an.
     *
     * @return Anzahl der übernommenen Linien
     */
    public static int uebernehmeRohdaten(Path roh, Path nig, Einstellungen e) throws IOException {
        List<String> z = Einleser.zeilen(roh);
        Einleser.Format f = Einleser.erkenneFormat(z);
        if (f == null || f == Einleser.Format.NIGRA_NIG) {
            throw new IOException("Rohdaten müssen im Format Leica GSI oder Trimble DAT vorliegen: " + roh);
        }
        List<Linie> linien = f == Einleser.Format.LEICA_GSI ? GsiLeser.lies(z) : TrimbleDatLeser.lies(z);
        if (linien.isEmpty()) {
            throw new IOException("Keine Nivellementmessungen in " + roh.getFileName() + " gefunden.");
        }
        int berNr = naechsteBerechnungsnummer(nig);
        List<String> neu = NigSchreiber.schreibe(linien, e, berNr);
        List<String> gesamt = new ArrayList<>();
        if (Files.exists(nig)) {
            gesamt.addAll(Einleser.zeilen(nig));
            while (!gesamt.isEmpty() && gesamt.get(gesamt.size() - 1).isBlank()) {
                gesamt.remove(gesamt.size() - 1);
            }
        }
        gesamt.addAll(neu);
        schreibeStapeldatei(nig, String.join("\r\n", gesamt) + "\r\n");
        return linien.size();
    }

    public static void schreibeStapeldatei(Path nig, String text) throws IOException {
        String t = text.replace("\r\n", "\n").replace("\n", "\r\n");
        Files.write(nig, t.getBytes(ANSI));
    }
}
