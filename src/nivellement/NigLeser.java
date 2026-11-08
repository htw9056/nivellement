package nivellement;

import java.util.ArrayList;
import java.util.List;

/**
 * Liest Stapeldateien im NIGRA-Format (*.NIG), damit vorhandene Projekte weiter ausgewertet
 * werden können.
 *
 * Aufbau je Nivellement: Steuerdatenblock (C, B, K-Zeilen, E) und Messdatenblock (D/0/1/2/3, E).
 * <ul>
 * <li>C = Kommentar/Titel, x und andere Zeichen = ignoriert</li>
 * <li>B = Berechnungsnummer</li>
 * <li>K = 12 Kopfzeilen (Ort 2x, Auftrag 2x, Linie, Datum, Wetter, Beobachter, Nivellier, Latte,
 * Bemerkung 2x) und 8 Parameterzeilen (Nivellementart, Teilungen, Lattenkonstante, erlaubte Differenz,
 * Lattenteilung, mit Strecken, Dezimalstellen Höhen, Dezimalstellen Ablesungen)</li>
 * <li>E im Steuerblock: 2. Spalte Mittelbildung, 3. Spalte Fehlerklasse, 4. Spalte Maßeinheit</li>
 * <li>Messdaten: Strecke (Sp. 2-9), Ablesungen mit r/z/v (Sp. 10-39), Punktnummer (Sp. 40-60);
 * Kennung D = Mittelbildung wie Linie, 0-3 = punktspezifisch</li>
 * </ul>
 */
public final class NigLeser {

    private NigLeser() {
    }

    public static List<Linie> lies(List<String> zeilen) {
        List<Linie> linien = new ArrayList<>();
        String titel = null;
        Integer berNr = null;
        List<String> kZeilen = new ArrayList<>();
        Linie linie = null;
        boolean messblock = false;
        int nr = 0;
        for (String roh : zeilen) {
            nr++;
            if (roh.isEmpty()) {
                continue;
            }
            char k = Character.toUpperCase(roh.charAt(0));
            if (!messblock) {
                switch (k) {
                    case 'C':
                        if (titel == null || linie == null) {
                            titel = roh.substring(1).trim();
                        }
                        break;
                    case 'B':
                        try {
                            berNr = Integer.parseInt(roh.substring(1).trim());
                        } catch (NumberFormatException ex) {
                            berNr = null;
                        }
                        break;
                    case 'K':
                        kZeilen.add(roh);
                        break;
                    case 'E':
                        linie = baueLinie(kZeilen, roh, titel, berNr, nr);
                        kZeilen.clear();
                        berNr = null;
                        messblock = true;
                        break;
                    default:
                        break;
                }
            } else {
                if (k == 'E') {
                    if (!linie.istLeer() || !linie.hinweise.isEmpty()) {
                        linien.add(linie);
                    }
                    linie = null;
                    titel = null;
                    messblock = false;
                } else if (roh.startsWith("xH ")) {
                    String[] t = roh.trim().split("\\s+");
                    if (t.length >= 3) {
                        try {
                            linie.startHoeheDatei = Double.parseDouble(t[2].replace(',', '.'));
                            linie.startPunktDatei = t[1];
                        } catch (NumberFormatException ex) {
                            linie.hinweise.add("Zeile " + nr + ": ung\u00fcltige Anfangsh\u00f6he.");
                        }
                    }
                } else if (k == 'D' || (k >= '0' && k <= '3')) {
                    messsatz(linie, roh, k == 'D' ? -1 : k - '0', nr);
                } else if (roh.trim().equalsIgnoreCase("split")) {
                    linie.hinweise.add("Zeile " + nr + ": SPLIT wird nicht unterstützt und ignoriert.");
                }
            }
        }
        if (linie != null && !linie.istLeer()) {
            linie.hinweise.add("Messdatenblock ohne abschließendes E.");
            linien.add(linie);
        }
        return linien;
    }

    private static Linie baueLinie(List<String> k, String eZeile, String titel, Integer berNr, int nr) {
        Linie l = new Linie(berNr != null ? "Berechnungs-Nr. " + berNr : "Nivellement");
        if (k.size() != 20) {
            l.hinweise.add("Zeile " + nr + ": Steuerdatenblock hat " + k.size()
                    + " K-Zeilen (erwartet 12 Kopf- und 8 Parameterzeilen), fehlende Werte = Standard.");
        }
        l.berechnungsnummer = berNr;
        l.titel = titel;
        Kopfdaten kd = new Kopfdaten();
        kd.ort = teil(k, 0, true) + teil(k, 1, false);
        kd.auftrag = teil(k, 2, true) + teil(k, 3, false);
        kd.linieZug = teil(k, 4, false).trim();
        kd.datum = teil(k, 5, false).trim();
        kd.wetter = teil(k, 6, false).trim();
        kd.beobachter = teil(k, 7, false).trim();
        kd.nivellier = teil(k, 8, false).trim();
        kd.latte = teil(k, 9, false).trim();
        kd.bemerkung = teil(k, 10, true) + teil(k, 11, false);
        kd.ort = kd.ort.trim();
        kd.auftrag = kd.auftrag.trim();
        kd.bemerkung = kd.bemerkung.trim();
        l.kopf = kd;

        LinienParameter p = new LinienParameter();
        p.nkHoehe = 3;
        p.nkAblesung = 4;
        String art = wert(k, 12, "00");
        char a0 = art.isEmpty() ? '0' : art.charAt(0);
        char a1 = art.length() > 1 ? art.charAt(1) : '0';
        boolean alternierend = art.toLowerCase().contains("a");
        switch (a0) {
            case '1':
                l.art = Linie.Art.STRECKE;
                break;
            case '4':
                l.art = Linie.Art.LINIENAUSGLEICHUNG;
                break;
            case '5':
                l.art = Linie.Art.INSTRUMENTENPRUEFUNG;
                break;
            default:
                l.art = Linie.Art.ZWISCHENBLICKE;
        }
        p.teilungen = (int) zahl(wert(k, 13, "1"), 1);
        p.lattenkonstante = zahl(wert(k, 14, "0"), 0);
        p.erlaubteDifferenz = zahl(wert(k, 15, "0"), 0);
        p.lattenteilung = zahl(wert(k, 16, "1"), 1);
        if (p.lattenteilung <= 0) {
            p.lattenteilung = 1;
        }
        p.mitStrecken = zahl(wert(k, 17, "1"), 1) == 1;
        p.nkHoehe = begrenze((int) zahl(wert(k, 18, "3"), 3), 2, 5, 3);
        p.nkAblesung = begrenze((int) zahl(wert(k, 19, "4"), 4), 2, 5, 4);
        String folge;
        switch (a1) {
            case '2':
                folge = "RVVR";
                break;
            case '4':
                folge = "RVRV";
                break;
            case '5':
                folge = "VRRV";
                break;
            default:
                folge = p.teilungen == 2 ? "RRVV" : "RV";
        }
        if (alternierend) {
            folge = "a" + folge;
        }
        if (l.art == Linie.Art.ZWISCHENBLICKE) {
            folge += p.teilungen == 2 ? "(ZZ)" : "(Z)";
        }
        p.beobachtungsfolge = folge;

        String e = eZeile.trim();
        if (e.length() > 1 && Character.isDigit(e.charAt(1))) {
            p.mittelbildung = begrenze(e.charAt(1) - '0', 0, 3, 1);
        }
        if (e.length() > 2 && Character.isDigit(e.charAt(2))) {
            p.fehlerklasse = begrenze(e.charAt(2) - '0', 1, 4, 3);
        }
        if (e.length() > 3 && Character.toLowerCase(e.charAt(3)) != 'm' && e.charAt(3) != ' ') {
            l.hinweise.add("Zeile " + nr + ": Maßeinheit '" + e.charAt(3)
                    + "' wird noch nicht unterstützt, es wird Meter angenommen.");
        }
        l.parameter = p;
        return l;
    }

    /** Messdatensatz: Strecke, Ablesung(en) mit Kennbuchstaben, Punktnummer. */
    private static void messsatz(Linie l, String z, int mittelbildung, int nr) {
        double strecke = Double.NaN;
        String punkt = "";
        Messung.Art art = null;
        Double ablesung = null;
        int i = 1;
        while (i < z.length()) {
            while (i < z.length() && Character.isWhitespace(z.charAt(i))) {
                i++;
            }
            if (i >= z.length()) {
                break;
            }
            int start = i;
            while (i < z.length() && !Character.isWhitespace(z.charAt(i))) {
                i++;
            }
            String t = z.substring(start, i);
            char c = Character.toLowerCase(t.charAt(0));
            if ((c == 'r' || c == 'z' || c == 'v' || c == 'm') && t.length() > 1 && start < 39) {
                art = c == 'r' ? Messung.Art.RUECKBLICK
                        : c == 'v' ? Messung.Art.VORBLICK : Messung.Art.ZWISCHENBLICK;
                try {
                    ablesung = Double.parseDouble(t.substring(1).replace(',', '.'));
                } catch (NumberFormatException ex) {
                    l.hinweise.add("Zeile " + nr + ": ungültige Ablesung '" + t + "'.");
                    return;
                }
            } else if (start < 9 && art == null && Double.isNaN(strecke)) {
                try {
                    strecke = Double.parseDouble(t.replace(',', '.'));
                } catch (NumberFormatException ex) {
                    l.hinweise.add("Zeile " + nr + ": ungültige Strecke '" + t + "'.");
                    return;
                }
            } else if (start >= 30 && start < 60 && punkt.isEmpty()) {
                // Punktnummer in Sp. 40-60; dahinter können Uhrzeit, Code u. a. folgen
                punkt = t;
            }
        }
        if (art == null) {
            if (l.art == Linie.Art.ZWISCHENBLICKE) {
                l.hinweise.add("Zeile " + nr + ": Messdatensatz ohne Ablesung übersprungen.");
            }
            return;
        }
        if (punkt.isEmpty()) {
            punkt = "?" + nr;
        }
        l.messungen.add(new Messung(art, punkt, ablesung, strecke, nr, mittelbildung));
    }

    private static String teil(List<String> k, int i, boolean ersterTeil) {
        if (i >= k.size()) {
            return "";
        }
        String s = k.get(i);
        String w = s.length() > 1 ? s.substring(1, Math.min(20, s.length())) : "";
        return ersterTeil ? w : w.stripTrailing();
    }

    private static String wert(List<String> k, int i, String standard) {
        if (i >= k.size()) {
            return standard;
        }
        String s = k.get(i).substring(1).trim();
        if (s.isEmpty()) {
            return standard;
        }
        return s.split("\\s+")[0];
    }

    private static double zahl(String s, double standard) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return standard;
        }
    }

    private static int begrenze(int w, int min, int max, int standard) {
        return w < min || w > max ? standard : w;
    }
}
