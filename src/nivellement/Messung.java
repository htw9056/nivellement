package nivellement;

/**
 * Eine einzelne Lattenablesung aus der Messdatei.
 */
public class Messung {

    /** Art der Ablesung. */
    public enum Art {
        RUECKBLICK, VORBLICK, ZWISCHENBLICK
    }

    public final Art art;
    public final String punkt;
    /** Lattenablesung (in Lattenteilungs-Einheiten, i. d. R. m). */
    public final double ablesung;
    /** Zielweite in m, Double.NaN wenn nicht vorhanden. */
    public final double zielweite;
    /** Zeilennummer in der Messdatei (für Hinweise). */
    public final int zeile;
    /** Punktspezifische Mittelbildung 0-3, -1 = wie Linie. */
    public final int mittelbildung;

    public Messung(Art art, String punkt, double ablesung, double zielweite, int zeile) {
        this(art, punkt, ablesung, zielweite, zeile, -1);
    }

    public Messung(Art art, String punkt, double ablesung, double zielweite, int zeile, int mittelbildung) {
        this.art = art;
        this.punkt = punkt;
        this.ablesung = ablesung;
        this.zielweite = zielweite;
        this.zeile = zeile;
        this.mittelbildung = mittelbildung;
    }

    @Override
    public String toString() {
        return art + " " + punkt + " " + ablesung + " (" + zielweite + " m, Zeile " + zeile + ")";
    }
}
