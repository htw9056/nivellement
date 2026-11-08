package nivellement;

/**
 * Steuerparameter, die für eine einzelne Linie gelten. Bei GSI/DAT kommen sie aus den
 * Einstellungen, bei Stapeldateien (.NIG) aus dem Steuerdatenblock der Linie.
 */
public class LinienParameter {

    /** Verteilung nach Strecken (true) oder nach Anzahl der Blicke (false). */
    public boolean mitStrecken = true;
    /** Anzahl Lattenteilungen bzw. Ablesungen je Ziel (1 oder 2). */
    public int teilungen = 1;
    /** Lattenkonstante (bei 2 Teilungen). */
    public double lattenkonstante = 0.0;
    /** Erlaubte Differenz bei 2 Ablesungen in 0,1 mm (0 = jede Abweichung wird markiert). */
    public double erlaubteDifferenz = 0;
    /** Lattenteilung: 1 = cm, 0.5 = 1/2 cm (Ablesungen werden damit multipliziert). */
    public double lattenteilung = 1.0;
    /** Dezimalstellen Höhen / Lattenablesungen. */
    public int nkHoehe = 4;
    public int nkAblesung = 5;
    /** Mittelbildung 0 = mitteln, 1 = neu, 2 = vergleichen, 3 = ohne. */
    public int mittelbildung = 1;
    /** Fehlerklasse 1-4. */
    public int fehlerklasse = 3;
    /** Beobachtungsfolge (nur Anzeige). */
    public String beobachtungsfolge = "";

    public static LinienParameter aus(Einstellungen e) {
        LinienParameter p = new LinienParameter();
        p.mitStrecken = e.verteilung == Einstellungen.Verteilung.ZIELWEITEN;
        p.erlaubteDifferenz = e.erlaubteDifferenz;
        p.nkHoehe = e.nachkommastellen;
        p.nkAblesung = e.nkAblesung;
        p.mittelbildung = e.mittelbildung;
        p.fehlerklasse = e.fehlerklasse;
        return p;
    }

    public static String mittelbildungText(int m) {
        switch (m) {
            case 0:
                return "mitteln - alte und neue Höhe werden gemittelt";
            case 1:
                return "neu - berechnete Höhe wird eingeführt";
            case 2:
                return "vergleichen - alte Höhe bleibt, neu und alt werden verglichen";
            case 3:
                return "ohne - kein Speichern, kein Vergleich";
            default:
                return "?";
        }
    }

    public static String mittelbildungKurz(int m) {
        switch (m) {
            case 0:
                return "0 - mitteln";
            case 1:
                return "1 - neu";
            case 2:
                return "2 - vergleichen";
            case 3:
                return "3 - ohne";
            default:
                return "?";
        }
    }

    /** Kennbuchstabe hinter der Punktnummer bei punktspezifischer Mittelbildung. */
    public static String kennung(int m) {
        switch (m) {
            case 0:
                return "m";
            case 1:
                return "n";
            case 2:
                return "a";
            case 3:
                return "o";
            default:
                return "";
        }
    }
}
