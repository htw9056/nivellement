package nivellement;

/**
 * Alle vom Anwender wählbaren Parameter (Projekt- und Programmkonfiguration).
 */
public class Einstellungen {

    /** Verteilung des Abschlussfehlers. */
    public enum Verteilung {
        ZIELWEITEN("streckenproportional (nach Zielweiten)"),
        AUFSTELLUNGEN("nach Anzahl der Rück- und Vorblicke");

        public final String text;

        Verteilung(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    // --- Programmkonfiguration ---
    /** Kopfzeile aller Ausdrucke (Firma/Vermessungsstelle). */
    public String firma = "";
    /** Fehlergrenzen der Klassen 1-4 als Formel in R [km], Ergebnis in mm. */
    public String[] fehlerformeln = { "1 + 3*Sqrt(R)", "2 + 3*Sqrt(R)", "2 + 5*Sqrt(R)", "3 + 8*Sqrt(R)" };
    /** Warnung, wenn |w| > Schwellfaktor * F (1 = aus). */
    public double schwellfaktor = 1.0;
    /** Zulässiger Fehler bei Mehrfachbestimmungen in mm. */
    public double mehrfachGrenzeMm = 2.0;
    /** Logo für den Seitenkopf beim Drucken (PNG, JPG, GIF), leer = ohne. */
    public String logoDatei = "";
    /** Höhe des Logos in mm und maximale Breite in mm (0 = nur durch Höhe begrenzt). */
    public double logoHoeheMm = 20;
    public double logoBreiteMm = 0;

    // --- Projekt/Berechnung ---
    public String projekttitel = "";
    public Kopfdaten kopf = new Kopfdaten();
    public Verteilung verteilung = Verteilung.ZIELWEITEN;
    public int fehlerklasse = 3;
    /** Mittelbildung 0 = mitteln, 1 = neu, 2 = vergleichen, 3 = ohne. */
    public int mittelbildung = 1;
    /** Erlaubte Differenz bei 2 Ablesungen in 0,1 mm. */
    public double erlaubteDifferenz = 3;
    public int nachkommastellen = 4;
    public int nkAblesung = 5;
    public int berechnungsnummerStart = 1;
    /** Stapelberechnung nur f\u00fcr Berechnungsnummern von-bis (null = alle). */
    public Integer berNrVon;
    public Integer berNrBis;
    /** Alte Berechnung (Protokoll) l\u00f6schen, sonst wird angeh\u00e4ngt. */
    public boolean alteBerechnungLoeschen = true;

    /** Optionale Festpunktdatei (Punkt Höhe), Punkte gelten als Festpunkte (BerNr. 999999). */
    public String festpunktDatei;
    /** Höhendatei des Projekts (wird gelesen und fortgeschrieben). */
    public String hoehenDatei;
    public boolean neupunkteLoeschen = false;
    /** Höhen einer Linie nicht speichern, wenn Fehlergrenze überschritten oder Anschluss fehlt. */
    public boolean stoppBeiFehler = false;
    /** Zusätzliche Protokollspalten dh und Verbesserung. */
    public boolean erweitertesProtokoll = false;

    /** Manuelle Anfangs-/Endhöhe (nur erste Linie), null = automatisch. */
    public Double manuelleAnfangshoehe;
    public Double manuelleEndhoehe;

    /** Zulässiger Abschlussfehler F in mm für Klasse k und Messweg R in km. */
    public double fehlergrenze(int klasse, double rKm) {
        int k = Math.max(1, Math.min(4, klasse));
        return Formel.berechne(fehlerformeln[k - 1], rKm);
    }
}
