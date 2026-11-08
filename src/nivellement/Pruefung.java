package nivellement;

import java.util.ArrayList;
import java.util.List;

/**
 * Prüft eine Messdatei vor der Berechnung: Aufbau, Reihenfolge der Ablesungen r/z/v,
 * Zahlenformate, Anschlüsse und Strecken.
 */
public final class Pruefung {

    private Pruefung() {
    }

    public static String pruefe(List<Linie> linien) {
        StringBuilder sb = new StringBuilder();
        int fehler = 0;
        int nr = 0;
        for (Linie l : linien) {
            nr++;
            List<String> h = new ArrayList<>(l.hinweise);
            sb.append(l.berechnungsnummer != null ? "Berechnungs-Nr. " + l.berechnungsnummer : l.name)
                    .append(" (").append(l.art.text).append("): ");
            if (l.art != Linie.Art.ZWISCHENBLICKE) {
                sb.append(l.messungen.size()).append(" Messsätze\n");
                for (String s : h) {
                    sb.append("   - ").append(s).append("\n");
                    fehler++;
                }
                continue;
            }
            LinienParameter p = l.parameter != null ? l.parameter : new LinienParameter();
            List<Station> st = Auswertung.bildeStationen(l, p.teilungen == 2, h);
            int ohneStrecke = 0;
            for (Messung m : l.messungen) {
                if (Double.isNaN(m.zielweite)) {
                    ohneStrecke++;
                }
            }
            sb.append(l.messungen.size()).append(" Messsätze, ").append(st.size()).append(" Aufstellungen");
            if (!st.isEmpty()) {
                sb.append(", ").append(st.get(0).rueckPunkt).append(" -> ").append(st.get(st.size() - 1).vorPunkt);
            }
            sb.append("\n");
            for (int i = 1; i < st.size(); i++) {
                if (!st.get(i).rueckPunkt.equals(st.get(i - 1).vorPunkt)) {
                    h.add("Aufstellung " + (i + 1) + ": Rückblick auf " + st.get(i).rueckPunkt
                            + ", vorheriger Vorblick auf " + st.get(i - 1).vorPunkt + " - Linie unterbrochen.");
                }
            }
            if (ohneStrecke > 0 && p.mitStrecken) {
                h.add(ohneStrecke + " Messsätze ohne Strecke - Verteilung nach Anzahl der Blicke.");
            }
            for (String s : h) {
                sb.append("   - ").append(s).append("\n");
                fehler++;
            }
        }
        sb.insert(0, nr + " Nivellement(s) geprüft, " + (fehler == 0 ? "keine Auffälligkeiten"
                : fehler + " Hinweis(e)") + ".\n\n");
        return sb.toString();
    }
}
