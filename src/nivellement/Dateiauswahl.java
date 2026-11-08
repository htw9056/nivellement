package nivellement;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Dateiauswahl. Unter Windows wird der normale Windows-Dateidialog verwendet (Adressleiste,
 * Schnellzugriff, Einfügen eines kopierten Pfads mit Strg+V). Auf anderen Systemen die Java-Auswahl,
 * in die ebenfalls ein vollständiger Pfad eingegeben oder eingefügt werden kann.
 */
public final class Dateiauswahl {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
            .startsWith("windows");

    private Dateiauswahl() {
    }

    /**
     * Datei öffnen.
     *
     * @param start Startordner oder vorgeschlagene Datei (darf null sein)
     * @param ext   erlaubte Endungen ohne Punkt, leer = alle
     */
    public static File oeffnen(Component eltern, String titel, File start, String beschreibung, String... ext) {
        File[] f = auswahl(eltern, titel, start, beschreibung, false, false, null, ext);
        return f.length == 0 ? null : f[0];
    }

    /** Mehrere Dateien öffnen. */
    public static File[] oeffnenMehrere(Component eltern, String titel, File start, String beschreibung,
            String... ext) {
        return auswahl(eltern, titel, start, beschreibung, false, true, null, ext);
    }

    /** Datei speichern; die erste Endung wird ergänzt, falls sie fehlt. */
    public static File speichern(Component eltern, String titel, File start, String vorschlag, String beschreibung,
            String... ext) {
        File[] f = auswahl(eltern, titel, start, beschreibung, true, false, vorschlag, ext);
        if (f.length == 0) {
            return null;
        }
        File d = f[0];
        if (ext.length > 0 && !hatEndung(d, ext)) {
            d = new File(d.getPath() + "." + ext[0]);
        }
        return d;
    }

    private static boolean hatEndung(File f, String... ext) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        for (String e : ext) {
            if (n.endsWith("." + e.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static File ordner(File start) {
        if (start == null) {
            return null;
        }
        File o = start.isDirectory() ? start : start.getAbsoluteFile().getParentFile();
        while (o != null && !o.isDirectory()) {
            o = o.getParentFile();
        }
        return o;
    }

    private static File[] auswahl(Component eltern, String titel, File start, String beschreibung, boolean speichern,
            boolean mehrere, String vorschlag, String... ext) {
        File ordner = ordner(start);
        String datei = vorschlag != null ? vorschlag
                : start != null && start.isFile() ? start.getName() : null;
        if (WINDOWS) {
            Window w = eltern == null ? null
                    : eltern instanceof Window ? (Window) eltern : SwingUtilities.getWindowAncestor(eltern);
            FileDialog fd = w instanceof Dialog ? new FileDialog((Dialog) w, titel)
                    : new FileDialog(w instanceof Frame ? (Frame) w : null, titel);
            fd.setMode(speichern ? FileDialog.SAVE : FileDialog.LOAD);
            fd.setMultipleMode(mehrere);
            if (ordner != null) {
                fd.setDirectory(ordner.getAbsolutePath());
            }
            if (datei != null) {
                fd.setFile(datei);
            } else if (ext.length > 0) {
                // unter Windows wirkt das Muster als Dateifilter
                fd.setFile(String.join(";", Arrays.stream(ext).map(e -> "*." + e).toArray(String[]::new)));
            }
            fd.setFilenameFilter((d, n) -> ext.length == 0 || hatEndung(new File(n), ext));
            fd.setVisible(true);
            File[] f = fd.getFiles();
            return f == null ? new File[0] : f;
        }
        JFileChooser fc = new JFileChooser(ordner);
        fc.setDialogTitle(titel);
        fc.setMultiSelectionEnabled(mehrere);
        if (datei != null) {
            fc.setSelectedFile(new File(ordner, datei));
        }
        if (ext.length > 0) {
            fc.setFileFilter(new FileNameExtensionFilter(beschreibung, ext));
        }
        int r = speichern ? fc.showSaveDialog(eltern) : fc.showOpenDialog(eltern);
        if (r != JFileChooser.APPROVE_OPTION) {
            return new File[0];
        }
        return mehrere ? fc.getSelectedFiles() : new File[] { fc.getSelectedFile() };
    }
}
