package nivellement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Klassische Nivellementauswertung (keine Ausgleichung):
 * <ol>
 * <li>Aufstellungen aus den Ablesungen bilden (Mehrfachablesungen werden gemittelt)</li>
 * <li>Abschlussfehler w = Soll - Ist = (H_E - H_A) - Σdh</li>
 * <li>w streckenproportional (Zielweiten R+V) oder nach Anzahl der Blicke auf die Aufstellungen
 * verteilen</li>
 * <li>Zwischenpunkte erhalten den Anteil der Verbesserung ihrer Aufstellung bis zum Instrument:
 * v_i * s_R / (s_R + s_V) bzw. v_i / 2</li>
 * </ol>
 */
public final class Auswertung {

    /** Gesamtergebnis einer Stapelberechnung. */
    public static class Gesamt {
        public final List<LinienErgebnis> linien = new ArrayList<>();
        public double summeStrecken;
        public double maxAbschlussfehler = Double.NaN;
        public int maxBerNr;
        public Hoehendatei hoehen;

        public int fehlerAnzahl() {
            int n = 0;
            for (LinienErgebnis l : linien) {
                n += l.fehlerAnzahl();
            }
            return n;
        }
    }

    private Auswertung() {
    }

    public static Gesamt werteAus(List<Linie> linien, Einstellungen e, Hoehendatei hd) {
        Gesamt g = new Gesamt();
        g.hoehen = hd;
        int berNr = e.berechnungsnummerStart;
        boolean erste = true;
        for (Linie l : linien) {
            if (l.berechnungsnummer != null) {
                berNr = l.berechnungsnummer;
            }
            if (e.berNrVon != null && berNr < e.berNrVon || e.berNrBis != null && berNr > e.berNrBis) {
                berNr++;
                continue;
            }
            LinienErgebnis r = werteLinieAus(l, e, hd, erste, berNr);
            erste = false;
            berNr++;
            g.linien.add(r);
            g.summeStrecken += r.summeStrecken;
            if (!Double.isNaN(r.abschlussfehler)
                    && (Double.isNaN(g.maxAbschlussfehler) || Math.abs(r.abschlussfehler) > Math.abs(g.maxAbschlussfehler))) {
                g.maxAbschlussfehler = r.abschlussfehler;
                g.maxBerNr = r.berNr;
            }
        }
        return g;
    }

    /** Bildet die Aufstellungen aus der Folge der Ablesungen. */
    static List<Station> bildeStationen(Linie l, boolean zweiAblesungen, List<String> hinweise) {
        List<Station> st = new ArrayList<>();
        Station akt = null;
        Messung vorige = null;
        for (Messung m : l.messungen) {
            switch (m.art) {
                case RUECKBLICK:
                    if (akt != null && m.punkt.equals(akt.rueckPunkt)
                            && (akt.hatVorblick() || akt.zwischenblicke.isEmpty())) {
                        // weitere Ablesung zum selben Rückblick (RRVV, RVVR, 2 Teilungen)
                        akt.rueckAblesungen.add(m.ablesung);
                        Station.addWeite(akt.rueckWeiten, m.zielweite);
                        break;
                    }
                    if (akt != null && !akt.hatVorblick()) {
                        hinweise.add("Zeile " + m.zeile + ": Aufstellung mit Rückblick auf " + akt.rueckPunkt
                                + " ohne Vorblick verworfen.");
                        st.remove(akt);
                    }
                    akt = new Station();
                    akt.rueckPunkt = m.punkt;
                    akt.rueckMittelbildung = m.mittelbildung;
                    akt.rueckAblesungen.add(m.ablesung);
                    Station.addWeite(akt.rueckWeiten, m.zielweite);
                    st.add(akt);
                    break;
                case VORBLICK:
                    if (akt == null) {
                        hinweise.add("Zeile " + m.zeile + ": Vorblick auf " + m.punkt
                                + " ohne vorherigen Rückblick übersprungen.");
                        break;
                    }
                    if (akt.vorPunkt != null && !akt.vorPunkt.equals(m.punkt)) {
                        hinweise.add("Zeile " + m.zeile + ": zweiter Vorblick auf anderen Punkt (" + m.punkt
                                + " statt " + akt.vorPunkt + ") übersprungen.");
                        break;
                    }
                    akt.vorPunkt = m.punkt;
                    if (m.mittelbildung >= 0) {
                        akt.vorMittelbildung = m.mittelbildung;
                    }
                    akt.vorAblesungen.add(m.ablesung);
                    Station.addWeite(akt.vorWeiten, m.zielweite);
                    break;
                case ZWISCHENBLICK:
                    if (akt == null) {
                        hinweise.add("Zeile " + m.zeile + ": Zwischenblick auf " + m.punkt
                                + " ohne Rückblick übersprungen.");
                        break;
                    }
                    Station.Zwischenblick zb = akt.zwischenblicke.isEmpty() ? null
                            : akt.zwischenblicke.get(akt.zwischenblicke.size() - 1);
                    if (zweiAblesungen && zb != null && zb.punkt.equals(m.punkt) && zb.ablesungen.size() == 1
                            && vorige != null && vorige.art == Messung.Art.ZWISCHENBLICK) {
                        zb.ablesungen.add(m.ablesung);
                    } else {
                        zb = new Station.Zwischenblick();
                        zb.punkt = m.punkt;
                        zb.ablesungen.add(m.ablesung);
                        zb.weite = m.zielweite;
                        zb.mittelbildung = m.mittelbildung;
                        akt.zwischenblicke.add(zb);
                    }
                    break;
                default:
                    break;
            }
            vorige = m;
        }
        if (akt != null && !akt.hatVorblick()) {
            hinweise.add("Letzte Aufstellung (Rückblick auf " + akt.rueckPunkt + ") ohne Vorblick verworfen.");
            st.remove(akt);
        }
        return st;
    }

    static LinienErgebnis werteLinieAus(Linie l, Einstellungen e, Hoehendatei hd, boolean ersteLinie, int berNr) {
        LinienErgebnis r = new LinienErgebnis();
        r.berNr = berNr;
        r.name = l.name;
        r.art = l.art;
        r.titel = l.titel != null ? l.titel : e.projekttitel;
        r.kopf = l.kopf != null ? l.kopf : e.kopf;
        r.parameter = l.parameter != null ? l.parameter : LinienParameter.aus(e);
        LinienParameter p = r.parameter;
        r.hinweise.addAll(l.hinweise);

        if (l.art != Linie.Art.ZWISCHENBLICKE) {
            r.fehler.add(l.art.text + " wird in dieser Version noch nicht berechnet.");
            r.abgebrochen = true;
            return r;
        }
        r.stationen.addAll(bildeStationen(l, p.teilungen == 2, r.hinweise));
        if (r.stationen.isEmpty()) {
            r.fehler.add("Keine vollständige Aufstellung gefunden.");
            r.abgebrochen = true;
            return r;
        }
        List<Station> st = r.stationen;
        int n = st.size();
        for (int i = 1; i < n; i++) {
            if (!st.get(i).rueckPunkt.equals(st.get(i - 1).vorPunkt)) {
                r.fehler.add("Aufstellung " + (i + 1) + ": Rückblick auf " + st.get(i).rueckPunkt
                        + ", vorheriger Vorblick auf " + st.get(i - 1).vorPunkt + " - Linie unterbrochen.");
            }
        }
        r.anfangspunkt = st.get(0).rueckPunkt;
        r.endpunkt = st.get(n - 1).vorPunkt;
        double f = p.lattenteilung;

        // --- Summen ---
        double sumDh = 0;
        boolean weitenVollstaendig = true;
        for (Station s : st) {
            r.summeR += s.rueckblick();
            r.summeV += s.vorblick();
            sumDh += s.hoehenunterschied(f);
            if (Double.isNaN(s.zielweitenSumme())) {
                weitenVollstaendig = false;
            } else {
                r.summeStreckenR += s.rueckWeite();
                r.summeStreckenV += s.vorWeite();
            }
        }
        r.summeStrecken = r.summeStreckenR + r.summeStreckenV;

        // --- Anschlüsse ---
        Double hA = null;
        Double hE = null;
        if (ersteLinie && e.manuelleAnfangshoehe != null) {
            hA = e.manuelleAnfangshoehe;
            r.herkunftAnfang = "manuelle Eingabe";
        } else if (hd.anschluss(r.anfangspunkt) != null) {
            hA = hd.anschluss(r.anfangspunkt);
            r.herkunftAnfang = hd.herkunft(r.anfangspunkt);
        } else if (l.startHoeheDatei != null && r.anfangspunkt.equals(l.startPunktDatei)) {
            hA = l.startHoeheDatei;
            r.herkunftAnfang = "Messdatei";
        }
        boolean schleife = r.anfangspunkt.equals(r.endpunkt);
        if (schleife) {
            hE = hA;
            r.herkunftEnde = "Schleife";
        } else if (ersteLinie && e.manuelleEndhoehe != null) {
            hE = e.manuelleEndhoehe;
            r.herkunftEnde = "manuelle Eingabe";
        } else if (hd.anschluss(r.endpunkt) != null) {
            hE = hd.anschluss(r.endpunkt);
            r.herkunftEnde = hd.herkunft(r.endpunkt);
        }

        boolean anfangSpeichern = false;
        if (hA != null && hE != null) {
            r.linienart = schleife ? LinienErgebnis.Linienart.SCHLEIFE : LinienErgebnis.Linienart.ANGEHAENGT;
            r.anfangshoehe = hA;
            r.endhoehe = hE;
        } else if (hA != null) {
            r.linienart = LinienErgebnis.Linienart.NUR_ANFANG;
            r.anfangshoehe = hA;
            r.fehler.add("Anschlusspunkt " + r.endpunkt + " nicht gefunden - Berechnung ohne Fehlerverteilung.");
        } else if (hE != null) {
            r.linienart = LinienErgebnis.Linienart.RUECKWAERTS;
            r.endhoehe = hE;
            r.anfangshoehe = hE - sumDh;
            r.herkunftAnfang = "rückwärts berechnet";
            anfangSpeichern = true;
            r.fehler.add("Anschlusspunkt " + r.anfangspunkt + " nicht gefunden - Rückwärtsberechnung "
                    + "ohne Fehlerverteilung.");
        } else {
            r.linienart = LinienErgebnis.Linienart.OHNE_ANSCHLUSS;
            r.anfangshoehe = 0.0;
            r.herkunftAnfang = "angenommen";
            anfangSpeichern = true;
            r.fehler.add("Anschlusspunkte " + r.anfangspunkt + " und " + r.endpunkt
                    + " nicht gefunden - Berechnung mit Anfangshöhe 0.");
        }

        // --- Abschlussfehler, Fehlergrenze, Verteilung ---
        double[] v = new double[n];
        double[] anteilZ = new double[n];
        r.streckenproportional = p.mitStrecken && weitenVollstaendig && r.summeStrecken > 0;
        if (p.mitStrecken && !weitenVollstaendig) {
            r.hinweise.add("Zielweiten unvollständig - Verteilung nach Anzahl der Blicke.");
        }
        for (int i = 0; i < n; i++) {
            Station s = st.get(i);
            anteilZ[i] = r.streckenproportional ? s.rueckWeite() / s.zielweitenSumme() : 0.5;
        }
        boolean kontrolle = r.linienart == LinienErgebnis.Linienart.ANGEHAENGT
                || r.linienart == LinienErgebnis.Linienart.SCHLEIFE;
        if (kontrolle) {
            r.abschlussfehler = (r.endhoehe - r.anfangshoehe) - sumDh;
            for (int i = 0; i < n; i++) {
                v[i] = r.streckenproportional ? r.abschlussfehler * st.get(i).zielweitenSumme() / r.summeStrecken
                        : r.abschlussfehler / n;
            }
            if (weitenVollstaendig && r.summeStrecken > 0) {
                try {
                    r.erlaubtMm = e.fehlergrenze(p.fehlerklasse, r.summeStrecken / 1000.0);
                } catch (IllegalArgumentException ex) {
                    r.fehler.add("Fehlerformel Klasse " + p.fehlerklasse + ": " + ex.getMessage());
                }
            } else {
                r.hinweise.add("Ohne Strecken kann die Fehlergrenze nicht berechnet werden.");
            }
            if (!Double.isNaN(r.erlaubtMm)) {
                double wMm = Math.abs(r.abschlussfehler * 1000);
                r.zulaessig = wMm <= r.erlaubtMm + 1e-9;
                if (!r.zulaessig) {
                    r.fehler.add(String.format(Locale.ROOT, "Fehlergrenze überschritten: |w| = %.1f mm > F = %.1f mm",
                            wMm, r.erlaubtMm));
                } else if (e.schwellfaktor < 1 && wMm > e.schwellfaktor * r.erlaubtMm + 1e-9) {
                    r.fehler.add(String.format(Locale.ROOT,
                            "Warnung: |w| = %.1f mm überschreitet %.2f-fache Fehlergrenze (%.1f mm)", wMm,
                            e.schwellfaktor, e.schwellfaktor * r.erlaubtMm));
                }
            }
        }

        // --- Höhen und Protokollzeilen ---
        double grenze = p.erlaubteDifferenz;
        double hB = r.anfangshoehe;
        for (int i = 0; i < n; i++) {
            Station s = st.get(i);
            double dRueck = Station.differenz(s.rueckAblesungen, p.lattenkonstante);

            // Rückblick-Zeile(n)
            LinienErgebnis.Zeile zr = new LinienErgebnis.Zeile();
            zr.strecke = s.rueckWeite();
            zr.rueck = s.rueckAblesungen.get(0);
            if (i == 0) {
                zr.hoehe = hB;
                zr.punkt = s.rueckPunkt;
                zr.kennung = LinienParameter.kennung(s.rueckMittelbildung);
                if (anfangSpeichern) {
                    zr.speichern = modus(s.rueckMittelbildung, p);
                }
            }
            r.zeilen.add(zr);
            if (s.rueckAblesungen.size() > 1) {
                LinienErgebnis.Zeile z2 = new LinienErgebnis.Zeile();
                z2.rueck = s.rueckAblesungen.get(1);
                z2.dR = dText(dRueck, Double.NaN, grenze, r);
                r.zeilen.add(z2);
            }

            // Zwischenblicke
            for (Station.Zwischenblick zb : s.zwischenblicke) {
                LinienErgebnis.Zeile zz = new LinienErgebnis.Zeile();
                zz.strecke = zb.weite;
                zz.zwischen = zb.ablesungen.get(0);
                zz.hoehe = hB + (s.rueckblick() - zb.ablesung()) * f + v[i] * anteilZ[i];
                zz.verbesserung = v[i] * anteilZ[i];
                zz.punkt = zb.punkt;
                zz.kennung = LinienParameter.kennung(zb.mittelbildung);
                zz.speichern = modus(zb.mittelbildung, p);
                r.zeilen.add(zz);
                if (zb.ablesungen.size() > 1) {
                    LinienErgebnis.Zeile z2 = new LinienErgebnis.Zeile();
                    z2.zwischen = zb.ablesungen.get(1);
                    z2.dZ = dText(Station.differenz(zb.ablesungen, p.lattenkonstante), dRueck, grenze, r);
                    r.zeilen.add(z2);
                }
            }

            // Vorblick-Zeile(n)
            double dh = s.hoehenunterschied(f);
            hB = hB + dh + v[i];
            r.summeVerbesserungen += v[i];
            LinienErgebnis.Zeile zv = new LinienErgebnis.Zeile();
            zv.strecke = s.vorWeite();
            zv.vor = s.vorAblesungen.get(0);
            zv.dh = dh;
            zv.verbesserung = v[i];
            zv.hoehe = hB;
            zv.punkt = s.vorPunkt;
            zv.kennung = LinienParameter.kennung(s.vorMittelbildung);
            boolean istEndanschluss = i == n - 1 && (kontrolle || r.linienart == LinienErgebnis.Linienart.RUECKWAERTS);
            if (!istEndanschluss) {
                zv.speichern = modus(s.vorMittelbildung, p);
            }
            r.zeilen.add(zv);
            if (s.vorAblesungen.size() > 1) {
                LinienErgebnis.Zeile z2 = new LinienErgebnis.Zeile();
                z2.vor = s.vorAblesungen.get(1);
                z2.dV = dText(Station.differenz(s.vorAblesungen, p.lattenkonstante), dRueck, grenze, r);
                r.zeilen.add(z2);
            }
        }

        // --- Speichern in der Höhendatei ---
        boolean sperren = e.stoppBeiFehler && (r.zulaessig != null && !r.zulaessig || !kontrolle);
        if (sperren) {
            r.hinweise.add("Höhen dieser Berechnung wurden NICHT gespeichert (Stopp bei Fehler).");
        } else {
            for (LinienErgebnis.Zeile z : r.zeilen) {
                if (z.speichern >= 0 && !Double.isNaN(z.hoehe)) {
                    hd.speichere(z.punkt, z.hoehe, r.berNr, z.speichern, e.mehrfachGrenzeMm);
                }
            }
            r.gespeichert = true;
        }
        return r;
    }

    private static int modus(int punktspezifisch, LinienParameter p) {
        return punktspezifisch >= 0 ? punktspezifisch : p.mittelbildung;
    }

    /** Differenztext: "d" bzw. "d/Standpunktdifferenz", *** bei Überschreitung. */
    private static String dText(double d, double dRueck, double grenze, LinienErgebnis r) {
        if (Double.isNaN(d)) {
            return "";
        }
        StringBuilder sb = new StringBuilder(String.valueOf((long) d));
        if (Math.abs(d) > grenze + 1e-9) {
            sb.append("***");
            r.tabellenFehler++;
        }
        if (!Double.isNaN(dRueck)) {
            double std = dRueck - d;
            sb.append('/').append((long) std);
            if (Math.abs(std) > grenze + 1e-9) {
                sb.append("***");
                r.tabellenFehler++;
            }
        }
        return sb.toString();
    }
}
