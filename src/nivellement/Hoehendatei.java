package nivellement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Höhendatei des Projekts (CSV, Semikolon):
 * {@code Punkt;Hoehe;BerNr;Diff_mm;AB;Datum;Bemerkung}.
 * Punkte mit Berechnungsnummer 999999 sind Festpunkte und werden nie verändert.
 */
public class Hoehendatei {

    public static final int FESTPUNKT = 999999;
    private static final String KOPF = "Punkt;Hoehe;BerNr;Diff_mm;AB;Datum;Bemerkung";

    /** Ein Eintrag der Höhendatei. */
    public static class Eintrag {
        public String punkt;
        public double hoehe;
        public int berNr;
        /** Mittelungsdifferenz (aktuelle - vorherige Höhe) in mm. */
        public double diffMm;
        /** Anzahl der Berechnungen. */
        public int ab = 1;
        public String datum = "";
        public String bemerkung = "";

        public boolean istFestpunkt() {
            return berNr == FESTPUNKT;
        }
    }

    /** Ein Eintrag des Mittelbildungsprotokolls. */
    public static class Mittelbildungseintrag {
        public String punkt;
        public double diffMm;
        public int berNr;
        public int modus;
        public boolean ueberschritten;
        public String text;
    }

    private final Map<String, Eintrag> punkte = new LinkedHashMap<>();
    /** Festpunkte aus einer separaten Festpunktdatei (nur lesend, haben Vorrang). */
    private final Map<String, Double> festpunkte = new LinkedHashMap<>();
    public final List<Mittelbildungseintrag> protokoll = new ArrayList<>();
    private final String heute = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

    public static Hoehendatei lies(Path datei) throws IOException {
        Hoehendatei h = new Hoehendatei();
        if (datei == null || !Files.exists(datei)) {
            return h;
        }
        for (String z : Einleser.zeilen(datei)) {
            String t = z.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("Punkt;")) {
                continue;
            }
            String[] f = t.split(";", -1);
            if (f.length < 2) {
                continue;
            }
            Eintrag e = new Eintrag();
            e.punkt = f[0].trim();
            try {
                e.hoehe = Double.parseDouble(f[1].trim().replace(',', '.'));
                e.berNr = f.length > 2 && !f[2].isBlank() ? Integer.parseInt(f[2].trim()) : FESTPUNKT;
                e.diffMm = f.length > 3 && !f[3].isBlank() ? Double.parseDouble(f[3].trim().replace(',', '.')) : 0;
                e.ab = f.length > 4 && !f[4].isBlank() ? Integer.parseInt(f[4].trim()) : 1;
            } catch (NumberFormatException ex) {
                continue;
            }
            e.datum = f.length > 5 ? f[5].trim() : "";
            e.bemerkung = f.length > 6 ? f[6].trim() : "";
            h.punkte.put(e.punkt, e);
        }
        return h;
    }

    public void schreibe(Path datei) throws IOException {
        StringBuilder sb = new StringBuilder(KOPF).append("\n");
        for (Eintrag e : punkte.values()) {
            sb.append(e.punkt).append(';').append(String.format(Locale.ROOT, "%.8f", e.hoehe)).append(';')
                    .append(e.berNr).append(';').append(String.format(Locale.ROOT, "%.3f", Math.round(e.diffMm * 1000) / 1000.0 + 0.0)).append(';')
                    .append(e.ab).append(';').append(e.datum).append(';').append(e.bemerkung).append('\n');
        }
        Files.writeString(datei, sb.toString(), StandardCharsets.UTF_8);
    }

    /** Alle Einträge der Höhendatei (für das Höhenverzeichnis). */
    public List<Eintrag> eintraege() {
        return new ArrayList<>(punkte.values());
    }

    /** Ersetzt den gesamten Inhalt (nach Bearbeitung im Höhenverzeichnis). */
    public void ersetze(List<Eintrag> neu) {
        punkte.clear();
        for (Eintrag e : neu) {
            punkte.put(e.punkt, e);
        }
    }

    /**
     * Liest ein Höhenverzeichnis im ASCII-Format von NIGRA
     * ({@code Punkt Höhe BerNr Diff AB Datum Bemerkung}). Zeilen mit nur Punkt und Höhe werden
     * als Festpunkte (BerNr. 999999) übernommen.
     */
    public static List<Eintrag> liesAsc(Path datei) throws IOException {
        List<Eintrag> liste = new ArrayList<>();
        for (String z : Einleser.zeilen(datei)) {
            String t = z.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String[] f = t.split("[;\\t ]+");
            if (f.length < 2 || !f[1].matches("-?\\d+([.,]\\d+)?")) {
                continue;
            }
            Eintrag e = new Eintrag();
            e.punkt = f[0];
            e.hoehe = Double.parseDouble(f[1].replace(',', '.'));
            e.berNr = FESTPUNKT;
            if (f.length >= 5 && f[2].matches("\\d+") && f[3].matches("-?\\d+([.,]\\d+)?") && f[4].matches("\\d+")) {
                e.berNr = Integer.parseInt(f[2]);
                e.diffMm = Double.parseDouble(f[3].replace(',', '.'));
                e.ab = Integer.parseInt(f[4]);
                int rest = 5;
                if (f.length > 5 && f[5].matches("\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}")) {
                    e.datum = f[5];
                    rest = 6;
                }
                e.bemerkung = String.join(" ", java.util.Arrays.copyOfRange(f, Math.min(rest, f.length), f.length));
            }
            liste.add(e);
        }
        return liste;
    }

    /** Höhenverzeichnis im ASCII-Format von NIGRA (feste Spalten). */
    public static String ascText(List<Eintrag> liste, int nk) {
        StringBuilder sb = new StringBuilder();
        for (Eintrag e : liste) {
            String z = String.format(Locale.ROOT, "     %14s %11s %6d %6s %2d %-10s  %s", e.punkt,
                    Protokoll.rund(e.hoehe, nk), e.berNr, Protokoll.rund(e.diffMm, 1), e.ab, e.datum, e.bemerkung);
            sb.append(z.stripTrailing()).append("\r\n");
        }
        return sb.toString();
    }

    /** Übernimmt Einträge (gleiche Punktnummern werden ersetzt). */
    public int uebernehme(List<Eintrag> liste) {
        for (Eintrag e : liste) {
            punkte.put(e.punkt, e);
        }
        return liste.size();
    }

    public void setzeFestpunkte(Map<String, Double> fp) {
        festpunkte.putAll(fp);
    }

    public int anzahl() {
        return punkte.size();
    }

    /** Löscht alle Neupunkte (BerNr. ungleich 999999). */
    public void neupunkteLoeschen() {
        Iterator<Eintrag> it = punkte.values().iterator();
        while (it.hasNext()) {
            if (!it.next().istFestpunkt()) {
                it.remove();
            }
        }
    }

    /** Sucht eine Anschlusshöhe: zuerst Festpunktdatei, dann Höhendatei. */
    public Double anschluss(String punkt) {
        if (festpunkte.containsKey(punkt)) {
            return festpunkte.get(punkt);
        }
        Eintrag e = punkte.get(punkt);
        return e == null ? null : e.hoehe;
    }

    public String herkunft(String punkt) {
        if (festpunkte.containsKey(punkt)) {
            return "Festpunktdatei";
        }
        Eintrag e = punkte.get(punkt);
        if (e == null) {
            return "";
        }
        return e.istFestpunkt() ? "Höhendatei, Festpunkt" : "Höhendatei, BerNr. " + e.berNr;
    }

    public boolean istFestpunkt(String punkt) {
        Eintrag e = punkte.get(punkt);
        return festpunkte.containsKey(punkt) || (e != null && e.istFestpunkt());
    }

    /**
     * Speichert eine neu berechnete Höhe gemäß Mittelbildung. Festpunkte bleiben unverändert,
     * werden aber verglichen.
     */
    public void speichere(String punkt, double hoehe, int berNr, int modus, double grenzeMm) {
        Double fest = festpunkte.get(punkt);
        Eintrag alt = punkte.get(punkt);
        if (fest != null || (alt != null && alt.istFestpunkt())) {
            double soll = fest != null ? fest : alt.hoehe;
            eintrag(punkt, (hoehe - soll) * 1000, berNr, modus, grenzeMm, "Festpunkt - nur Vergleich");
            return;
        }
        switch (modus) {
            case 0: // mitteln
                if (alt == null) {
                    neu(punkt, hoehe, berNr);
                } else {
                    double diff = (hoehe - alt.hoehe) * 1000;
                    eintrag(punkt, diff, berNr, modus, grenzeMm, null);
                    alt.hoehe = (alt.hoehe * alt.ab + hoehe) / (alt.ab + 1);
                    alt.ab++;
                    alt.diffMm = diff;
                    alt.berNr = berNr;
                    alt.datum = heute;
                }
                break;
            case 1: // neu
                neu(punkt, hoehe, berNr);
                break;
            case 2: // vergleichen
                if (alt == null) {
                    neu(punkt, hoehe, berNr);
                } else {
                    eintrag(punkt, (hoehe - alt.hoehe) * 1000, berNr, modus, grenzeMm, null);
                }
                break;
            default: // ohne
                break;
        }
    }

    private void neu(String punkt, double hoehe, int berNr) {
        Eintrag e = new Eintrag();
        e.punkt = punkt;
        e.hoehe = hoehe;
        e.berNr = berNr;
        e.diffMm = 0;
        e.ab = 1;
        e.datum = heute;
        punkte.put(punkt, e);
    }

    private void eintrag(String punkt, double diffMm, int berNr, int modus, double grenzeMm, String text) {
        Mittelbildungseintrag m = new Mittelbildungseintrag();
        m.punkt = punkt;
        m.diffMm = diffMm;
        m.berNr = berNr;
        m.modus = modus;
        m.ueberschritten = Math.abs(diffMm) > grenzeMm + 1e-9;
        m.text = text;
        protokoll.add(m);
    }
}
