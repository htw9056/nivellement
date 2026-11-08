package nivellement;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/**
 * Höhenverzeichnis: Anzeigen und Bearbeiten der Höhendatei des Projekts.
 */
public class HoehenverzeichnisPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String[] SPALTEN = { "Punkt-Nr.", "Höhe", "BerNr.", "Diff. [mm]", "AB", "Datum",
            "Bemerkung" };

    private final DefaultTableModel modell = new DefaultTableModel(SPALTEN, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public Class<?> getColumnClass(int c) {
            switch (c) {
                case 1:
                case 3:
                    return Double.class;
                case 2:
                case 4:
                    return Integer.class;
                default:
                    return String.class;
            }
        }
    };
    private final JTable tabelle = new JTable(modell);
    private final TableRowSorter<DefaultTableModel> sortierer = new TableRowSorter<>(modell);
    private final JTextField filter = new JTextField(15);
    private final JLabel status = new JLabel();
    private final JPanel leiste = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final Supplier<Path> pfad;
    private Path geladen;
    private boolean geaendert;

    /** @param pfad liefert die Höhendatei des aktuellen Projekts */
    public HoehenverzeichnisPanel(Supplier<Path> pfad) {
        super(new BorderLayout());
        this.pfad = pfad;
        tabelle.setRowSorter(sortierer);
        tabelle.getColumnModel().getColumn(1).setCellRenderer(zahlen("%.5f"));
        tabelle.getColumnModel().getColumn(3).setCellRenderer(zahlen("%.1f"));
        tabelle.getColumnModel().getColumn(0).setPreferredWidth(140);
        tabelle.getColumnModel().getColumn(6).setPreferredWidth(220);
        tabelle.setAutoCreateRowSorter(false);
        modell.addTableModelListener(ev -> geaendert = true);

        leiste.add(knopf("Laden", this::laden));
        leiste.add(knopf("Speichern", this::speichern));
        leiste.add(knopf("Neuer Punkt", this::neu));
        leiste.add(knopf("Löschen", this::loeschen));
        leiste.add(knopf("Als Festpunkt", this::festpunkt));
        leiste.add(knopf("Umnummerieren", this::umnummerieren));
        leiste.add(knopf("Import ASCII", this::importieren));
        leiste.add(knopf("Import MDB", this::importMdb));
        leiste.add(knopf("Export ASCII", this::exportieren));
        leiste.add(new JLabel("Filter:"));
        leiste.add(filter);
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filtern();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filtern();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filtern();
            }
        });
        add(leiste, BorderLayout.NORTH);
        add(new JScrollPane(tabelle), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    private static javax.swing.table.DefaultTableCellRenderer zahlen(String format) {
        javax.swing.table.DefaultTableCellRenderer r = new javax.swing.table.DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void setValue(Object v) {
                setText(v instanceof Number ? String.format(Locale.ROOT, format, ((Number) v).doubleValue()) : "");
            }
        };
        r.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        return r;
    }

    /** Fügt der Knopfleiste vor dem Filter einen Knopf hinzu. */
    public void zusatzKnopf(String text, Runnable aktion) {
        leiste.add(knopf(text, aktion), leiste.getComponentCount() - 2);
    }

    /** Höhenverzeichnis als Drucktext (aktuelle Sortierung und Filter). */
    public String drucktext() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%15s %12s %7s %8s %3s  %-10s  %s%n", "Punkt-Nr.", "Höhe", "BerNr.",
                "Diff.", "AB", "Datum", "Bemerkung"));
        sb.append("-".repeat(90)).append('\n');
        int n = 0;
        for (int v = 0; v < tabelle.getRowCount(); v++) {
            int i = tabelle.convertRowIndexToModel(v);
            Object h = modell.getValueAt(i, 1);
            if (h == null) {
                continue;
            }
            Object d = modell.getValueAt(i, 3);
            sb.append(String.format(Locale.ROOT, "%15s %12s %7d %8s %3d  %-10s  %s", text(modell.getValueAt(i, 0)),
                    Protokoll.rund(((Number) h).doubleValue(), 4), zahl(modell.getValueAt(i, 2), 0),
                    d == null ? "" : Protokoll.rund(((Number) d).doubleValue(), 1), zahl(modell.getValueAt(i, 4), 1),
                    text(modell.getValueAt(i, 5)), text(modell.getValueAt(i, 6))).stripTrailing()).append('\n');
            n++;
        }
        sb.append("-".repeat(90)).append('\n').append(n).append(" Punkte");
        if (!filter.getText().isBlank()) {
            sb.append("  (Filter: ").append(filter.getText().trim()).append(')');
        }
        sb.append('\n');
        return sb.toString();
    }

    private static JButton knopf(String text, Runnable aktion) {
        JButton b = new JButton(text);
        b.addActionListener(ev -> aktion.run());
        return b;
    }

    private void filtern() {
        String t = filter.getText().trim();
        sortierer.setRowFilter(t.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(t), 0, 6));
    }

    /** Lädt die Höhendatei des aktuellen Projekts. */
    public void laden() {
        try {
            Path p = pfad.get();
            if (p == null) {
                status.setText("Kein Projekt gewählt.");
                return;
            }
            Hoehendatei hd = Hoehendatei.lies(p);
            modell.setRowCount(0);
            for (Hoehendatei.Eintrag e : hd.eintraege()) {
                modell.addRow(new Object[] { e.punkt, e.hoehe, e.berNr, e.diffMm, e.ab, e.datum, e.bemerkung });
            }
            geladen = p;
            geaendert = false;
            status.setText(" " + hd.anzahl() + " Punkte  -  " + p + (Files.exists(p) ? "" : "  (neu)"));
        } catch (Exception ex) {
            fehler(ex);
        }
    }

    /** Lädt neu, wenn ein anderes Projekt gewählt wurde oder keine ungespeicherten Änderungen vorliegen. */
    public void aktualisieren() {
        if (!geaendert || geladen == null || !geladen.equals(pfad.get())) {
            laden();
        }
    }

    public boolean hatAenderungen() {
        return geaendert;
    }

    public void speichern() {
        try {
            if (tabelle.isEditing()) {
                tabelle.getCellEditor().stopCellEditing();
            }
            Path p = geladen != null ? geladen : pfad.get();
            if (p == null) {
                return;
            }
            List<Hoehendatei.Eintrag> liste = new ArrayList<>();
            for (int i = 0; i < modell.getRowCount(); i++) {
                Hoehendatei.Eintrag e = new Hoehendatei.Eintrag();
                e.punkt = text(modell.getValueAt(i, 0));
                if (e.punkt.isEmpty()) {
                    continue;
                }
                Object h = modell.getValueAt(i, 1);
                if (h == null) {
                    throw new Exception("Punkt " + e.punkt + ": Höhe fehlt.");
                }
                e.hoehe = ((Number) h).doubleValue();
                e.berNr = zahl(modell.getValueAt(i, 2), 0);
                Object d = modell.getValueAt(i, 3);
                e.diffMm = d == null ? 0 : ((Number) d).doubleValue();
                e.ab = zahl(modell.getValueAt(i, 4), 1);
                e.datum = text(modell.getValueAt(i, 5));
                e.bemerkung = text(modell.getValueAt(i, 6));
                liste.add(e);
            }
            Hoehendatei hd = new Hoehendatei();
            hd.ersetze(liste);
            hd.schreibe(p);
            geaendert = false;
            status.setText(" " + liste.size() + " Punkte gespeichert  -  " + p);
        } catch (Exception ex) {
            fehler(ex);
        }
    }

    private void neu() {
        modell.addRow(new Object[] { "", 0.0, Hoehendatei.FESTPUNKT, 0.0, 1, "", "" });
        int z = tabelle.convertRowIndexToView(modell.getRowCount() - 1);
        tabelle.setRowSelectionInterval(z, z);
        tabelle.editCellAt(z, 0);
    }

    private int[] auswahl() {
        int[] v = tabelle.getSelectedRows();
        int[] m = new int[v.length];
        for (int i = 0; i < v.length; i++) {
            m[i] = tabelle.convertRowIndexToModel(v[i]);
        }
        Arrays.sort(m);
        return m;
    }

    private void loeschen() {
        int[] m = auswahl();
        if (m.length == 0) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this, m.length + " Punkt(e) löschen?", "Löschen",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        for (int i = m.length - 1; i >= 0; i--) {
            modell.removeRow(m[i]);
        }
    }

    private void festpunkt() {
        for (int i : auswahl()) {
            modell.setValueAt(Hoehendatei.FESTPUNKT, i, 2);
        }
    }

    /**
     * Umnummerieren: ein Punkt erhält eine neue Nummer; bei mehreren Punkten wird ein Textteil
     * ersetzt (z. B. Präfix "1" durch "2") oder ein Zahlenwert addiert ("+1000").
     */
    private void umnummerieren() {
        int[] m = auswahl();
        if (m.length == 0) {
            JOptionPane.showMessageDialog(this, "Bitte zuerst Punkte auswählen.");
            return;
        }
        if (m.length == 1) {
            String alt = text(modell.getValueAt(m[0], 0));
            String neu = JOptionPane.showInputDialog(this, "Neue Punktnummer für " + alt + ":", alt);
            if (neu != null && !neu.isBlank()) {
                modell.setValueAt(neu.trim(), m[0], 0);
            }
            return;
        }
        String regel = JOptionPane.showInputDialog(this,
                m.length + " Punkte umnummerieren:\n"
                        + "  alt=neu   ersetzt den Anfang der Punktnummer (z. B. 1=2)\n"
                        + "  +n / -n   addiert n zu numerischen Punktnummern (z. B. +1000)");
        if (regel == null || regel.isBlank()) {
            return;
        }
        regel = regel.trim();
        for (int i : m) {
            String p = text(modell.getValueAt(i, 0));
            if (regel.contains("=")) {
                String[] t = regel.split("=", 2);
                if (p.startsWith(t[0])) {
                    modell.setValueAt(t[1] + p.substring(t[0].length()), i, 0);
                }
            } else {
                try {
                    long d = Long.parseLong(regel.replace("+", ""));
                    modell.setValueAt(String.valueOf(Long.parseLong(p) + d), i, 0);
                } catch (NumberFormatException ex) {
                    // nicht numerisch - unverändert
                }
            }
        }
    }

    private void importieren() {
        java.io.File d = Dateiauswahl.oeffnen(this, "Höhen importieren (ASCII)",
                geladen != null ? geladen.getParent().toFile() : null, "Alle Dateien");
        if (d == null) {
            return;
        }
        try {
            List<Hoehendatei.Eintrag> liste = Hoehendatei.liesAsc(d.toPath());
            boolean vollstaendig = liste.stream().anyMatch(e -> e.berNr != Hoehendatei.FESTPUNKT || e.ab != 1);
            if (!vollstaendig) {
                int wahl = JOptionPane.showConfirmDialog(this,
                        liste.size() + " Punkte gelesen. Als Festpunkte (BerNr. 999999) übernehmen?\n"
                                + "Nein = als Neupunkte mit BerNr. 0",
                        "Import", JOptionPane.YES_NO_CANCEL_OPTION);
                if (wahl == JOptionPane.CANCEL_OPTION || wahl == JOptionPane.CLOSED_OPTION) {
                    return;
                }
                if (wahl == JOptionPane.NO_OPTION) {
                    liste.forEach(e -> e.berNr = 0);
                }
            }
            uebernehme(liste, "Import ASCII");
        } catch (Exception ex) {
            fehler(ex);
        }
    }

    private void importMdb() {
        java.io.File d = Dateiauswahl.oeffnen(this, "NIGRA-Höhendatei (MDB) importieren",
                geladen != null ? geladen.getParent().toFile() : null, "NIGRA-Höhendatei (MDB)", "mdb");
        if (d != null) {
            importiereMdb(d.toPath());
        }
    }

    /** Übernimmt eine NIGRA-Höhendatei (.MDB) in die Tabelle (danach Speichern). */
    public void importiereMdb(Path mdb) {
        try {
            uebernehme(MdbLeser.lies(mdb), "Import " + mdb.getFileName());
        } catch (Exception ex) {
            fehler(ex);
        }
    }

    private void uebernehme(List<Hoehendatei.Eintrag> liste, String quelle) {
        int neu = 0, ersetzt = 0;
        for (Hoehendatei.Eintrag e : liste) {
            int zeile = -1;
            for (int i = 0; i < modell.getRowCount(); i++) {
                if (e.punkt.equals(text(modell.getValueAt(i, 0)))) {
                    zeile = i;
                    break;
                }
            }
            Object[] werte = { e.punkt, e.hoehe, e.berNr, e.diffMm, e.ab, e.datum, e.bemerkung };
            if (zeile >= 0) {
                for (int k = 0; k < werte.length; k++) {
                    modell.setValueAt(werte[k], zeile, k);
                }
                ersetzt++;
            } else {
                modell.addRow(werte);
                neu++;
            }
        }
        status.setText(" " + quelle + ": " + neu + " neu, " + ersetzt + " ersetzt - bitte Speichern");
    }

    private void exportieren() {
        String[] formate = { "NIGRA-Höhenverzeichnis, 3 Dezimalstellen", "NIGRA-Höhenverzeichnis, 4 Dezimalstellen",
                "Punkt Höhe (5 Dezimalstellen)" };
        Object f = JOptionPane.showInputDialog(this, "Format der ASCII-Ausgabe:", "Export",
                JOptionPane.QUESTION_MESSAGE, null, formate, formate[0]);
        if (f == null) {
            return;
        }
        java.io.File ziel = Dateiauswahl.speichern(this, "Höhenverzeichnis exportieren",
                geladen != null ? geladen.getParent().toFile() : null, "Hoehen.ASC", "ASCII (ASC, TXT)", "ASC", "TXT");
        if (ziel == null) {
            return;
        }
        List<Hoehendatei.Eintrag> liste = new ArrayList<>();
        for (int v = 0; v < tabelle.getRowCount(); v++) {
            int i = tabelle.convertRowIndexToModel(v);
            Object h = modell.getValueAt(i, 1);
            if (h == null) {
                continue;
            }
            Hoehendatei.Eintrag e = new Hoehendatei.Eintrag();
            e.punkt = text(modell.getValueAt(i, 0));
            e.hoehe = ((Number) h).doubleValue();
            e.berNr = zahl(modell.getValueAt(i, 2), 0);
            Object d = modell.getValueAt(i, 3);
            e.diffMm = d == null ? 0 : ((Number) d).doubleValue();
            e.ab = zahl(modell.getValueAt(i, 4), 1);
            e.datum = text(modell.getValueAt(i, 5));
            e.bemerkung = text(modell.getValueAt(i, 6));
            liste.add(e);
        }
        StringBuilder sb = new StringBuilder();
        if (f == formate[2]) {
            for (Hoehendatei.Eintrag e : liste) {
                sb.append(String.format(Locale.ROOT, "%-15s %12s%n", e.punkt, Protokoll.rund(e.hoehe, 5)));
            }
        } else {
            sb.append(Hoehendatei.ascText(liste, f == formate[0] ? 3 : 4));
        }
        try {
            Files.write(ziel.toPath(), sb.toString().getBytes(Projekt.ANSI));
            status.setText(" Export: " + liste.size() + " Punkte nach " + ziel);
        } catch (Exception ex) {
            fehler(ex);
        }
    }

    private void fehler(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Höhenverzeichnis", JOptionPane.ERROR_MESSAGE);
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static int zahl(Object o, int standard) {
        return o instanceof Number ? ((Number) o).intValue() : standard;
    }
}
