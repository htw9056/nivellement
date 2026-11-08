package nivellement;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Speichert Einstellungen auf zwei Ebenen:
 * <ul>
 * <li><b>Programm</b> ({@code ~/.nivellement.properties}): Programmkonfiguration (Firma, Fehlerklassen,
 * Schwellfaktor, Mehrfachbestimmungen - gilt für alle Projekte) und die zuletzt verwendeten Einstellungen
 * als Vorgabe für jedes neue Projekt.</li>
 * <li><b>Projekt</b> ({@code <Projekt>_Einstellungen.properties} neben der Stapeldatei): alle Einstellungen
 * des Projekts einschließlich Festpunkt- und Höhendatei. Beim Öffnen werden sie wiederhergestellt.</li>
 * </ul>
 * Hat ein Projekt noch keine eigenen Einstellungen, aber eine NIGRA-Projektdatei {@code Projekt.xml}
 * im selben Ordner, werden deren Werte übernommen.
 */
public final class Konfiguration {

    static Path datei = Path.of(System.getProperty("user.home"), ".nivellement.properties");

    private Konfiguration() {
    }

    // ------------------------------------------------------------------ Laden

    /** Programmeinstellungen (Vorgaben für neue Projekte). */
    public static Einstellungen lade() {
        Einstellungen e = new Einstellungen();
        Properties p = lies(datei);
        if (p != null) {
            setze(e, p, false);
        }
        return e;
    }

    /**
     * Einstellungen für ein Projekt: Vorgaben aus dem Programm, überlagert mit den Projekteinstellungen
     * bzw. einer NIGRA-Projekt.xml. Liefert zusätzlich, woher die Werte stammen.
     */
    public static Einstellungen fuerProjekt(Path messdatei, StringBuilder herkunft) {
        Einstellungen e = lade();
        if (messdatei == null) {
            return e;
        }
        Path pd = projektdatei(messdatei);
        Properties p = lies(pd);
        if (p != null) {
            setze(e, p, true);
            herkunft.append("Projekteinstellungen ").append(pd.getFileName());
            return e;
        }
        Path xml = Projekt.ordner(messdatei).resolve("Projekt.xml");
        if (Files.exists(xml) && nigraProjekt(xml, e)) {
            herkunft.append("NIGRA-Projekt.xml");
            return e;
        }
        herkunft.append("Vorgaben (zuletzt verwendete Einstellungen)");
        return e;
    }

    public static Path projektdatei(Path messdatei) {
        return Projekt.ordner(messdatei).resolve(Projekt.name(messdatei) + "_Einstellungen.properties");
    }

    private static Properties lies(Path d) {
        if (d == null || !Files.exists(d)) {
            return null;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(d)) {
            p.load(in);
            return p;
        } catch (IOException ex) {
            return null;
        }
    }

    private static void setze(Einstellungen e, Properties p, boolean projekt) {
        if (!projekt) {
            // Programmkonfiguration gilt für alle Projekte
            e.firma = p.getProperty("firma", e.firma);
            for (int k = 0; k < 4; k++) {
                e.fehlerformeln[k] = p.getProperty("fehlerklasse" + (k + 1), e.fehlerformeln[k]);
            }
            e.schwellfaktor = zahl(p, "schwellfaktor", e.schwellfaktor);
            e.mehrfachGrenzeMm = zahl(p, "mehrfachGrenzeMm", e.mehrfachGrenzeMm);
            e.logoDatei = p.getProperty("logo", e.logoDatei);
            e.logoHoeheMm = zahl(p, "logoHoeheMm", e.logoHoeheMm);
            e.logoBreiteMm = zahl(p, "logoBreiteMm", e.logoBreiteMm);
        }
        e.projekttitel = p.getProperty("projekttitel", e.projekttitel);
        Kopfdaten k = e.kopf;
        k.ort = p.getProperty("kopf.ort", k.ort);
        k.auftrag = p.getProperty("kopf.auftrag", k.auftrag);
        k.linieZug = p.getProperty("kopf.linie", k.linieZug);
        k.datum = p.getProperty("kopf.datum", k.datum);
        k.wetter = p.getProperty("kopf.wetter", k.wetter);
        k.beobachter = p.getProperty("kopf.beobachter", k.beobachter);
        k.nivellier = p.getProperty("kopf.nivellier", k.nivellier);
        k.latte = p.getProperty("kopf.latte", k.latte);
        k.bemerkung = p.getProperty("kopf.bemerkung", k.bemerkung);
        try {
            e.verteilung = Einstellungen.Verteilung.valueOf(p.getProperty("verteilung", e.verteilung.name()));
        } catch (IllegalArgumentException ex) {
            // Standard
        }
        e.fehlerklasse = (int) zahl(p, "fehlerklasse", e.fehlerklasse);
        e.mittelbildung = (int) zahl(p, "mittelbildung", e.mittelbildung);
        e.erlaubteDifferenz = zahl(p, "erlaubteDifferenz", e.erlaubteDifferenz);
        e.nachkommastellen = (int) zahl(p, "nachkommastellen", e.nachkommastellen);
        e.nkAblesung = (int) zahl(p, "nkAblesung", e.nkAblesung);
        e.erweitertesProtokoll = Boolean.parseBoolean(p.getProperty("erweitert", String.valueOf(e.erweitertesProtokoll)));
        e.stoppBeiFehler = Boolean.parseBoolean(p.getProperty("stopp", String.valueOf(e.stoppBeiFehler)));
        e.alteBerechnungLoeschen = Boolean.parseBoolean(
                p.getProperty("alteBerechnungLoeschen", String.valueOf(e.alteBerechnungLoeschen)));
        e.neupunkteLoeschen = Boolean.parseBoolean(
                p.getProperty("neupunkteLoeschen", String.valueOf(e.neupunkteLoeschen)));
        if (projekt) {
            // nur projektbezogen - würde in einem anderen Projekt falsche Dateien verwenden
            e.festpunktDatei = p.getProperty("festpunkte", "");
            e.hoehenDatei = p.getProperty("hoehendatei", "");
            e.berechnungsnummerStart = (int) zahl(p, "berechnungsnummerStart", e.berechnungsnummerStart);
            e.manuelleAnfangshoehe = optZahl(p.getProperty("anfangshoehe"));
            e.manuelleEndhoehe = optZahl(p.getProperty("endhoehe"));
            Double von = optZahl(p.getProperty("berNrVon"));
            Double bis = optZahl(p.getProperty("berNrBis"));
            e.berNrVon = von == null ? null : von.intValue();
            e.berNrBis = bis == null ? null : bis.intValue();
        }
    }

    /** Übernimmt die Werte einer NIGRA-Projekt.xml (Attribute des Elements "Projekt"). */
    static boolean nigraProjekt(Path xml, Einstellungen e) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            NodeList n = f.newDocumentBuilder().parse(xml.toFile()).getElementsByTagName("Projekt");
            if (n.getLength() == 0) {
                return false;
            }
            Element p = (Element) n.item(0);
            e.fehlerklasse = ganz(p, "FehlerKl", e.fehlerklasse, 1, 4);
            e.mittelbildung = ganz(p, "Mittelbi", e.mittelbildung, 0, 3);
            e.nachkommastellen = ganz(p, "DezBe", e.nachkommastellen, 2, 5);
            e.nkAblesung = ganz(p, "DezAb", e.nkAblesung, 2, 5);
            if (!p.getAttribute("DifToleranz").isBlank()) {
                e.erlaubteDifferenz = zahl(p.getAttribute("DifToleranz"), e.erlaubteDifferenz);
            }
            if (!p.getAttribute("Strecken").isBlank()) {
                e.verteilung = "1".equals(p.getAttribute("Strecken").trim()) ? Einstellungen.Verteilung.ZIELWEITEN
                        : Einstellungen.Verteilung.AUFSTELLUNGEN;
            }
            e.stoppBeiFehler = "J".equalsIgnoreCase(p.getAttribute("FStop").trim());
            e.alteBerechnungLoeschen = !"N".equalsIgnoreCase(p.getAttribute("AutoLoesch").trim());
            e.neupunkteLoeschen = "J".equalsIgnoreCase(p.getAttribute("LoeschePunkte").trim());
            Double von = optZahl(p.getAttribute("BerVonNr"));
            Double bis = optZahl(p.getAttribute("BerBisNr"));
            e.berNrVon = von == null || von <= 1 ? null : von.intValue();
            e.berNrBis = bis == null || bis >= 999999 ? null : bis.intValue();
            Kopfdaten k = e.kopf;
            k.ort = p.getAttribute("Ort").trim();
            k.auftrag = p.getAttribute("Auftrag").trim();
            k.linieZug = p.getAttribute("Linie").trim();
            k.datum = p.getAttribute("Datum").trim();
            k.wetter = p.getAttribute("Wetter").trim();
            k.beobachter = p.getAttribute("Beobachter").trim();
            k.nivellier = p.getAttribute("Nivellier").trim();
            k.latte = p.getAttribute("Latte").trim();
            k.bemerkung = p.getAttribute("Bemerkung").trim();
            e.festpunktDatei = "";
            e.hoehenDatei = "";
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    // --------------------------------------------------------------- Speichern

    /**
     * Speichert die Programmeinstellungen (Vorgaben) und - falls eine Stapeldatei gewählt ist - die
     * Projekteinstellungen.
     */
    public static void speichere(Einstellungen e, String messdatei) {
        Properties p = new Properties();
        programm(p, e);
        alles(p, e);
        // Programmdatei: zuletzt verwendetes Projekt, aber keine projektbezogenen Dateien
        Properties prog = lies(datei);
        for (String s : new String[] { "fenster.x", "fenster.y", "fenster.breite", "fenster.hoehe", "fenster.max",
                "register", "ordner" }) {
            if (prog != null && prog.getProperty(s) != null) {
                p.setProperty(s, prog.getProperty(s));
            }
        }
        p.setProperty("messdatei", messdatei == null ? "" : messdatei);
        schreibe(datei, p, "Programm- und Vorgabeeinstellungen");

        if (messdatei != null && !messdatei.isBlank()) {
            Path m = Path.of(messdatei);
            if (Files.exists(m) && Projekt.istStapeldatei(m)) {
                Properties pp = new Properties();
                alles(pp, e);
                pp.setProperty("festpunkte", e.festpunktDatei == null ? "" : e.festpunktDatei);
                pp.setProperty("hoehendatei", e.hoehenDatei == null ? "" : e.hoehenDatei);
                pp.setProperty("berechnungsnummerStart", String.valueOf(e.berechnungsnummerStart));
                pp.setProperty("anfangshoehe", e.manuelleAnfangshoehe == null ? "" : String.valueOf(e.manuelleAnfangshoehe));
                pp.setProperty("endhoehe", e.manuelleEndhoehe == null ? "" : String.valueOf(e.manuelleEndhoehe));
                pp.setProperty("berNrVon", e.berNrVon == null ? "" : String.valueOf(e.berNrVon));
                pp.setProperty("berNrBis", e.berNrBis == null ? "" : String.valueOf(e.berNrBis));
                schreibe(projektdatei(m), pp, "Projekteinstellungen " + Projekt.name(m));
            }
        }
    }

    private static void programm(Properties p, Einstellungen e) {
        p.setProperty("firma", e.firma);
        for (int k = 0; k < 4; k++) {
            p.setProperty("fehlerklasse" + (k + 1), e.fehlerformeln[k]);
        }
        p.setProperty("schwellfaktor", String.valueOf(e.schwellfaktor));
        p.setProperty("mehrfachGrenzeMm", String.valueOf(e.mehrfachGrenzeMm));
        p.setProperty("logo", e.logoDatei == null ? "" : e.logoDatei);
        p.setProperty("logoHoeheMm", String.valueOf(e.logoHoeheMm));
        p.setProperty("logoBreiteMm", String.valueOf(e.logoBreiteMm));
    }

    private static void alles(Properties p, Einstellungen e) {
        p.setProperty("projekttitel", e.projekttitel);
        Kopfdaten k = e.kopf;
        p.setProperty("kopf.ort", k.ort);
        p.setProperty("kopf.auftrag", k.auftrag);
        p.setProperty("kopf.linie", k.linieZug);
        p.setProperty("kopf.datum", k.datum);
        p.setProperty("kopf.wetter", k.wetter);
        p.setProperty("kopf.beobachter", k.beobachter);
        p.setProperty("kopf.nivellier", k.nivellier);
        p.setProperty("kopf.latte", k.latte);
        p.setProperty("kopf.bemerkung", k.bemerkung);
        p.setProperty("verteilung", e.verteilung.name());
        p.setProperty("fehlerklasse", String.valueOf(e.fehlerklasse));
        p.setProperty("mittelbildung", String.valueOf(e.mittelbildung));
        p.setProperty("erlaubteDifferenz", String.valueOf(e.erlaubteDifferenz));
        p.setProperty("nachkommastellen", String.valueOf(e.nachkommastellen));
        p.setProperty("nkAblesung", String.valueOf(e.nkAblesung));
        p.setProperty("erweitert", String.valueOf(e.erweitertesProtokoll));
        p.setProperty("stopp", String.valueOf(e.stoppBeiFehler));
        p.setProperty("alteBerechnungLoeschen", String.valueOf(e.alteBerechnungLoeschen));
        p.setProperty("neupunkteLoeschen", String.valueOf(e.neupunkteLoeschen));
    }

    private static void schreibe(Path d, Properties p, String kommentar) {
        try (OutputStream out = Files.newOutputStream(d)) {
            p.store(out, Version.titel() + " - " + kommentar);
        } catch (IOException ex) {
            // nicht kritisch
        }
    }

    // ------------------------------------------------------ Fenster und Ordner

    /** Sonstige Werte der Programmdatei (Fensterlage, Register, letzter Ordner). */
    public static String wert(String schluessel, String standard) {
        Properties p = lies(datei);
        return p == null ? standard : p.getProperty(schluessel, standard);
    }

    public static void setzeWerte(String... paare) {
        Properties p = lies(datei);
        if (p == null) {
            p = new Properties();
        }
        for (int i = 0; i + 1 < paare.length; i += 2) {
            p.setProperty(paare[i], paare[i + 1]);
        }
        schreibe(datei, p, "Programm- und Vorgabeeinstellungen");
    }

    public static String letzteMessdatei() {
        return wert("messdatei", "");
    }

    // ------------------------------------------------------------------ Hilfen

    private static double zahl(Properties p, String k, double standard) {
        return zahl(p.getProperty(k), standard);
    }

    private static double zahl(String s, double standard) {
        if (s == null) {
            return standard;
        }
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return standard;
        }
    }

    private static Double optZahl(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int ganz(Element p, String a, int standard, int min, int max) {
        String s = p.getAttribute(a).trim();
        try {
            int w = Integer.parseInt(s);
            return w < min || w > max ? standard : w;
        } catch (NumberFormatException ex) {
            return standard;
        }
    }
}
