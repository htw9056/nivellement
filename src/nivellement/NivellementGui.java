package nivellement;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Hauptfenster: Menüs wie Datei, Digital-Niv, Berechnen, Höhen und die Register Berechnung,
 * Stapeldatei (Editor), Protokoll, Höhenverzeichnis, Kopfdaten und Programmkonfiguration.
 */
public class NivellementGui extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final String[] MITTELBILDUNG = { "0 - mitteln", "1 - neu", "2 - vergleichen", "3 - ohne" };

    // Berechnung
    private final JTextField feldMessdatei = new JTextField(40);
    private final JTextField feldFestpunkte = new JTextField(40);
    private final JTextField feldHoehendatei = new JTextField(40);
    private final JComboBox<Einstellungen.Verteilung> auswahlVerteilung =
            new JComboBox<>(Einstellungen.Verteilung.values());
    private final JComboBox<Integer> auswahlKlasse = new JComboBox<>(new Integer[] { 1, 2, 3, 4 });
    private final JComboBox<String> auswahlMittelbildung = new JComboBox<>(MITTELBILDUNG);
    private final JTextField feldErlaubteDiff = new JTextField(5);
    private final JComboBox<Integer> auswahlNkH = new JComboBox<>(new Integer[] { 2, 3, 4, 5 });
    private final JComboBox<Integer> auswahlNkA = new JComboBox<>(new Integer[] { 2, 3, 4, 5 });
    private final JTextField feldBerNr = new JTextField("1", 6);
    private final JTextField feldVon = new JTextField(6);
    private final JTextField feldBis = new JTextField(6);
    private final JTextField feldHa = new JTextField(10);
    private final JTextField feldHe = new JTextField(10);
    private final JCheckBox boxAlteLoeschen = new JCheckBox("Alte Berechnung löschen", true);
    private final JCheckBox boxNeupunkte = new JCheckBox("Neupunkte vorher löschen");
    private final JCheckBox boxStopp = new JCheckBox("Höhen nicht speichern, wenn Fehlergrenze überschritten");
    private final JCheckBox boxErweitert = new JCheckBox("Erweitertes Protokoll (dh, v, Probe)");

    // Kopfdaten
    private final JTextField feldTitel = new JTextField(40);
    private final JTextField feldOrt = new JTextField(38);
    private final JTextField feldAuftrag = new JTextField(38);
    private final JTextField feldLinie = new JTextField(19);
    private final JTextField feldDatum = new JTextField(19);
    private final JTextField feldWetter = new JTextField(19);
    private final JTextField feldBeobachter = new JTextField(19);
    private final JTextField feldNivellier = new JTextField(19);
    private final JTextField feldLatte = new JTextField(19);
    private final JTextField feldBemerkung = new JTextField(38);

    // Programmkonfiguration
    private final JTextField feldFirma = new JTextField(40);
    private final JTextField[] feldKlasse = { new JTextField(20), new JTextField(20), new JTextField(20),
            new JTextField(20) };
    private final JTextField feldSchwell = new JTextField(5);
    private final JTextField feldMehrfach = new JTextField(5);
    private final JTextField feldLogo = new JTextField(40);
    private final JTextField feldLogoHoehe = new JTextField("20", 5);
    private final JTextField feldLogoBreite = new JTextField("0", 5);
    private final JLabel logoVorschau = new JLabel();

    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel statuszeile = new JLabel(" ");
    /** Verzögertes automatisches Speichern der Einstellungen nach jeder Änderung. */
    private final javax.swing.Timer speicherTimer = new javax.swing.Timer(700, ev -> speichereEinstellungen());
    private boolean wirdGeladen;
    /** Das zuletzt geöffnete Projekt (Ziel des automatischen Speicherns). */
    private Path geladenesProjekt;
    private final JTextArea ausgabe = new JTextArea(30, 120);
    private final EditorPanel editor;
    private final HoehenverzeichnisPanel hoehen;

    private static final int TAB_EDITOR = 1;
    private static final int TAB_PROTOKOLL = 2;
    private static final int TAB_HOEHEN = 3;

    public static void starte() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Standard-Look
            }
            new NivellementGui().setVisible(true);
        });
    }

    public NivellementGui() {
        super(Version.titel());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        try {
            setIconImages(List.of(javax.imageio.ImageIO.read(getClass().getResource("bilder/symbol64.png")),
                    javax.imageio.ImageIO.read(getClass().getResource("bilder/symbol.png"))));
        } catch (Exception ignored) {
            // ohne Symbol
        }
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                beenden();
            }
        });

        editor = new EditorPanel(this::messdatei, this::pruefen);
        hoehen = new HoehenverzeichnisPanel(this::hoehendatei);

        ausgabe.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ausgabe.setEditable(false);

        tabs.addTab("Berechnung", registerBerechnung());
        tabs.addTab("Stapeldatei", editor);
        JPanel protokollPanel = new JPanel(new BorderLayout());
        protokollPanel.add(reihe(knopf("Drucken ...", this::drucken), knopf("Druckvorschau", this::vorschau),
                knopf("Protokolldatei laden", () -> ladeProtokolldatei(messdatei()))), BorderLayout.NORTH);
        protokollPanel.add(new JScrollPane(ausgabe), BorderLayout.CENTER);
        tabs.addTab("Protokoll", protokollPanel);
        editor.zusatzKnopf("Drucken ...", this::drucken);
        editor.zusatzKnopf("Druckvorschau", this::vorschau);
        hoehen.zusatzKnopf("Drucken ...", this::drucken);
        hoehen.zusatzKnopf("Druckvorschau", this::vorschau);
        tabs.addTab("Höhenverzeichnis", hoehen);
        tabs.addTab("Kopfdaten", registerKopfdaten());
        tabs.addTab("Programmkonfiguration", registerKonfiguration());
        tabs.addChangeListener(ev -> {
            if (tabs.getSelectedIndex() == TAB_EDITOR) {
                editor.aktualisieren();
            } else if (tabs.getSelectedIndex() == TAB_HOEHEN) {
                hoehen.aktualisieren();
            }
        });
        getContentPane().add(tabs);
        statuszeile.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        getContentPane().add(statuszeile, BorderLayout.SOUTH);
        setJMenuBar(menues());

        speicherTimer.setRepeats(false);
        Path letzte = Konfiguration.letzteMessdatei().isBlank() ? null : Path.of(Konfiguration.letzteMessdatei());
        if (letzte != null && Files.exists(letzte)) {
            ladeProjekteinstellungen(letzte, false);
        } else {
            wirdGeladen = true;
            uebernehme(Konfiguration.lade());
            wirdGeladen = false;
            statuszeile.setText(" Kein Projekt gewählt - Einstellungen: Vorgaben");
        }
        beobachteAenderungen(getContentPane());
        tooltips();
        for (JTextField f : List.of(feldMessdatei, feldFestpunkte, feldHoehendatei, feldLogo)) {
            pfadFeld(f);
        }
        feldMessdatei.addActionListener(ev -> pfadUebernehmen());
        feldMessdatei.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                pfadUebernehmen();
            }
        });
        auswahlBreiten();
        javax.swing.ToolTipManager.sharedInstance().setDismissDelay(15000);
        pack();
        fensterWiederherstellen();
    }

    /** Fensterlage und Register der letzten Sitzung. */
    private void fensterWiederherstellen() {
        try {
            int x = Integer.parseInt(Konfiguration.wert("fenster.x", "-1"));
            int y = Integer.parseInt(Konfiguration.wert("fenster.y", "-1"));
            int b = Integer.parseInt(Konfiguration.wert("fenster.breite", "1100"));
            int h = Integer.parseInt(Konfiguration.wert("fenster.hoehe", "760"));
            java.awt.Rectangle schirm = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getMaximumWindowBounds();
            setSize(Math.max(800, Math.min(b, schirm.width)), Math.max(500, Math.min(h, schirm.height)));
            if (x >= 0 && y >= 0 && x < schirm.width - 100 && y < schirm.height - 100) {
                setLocation(x, y);
            } else {
                setLocationRelativeTo(null);
            }
            if (Boolean.parseBoolean(Konfiguration.wert("fenster.max", "false"))) {
                setExtendedState(MAXIMIZED_BOTH);
            }
            int r = Integer.parseInt(Konfiguration.wert("register", "0"));
            if (r >= 0 && r < tabs.getTabCount() && r != TAB_PROTOKOLL) {
                tabs.setSelectedIndex(r);
            }
        } catch (NumberFormatException ex) {
            setSize(1100, 760);
            setLocationRelativeTo(null);
        }
    }

    /** Jede Änderung eines Eingabefelds löst (verzögert) das Speichern der Einstellungen aus. */
    private void beobachteAenderungen(java.awt.Container c) {
        for (Component k : c.getComponents()) {
            if (k == feldMessdatei) {
                continue;
            }
            if (k instanceof JTextField) {
                ((JTextField) k).getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    @Override
                    public void insertUpdate(javax.swing.event.DocumentEvent e) {
                        geaendert();
                    }

                    @Override
                    public void removeUpdate(javax.swing.event.DocumentEvent e) {
                        geaendert();
                    }

                    @Override
                    public void changedUpdate(javax.swing.event.DocumentEvent e) {
                        geaendert();
                    }
                });
            } else if (k instanceof JComboBox) {
                ((JComboBox<?>) k).addActionListener(ev -> geaendert());
            } else if (k instanceof JCheckBox) {
                ((JCheckBox) k).addActionListener(ev -> geaendert());
            } else if (k instanceof java.awt.Container && !(k instanceof EditorPanel)
                    && !(k instanceof HoehenverzeichnisPanel)) {
                beobachteAenderungen((java.awt.Container) k);
            }
        }
    }

    private void geaendert() {
        if (!wirdGeladen) {
            speicherTimer.restart();
        }
    }

    /**
     * Wechselt das Projekt: Einstellungen des bisherigen Projekts werden gespeichert, die des neuen geladen.
     * Ein neues Projekt erhält die zuletzt verwendeten Einstellungen als Vorgabe (ohne Festpunkt- und
     * Höhendatei des alten Projekts).
     */
    private void ladeProjekteinstellungen(Path p, boolean bisheriges) {
        if (bisheriges) {
            speicherTimer.stop();
            speichereEinstellungen();
        }
        geladenesProjekt = p.toAbsolutePath().normalize();
        StringBuilder herkunft = new StringBuilder();
        Einstellungen e = Konfiguration.fuerProjekt(p, herkunft);
        wirdGeladen = true;
        feldMessdatei.setText(p.toAbsolutePath().toString());
        uebernehme(e);
        wirdGeladen = false;
        ladeProtokolldatei(p);
        statuszeile.setText(" Projekt " + Projekt.name(p) + "  -  Einstellungen: " + herkunft
                + "  -  Änderungen werden automatisch gespeichert");
        if (Projekt.istStapeldatei(p) && !Files.exists(Konfiguration.projektdatei(p))) {
            speichereEinstellungen();
        }
    }

    // ------------------------------------------------------------------ Menüs

    private JMenuBar menues() {
        JMenuBar mb = new JMenuBar();
        JMenu datei = new JMenu("Datei");
        datei.add(eintrag("Projekt öffnen ...", () -> waehle(feldMessdatei, true)));
        datei.add(eintrag("Neues Projekt ...", this::neuesProjekt));
        datei.add(eintrag("Stapeldatei bearbeiten", () -> tabs.setSelectedIndex(TAB_EDITOR)));
        datei.addSeparator();
        JMenuItem druck = eintrag("Drucken ...", this::drucken);
        druck.setAccelerator(javax.swing.KeyStroke.getKeyStroke("control P"));
        datei.add(druck);
        datei.add(eintrag("Druckvorschau", this::vorschau));
        datei.addSeparator();
        datei.add(eintrag("Beenden", this::beenden));
        JMenu digi = new JMenu("Digital-Niv");
        digi.add(eintrag("Rohdaten (GSI / DAT) → Stapeldatei ...", this::rohdatenUebernehmen));
        JMenu ber = new JMenu("Berechnen");
        ber.add(eintrag("Messdaten prüfen", this::pruefen));
        ber.add(eintrag("Stapelberechnung", this::auswerten));
        ber.add(eintrag("Berechnung zeigen", () -> tabs.setSelectedIndex(TAB_PROTOKOLL)));
        JMenu h = new JMenu("Höhen");
        h.add(eintrag("Höhenverzeichnis", () -> tabs.setSelectedIndex(TAB_HOEHEN)));
        JMenu hilfe = new JMenu("?");
        JMenuItem handbuch = eintrag("Handbuch", () -> HilfeFenster.zeige(this, kontextAnker()));
        handbuch.setAccelerator(javax.swing.KeyStroke.getKeyStroke("F1"));
        hilfe.add(handbuch);
        hilfe.add(eintrag("Schnellstart", () -> HilfeFenster.zeige(this, "schnellstart")));
        hilfe.add(eintrag("Rechenweg und Formeln", () -> HilfeFenster.zeige(this, "rechenweg")));
        hilfe.add(eintrag("Meldungen", () -> HilfeFenster.zeige(this, "meldungen")));
        hilfe.add(eintrag("Übernahme aus NIGRA", () -> HilfeFenster.zeige(this, "nigra")));
        hilfe.add(eintrag("Aufruf in der Konsole", () -> HilfeFenster.zeige(this, "konsole")));
        hilfe.addSeparator();
        hilfe.add(eintrag("Über", () -> JOptionPane.showMessageDialog(this,
                Version.titel() + "\nKlassische Nivellementauswertung\n\n"
                        + "Lizenz: MIT (freie Software)\n"
                        + "Java-Laufzeit: Eclipse Temurin (GPLv2 mit Classpath Exception)\n\n"
                        + "Einstellungen: " + Konfiguration.datei,
                "Über", JOptionPane.INFORMATION_MESSAGE)));
        mb.add(datei);
        mb.add(digi);
        mb.add(ber);
        mb.add(h);
        mb.add(hilfe);
        return mb;
    }

    /**
     * Pfadfelder: Beim Einfügen werden Anführungszeichen entfernt (Windows "Als Pfad kopieren")
     * sowie führende und folgende Leerzeichen.
     */
    private static void pfadFeld(JTextField f) {
        ((javax.swing.text.AbstractDocument) f.getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
            private String bereinigen(String t) {
                return t == null ? null : t.replace("\"", "").replace("\r", "").replace("\n", "").strip();
            }

            @Override
            public void insertString(FilterBypass fb, int pos, String t, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                super.insertString(fb, pos, bereinigen(t), a);
            }

            @Override
            public void replace(FilterBypass fb, int pos, int laenge, String t, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                super.replace(fb, pos, laenge, t != null && t.length() > 1 ? bereinigen(t) : t, a);
            }
        });
        f.setToolTipText((f.getToolTipText() == null ? "" : f.getToolTipText().replace("<html>", "").replace("</html>", "")
                + "<br>") + "Ein kopierter Pfad kann direkt eingefügt werden (Strg+V).");
        if (!f.getToolTipText().startsWith("<html>")) {
            f.setToolTipText("<html>" + f.getToolTipText() + "</html>");
        }
    }

    /** Ein von Hand eingegebener oder eingefügter Pfad im Projektfeld öffnet das Projekt. */
    private void pfadUebernehmen() {
        String t = feldMessdatei.getText().trim();
        if (t.isEmpty()) {
            return;
        }
        Path p;
        try {
            p = Path.of(t).toAbsolutePath().normalize();
        } catch (Exception ex) {
            return;
        }
        if (p.equals(geladenesProjekt) || !Files.isRegularFile(p)) {
            if (!Files.exists(p) && !t.isEmpty()) {
                statuszeile.setText(" Datei nicht gefunden: " + t);
            }
            return;
        }
        if (!ungespeichertOk()) {
            feldMessdatei.setText(geladenesProjekt == null ? "" : geladenesProjekt.toString());
            return;
        }
        Konfiguration.setzeWerte("ordner", p.getParent().toString());
        ladeProjekteinstellungen(p, true);
        editor.laden();
        hoehen.laden();
        angebotMdb();
    }

    /** Mindestbreite der Auswahlfelder, damit der Wert auch mit Windows-Schriften lesbar ist. */
    private void auswahlBreiten() {
        auswahlKlasse.setPrototypeDisplayValue(4444);
        auswahlNkH.setPrototypeDisplayValue(4444);
        auswahlNkA.setPrototypeDisplayValue(4444);
        auswahlMittelbildung.setPrototypeDisplayValue("2 - vergleichen  ");
        for (JComboBox<?> k : List.of(auswahlKlasse, auswahlNkH, auswahlNkA, auswahlMittelbildung)) {
            java.awt.Dimension d = k.getPreferredSize();
            k.setPreferredSize(new java.awt.Dimension(Math.max(d.width, k == auswahlMittelbildung ? 170 : 70),
                    d.height));
            k.setMinimumSize(k.getPreferredSize());
        }
    }

    // ------------------------------------------------------------- Drucken

    /** Druckt den Inhalt des aktuellen Registers (Protokoll, Stapeldatei oder Höhenverzeichnis). */
    private void drucken() {
        Druck d = druckauftrag();
        if (d != null) {
            d.drucken(this);
        }
    }

    private void vorschau() {
        Druck d = druckauftrag();
        if (d != null) {
            d.vorschau(this);
        }
    }

    private Druck druckauftrag() {
        Einstellungen e;
        try {
            e = lies(false);
        } catch (Exception ex) {
            e = new Einstellungen();
        }
        Path m = messdatei();
        String projekt = m == null ? "" : Projekt.name(m);
        int t = tabs.getSelectedIndex();
        if (t == TAB_EDITOR) {
            if (editor.text().isBlank()) {
                JOptionPane.showMessageDialog(this, "Die Stapeldatei ist leer.");
                return null;
            }
            return new Druck("Messdaten (Stapeldatei " + (m == null ? "" : m.getFileName()) + ")", projekt,
                    Druck.einBlock(editor.text()), e);
        }
        if (t == TAB_HOEHEN) {
            return new Druck("Höhenverzeichnis", projekt, Druck.einBlock(hoehen.drucktext()), e);
        }
        if (ausgabe.getText().isBlank()) {
            ladeProtokolldatei(m);
        }
        if (ausgabe.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Es liegt noch kein Protokoll vor. Bitte zuerst berechnen.");
            return null;
        }
        tabs.setSelectedIndex(TAB_PROTOKOLL);
        return new Druck("Berechnungsprotokoll", projekt, Druck.protokollBloecke(ausgabe.getText(), e.firma), e);
    }

    /** Zeigt eine vorhandene Protokolldatei des Projekts an (z. B. nach dem Öffnen). */
    private void ladeProtokolldatei(Path m) {
        if (m == null) {
            return;
        }
        Path prot = Projekt.protokoll(m);
        try {
            if (Files.exists(prot)) {
                ausgabe.setText(Files.readString(prot, java.nio.charset.StandardCharsets.UTF_8));
                ausgabe.setCaretPosition(0);
            } else {
                ausgabe.setText("");
            }
        } catch (Exception ex) {
            ausgabe.setText("");
        }
    }

    private void waehleLogo() {
        java.io.File start = !feldLogo.getText().isBlank() ? new java.io.File(feldLogo.getText().trim())
                : new java.io.File(Konfiguration.wert("ordner", "."));
        java.io.File f = Dateiauswahl.oeffnen(this, "Logo für den Druckkopf wählen", start,
                "Bilder (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp");
        if (f != null) {
            feldLogo.setText(f.getAbsolutePath());
        }
    }

    private void zeigeLogo() {
        java.awt.Image img = Druck.ladeLogo(feldLogo.getText());
        if (img == null) {
            logoVorschau.setIcon(null);
            logoVorschau.setText(feldLogo.getText().isBlank() ? "(kein Logo)" : "Bild kann nicht gelesen werden");
            return;
        }
        int h = 60;
        int w = Math.max(1, img.getWidth(null) * h / Math.max(1, img.getHeight(null)));
        logoVorschau.setText("");
        logoVorschau.setIcon(new javax.swing.ImageIcon(img.getScaledInstance(Math.min(w, 300), h,
                java.awt.Image.SCALE_SMOOTH)));
    }

    /** Handbuchkapitel passend zum aktuellen Register (F1). */
    private String kontextAnker() {
        switch (tabs.getSelectedIndex()) {
            case 1:
                return "stapeldatei";
            case 2:
                return "protokoll";
            case 3:
                return "hoehenverzeichnis";
            case 4:
                return "kopfdaten";
            case 5:
                return "konfiguration";
            default:
                return "berechnung";
        }
    }

    /** Hinweistexte an den Eingabefeldern. */
    private void tooltips() {
        feldMessdatei.setToolTipText("Stapeldatei (.NIG) des Projekts oder direkt eine Leica-GSI- bzw. Trimble-DAT-Datei");
        feldFestpunkte.setToolTipText("<html>Optional: Textdatei mit Punktnummer und Höhe je Zeile.<br>"
                + "Diese Punkte gelten als Festpunkte und haben Vorrang vor der Höhendatei.</html>");
        feldHoehendatei.setToolTipText("Leer lassen für <Projekt>_Hoehen.csv neben der Stapeldatei");
        auswahlVerteilung.setToolTipText("<html>Verteilung des Abschlussfehlers auf die Aufstellungen:<br>"
                + "streckenproportional (Zielweiten R+V) oder gleichmäßig je Aufstellung</html>");
        auswahlKlasse.setToolTipText("Fehlerklasse 1-4, Formeln im Register Programmkonfiguration");
        auswahlMittelbildung.setToolTipText("<html>Was geschieht mit Punkten, die bereits in der Höhendatei stehen:<br>"
                + "0 mitteln, 1 neue Höhe einführen, 2 nur vergleichen, 3 weder speichern noch vergleichen</html>");
        feldErlaubteDiff.setToolTipText("Grenzwert in 0,1 mm für die Differenz zweier Ablesungen und die Standpunktdifferenz");
        auswahlNkH.setToolTipText("Dezimalstellen der Höhen im Protokoll (intern wird voll gerechnet)");
        auswahlNkA.setToolTipText("Dezimalstellen der Lattenablesungen im Protokoll");
        feldBerNr.setToolTipText("Erste Berechnungsnummer bei der direkten Berechnung von GSI/DAT-Dateien");
        feldHa.setToolTipText("Anfangshöhe von Hand (nur erste Linie, hat Vorrang)");
        feldHe.setToolTipText("Endhöhe von Hand (nur erste Linie, hat Vorrang)");
        feldVon.setToolTipText("Nur Berechnungen ab dieser Nummer rechnen (leer = alle)");
        feldBis.setToolTipText("Nur Berechnungen bis zu dieser Nummer rechnen (leer = alle)");
        boxAlteLoeschen.setToolTipText("Aus: neues Protokoll an das vorhandene anhängen");
        boxNeupunkte.setToolTipText("<html>Vor der Berechnung alle Punkte außer Festpunkten aus der Höhendatei löschen.<br>"
                + "Nötig bei vollständiger Neuberechnung mit Mittelbildung 'mitteln'.</html>");
        boxStopp.setToolTipText("Linien mit überschrittener Fehlergrenze werden protokolliert, aber nicht gespeichert");
        boxErweitert.setToolTipText("Zusätzlich dh und Verbesserung je Punkt sowie die Rechenprobe");
        feldFirma.setToolTipText("Erste Zeile jedes Protokolls");
        for (javax.swing.JTextField k : feldKlasse) {
            k.setToolTipText("<html>Zulässiger Abschlussfehler F in mm, R = Messweg in km.<br>"
                    + "Erlaubt: Zahlen, R, + - * / ^, Klammern, Sqrt(), Abs()</html>");
        }
        feldSchwell.setToolTipText("Warnung, wenn |w| größer als dieser Anteil der Fehlergrenze ist (1 = aus)");
        feldMehrfach.setToolTipText("Zulässige Differenz in mm bei mehrfach bestimmten Punkten");
        feldTitel.setToolTipText("Erscheint über jeder Berechnung im Protokoll (C-Zeile der Stapeldatei)");
    }

    private static JMenuItem eintrag(String text, Runnable a) {
        JMenuItem m = new JMenuItem(text);
        m.addActionListener(ev -> a.run());
        return m;
    }

    // --------------------------------------------------------------- Register

    private JPanel registerBerechnung() {
        JPanel p = panel();
        GridBagConstraints c = gbc();
        int y = 0;
        zeile(p, c, y++, "Projekt / Messdatei (NIG, GSI, DAT):", feldMessdatei,
                reihe(dateiKnopf(feldMessdatei, true), knopf("Neu ...", this::neuesProjekt)));
        zeile(p, c, y++, "Rohdaten übernehmen:", reihe(knopf("Rohdaten (GSI / DAT) → Stapeldatei ...",
                this::rohdatenUebernehmen), new JLabel("Kopfdaten und Parameter werden aus diesem Fenster übernommen")),
                null);
        zeile(p, c, y++, "Festpunktdatei (optional):", feldFestpunkte, dateiKnopf(feldFestpunkte, false));
        zeile(p, c, y++, "Höhendatei (leer = <Projekt>_Hoehen.csv):", feldHoehendatei,
                dateiKnopf(feldHoehendatei, false));
        zeile(p, c, y++, "Fehlerverteilung:", auswahlVerteilung, null);
        zeile(p, c, y++, "Fehlerklasse / Mittelbildung:", reihe(auswahlKlasse, new JLabel("Mittelbildung:"),
                auswahlMittelbildung, new JLabel("Erlaubte Diff. 2 Ablesungen [0,1 mm]:"), feldErlaubteDiff), null);
        zeile(p, c, y++, "Dezimalstellen:", reihe(new JLabel("Höhen"), auswahlNkH, new JLabel("Lattenablesung"),
                auswahlNkA, new JLabel("Berechnungs-Nr. ab (GSI/DAT)"), feldBerNr), null);
        zeile(p, c, y++, "Höhen manuell [m]:", reihe(new JLabel("Anfang"), feldHa, new JLabel("Ende"), feldHe,
                new JLabel("(nur erste Linie, leer = automatisch)")), null);
        zeile(p, c, y++, "Stapelberechnung:", reihe(new JLabel("Berechnungs-Nr. von"), feldVon, new JLabel("bis"),
                feldBis, new JLabel("(leer = alle)"), boxAlteLoeschen), null);
        zeile(p, c, y++, "Optionen:", reihe(boxNeupunkte, boxErweitert), null);
        zeile(p, c, y++, "", reihe(boxStopp), null);
        zeile(p, c, y++, "", new JLabel("Bei Stapeldateien (.NIG) gelten Kopfdaten und Parameter aus der Datei."),
                null);

        JButton pruef = knopf("Messdaten prüfen", this::pruefen);
        JButton start = knopf("Stapelberechnung", this::auswerten);
        start.setFont(start.getFont().deriveFont(Font.BOLD, 14f));
        c.gridx = 1;
        c.gridy = y;
        c.fill = GridBagConstraints.NONE;
        p.add(reihe(start, pruef), c);
        c.gridy = y + 1;
        c.weighty = 1;
        p.add(new JLabel(" "), c);
        return p;
    }

    private JPanel registerKopfdaten() {
        JPanel p = panel();
        GridBagConstraints c = gbc();
        int y = 0;
        zeile(p, c, y++, "Projekttitel (C-Zeile):", feldTitel, null);
        zeile(p, c, y++, "Ort (max. 38 Zeichen):", feldOrt, null);
        zeile(p, c, y++, "Auftrag (max. 38 Zeichen):", feldAuftrag, null);
        zeile(p, c, y++, "Linie/Zug, Datum:", reihe(feldLinie, new JLabel("Datum"), feldDatum), null);
        zeile(p, c, y++, "Wetter, Beobachter:", reihe(feldWetter, new JLabel("Beobachter"), feldBeobachter), null);
        zeile(p, c, y++, "Nivellier, Latte:", reihe(feldNivellier, new JLabel("Latte"), feldLatte), null);
        zeile(p, c, y++, "Bemerkung (max. 38 Zeichen):", feldBemerkung, null);
        zeile(p, c, y++, "", new JLabel("Einzeilige Felder werden in der Stapeldatei auf 19 Zeichen begrenzt."),
                null);
        c.gridy = y;
        c.weighty = 1;
        p.add(new JLabel(" "), c);
        return p;
    }

    private JPanel registerKonfiguration() {
        JPanel p = panel();
        GridBagConstraints c = gbc();
        int y = 0;
        zeile(p, c, y++, "Firma (Kopfzeile):", feldFirma, null);
        for (int k = 0; k < 4; k++) {
            zeile(p, c, y++, "Fehlerklasse " + (k + 1) + ":  F [mm] =", feldKlasse[k],
                    new JLabel("R = Messweg in km, z. B. 2 + 3*Sqrt(R)"));
        }
        zeile(p, c, y++, "Schwellfaktor:", reihe(feldSchwell,
                new JLabel("Warnung ab diesem Anteil der Fehlergrenze (1 = aus, z. B. 0.5)")), null);
        zeile(p, c, y++, "Mehrfachbestimmungen:", reihe(feldMehrfach,
                new JLabel("mm zulässige Differenz (Mittelbildungsprotokoll)")), null);
        zeile(p, c, y++, " ", new JLabel(" "), null);
        zeile(p, c, y++, "Logo im Druckkopf:", feldLogo, reihe(knopf("...", this::waehleLogo),
                knopf("Entfernen", () -> feldLogo.setText(""))));
        zeile(p, c, y++, "Logo-Größe:", reihe(new JLabel("Höhe"), feldLogoHoehe, new JLabel("mm,  max. Breite"),
                feldLogoBreite, new JLabel("mm (0 = nur Höhe)"), knopf("Druckvorschau ...", this::vorschau)), null);
        zeile(p, c, y++, "", logoVorschau, null);
        feldLogo.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                zeigeLogo();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                zeigeLogo();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                zeigeLogo();
            }
        });
        c.gridy = y;
        c.weighty = 1;
        p.add(new JLabel(" "), c);
        return p;
    }

    private static JPanel panel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        return p;
    }

    private static GridBagConstraints gbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 5);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }

    private static JPanel reihe(Component... k) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (Component x : k) {
            p.add(x);
        }
        return p;
    }

    private static JButton knopf(String text, Runnable a) {
        JButton b = new JButton(text);
        b.addActionListener(ev -> a.run());
        return b;
    }

    private static void zeile(JPanel p, GridBagConstraints c, int y, String text, Component feld, Component extra) {
        c.gridy = y;
        c.gridx = 0;
        c.weightx = 0;
        c.weighty = 0;
        p.add(new JLabel(text), c);
        c.gridx = 1;
        c.weightx = 1;
        p.add(feld, c);
        if (extra != null) {
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            p.add(extra, c);
            c.fill = GridBagConstraints.HORIZONTAL;
        }
    }

    private JButton dateiKnopf(JTextField feld, boolean messdatei) {
        return knopf("...", () -> waehle(feld, messdatei));
    }

    private void waehle(JTextField feld, boolean messdatei) {
        java.io.File start = !feld.getText().isBlank() ? new java.io.File(feld.getText().trim())
                : messdatei() != null ? Projekt.ordner(messdatei()).toFile()
                        : new java.io.File(Konfiguration.wert("ordner", "."));
        java.io.File gewaehlt = messdatei
                ? Dateiauswahl.oeffnen(this, "Projekt bzw. Messdatei öffnen", start, "Nivellement (NIG, GSI, DAT)",
                        "nig", "gsi", "dat", "txt")
                : Dateiauswahl.oeffnen(this, "Datei wählen", start, "Alle Dateien");
        if (gewaehlt != null) {
            if (messdatei && !ungespeichertOk()) {
                return;
            }
            Konfiguration.setzeWerte("ordner", gewaehlt.getAbsoluteFile().getParent());
            if (messdatei) {
                ladeProjekteinstellungen(gewaehlt.toPath(), true);
                editor.laden();
                hoehen.laden();
                angebotMdb();
            } else {
                feld.setText(gewaehlt.getAbsolutePath());
            }
        }
    }

    // ---------------------------------------------------------------- Aktionen

    Path messdatei() {
        String t = feldMessdatei.getText().trim();
        return t.isEmpty() ? null : Path.of(t);
    }

    Path hoehendatei() {
        Path m = messdatei();
        if (!feldHoehendatei.getText().isBlank()) {
            return Path.of(feldHoehendatei.getText().trim());
        }
        return m == null ? null : Projekt.hoehendatei(m);
    }

    private boolean ungespeichertOk() {
        if (editor.hatAenderungen() || hoehen.hatAenderungen()) {
            int w = JOptionPane.showConfirmDialog(this,
                    "Es gibt ungespeicherte Änderungen (Stapeldatei / Höhenverzeichnis). Jetzt speichern?",
                    "Änderungen", JOptionPane.YES_NO_CANCEL_OPTION);
            if (w == JOptionPane.CANCEL_OPTION || w == JOptionPane.CLOSED_OPTION) {
                return false;
            }
            if (w == JOptionPane.YES_OPTION) {
                editor.speichern();
                if (hoehen.hatAenderungen()) {
                    hoehen.speichern();
                }
            }
        }
        return true;
    }

    /** Gibt es zum Projekt noch keine Höhendatei, aber eine NIGRA-MDB gleichen Namens, wird der Import angeboten. */
    private void angebotMdb() {
        Path m = messdatei();
        Path hd = hoehendatei();
        if (m == null || hd == null || Files.exists(hd)) {
            return;
        }
        for (String ext : new String[] { ".mdb", ".MDB", ".Mdb" }) {
            Path mdb = Projekt.ordner(m).resolve(Projekt.name(m) + ext);
            if (Files.exists(mdb)) {
                if (JOptionPane.showConfirmDialog(this, "Zum Projekt gibt es die NIGRA-Höhendatei " + mdb.getFileName()
                        + ".\nJetzt in die Höhendatei " + hd.getFileName() + " übernehmen?", "NIGRA-Höhendatei",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    hoehen.importiereMdb(mdb);
                    hoehen.speichern();
                    tabs.setSelectedIndex(TAB_HOEHEN);
                }
                return;
            }
        }
    }

    private void neuesProjekt() {
        java.io.File start = messdatei() != null ? Projekt.ordner(messdatei()).toFile()
                : new java.io.File(Konfiguration.wert("ordner", "."));
        java.io.File gewaehlt = Dateiauswahl.speichern(this, "Neues Projekt (Stapeldatei .NIG)", start, "Projekt.NIG",
                "Stapeldatei (NIG)", "NIG");
        if (gewaehlt == null || !ungespeichertOk()) {
            return;
        }
        Path p = gewaehlt.toPath();
        if (!Projekt.istStapeldatei(p)) {
            p = p.resolveSibling(p.getFileName() + ".NIG");
        }
        try {
            if (!Files.exists(p)) {
                Projekt.schreibeStapeldatei(p, "");
            }
            Konfiguration.setzeWerte("ordner", p.toAbsolutePath().getParent().toString());
            ladeProjekteinstellungen(p, true);
            editor.laden();
            hoehen.laden();
            JOptionPane.showMessageDialog(this, "Projekt " + Projekt.name(p) + " angelegt.\n"
                    + "Alle Einstellungen und Kopfdaten wurden vom letzten Projekt übernommen\n"
                    + "(Festpunkt- und Höhendatei ausgenommen).\n\n"
                    + "Übernehmen Sie jetzt Rohdaten (Digital-Niv) oder erfassen Sie Messdaten im Register Stapeldatei.");
        } catch (Exception ex) {
            fehler(ex);
        }
    }

    private void rohdatenUebernehmen() {
        try {
            Path nig = messdatei();
            if (nig == null || !Projekt.istStapeldatei(nig)) {
                int w = JOptionPane.showConfirmDialog(this,
                        "Rohdaten werden an eine Stapeldatei (.NIG) angehängt.\nJetzt ein Projekt anlegen bzw. wählen?",
                        "Kein Projekt", JOptionPane.OK_CANCEL_OPTION);
                if (w == JOptionPane.OK_OPTION) {
                    neuesProjekt();
                }
                nig = messdatei();
                if (nig == null || !Projekt.istStapeldatei(nig)) {
                    return;
                }
            }
            editor.speichern();
            java.io.File[] dateien = Dateiauswahl.oeffnenMehrere(this, "Rohdaten wählen (Leica GSI / Trimble DAT)",
                    Projekt.ordner(nig).toFile(), "Rohdaten (GSI, DAT)", "gsi", "dat", "txt");
            if (dateien.length == 0) {
                return;
            }
            Einstellungen e = lies();
            int von = Projekt.naechsteBerechnungsnummer(nig);
            int n = 0;
            StringBuilder info = new StringBuilder();
            for (java.io.File f : dateien) {
                int k = Projekt.uebernehmeRohdaten(f.toPath(), nig, e);
                n += k;
                info.append(f.getName()).append(": ").append(k).append(" Linie(n)\n");
            }
            speichereEinstellungen();
            editor.laden();
            tabs.setSelectedIndex(TAB_EDITOR);
            JOptionPane.showMessageDialog(this, info + "\n" + n + " Linie(n) als Berechnungs-Nr. " + von + " bis "
                    + (von + n - 1) + " angehängt.\nBitte Kopfdaten und Punktnummern prüfen und dann berechnen.");
        } catch (Exception ex) {
            fehler(ex);
        }
    }

    private void pruefen() {
        try {
            Path m = messdatei();
            if (m == null) {
                throw new Exception("Bitte ein Projekt bzw. eine Messdatei wählen.");
            }
            editor.speichern();
            List<Linie> l = Einleser.liesMessdatei(m);
            JTextArea t = new JTextArea(Pruefung.pruefe(l), 20, 90);
            t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            t.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(t), "Messdaten prüfen - " + m.getFileName(),
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            fehler(ex);
        }
    }

    private void auswerten() {
        try {
            Path m = messdatei();
            if (m == null) {
                throw new Exception("Bitte ein Projekt bzw. eine Messdatei wählen.");
            }
            editor.speichern();
            if (hoehen.hatAenderungen()) {
                if (JOptionPane.showConfirmDialog(this, "Änderungen im Höhenverzeichnis vor der Berechnung speichern?",
                        "Höhenverzeichnis", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    hoehen.speichern();
                }
            }
            Einstellungen e = lies();
            Main.Ergebnis r = Main.fuehreAus(m, e);
            speichereEinstellungen();
            ausgabe.setText(r.protokoll + "\nGespeichert:\n  " + r.protokollDatei + "\n  " + r.hoehenDatei + "\n");
            ausgabe.setCaretPosition(0);
            hoehen.laden();
            tabs.setSelectedIndex(TAB_PROTOKOLL);
        } catch (Exception ex) {
            fehler(ex);
        }
    }

    private void beenden() {
        if (ungespeichertOk()) {
            speicherTimer.stop();
            speichereEinstellungen();
            boolean max = (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
            if (max) {
                Konfiguration.setzeWerte("fenster.max", "true", "register", String.valueOf(tabs.getSelectedIndex()));
            } else {
                Konfiguration.setzeWerte("fenster.max", "false", "fenster.x", String.valueOf(getX()), "fenster.y",
                        String.valueOf(getY()), "fenster.breite", String.valueOf(getWidth()), "fenster.hoehe",
                        String.valueOf(getHeight()), "register", String.valueOf(tabs.getSelectedIndex()));
            }
            dispose();
            System.exit(0);
        }
    }

    private void fehler(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    // ----------------------------------------------------------- Einstellungen

    private void speichereEinstellungen() {
        try {
            Konfiguration.speichere(lies(false),
                    geladenesProjekt != null ? geladenesProjekt.toString() : feldMessdatei.getText().trim());
        } catch (Exception ignored) {
            // ungültige Eingaben werden beim Berechnen gemeldet
        }
    }

    private void uebernehme(Einstellungen e) {
        feldFestpunkte.setText(e.festpunktDatei == null ? "" : e.festpunktDatei);
        feldHoehendatei.setText(e.hoehenDatei == null ? "" : e.hoehenDatei);
        auswahlVerteilung.setSelectedItem(e.verteilung);
        auswahlKlasse.setSelectedItem(e.fehlerklasse);
        auswahlMittelbildung.setSelectedIndex(Math.max(0, Math.min(3, e.mittelbildung)));
        feldErlaubteDiff.setText(zahl(e.erlaubteDifferenz));
        auswahlNkH.setSelectedItem(e.nachkommastellen);
        auswahlNkA.setSelectedItem(e.nkAblesung);
        boxErweitert.setSelected(e.erweitertesProtokoll);
        boxStopp.setSelected(e.stoppBeiFehler);
        boxAlteLoeschen.setSelected(e.alteBerechnungLoeschen);
        boxNeupunkte.setSelected(e.neupunkteLoeschen);
        feldBerNr.setText(String.valueOf(e.berechnungsnummerStart));
        feldVon.setText(e.berNrVon == null ? "" : String.valueOf(e.berNrVon));
        feldBis.setText(e.berNrBis == null ? "" : String.valueOf(e.berNrBis));
        feldHa.setText(e.manuelleAnfangshoehe == null ? "" : zahl(e.manuelleAnfangshoehe));
        feldHe.setText(e.manuelleEndhoehe == null ? "" : zahl(e.manuelleEndhoehe));
        feldLogo.setText(e.logoDatei == null ? "" : e.logoDatei);
        feldLogoHoehe.setText(zahl(e.logoHoeheMm));
        feldLogoBreite.setText(zahl(e.logoBreiteMm));
        zeigeLogo();
        feldTitel.setText(e.projekttitel);
        Kopfdaten k = e.kopf;
        feldOrt.setText(k.ort);
        feldAuftrag.setText(k.auftrag);
        feldLinie.setText(k.linieZug);
        feldDatum.setText(k.datum);
        feldWetter.setText(k.wetter);
        feldBeobachter.setText(k.beobachter);
        feldNivellier.setText(k.nivellier);
        feldLatte.setText(k.latte);
        feldBemerkung.setText(k.bemerkung);
        feldFirma.setText(e.firma);
        for (int i = 0; i < 4; i++) {
            feldKlasse[i].setText(e.fehlerformeln[i]);
        }
        feldSchwell.setText(zahl(e.schwellfaktor));
        feldMehrfach.setText(zahl(e.mehrfachGrenzeMm));
    }

    private Einstellungen lies() throws Exception {
        return lies(true);
    }

    /**
     * Liest alle Eingaben. Streng: ungültige Zahlen führen zu einer Fehlermeldung (vor dem Rechnen).
     * Tolerant: ungültige oder halb eingegebene Zahlen werden übersprungen, damit beim automatischen
     * Speichern die übrigen Einstellungen trotzdem gesichert werden.
     */
    private Einstellungen lies(boolean streng) throws Exception {
        Einstellungen e = new Einstellungen();
        e.festpunktDatei = feldFestpunkte.getText().trim();
        e.hoehenDatei = feldHoehendatei.getText().trim();
        e.verteilung = (Einstellungen.Verteilung) auswahlVerteilung.getSelectedItem();
        e.fehlerklasse = (Integer) auswahlKlasse.getSelectedItem();
        e.mittelbildung = auswahlMittelbildung.getSelectedIndex();
        e.erlaubteDifferenz = zahl(feldErlaubteDiff, "Erlaubte Differenz", e.erlaubteDifferenz, streng);
        e.nachkommastellen = (Integer) auswahlNkH.getSelectedItem();
        e.nkAblesung = (Integer) auswahlNkA.getSelectedItem();
        e.berechnungsnummerStart = (int) zahl(feldBerNr, "Berechnungs-Nr. ab", 1, streng);
        e.berNrVon = optGanz(feldVon, "Berechnungs-Nr. von", streng);
        e.berNrBis = optGanz(feldBis, "Berechnungs-Nr. bis", streng);
        e.alteBerechnungLoeschen = boxAlteLoeschen.isSelected();
        e.manuelleAnfangshoehe = optZahl(feldHa, "Anfangshöhe", streng);
        e.manuelleEndhoehe = optZahl(feldHe, "Endhöhe", streng);
        e.neupunkteLoeschen = boxNeupunkte.isSelected();
        e.stoppBeiFehler = boxStopp.isSelected();
        e.erweitertesProtokoll = boxErweitert.isSelected();
        e.projekttitel = feldTitel.getText().trim();
        Kopfdaten k = e.kopf;
        k.ort = feldOrt.getText().trim();
        k.auftrag = feldAuftrag.getText().trim();
        k.linieZug = feldLinie.getText().trim();
        k.datum = feldDatum.getText().trim();
        k.wetter = feldWetter.getText().trim();
        k.beobachter = feldBeobachter.getText().trim();
        k.nivellier = feldNivellier.getText().trim();
        k.latte = feldLatte.getText().trim();
        k.bemerkung = feldBemerkung.getText().trim();
        e.firma = feldFirma.getText().trim();
        for (int i = 0; i < 4; i++) {
            e.fehlerformeln[i] = feldKlasse[i].getText().trim();
        }
        e.schwellfaktor = zahl(feldSchwell, "Schwellfaktor", 1.0, streng);
        e.mehrfachGrenzeMm = zahl(feldMehrfach, "Mehrfachbestimmungen", 2.0, streng);
        e.logoDatei = feldLogo.getText().trim();
        e.logoHoeheMm = zahl(feldLogoHoehe, "Logo-Höhe", 20, streng);
        e.logoBreiteMm = zahl(feldLogoBreite, "Logo-Breite", 0, streng);
        return e;
    }

    private static double zahl(JTextField f, String name, double standard, boolean streng) throws Exception {
        try {
            return Main.zahl(f.getText());
        } catch (NumberFormatException ex) {
            if (streng) {
                throw new Exception(name + ": ungültige Zahl '" + f.getText() + "'");
            }
            return standard;
        }
    }

    private static Double optZahl(JTextField f, String name, boolean streng) throws Exception {
        if (f.getText().isBlank()) {
            return null;
        }
        try {
            return Main.zahl(f.getText());
        } catch (NumberFormatException ex) {
            if (streng) {
                throw new Exception(name + ": ungültige Zahl '" + f.getText() + "'");
            }
            return null;
        }
    }

    private static Integer optGanz(JTextField f, String name, boolean streng) throws Exception {
        Double d = optZahl(f, name, streng);
        return d == null ? null : d.intValue();
    }

    private static String zahl(double w) {
        return w == Math.rint(w) ? String.valueOf((long) w) : String.valueOf(w);
    }
}
