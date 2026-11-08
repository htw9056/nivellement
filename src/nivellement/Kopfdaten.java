package nivellement;

/**
 * Beschreibende Kopfdaten eines Nivellements (erscheinen im Protokollkopf).
 */
public class Kopfdaten {

    public String ort = "";
    public String auftrag = "";
    public String linieZug = "";
    public String datum = "";
    public String wetter = "";
    public String beobachter = "";
    public String nivellier = "";
    public String latte = "";
    public String bemerkung = "";

    public Kopfdaten kopie() {
        Kopfdaten k = new Kopfdaten();
        k.ort = ort;
        k.auftrag = auftrag;
        k.linieZug = linieZug;
        k.datum = datum;
        k.wetter = wetter;
        k.beobachter = beobachter;
        k.nivellier = nivellier;
        k.latte = latte;
        k.bemerkung = bemerkung;
        return k;
    }
}
