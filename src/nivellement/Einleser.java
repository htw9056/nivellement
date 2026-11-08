package nivellement;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Erkennt das Dateiformat und liest Messdaten bzw. Festpunkte ein.
 */
public final class Einleser {

    public enum Format {
        LEICA_GSI("Leica GSI"), TRIMBLE_DAT("Trimble DAT (M5)"), NIGRA_NIG("Stapeldatei NIG");

        public final String text;

        Format(String text) {
            this.text = text;
        }
    }

    private Einleser() {
    }

    public static List<String> zeilen(Path datei) throws IOException {
        byte[] b = Files.readAllBytes(datei);
        try {
            return List.of(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b))
                    .toString().split("\\r?\\n"));
        } catch (java.nio.charset.CharacterCodingException e) {
            return List.of(new String(b, Charset.forName("windows-1252")).split("\\r?\\n"));
        }
    }

    public static Format erkenneFormat(List<String> zeilen) {
        boolean b = false, k = false, e = false;
        for (String z : zeilen) {
            if (z.isEmpty()) {
                continue;
            }
            char c = Character.toUpperCase(z.charAt(0));
            b |= c == 'B' && z.substring(1).trim().matches("\\d+");
            k |= c == 'K';
            e |= c == 'E' && z.trim().length() <= 25;
        }
        if (b && k && e) {
            return Format.NIGRA_NIG;
        }
        for (String z : zeilen) {
            String t = z.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("For M5") || t.contains("|Adr")) {
                return Format.TRIMBLE_DAT;
            }
            String s = t.startsWith("*") ? t.substring(1) : t;
            if (s.matches("^\\d\\d[\\d.]{4}[+-].*")) {
                return Format.LEICA_GSI;
            }
        }
        return null;
    }

    public static List<Linie> liesMessdatei(Path datei) throws IOException {
        List<String> z = zeilen(datei);
        Format f = erkenneFormat(z);
        if (f == null) {
            throw new IOException("Dateiformat nicht erkannt (erwartet Leica GSI, Trimble DAT M5 oder NIG): " + datei);
        }
        switch (f) {
            case LEICA_GSI:
                return GsiLeser.lies(z);
            case TRIMBLE_DAT:
                return TrimbleDatLeser.lies(z);
            default:
                return NigLeser.lies(z);
        }
    }

    /**
     * Festpunktdatei: je Zeile Punktnummer und Höhe, getrennt durch Semikolon,
     * Komma, Tabulator oder Leerzeichen. Zeilen mit # werden ignoriert.
     * Stehen mehr als zwei Werte in der Zeile (z. B. P;R;H;Z), wird der letzte
     * Zahlenwert als Höhe verwendet.
     */
    public static Map<String, Double> liesFestpunkte(Path datei) throws IOException {
        Map<String, Double> fp = new LinkedHashMap<>();
        for (String z : zeilen(datei)) {
            String t = z.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String[] teile = t.split("[;\\t ]+");
            if (teile.length < 2) {
                continue;
            }
            Double h = null;
            if (teile.length >= 5 && teile[2].matches("\\d+") && teile[1].matches("-?\\d+[.,]\\d+")) {
                // Höhenverzeichnis (ASC): Punkt Höhe BerNr Diff AB Datum Bemerkung
                fp.put(teile[0], Double.parseDouble(teile[1].replace(',', '.')));
                continue;
            }
            for (int i = teile.length - 1; i >= 1; i--) {
                try {
                    h = Double.parseDouble(teile[i].replace(',', '.'));
                    break;
                } catch (NumberFormatException e) {
                    // weiter suchen
                }
            }
            if (h != null) {
                fp.put(teile[0], h);
            }
        }
        return fp;
    }
}
