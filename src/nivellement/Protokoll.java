package nivellement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Erzeugt die Berechnungsausgabe (Protokoll) und das Mittelbildungsprotokoll.
 * Alle Zahlen werden mit Dezimalpunkt ausgegeben.
 */
public final class Protokoll {

    private static final Locale L = Locale.ROOT;
    private static final String STERNE = "***";

    private Protokoll() {
    }

    public static String erzeuge(Auswertung.Gesamt g, Einstellungen e, Path messdatei) {
        StringBuilder sb = new StringBuilder();
        String datum = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String projekt = projektname(messdatei);
        for (LinienErgebnis r : g.linien) {
            kopf(sb, e, datum, projekt);
            linie(sb, r, e);
            sb.append("\n");
        }

        sb.append("Meldung nach Abschluss aller Berechnungen:\n");
        sb.append(String.format(L, "Gesamtsumme aller Strecken (ohne Zwischenblicke) = %12.2f m%n", g.summeStrecken));
        if (!Double.isNaN(g.maxAbschlussfehler)) {
            sb.append(String.format(L, "Max. Abschlussfehler = %10.1f mm      (Berechnung Nr. %d)%n",
                    g.maxAbschlussfehler * 1000, g.maxBerNr));
        }
        int n = g.fehlerAnzahl();
        sb.append("\n");
        if (n > 0) {
            sb.append(String.format(L, "%s %d %s%n", STERNE, n, n == 1 ? "Fehler ist aufgetreten" : "Fehler sind aufgetreten"));
        } else {
            sb.append("Es sind keine Fehler aufgetreten.\n");
        }

        if (!g.hoehen.protokoll.isEmpty()) {
            sb.append("\n");
            kopf(sb, e, datum, projekt);
            sb.append("\n").append(" ".repeat(22)).append("Mittelbildungsprotokoll\n\n");
            sb.append(String.format(L, "Fehlergrenze für mehrfach bestimmte Punkte = %s mm%n", zahl(e.mehrfachGrenzeMm)));
            sb.append(STERNE).append(" = Fehlergrenze überschritten, Punkt wird bei '0 - mitteln' trotzdem gemittelt\n\n");
            sb.append(String.format(L, "%15s %18s %18s   %s%n", "Punkt-Nr.", "Dif.Höhe(mm)", "Berechnungsnr.",
                    "Mittelbildung"));
            for (Hoehendatei.Mittelbildungseintrag m : g.hoehen.protokoll) {
                sb.append(String.format(L, "%15s %15.1f%-3s %18d   %s%n", m.punkt, Math.round(m.diffMm * 10) / 10.0 + 0.0,
                        m.ueberschritten ? STERNE : "", m.berNr,
                        m.text != null ? m.text : LinienParameter.mittelbildungKurz(m.modus)));
            }
        }
        sb.append("\n").append(Version.titel()).append("\n");
        return sb.toString().replaceAll("(?m)[ \\t]+$", "");
    }

    private static void kopf(StringBuilder sb, Einstellungen e, String datum, String projekt) {
        if (e.firma != null && !e.firma.isBlank()) {
            sb.append(e.firma).append("\n");
        }
        sb.append(String.format(L, "%-60s%s%n", Version.titel(), datum));
        sb.append("Projekt: ").append(projekt).append("\n\n");
    }

    private static void linie(StringBuilder sb, LinienErgebnis r, Einstellungen e) {
        LinienParameter p = r.parameter;
        Kopfdaten k = r.kopf;
        if (r.titel != null && !r.titel.isBlank()) {
            sb.append(r.titel).append("\n");
        }
        sb.append("Berechnungs-Nr.: ").append(r.berNr);
        if (r.name != null && !r.name.startsWith("Berechnungs-Nr.")) {
            sb.append("   (").append(r.name).append(")");
        }
        sb.append("\n");
        zeile1(sb, "Ort", k.ort);
        zeile1(sb, "Auftrag", k.auftrag);
        zeile2(sb, "Linie/Zug", k.linieZug, "Datum", k.datum);
        zeile2(sb, "Wetter", k.wetter, "Beobachter", k.beobachter);
        zeile2(sb, "Nivellier", k.nivellier, "Latte", k.latte);
        String teilung = p.lattenteilung == 0.5 ? "1/2 cm" : "1 cm";
        zeile2(sb, "Lattenteilung", teilung, "Beobacht.folge",
                p.beobachtungsfolge.isEmpty() ? "RV(Z)" : p.beobachtungsfolge);
        if (p.teilungen == 2 || p.lattenkonstante != 0) {
            zeile2(sb, "Lattenkonstante", zahl(p.lattenkonstante), "Erlaubte Diff.",
                    zahl(p.erlaubteDifferenz) + " [" + zahl(p.erlaubteDifferenz / 10) + " mm]");
        }
        zeile1(sb, "Bemerkung", k.bemerkung);
        sb.append("Mittelbildung: ").append(LinienParameter.mittelbildungText(p.mittelbildung)).append("\n");

        if (r.abgebrochen) {
            sb.append("\n");
            for (String f : r.fehler) {
                sb.append(STERNE).append(" ").append(f).append("\n");
            }
            hinweise(sb, r);
            return;
        }

        int nkH = p.nkHoehe;
        int nkA = p.nkAblesung;
        sb.append(String.format(L, "Anschluss: %s = %s (%s)", r.anfangspunkt, rund(r.anfangshoehe, nkH),
                r.herkunftAnfang));
        if (!Double.isNaN(r.endhoehe) && r.linienart != LinienErgebnis.Linienart.SCHLEIFE) {
            sb.append(String.format(L, "  ->  %s = %s (%s)", r.endpunkt, rund(r.endhoehe, nkH), r.herkunftEnde));
        }
        sb.append("\nLinienart: ").append(r.linienart.text).append("\n\n");

        if (!Double.isNaN(r.abschlussfehler)) {
            sb.append(String.format(L, "Abschlussfehler = %12.1f mm", r.abschlussfehler * 1000));
            if (!Double.isNaN(r.erlaubtMm)) {
                sb.append(String.format(L, "     Erlaubter Fehler F (%d) = %6.1f mm%s", p.fehlerklasse, r.erlaubtMm,
                        Boolean.FALSE.equals(r.zulaessig) ? " " + STERNE : ""));
            }
            sb.append("\nVerteilung: ").append(r.streckenproportional ? "streckenproportional"
                    : "nach Anzahl der Rück- und Vorblicke").append("\n\n");
        }

        boolean erw = e.erweitertesProtokoll;
        String fmt = "%7s %10s %-9s %10s %-9s %10s %-9s %" + (nkH + 7) + "s %15s%-2s";
        sb.append(String.format(L, fmt, "Strecke", "Rück", "d", "Zwischen", "d", "Vor", "d", "Höhe",
                "Punkt-Nr.", ""));
        if (erw) {
            sb.append(String.format(L, " %10s %7s", "dh", "v[mm]"));
        }
        sb.append("\n");
        for (LinienErgebnis.Zeile z : r.zeilen) {
            String zl = String.format(L, fmt, f(z.strecke, 2), f(z.rueck, nkA), z.dR, f(z.zwischen, nkA), z.dZ,
                    f(z.vor, nkA), z.dV, f(z.hoehe, nkH), z.punkt, z.kennung);
            if (erw && (!Double.isNaN(z.dh) || !Double.isNaN(z.verbesserung))) {
                zl += String.format(L, " %10s %7s", f(z.dh, nkA),
                        Double.isNaN(z.verbesserung) ? "" : String.format(L, "%.2f", z.verbesserung * 1000));
            }
            sb.append(zl.replaceAll("\\s+$", "")).append("\n");
        }
        sb.append("\n");
        sb.append(String.format(L, "Summe S-Gesamt     = %12.2f m%n", r.summeStrecken));
        sb.append(String.format(L, "Summe S-Rückblicke = %12.2f m%n", r.summeStreckenR));
        sb.append(String.format(L, "Summe S-Vorblicke  = %12.2f m%n", r.summeStreckenV));
        if (erw && !Double.isNaN(r.abschlussfehler)) {
            double probe = (r.summeR - r.summeV) * p.lattenteilung + r.summeVerbesserungen
                    - (r.endhoehe - r.anfangshoehe);
            sb.append(String.format(L, "Probe (ΣR-ΣV)+Σv-(H_E-H_A) = %.3f mm  %s%n", probe * 1000,
                    Math.abs(probe) < 1e-7 ? "in Ordnung" : STERNE + " FEHLER"));
        }
        if (!r.fehler.isEmpty()) {
            sb.append("\n");
            for (String f : r.fehler) {
                sb.append(STERNE).append(" ").append(f).append("\n");
            }
        }
        hinweise(sb, r);
    }

    private static void hinweise(StringBuilder sb, LinienErgebnis r) {
        if (!r.hinweise.isEmpty()) {
            sb.append("\nHinweise:\n");
            for (String h : r.hinweise) {
                sb.append("  - ").append(h).append("\n");
            }
        }
    }

    private static void zeile1(StringBuilder sb, String a, String w) {
        sb.append(String.format(L, "%-17s%s%n", a, w == null ? "" : w));
    }

    private static void zeile2(StringBuilder sb, String a, String w1, String b, String w2) {
        sb.append(String.format(L, "%-17s%-20s%-17s%s%n", a, w1 == null ? "" : w1, b, w2 == null ? "" : w2));
    }

    public static Path speichere(Path messdatei, String protokoll, boolean anhaengen) throws IOException {
        Path prot = Projekt.protokoll(messdatei);
        if (anhaengen && Files.exists(prot)) {
            Files.writeString(prot, "\n" + "=".repeat(100) + "\n\n" + protokoll, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
        } else {
            Files.writeString(prot, protokoll, StandardCharsets.UTF_8);
        }
        return prot;
    }

    static String projektname(Path messdatei) {
        String basis = messdatei.getFileName().toString();
        int p = basis.lastIndexOf('.');
        return p > 0 ? basis.substring(0, p) : basis;
    }

    private static String zahl(double w) {
        if (w == Math.rint(w)) {
            return String.valueOf((long) w);
        }
        return String.valueOf(w);
    }

    private static String f(double w, int nk) {
        if (Double.isNaN(w)) {
            return "";
        }
        return rund(w, nk);
    }

    /**
     * Vermessungstechnisches Runden: genau in der Mitte liegende Werte werden auf die gerade
     * Ziffer gerundet (109.0625 -> 109.062). Grundlage ist die kürzeste Dezimaldarstellung des Werts.
     */
    public static String rund(double w, int nk) {
        return java.math.BigDecimal.valueOf(w).setScale(nk, java.math.RoundingMode.HALF_EVEN).toPlainString();
    }
}
