package nivellement;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

/**
 * Editor für die Mess- bzw. Stapeldatei mit Spaltenlineal und Positionsanzeige.
 */
public class EditorPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    private final JTextArea text = new JTextArea();
    private final JLabel position = new JLabel(" ");
    private final JLabel datei = new JLabel(" ");
    private final JPanel leiste = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final Supplier<Path> pfad;
    private Path geladen;
    private boolean geaendert;

    public EditorPanel(Supplier<Path> pfad, Runnable pruefen) {
        super(new BorderLayout());
        this.pfad = pfad;
        text.setFont(MONO);
        text.setTabSize(1);
        text.addCaretListener(ev -> zeigePosition());
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markiere();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markiere();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Attribute - ohne Bedeutung
            }
        });

        JTextArea lineal = new JTextArea(" " + NigSchreiber.LINEAL2.substring(1) + "\n"
                + "1" + NigSchreiber.LINEAL1.substring(1) + "\n"
                + "K Strecke<--- Rück     Mitte     Vor ---><--- Punktnummer --->");
        lineal.setFont(MONO);
        lineal.setEditable(false);
        lineal.setFocusable(false);
        lineal.setBackground(new Color(235, 235, 235));
        lineal.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setColumnHeaderView(lineal);

        leiste.add(knopf("Speichern", this::speichern));
        leiste.add(knopf("Neu laden", this::laden));
        leiste.add(knopf("Messdaten prüfen", () -> {
            speichern();
            pruefen.run();
        }));
        leiste.add(datei);

        add(leiste, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(position, BorderLayout.SOUTH);
    }

    /** Fügt der Knopfleiste vor dem Dateinamen einen Knopf hinzu. */
    public void zusatzKnopf(String text, Runnable aktion) {
        leiste.add(knopf(text, aktion), leiste.getComponentCount() - 1);
    }

    /** Aktueller Text des Editors. */
    public String text() {
        return text.getText();
    }

    private static JButton knopf(String t, Runnable a) {
        JButton b = new JButton(t);
        b.addActionListener(ev -> a.run());
        return b;
    }

    private void markiere() {
        if (!geaendert) {
            geaendert = true;
            datei.setText(datei.getText() + "  *geändert*");
        }
    }

    private void zeigePosition() {
        try {
            int p = text.getCaretPosition();
            int z = text.getLineOfOffset(p);
            int s = p - text.getLineStartOffset(z);
            position.setText(" Zeile " + (z + 1) + ", Spalte " + (s + 1));
        } catch (BadLocationException e) {
            position.setText(" ");
        }
    }

    /** Lädt die aktuelle Datei, wenn keine ungespeicherten Änderungen vorliegen oder eine andere gewählt wurde. */
    public void aktualisieren() {
        Path p = pfad.get();
        if (p != null && (!p.equals(geladen) || !geaendert)) {
            laden();
        }
    }

    public void laden() {
        Path p = pfad.get();
        if (p == null) {
            return;
        }
        try {
            text.setText(Files.exists(p) ? String.join("\n", Einleser.zeilen(p)) : "");
            text.setCaretPosition(0);
            geladen = p;
            geaendert = false;
            datei.setText(p.getFileName() + (Files.exists(p) ? "" : "  (neu)"));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Editor", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Speichert, falls geändert. */
    public void speichern() {
        if (!geaendert || geladen == null) {
            return;
        }
        try {
            Projekt.schreibeStapeldatei(geladen, text.getText().stripTrailing() + "\n");
            geaendert = false;
            datei.setText(geladen.getFileName().toString());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Editor", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean hatAenderungen() {
        return geaendert;
    }
}
