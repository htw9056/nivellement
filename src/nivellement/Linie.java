package nivellement;

import java.util.ArrayList;
import java.util.List;

/**
 * Rohdaten einer Nivellementlinie, so wie sie aus der Messdatei gelesen wurden.
 */
public class Linie {

    /** Nivellementart. */
    public enum Art {
        ZWISCHENBLICKE("Nivellement mit Zwischenblicken"),
        STRECKE("Streckennivellement"),
        LINIENAUSGLEICHUNG("Linienausgleichung"),
        INSTRUMENTENPRUEFUNG("Instrumentenprüfung");

        public final String text;

        Art(String text) {
            this.text = text;
        }
    }

    public String name;
    public Art art = Art.ZWISCHENBLICKE;
    public final List<Messung> messungen = new ArrayList<>();

    /** Berechnungsnummer aus der Datei (null = fortlaufend vergeben). */
    public Integer berechnungsnummer;
    /** Titel/Kommentar (C-Zeile), null = Projekttitel aus Einstellungen. */
    public String titel;
    /** Kopfdaten aus der Datei, null = aus Einstellungen. */
    public Kopfdaten kopf;
    /** Parameter aus der Datei, null = aus Einstellungen. */
    public LinienParameter parameter;

    /** Anfangshöhe, falls in der Messdatei enthalten (sonst null). */
    public Double startHoeheDatei;
    /** Punkt, zu dem die Anfangshöhe aus der Messdatei gehört. */
    public String startPunktDatei;

    /** Hinweise, die beim Einlesen entstanden sind. */
    public final List<String> hinweise = new ArrayList<>();

    public Linie(String name) {
        this.name = name;
    }

    public boolean istLeer() {
        return messungen.isEmpty();
    }
}
