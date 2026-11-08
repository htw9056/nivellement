package nivellement;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;

/**
 * Handbuch: Inhaltsverzeichnis links, Text rechts, Suche, Drucken und Öffnen im Browser.
 */
public final class HilfeFenster extends JFrame {

    private static final long serialVersionUID = 1L;
    private static HilfeFenster instanz;

    private final JEditorPane text = new JEditorPane();
    private final DefaultListModel<String> kapitel = new DefaultListModel<>();
    private final List<String> anker = new ArrayList<>();
    private final JList<String> liste = new JList<>(kapitel);
    private final JTextField suche = new JTextField(18);
    private final JLabel trefferInfo = new JLabel(" ");
    private final String html;
    private int letzterTreffer = -1;

    /** Öffnet das Handbuch (bzw. holt es nach vorn) beim angegebenen Kapitel. */
    public static void zeige(Window besitzer, String sprungziel) {
        if (instanz == null) {
            instanz = new HilfeFenster();
            instanz.setLocationRelativeTo(besitzer);
        }
        instanz.setVisible(true);
        instanz.toFront();
        if (sprungziel != null) {
            instanz.springe(sprungziel);
        }
    }

    /** Der Handbuchtext (HTML) mit eingesetzter Programmversion. */
    public static String handbuch() {
        try (InputStream in = HilfeFenster.class.getResourceAsStream("hilfe/hilfe.html")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("@VERSION@", Version.titel()
                    .replace(" © Dipl.-Ing. (FH) Stefan Klein", ""));
        } catch (Exception e) {
            return "<html><body><h1>Handbuch nicht gefunden</h1></body></html>";
        }
    }

    private HilfeFenster() {
        super("Handbuch - " + Version.titel());
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        html = handbuch();

        HTMLEditorKit kit = new HTMLEditorKit();
        text.setEditorKit(kit);
        text.setEditable(false);
        text.setContentType("text/html");
        text.setText(html.replaceFirst("(?s)<meta[^>]*>", ""));
        text.setCaretPosition(0);
        text.addHyperlinkListener(ev -> {
            if (ev.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
                return;
            }
            String d = ev.getDescription();
            if (d != null && d.startsWith("#")) {
                springe(d.substring(1));
            } else if (ev.getURL() != null && Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(ev.getURL().toURI());
                } catch (Exception ex) {
                    // ignorieren
                }
            }
        });

        Matcher m = Pattern.compile("<h2><a name=\"([^\"]+)\"></a>(.*?)</h2>").matcher(html);
        kapitel.addElement("Übersicht");
        anker.add("start");
        while (m.find()) {
            anker.add(m.group(1));
            kapitel.addElement(m.group(2).replace("&ndash;", "-").replaceAll("<[^>]+>", ""));
        }
        liste.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        liste.addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting() && liste.getSelectedIndex() >= 0) {
                text.scrollToReference(anker.get(liste.getSelectedIndex()));
            }
        });

        JPanel leiste = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leiste.add(new JLabel("Suchen:"));
        leiste.add(suche);
        JButton weiter = new JButton("Weiter");
        leiste.add(weiter);
        leiste.add(trefferInfo);
        JButton drucken = new JButton("Drucken ...");
        JButton browser = new JButton("Im Browser öffnen");
        leiste.add(drucken);
        leiste.add(browser);
        suche.addActionListener(ev -> suchen(true));
        weiter.addActionListener(ev -> suchen(false));
        drucken.addActionListener(ev -> {
            try {
                text.print();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Drucken", JOptionPane.ERROR_MESSAGE);
            }
        });
        browser.addActionListener(ev -> imBrowser());

        JScrollPane links = new JScrollPane(liste);
        links.setBorder(BorderFactory.createTitledBorder("Inhalt"));
        links.setMinimumSize(new Dimension(180, 100));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, links, new JScrollPane(text));
        split.setDividerLocation(260);
        getContentPane().add(leiste, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);
        try {
            setIconImages(List.of(javax.imageio.ImageIO.read(getClass().getResource("bilder/symbol64.png"))));
        } catch (Exception ignored) {
            // ohne Symbol
        }
        setSize(1050, 760);
    }

    private void springe(String ziel) {
        int i = anker.indexOf(ziel);
        if (i >= 0 && liste.getSelectedIndex() != i) {
            liste.setSelectedIndex(i);
            liste.ensureIndexIsVisible(i);
        }
        SwingUtilities.invokeLater(() -> text.scrollToReference(ziel));
    }

    private void suchen(boolean vonVorn) {
        String s = suche.getText().trim().toLowerCase(Locale.GERMAN);
        text.getHighlighter().removeAllHighlights();
        if (s.isEmpty()) {
            trefferInfo.setText(" ");
            return;
        }
        try {
            Document d = text.getDocument();
            String inhalt = d.getText(0, d.getLength()).toLowerCase(Locale.GERMAN);
            int anzahl = 0;
            for (int p = inhalt.indexOf(s); p >= 0; p = inhalt.indexOf(s, p + 1)) {
                text.getHighlighter().addHighlight(p, p + s.length(),
                        new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 230, 120)));
                anzahl++;
            }
            int start = vonVorn ? 0 : letzterTreffer + 1;
            int p = inhalt.indexOf(s, start);
            if (p < 0) {
                p = inhalt.indexOf(s);
            }
            if (p < 0) {
                trefferInfo.setText("nicht gefunden");
                letzterTreffer = -1;
                return;
            }
            letzterTreffer = p;
            text.getHighlighter().addHighlight(p, p + s.length(),
                    new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 160, 60)));
            java.awt.geom.Rectangle2D r = text.modelToView2D(p);
            if (r != null) {
                text.scrollRectToVisible(r.getBounds());
            }
            trefferInfo.setText(anzahl + " Treffer");
        } catch (BadLocationException ex) {
            trefferInfo.setText(" ");
        }
    }

    private void imBrowser() {
        try {
            Path t = Files.createTempFile("Nivellement_Handbuch_", ".html");
            Files.writeString(t, html, StandardCharsets.UTF_8);
            t.toFile().deleteOnExit();
            Desktop.getDesktop().browse(t.toUri());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Browser konnte nicht geöffnet werden: " + ex.getMessage(), "Handbuch",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
