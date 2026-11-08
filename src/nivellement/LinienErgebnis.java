package nivellement;

import java.util.ArrayList;
import java.util.List;

/**
 * Ergebnis der Auswertung einer Nivellementlinie.
 */
public class LinienErgebnis {

    public enum Linienart {
        ANGEHAENGT("angehängte Linie"),
        SCHLEIFE("Schleife"),
        NUR_ANFANG("nur Anfangspunkt bekannt - ohne Fehlerverteilung"),
        RUECKWAERTS("nur Endpunkt bekannt - Rückwärtsberechnung ohne Fehlerverteilung"),
        OHNE_ANSCHLUSS("kein Anschlusspunkt bekannt - Anfangshöhe = 0");

        public final String text;

        Linienart(String text) {
            this.text = text;
        }
    }

    /** Eine Protokollzeile. */
    public static class Zeile {
        public double strecke = Double.NaN;
        public double rueck = Double.NaN;
        public double zwischen = Double.NaN;
        public double vor = Double.NaN;
        /** Differenztexte (0,1 mm) hinter R, Z, V, z. B. "1" oder "0/2***". */
        public String dR = "";
        public String dZ = "";
        public String dV = "";
        public double dh = Double.NaN;
        public double verbesserung = Double.NaN;
        public double hoehe = Double.NaN;
        public String punkt = "";
        /** Kennbuchstabe der punktspezifischen Mittelbildung. */
        public String kennung = "";
        /** Mittelbildung für diesen Punkt (-1 = keine Speicherung). */
        public int speichern = -1;
    }

    public int berNr;
    public String name;
    public String titel = "";
    public Kopfdaten kopf;
    public LinienParameter parameter;
    public Linie.Art art;
    public Linienart linienart;
    public String anfangspunkt;
    public String endpunkt;
    public double anfangshoehe = Double.NaN;
    public String herkunftAnfang = "";
    public double endhoehe = Double.NaN;
    public String herkunftEnde = "";

    public final List<Station> stationen = new ArrayList<>();
    public final List<Zeile> zeilen = new ArrayList<>();
    /** Fehler (werden mit *** ausgegeben und gezählt). */
    public final List<String> fehler = new ArrayList<>();
    /** Hinweise ohne Fehlerstatus. */
    public final List<String> hinweise = new ArrayList<>();
    /** Anzahl der *** in Tabellenzeilen (Lattendifferenzen). */
    public int tabellenFehler;

    public double summeStrecken;
    public double summeStreckenR;
    public double summeStreckenV;
    public double summeR;
    public double summeV;
    /** Abschlussfehler w = Soll - Ist in m (NaN wenn keine Abschlusskontrolle). */
    public double abschlussfehler = Double.NaN;
    /** Erlaubter Fehler F in mm. */
    public double erlaubtMm = Double.NaN;
    public Boolean zulaessig;
    public boolean streckenproportional;
    public double summeVerbesserungen;
    public boolean gespeichert;
    /** Linie konnte nicht berechnet werden. */
    public boolean abgebrochen;

    public int fehlerAnzahl() {
        return fehler.size() + tabellenFehler;
    }
}
