package nivellement;

import java.util.ArrayList;
import java.util.List;

/**
 * Liest Leica-GSI-Dateien (GSI-8 und GSI-16) von Digitalnivellieren
 * (z. B. DNA03, LS10/LS15, Sprinter).
 *
 * Ausgewertete Wortindizes (WI):
 * <ul>
 * <li>11 – Punktnummer</li>
 * <li>32 – Horizontalstrecke (Zielweite)</li>
 * <li>331 / 335 – Rückblick (1. / 2. Ablesung)</li>
 * <li>332 / 336 – Vorblick (1. / 2. Ablesung)</li>
 * <li>333 – Zwischenblick</li>
 * <li>83 – Höhe (nur am Anfangspunkt als Anfangshöhe genutzt)</li>
 * <li>41 – Codeblock: Beginn einer neuen Linie (Name aus WI 42, falls vorhanden)</li>
 * </ul>
 */
public final class GsiLeser {

    private GsiLeser() {
    }

    public static List<Linie> lies(List<String> zeilen) {
        List<Linie> linien = new ArrayList<>();
        Linie linie = new Linie("Linie 1");
        int nr = 0;
        for (String roh : zeilen) {
            nr++;
            String z = roh.trim();
            if (z.isEmpty()) {
                continue;
            }
            if (z.startsWith("*")) {
                z = z.substring(1);
            }
            String punkt = null;
            double weite = Double.NaN;
            Double rueck = null, rueck2 = null, vor = null, vor2 = null, zwischen = null, hoehe = null;
            boolean codeblock = false;
            String codeName = null;

            for (String wort : z.split("\\s+")) {
                if (wort.length() < 8) {
                    continue;
                }
                String wi = wortindex(wort);
                char einheit = wort.charAt(5);
                char vz = wort.charAt(6);
                String daten = wort.substring(7);
                switch (wi) {
                    case "11":
                        punkt = punktnummer(daten);
                        break;
                    case "41":
                        codeblock = true;
                        break;
                    case "42":
                        codeName = punktnummer(daten);
                        break;
                    case "32":
                        weite = zahl(daten, vz, einheit, linie, nr);
                        break;
                    case "331":
                        rueck = zahl(daten, vz, einheit, linie, nr);
                        break;
                    case "335":
                        rueck2 = zahl(daten, vz, einheit, linie, nr);
                        break;
                    case "332":
                        vor = zahl(daten, vz, einheit, linie, nr);
                        break;
                    case "336":
                        vor2 = zahl(daten, vz, einheit, linie, nr);
                        break;
                    case "333":
                        zwischen = zahl(daten, vz, einheit, linie, nr);
                        break;
                    case "83":
                        hoehe = zahl(daten, vz, einheit, linie, nr);
                        break;
                    default:
                        break;
                }
            }

            if (codeblock) {
                if (!linie.istLeer()) {
                    linien.add(linie);
                    linie = new Linie("Linie " + (linien.size() + 1));
                }
                if (codeName != null && !codeName.isEmpty()) {
                    linie.name = codeName;
                }
                continue;
            }
            if (punkt == null) {
                continue;
            }
            if (hoehe != null && linie.istLeer() && rueck == null && rueck2 == null && vor == null) {
                linie.startHoeheDatei = hoehe;
                linie.startPunktDatei = punkt;
            } else if (hoehe != null && linie.istLeer() && (rueck != null || rueck2 != null)) {
                linie.startHoeheDatei = hoehe;
                linie.startPunktDatei = punkt;
            }
            if (rueck != null) {
                linie.messungen.add(new Messung(Messung.Art.RUECKBLICK, punkt, rueck, weite, nr));
            }
            if (rueck2 != null) {
                linie.messungen.add(new Messung(Messung.Art.RUECKBLICK, punkt, rueck2, weite, nr));
            }
            if (vor != null) {
                linie.messungen.add(new Messung(Messung.Art.VORBLICK, punkt, vor, weite, nr));
            }
            if (vor2 != null) {
                linie.messungen.add(new Messung(Messung.Art.VORBLICK, punkt, vor2, weite, nr));
            }
            if (zwischen != null) {
                linie.messungen.add(new Messung(Messung.Art.ZWISCHENBLICK, punkt, zwischen, weite, nr));
            }
        }
        if (!linie.istLeer()) {
            linien.add(linie);
        }
        return linien;
    }

    /** Ermittelt den Wortindex (2- oder 3-stellig). */
    static String wortindex(String wort) {
        String zwei = wort.substring(0, 2);
        if (zwei.equals("11") || zwei.equals("41")) {
            return zwei; // Stellen 3-6 enthalten hier die Blocknummer
        }
        char c = wort.charAt(2);
        if (Character.isDigit(c)) {
            return wort.substring(0, 3);
        }
        return zwei;
    }

    static String punktnummer(String daten) {
        String s = daten.trim();
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') {
            i++;
        }
        return s.substring(i);
    }

    static double zahl(String daten, char vorzeichen, char einheit, Linie linie, int zeile) {
        double faktor;
        switch (einheit) {
            case '0':
                faktor = 0.001;
                break;
            case '6':
                faktor = 0.0001;
                break;
            case '8':
                faktor = 0.00001;
                break;
            case '.':
                faktor = 0.001;
                break;
            default:
                linie.hinweise.add("Zeile " + zeile + ": unbekannte/nicht metrische Einheit '" + einheit
                        + "', es wird mm angenommen.");
                faktor = 0.001;
        }
        long n = Long.parseLong(daten.trim());
        double w = n * faktor;
        return vorzeichen == '-' ? -w : w;
    }
}
