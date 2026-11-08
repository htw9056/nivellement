package nivellement;

import java.util.ArrayList;
import java.util.List;

/**
 * Liest Trimble-DAT-Dateien im Format M5 (DiNi).
 *
 * Aufbau einer Zeile: {@code For M5|Adr nnnnn|<Typ/Punkt>|<Wert1>|<Wert2>|<Wert3>|}
 * <ul>
 * <li>Typfeld "KD1"/"KD2" + Punktnummer</li>
 * <li>Rb = Rückblick, Rf = Vorblick, Rz = Zwischenblick, HD = Zielweite</li>
 * <li>Z = Höhe (nur am Anfangspunkt vor der ersten Ablesung als Anfangshöhe genutzt)</li>
 * <li>"Start-Line"/"Zugbeginn" und "End-Line"/"Zugende" trennen die Linien</li>
 * </ul>
 */
public final class TrimbleDatLeser {

    private TrimbleDatLeser() {
    }

    public static List<Linie> lies(List<String> zeilen) {
        List<Linie> linien = new ArrayList<>();
        Linie linie = new Linie("Linie 1");
        int nr = 0;
        for (String roh : zeilen) {
            nr++;
            if (!roh.contains("|")) {
                continue;
            }
            String[] f = roh.split("\\|", -1);
            if (f.length < 4) {
                continue;
            }
            String typ = f[2].trim();

            if (typ.contains("Start-Line") || typ.contains("Zugbeginn")) {
                if (!linie.istLeer()) {
                    linien.add(linie);
                }
                linie = new Linie("Linie " + (linien.size() + 1));
                String[] t = typ.split("\\s+");
                String letzte = t[t.length - 1];
                if (!letzte.equals("Start-Line") && letzte.matches("\\d+")) {
                    linie.name = "Linie " + letzte;
                }
                continue;
            }
            if (typ.contains("End-Line") || typ.contains("Zugende")) {
                if (!linie.istLeer()) {
                    linien.add(linie);
                }
                linie = new Linie("Linie " + (linien.size() + 1));
                continue;
            }
            if (!(typ.startsWith("KD1") || typ.startsWith("KD2"))) {
                continue;
            }
            String[] t = typ.substring(3).trim().split("\\s+");
            if (t.length == 0 || t[0].isEmpty()) {
                continue;
            }
            String punkt = t[0];

            Double rb = null, rf = null, rz = null, z = null;
            double hd = Double.NaN;
            for (int i = 3; i < f.length; i++) {
                String feld = f[i].trim();
                if (feld.isEmpty()) {
                    continue;
                }
                String[] w = feld.split("\\s+");
                if (w.length < 2) {
                    continue;
                }
                if (w[1].contains("#")) {
                    linie.hinweise.add("Zeile " + nr + ": ungültiger Messwert (" + feld + ") übersprungen.");
                    continue;
                }
                double wert;
                try {
                    wert = Double.parseDouble(w[1].replace(',', '.'));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (w.length > 2 && w[2].toLowerCase().startsWith("ft")) {
                    linie.hinweise.add("Zeile " + nr + ": Einheit Fuß wird nicht unterstützt.");
                }
                switch (w[0]) {
                    case "Rb":
                    case "Lr":
                        rb = wert;
                        break;
                    case "Rf":
                    case "Lv":
                        rf = wert;
                        break;
                    case "Rz":
                    case "Lz":
                        rz = wert;
                        break;
                    case "HD":
                    case "E":
                        hd = wert;
                        break;
                    case "Z":
                        z = wert;
                        break;
                    default:
                        break;
                }
            }
            if (z != null && linie.istLeer() && rb == null && rf == null && rz == null) {
                linie.startHoeheDatei = z;
                linie.startPunktDatei = punkt;
            }
            if (rb != null) {
                linie.messungen.add(new Messung(Messung.Art.RUECKBLICK, punkt, rb, hd, nr));
            }
            if (rf != null) {
                linie.messungen.add(new Messung(Messung.Art.VORBLICK, punkt, rf, hd, nr));
            }
            if (rz != null) {
                linie.messungen.add(new Messung(Messung.Art.ZWISCHENBLICK, punkt, rz, hd, nr));
            }
        }
        if (!linie.istLeer()) {
            linien.add(linie);
        }
        return linien;
    }
}
