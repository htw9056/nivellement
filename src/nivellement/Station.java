package nivellement;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Instrumentenaufstellung: Rückblick, Vorblick und beliebig viele Zwischenblicke.
 * Mehrfachablesungen (2 Lattenteilungen, RRVV, RVVR) werden gemittelt.
 */
public class Station {

    /** Ein Zwischenblick (ggf. mit 2 Ablesungen). */
    public static class Zwischenblick {
        public String punkt;
        public final List<Double> ablesungen = new ArrayList<>();
        public double weite = Double.NaN;
        public int mittelbildung = -1;

        public double ablesung() {
            return mittel(ablesungen);
        }
    }

    public String rueckPunkt;
    public final List<Double> rueckAblesungen = new ArrayList<>();
    public final List<Double> rueckWeiten = new ArrayList<>();
    public int rueckMittelbildung = -1;

    public String vorPunkt;
    public final List<Double> vorAblesungen = new ArrayList<>();
    public final List<Double> vorWeiten = new ArrayList<>();
    public int vorMittelbildung = -1;

    public final List<Zwischenblick> zwischenblicke = new ArrayList<>();

    public boolean hatVorblick() {
        return !vorAblesungen.isEmpty();
    }

    public double rueckblick() {
        return mittel(rueckAblesungen);
    }

    public double vorblick() {
        return mittel(vorAblesungen);
    }

    public double rueckWeite() {
        return mittel(rueckWeiten);
    }

    public double vorWeite() {
        return mittel(vorWeiten);
    }

    /** Höhenunterschied in m (Lattenteilung berücksichtigt). */
    public double hoehenunterschied(double lattenteilung) {
        return (rueckblick() - vorblick()) * lattenteilung;
    }

    /** Summe der Zielweiten (R + V) in m, NaN wenn eine fehlt. */
    public double zielweitenSumme() {
        return rueckWeite() + vorWeite();
    }

    static void addWeite(List<Double> liste, double w) {
        if (!Double.isNaN(w)) {
            liste.add(w);
        }
    }

    static double mittel(List<Double> werte) {
        if (werte.isEmpty()) {
            return Double.NaN;
        }
        double s = 0;
        for (double w : werte) {
            s += w;
        }
        return s / werte.size();
    }

    /**
     * Differenz zweier Ablesungen (1. - 2. - Lattenkonstante) in 0,1 mm, NaN bei weniger als 2 Ablesungen.
     */
    public static double differenz(List<Double> abl, double lk) {
        if (abl.size() < 2) {
            return Double.NaN;
        }
        double a1 = abl.get(0);
        double a2 = abl.get(1);
        double k = Math.abs(a1 - a2) > Math.abs(lk) / 2 && lk != 0 ? lk : 0;
        if (a1 < a2 && k != 0) {
            k = -k;
        }
        return Math.round((a1 - a2 - k) * 10000.0);
    }
}
