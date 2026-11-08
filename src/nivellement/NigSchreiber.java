package nivellement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Umformatierung von Rohdaten (GSI/DAT) in eine Stapeldatei (.NIG).
 *
 * Spaltenaufbau der Messdatensätze:
 * <pre>
 * Sp. 1      Kennung (D = Mittelbildung wie Linie, 0-3 punktspezifisch)
 * Sp. 2-9    Strecke
 * Sp. 11-20  Rückblick  r...
 * Sp. 21-30  Zwischenblick z...
 * Sp. 31-39  Vorblick v...
 * Sp. 40-60  Punktnummer (rechtsbündig)
 * </pre>
 * Eine Anfangshöhe aus der Messdatei wird als Kommentarzeile {@code xH <Punkt> <Höhe>} abgelegt
 * (wird von anderen Programmen als Kommentar ignoriert).
 */
public final class NigSchreiber {

    public static final String LINEAL1 = "x23456789012345678901234567890123456789012345678901234567890123456789012";
    public static final String LINEAL2 = "x        1         2         3         4         5         6         7  ";
    public static final String LINEAL3 = "x Strecke<--- Rück     Mitte     Vor ---><--- Punktnummer --->";

    private NigSchreiber() {
    }

    /** Erzeugt die Stapeldatei-Zeilen für alle Linien. Berechnungsnummern ab {@code berNr}. */
    public static List<String> schreibe(List<Linie> linien, Einstellungen e, int berNr) {
        List<String> z = new ArrayList<>();
        for (Linie l : linien) {
            schreibeLinie(z, l, e, berNr++);
        }
        return z;
    }

    private static void schreibeLinie(List<String> z, Linie l, Einstellungen e, int berNr) {
        Kopfdaten k = l.kopf != null ? l.kopf : e.kopf;
        LinienParameter p = l.parameter != null ? l.parameter : LinienParameter.aus(e);
        boolean doppelt = hatDoppelablesungen(l);
        String titel = l.titel != null ? l.titel : e.projekttitel;
        z.add("C" + (titel == null || titel.isBlank() ? l.name : titel));
        z.add(LINEAL1);
        z.add(LINEAL2);
        z.add(LINEAL3);
        z.add("B" + berNr);
        zweiZeilen(z, k.ort, "Ort");
        zweiZeilen(z, k.auftrag, "Auftrag");
        z.add(kZeile(k.linieZug.isBlank() ? l.name : k.linieZug, "Linie/Zug"));
        z.add(kZeile(k.datum, "Datum"));
        z.add(kZeile(k.wetter, "Wetter"));
        z.add(kZeile(k.beobachter, "Beobachter"));
        z.add(kZeile(k.nivellier, "Nivellier"));
        z.add(kZeile(k.latte, "Latte"));
        zweiZeilen(z, k.bemerkung, "Bemerkung");
        z.add(kZeile(doppelt ? "02" : "00", "2.Spalte:0=Zwbl.,1=Strecke,4=Linie, 5=Instr.-Prüfung"));
        z.add("*                    3.Spalte:0=RV,RRVV,2=RVVR,4=RVRV,5=VRRV,4.Sp.:a=altern.");
        z.add(kZeile(doppelt ? "2" : "1", "Anzahl der Lattenteilungen oder Ablesungen"));
        z.add(kZeile("0", "Lattenkonstante bei 2 Lattenteilungen"));
        z.add(kZeile(zahl(p.erlaubteDifferenz), "erlaubte Differenz bei 2 Ablesungen"));
        z.add(kZeile("1", "Lattenteilung 1=cm,foot,inch, 0.5=1/2cm"));
        z.add(kZeile(p.mitStrecken ? "1" : "0", "Berechnung mit Strecken 1=ja, 0=nein"));
        z.add(kZeile(String.valueOf(p.nkHoehe), "Dezimalstellen Höhen bei Berechnungen"));
        z.add(kZeile(String.valueOf(p.nkAblesung), "Dezimalstellen Lattenablesung bei Berechnungen"));
        z.add(String.format(Locale.ROOT, "%-21s%s", "E" + p.mittelbildung + p.fehlerklasse + "m",
                "E/Mittelbildung/Fehlerklasse/Maßeinheit"));
        if (l.startHoeheDatei != null && l.startPunktDatei != null) {
            z.add(String.format(Locale.ROOT, "xH %s %.5f", l.startPunktDatei, l.startHoeheDatei));
        }
        for (Messung m : l.messungen) {
            z.add(messsatz(m, p.nkAblesung));
        }
        z.add("E");
    }

    /** Ein Messdatensatz in fester Spaltenaufteilung. */
    public static String messsatz(Messung m, int nk) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.mittelbildung >= 0 ? (char) ('0' + m.mittelbildung) : 'D');
        sb.append(Double.isNaN(m.zielweite) ? " ".repeat(8) : String.format(Locale.ROOT, "%8s", Protokoll.rund(m.zielweite, 2)));
        sb.append(' ');
        String a = Protokoll.rund(m.ablesung, Math.max(nk, 4));
        String r = "", zz = "", v = "";
        switch (m.art) {
            case RUECKBLICK:
                r = "r" + a;
                break;
            case ZWISCHENBLICK:
                zz = "z" + a;
                break;
            default:
                v = "v" + a;
        }
        sb.append(String.format(Locale.ROOT, "%-10s%-10s%-9s", r, zz, v));
        sb.append(String.format(Locale.ROOT, "%21s", m.punkt));
        return sb.toString();
    }

    static boolean hatDoppelablesungen(Linie l) {
        Messung vor = null;
        for (Messung m : l.messungen) {
            if (vor != null && m.art == Messung.Art.RUECKBLICK && vor.art == Messung.Art.RUECKBLICK
                    && m.punkt.equals(vor.punkt) || vor != null && m.art == Messung.Art.VORBLICK
                            && vor.art == Messung.Art.VORBLICK && m.punkt.equals(vor.punkt)) {
                return true;
            }
            vor = m;
        }
        return false;
    }

    private static String kZeile(String wert, String text) {
        String w = wert == null ? "" : wert;
        if (w.length() > 19) {
            w = w.substring(0, 19);
        }
        return String.format(Locale.ROOT, "K%-20s%s", w, text);
    }

    private static void zweiZeilen(List<String> z, String wert, String text) {
        String w = wert == null ? "" : wert;
        String a = w.length() > 19 ? w.substring(0, 19) : w;
        String b = w.length() > 19 ? w.substring(19, Math.min(38, w.length())) : "";
        z.add(String.format(Locale.ROOT, "K%-19s %s", a, text));
        z.add(String.format(Locale.ROOT, "K%-19s %s", b, text));
    }

    private static String zahl(double w) {
        return w == Math.rint(w) ? String.valueOf((long) w) : String.valueOf(w);
    }
}
