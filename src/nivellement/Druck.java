package nivellement;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 * Druck von Protokoll, Stapeldatei und Höhenverzeichnis auf DIN A4 mit Seitenkopf
 * (Firma, Titel, Projekt, eigenes Logo) und Fußzeile (Programm, Datum, Seitenzahl).
 * Jede Berechnung beginnt auf einer neuen Seite. Zum Erzeugen einer PDF-Datei den Drucker
 * "Microsoft Print to PDF" wählen.
 */
public final class Druck implements Printable {

    private static final double MM = 72.0 / 25.4;

    private final String titel;
    private final String projekt;
    private final String firma;
    private final Image logo;
    private final double logoHoeheMm;
    private final double logoBreiteMm;
    private final List<List<String>> bloecke;
    private final String datum = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

    // Seitenaufteilung für das zuletzt verwendete Seitenformat
    private PageFormat aufteilungFormat;
    private final List<int[]> seiten = new ArrayList<>();
    private float schrift;

    /**
     * @param titel   Titel im Seitenkopf, z. B. "Berechnungsprotokoll"
     * @param projekt Projektname
     * @param bloecke Textblöcke; jeder Block beginnt auf einer neuen Seite
     */
    public Druck(String titel, String projekt, List<List<String>> bloecke, Einstellungen e) {
        this.titel = titel;
        this.projekt = projekt == null ? "" : projekt;
        this.firma = e.firma == null ? "" : e.firma;
        this.bloecke = bloecke;
        this.logoHoeheMm = e.logoHoeheMm > 0 ? e.logoHoeheMm : 20;
        this.logoBreiteMm = e.logoBreiteMm;
        this.logo = ladeLogo(e.logoDatei);
    }

    static Image ladeLogo(String datei) {
        if (datei == null || datei.isBlank()) {
            return null;
        }
        try {
            Path p = Path.of(datei.trim());
            return Files.exists(p) ? ImageIO.read(p.toFile()) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    // ------------------------------------------------------------ Textblöcke

    /**
     * Zerlegt ein Berechnungsprotokoll in Blöcke je Berechnung. Der Kopf jeder Berechnung
     * (Firma, Programmzeile, "Projekt:") wird entfernt, weil der Seitenkopf ihn ersetzt.
     */
    public static List<List<String>> protokollBloecke(String text, String firma) {
        List<List<String>> bl = new ArrayList<>();
        List<String> akt = new ArrayList<>();
        String[] z = text.replace("\r", "").split("\n", -1);
        for (int i = 0; i < z.length; i++) {
            String s = z[i];
            if (s.startsWith(Version.NAME + " V") && s.contains("©") && i + 1 < z.length
                    && z[i + 1].startsWith("Projekt:")) {
                // Firmenzeile direkt davor gehört zum Kopf
                if (!akt.isEmpty() && firma != null && !firma.isBlank()
                        && akt.get(akt.size() - 1).trim().equals(firma.trim())) {
                    akt.remove(akt.size() - 1);
                }
                abschliessen(bl, akt);
                akt = new ArrayList<>();
                i++; // "Projekt:"
                if (i + 1 < z.length && z[i + 1].isBlank()) {
                    i++;
                }
                continue;
            }
            if (s.startsWith("Gespeichert:")) {
                break; // Dateihinweise der Oberfläche nicht drucken
            }
            akt.add(s);
        }
        abschliessen(bl, akt);
        // Gesamtmeldung ans Ende der letzten Berechnung, Schlusszeile mit Programmname entfernen
        for (List<String> b : bl) {
            while (!b.isEmpty() && (b.get(b.size() - 1).isBlank()
                    || b.get(b.size() - 1).trim().equals(Version.titel()))) {
                b.remove(b.size() - 1);
            }
        }
        return bl;
    }

    private static void abschliessen(List<List<String>> bl, List<String> akt) {
        while (!akt.isEmpty() && akt.get(0).isBlank()) {
            akt.remove(0);
        }
        boolean leer = akt.stream().allMatch(String::isBlank);
        if (!leer) {
            bl.add(akt);
        }
    }

    /** Ein einzelner Block aus einem beliebigen Text (Stapeldatei, Höhenverzeichnis). */
    public static List<List<String>> einBlock(String text) {
        List<List<String>> bl = new ArrayList<>();
        List<String> z = new ArrayList<>(List.of(text.replace("\r", "").split("\n", -1)));
        while (!z.isEmpty() && z.get(z.size() - 1).isBlank()) {
            z.remove(z.size() - 1);
        }
        bl.add(z);
        return bl;
    }

    // ---------------------------------------------------------- Seitenlayout

    private double kopfHoehe() {
        double logoH = logo == null ? 0 : logoHoeheMm * MM;
        return Math.max(logoH, 3 * 14) + 10;
    }

    private static final double FUSS = 22;

    private void aufteilen(PageFormat pf) {
        if (pf == aufteilungFormat) {
            return;
        }
        aufteilungFormat = pf;
        seiten.clear();
        int laengste = 60;
        for (List<String> b : bloecke) {
            for (String s : b) {
                laengste = Math.max(laengste, s.stripTrailing().length());
            }
        }
        // Courier: Zeichenbreite 0,6 * Schriftgröße
        schrift = (float) Math.max(5.5, Math.min(9.0, pf.getImageableWidth() / (laengste * 0.6)));
        double zeile = schrift * 1.18;
        int jeSeite = (int) Math.floor((pf.getImageableHeight() - kopfHoehe() - FUSS) / zeile);
        jeSeite = Math.max(10, jeSeite);
        for (int b = 0; b < bloecke.size(); b++) {
            int n = bloecke.get(b).size();
            for (int start = 0; start < Math.max(1, n); start += jeSeite) {
                seiten.add(new int[] { b, start, Math.min(n, start + jeSeite) });
            }
        }
    }

    public int seitenzahl(PageFormat pf) {
        aufteilen(pf);
        return seiten.size();
    }

    @Override
    public int print(Graphics g0, PageFormat pf, int index) throws PrinterException {
        aufteilen(pf);
        if (index >= seiten.size()) {
            return NO_SUCH_PAGE;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(Color.BLACK);
        double x0 = pf.getImageableX();
        double y0 = pf.getImageableY();
        double b = pf.getImageableWidth();
        double h = pf.getImageableHeight();

        // --- Kopf ---
        double logoB = 0;
        if (logo != null) {
            double lh = logoHoeheMm * MM;
            double lb = lh * logo.getWidth(null) / Math.max(1, logo.getHeight(null));
            if (logoBreiteMm > 0) {
                lb = Math.min(lb, logoBreiteMm * MM);
                lh = lb * logo.getHeight(null) / Math.max(1, logo.getWidth(null));
            }
            lb = Math.min(lb, b / 2);
            logoB = lb;
            g.drawImage(logo, (int) Math.round(x0 + b - lb), (int) Math.round(y0), (int) Math.round(lb),
                    (int) Math.round(lh), null);
        }
        double textB = b - logoB - 8;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        float y = (float) (y0 + 12);
        if (!firma.isBlank()) {
            g.drawString(kuerzen(g, firma, textB), (float) x0, y);
            y += 15;
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g.drawString(kuerzen(g, titel, textB), (float) x0, y);
        y += 13;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        if (!projekt.isBlank()) {
            g.drawString(kuerzen(g, "Projekt: " + projekt, textB), (float) x0, y);
        }
        double linieY = y0 + kopfHoehe() - 5;
        g.setStroke(new BasicStroke(0.7f));
        g.drawLine((int) x0, (int) linieY, (int) (x0 + b), (int) linieY);

        // --- Text ---
        int[] s = seiten.get(index);
        List<String> zeilen = bloecke.get(s[0]);
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 10).deriveFont(schrift);
        g.setFont(mono);
        double zh = schrift * 1.18;
        double ty = y0 + kopfHoehe() + schrift;
        for (int i = s[1]; i < s[2]; i++) {
            String t = zeilen.get(i).replace("\t", "    ");
            if (!t.isEmpty()) {
                g.drawString(t, (float) x0, (float) ty);
            }
            ty += zh;
        }

        // --- Fuß ---
        double fy = y0 + h - 6;
        g.drawLine((int) x0, (int) (fy - 11), (int) (x0 + b), (int) (fy - 11));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Version.titel(), (float) x0, (float) fy);
        String mitte = datum;
        g.drawString(mitte, (float) (x0 + (b - fm.stringWidth(mitte)) / 2), (float) fy);
        String rechts = "Seite " + (index + 1) + " von " + seiten.size();
        g.drawString(rechts, (float) (x0 + b - fm.stringWidth(rechts)), (float) fy);
        g.dispose();
        return PAGE_EXISTS;
    }

    private static String kuerzen(Graphics2D g, String s, double breite) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= breite) {
            return s;
        }
        String t = s;
        while (t.length() > 3 && fm.stringWidth(t + "...") > breite) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    // --------------------------------------------------------------- Drucken

    /** DIN A4 hochkant, Ränder links 20 mm, sonst 12 mm. */
    public static PageFormat a4() {
        PageFormat pf = new PageFormat();
        Paper p = new Paper();
        double w = 210 * MM;
        double h = 297 * MM;
        p.setSize(w, h);
        p.setImageableArea(20 * MM, 12 * MM, w - 32 * MM, h - 24 * MM);
        pf.setPaper(p);
        pf.setOrientation(PageFormat.PORTRAIT);
        return pf;
    }

    /** Öffnet den Druckdialog und druckt. */
    public void drucken(Component eltern) {
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName(Version.NAME + " - " + titel);
            PrintRequestAttributeSet a = new HashPrintRequestAttributeSet();
            a.add(MediaSizeName.ISO_A4);
            a.add(OrientationRequested.PORTRAIT);
            a.add(new JobName(Version.NAME + " - " + titel, null));
            if (job.getPrintService() == null) {
                JOptionPane.showMessageDialog(eltern, "Es ist kein Drucker eingerichtet.", "Drucken",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            job.setPrintable(this, a4());
            if (job.printDialog(a)) {
                aufteilungFormat = null;
                job.print(a);
            }
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(eltern, "Drucken fehlgeschlagen: " + ex.getMessage(), "Drucken",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Erzeugt das Bild einer Seite (für die Vorschau). */
    public BufferedImage seitenbild(int index, double massstab) {
        PageFormat pf = a4();
        int w = (int) Math.round(pf.getWidth() * massstab);
        int h = (int) Math.round(pf.getHeight() * massstab);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.scale(massstab, massstab);
        try {
            print(g, pf, index);
        } catch (PrinterException ex) {
            // leer lassen
        }
        g.dispose();
        return img;
    }

    /** Druckvorschau mit Blättern und Drucken. */
    public void vorschau(Window eltern) {
        JDialog d = new JDialog(eltern, "Druckvorschau - " + titel);
        d.setModalityType(JDialog.ModalityType.MODELESS);
        int anzahl = seitenzahl(a4());
        int[] seite = { 0 };
        JLabel bild = new JLabel();
        bild.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel info = new JLabel();
        Runnable zeigen = () -> {
            bild.setIcon(new javax.swing.ImageIcon(seitenbild(seite[0], 1.4)));
            info.setText("  Seite " + (seite[0] + 1) + " von " + anzahl + "  ");
        };
        JButton zurueck = new JButton("< Zurück");
        JButton vor = new JButton("Weiter >");
        JButton druck = new JButton("Drucken ...");
        zurueck.addActionListener(ev -> {
            if (seite[0] > 0) {
                seite[0]--;
                zeigen.run();
            }
        });
        vor.addActionListener(ev -> {
            if (seite[0] < anzahl - 1) {
                seite[0]++;
                zeigen.run();
            }
        });
        druck.addActionListener(ev -> drucken(d));
        JPanel leiste = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leiste.add(zurueck);
        leiste.add(info);
        leiste.add(vor);
        leiste.add(druck);
        JPanel hinter = new JPanel(new BorderLayout());
        hinter.setBackground(new Color(120, 120, 120));
        hinter.add(bild, BorderLayout.CENTER);
        JScrollPane sp = new JScrollPane(hinter);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        d.getContentPane().add(leiste, BorderLayout.NORTH);
        d.getContentPane().add(sp, BorderLayout.CENTER);
        zeigen.run();
        d.setSize(new Dimension(900, 980));
        d.setLocationRelativeTo(eltern);
        d.setVisible(true);
    }
}
