package nivellement;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Liest die Höhentabelle einer NIGRA-Höhendatei (*.MDB, Microsoft Access 2000 und neuer, Jet-4-Format)
 * ohne externe Bibliotheken.
 *
 * Gesucht wird die Tabelle mit den Spalten PNR, Hoehe, BerNr, AnzB, Dif, Datum und Bemerk.
 * Gelesen werden nur die Datenseiten dieser Tabelle; die Datei wird nicht verändert.
 */
public final class MdbLeser {

    private static final int SEITE = 4096;

    private final ByteBuffer d;
    private final int seiten;

    /** Spaltenbeschreibung aus der Tabellendefinition. */
    private static final class Spalte {
        int typ;
        int nummer;
        int varIndex;
        boolean fest;
        int festOffset;
        int laenge;
        String name;
    }

    private MdbLeser(byte[] daten) {
        this.d = ByteBuffer.wrap(daten).order(ByteOrder.LITTLE_ENDIAN);
        this.seiten = daten.length / SEITE;
    }

    /** Liest alle Punkte der Höhentabelle. */
    public static List<Hoehendatei.Eintrag> lies(Path datei) throws IOException {
        byte[] b = Files.readAllBytes(datei);
        if (b.length < SEITE || b[0x14] < 1) {
            throw new IOException("Nicht unterstütztes Access-Format (benötigt Access 2000 oder neuer): " + datei);
        }
        return new MdbLeser(b).hoehen();
    }

    private int u8(int o) {
        return d.get(o) & 0xFF;
    }

    private int u16(int o) {
        return d.getShort(o) & 0xFFFF;
    }

    private int i32(int o) {
        return d.getInt(o);
    }

    private List<Hoehendatei.Eintrag> hoehen() throws IOException {
        for (int pg = 1; pg < seiten; pg++) {
            int o = pg * SEITE;
            if (u8(o) != 0x02 || u8(o + 1) != 0x01) {
                continue;
            }
            List<Spalte> sp = tabellendefinition(pg);
            if (finde(sp, "PNR") != null && finde(sp, "Hoehe") != null) {
                return zeilen(pg, sp);
            }
        }
        throw new IOException("Keine Höhentabelle (Spalten PNR/Hoehe) in der MDB-Datei gefunden.");
    }

    private static Spalte finde(List<Spalte> sp, String name) {
        for (Spalte s : sp) {
            if (s.name.equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    /** Liest die Tabellendefinition (ggf. über mehrere Seiten). */
    private List<Spalte> tabellendefinition(int pg) {
        ByteBuffer t = ByteBuffer.allocate(SEITE * 16).order(ByteOrder.LITTLE_ENDIAN);
        int seite = pg;
        boolean erste = true;
        while (seite > 0 && seite < seiten && t.remaining() >= SEITE) {
            int o = seite * SEITE;
            int von = erste ? 0 : 8;
            for (int i = von; i < SEITE; i++) {
                t.put(d.get(o + i));
            }
            seite = i32(o + 4);
            erste = false;
        }
        int anzahlSpalten = t.getShort(0x2d) & 0xFFFF;
        int echteIndizes = t.getInt(0x33);
        int o = 0x3f + echteIndizes * 12;
        List<Spalte> sp = new ArrayList<>();
        for (int i = 0; i < anzahlSpalten && o + 25 <= t.capacity(); i++) {
            Spalte s = new Spalte();
            s.typ = t.get(o) & 0xFF;
            s.nummer = t.getShort(o + 5) & 0xFFFF;
            s.varIndex = t.getShort(o + 7) & 0xFFFF;
            s.fest = (t.get(o + 15) & 0x01) != 0;
            s.festOffset = t.getShort(o + 21) & 0xFFFF;
            s.laenge = t.getShort(o + 23) & 0xFFFF;
            sp.add(s);
            o += 25;
        }
        for (Spalte s : sp) {
            int l = t.getShort(o) & 0xFFFF;
            byte[] n = new byte[l];
            for (int i = 0; i < l; i++) {
                n[i] = t.get(o + 2 + i);
            }
            s.name = new String(n, StandardCharsets.UTF_16LE);
            o += 2 + l;
        }
        return sp;
    }

    private List<Hoehendatei.Eintrag> zeilen(int tdef, List<Spalte> sp) {
        List<Hoehendatei.Eintrag> liste = new ArrayList<>();
        for (int pg = 1; pg < seiten; pg++) {
            int o = pg * SEITE;
            if (u8(o) != 0x01 || i32(o + 4) != tdef) {
                continue;
            }
            int n = u16(o + 0x0c);
            for (int i = 0; i < n; i++) {
                int[] z = zeile(pg, i);
                if (z == null) {
                    continue;
                }
                Hoehendatei.Eintrag e = eintrag(z[0], z[1], sp);
                if (e != null && !e.punkt.isEmpty()) {
                    liste.add(e);
                }
            }
        }
        return liste;
    }

    /** Liefert {Start, Ende} einer Zeile; folgt Überlauf-Verweisen; null bei gelöschten Zeilen. */
    private int[] zeile(int pg, int i) {
        for (int tiefe = 0; tiefe < 5; tiefe++) {
            int o = pg * SEITE;
            int off = u16(o + 0x0e + 2 * i);
            if ((off & 0x8000) != 0) {
                return null;
            }
            int start = o + (off & 0x1FFF);
            int ende = o + (i == 0 ? SEITE : (u16(o + 0x0e + 2 * (i - 1)) & 0x1FFF));
            if ((off & 0x4000) == 0) {
                return new int[] { start, ende };
            }
            // Überlaufzeile: 1 Byte Zeilennummer, 3 Byte Seitennummer
            i = u8(start);
            pg = u8(start + 1) | (u8(start + 2) << 8) | (u8(start + 3) << 16);
            if (pg <= 0 || pg >= seiten) {
                return null;
            }
        }
        return null;
    }

    private Hoehendatei.Eintrag eintrag(int start, int ende, List<Spalte> sp) {
        int spaltenZeile = u16(start);
        int maske = (spaltenZeile + 7) / 8;
        int maskeStart = ende - maske;
        int varAnzahl = u16(maskeStart - 2);
        int[] varOff = new int[varAnzahl + 1];
        for (int j = 0; j <= varAnzahl; j++) {
            varOff[j] = u16(maskeStart - 4 - 2 * j);
        }
        Hoehendatei.Eintrag e = new Hoehendatei.Eintrag();
        for (Spalte s : sp) {
            boolean vorhanden = s.nummer / 8 < maske && ((u8(maskeStart + s.nummer / 8) >> (s.nummer % 8)) & 1) == 1;
            Object w = null;
            if (vorhanden) {
                if (s.fest) {
                    int p = start + 2 + s.festOffset;
                    switch (s.typ) {
                        case 0x07:
                        case 0x08:
                            w = d.getDouble(p);
                            break;
                        case 0x06:
                            w = (double) d.getFloat(p);
                            break;
                        case 0x04:
                            w = i32(p);
                            break;
                        case 0x03:
                            w = (int) d.getShort(p);
                            break;
                        default:
                            break;
                    }
                } else if (s.varIndex < varAnzahl) {
                    w = text(start + varOff[s.varIndex], start + varOff[s.varIndex + 1]);
                }
            }
            setze(e, s.name, w);
        }
        return e;
    }

    private static void setze(Hoehendatei.Eintrag e, String name, Object w) {
        switch (name.toLowerCase()) {
            case "pnr":
                e.punkt = w == null ? "" : w.toString().trim();
                break;
            case "hoehe":
                e.hoehe = w instanceof Number ? ((Number) w).doubleValue() : 0;
                break;
            case "bernr":
                e.berNr = w instanceof Number ? ((Number) w).intValue() : 0;
                break;
            case "anzb":
                e.ab = w instanceof Number ? ((Number) w).intValue() : 1;
                break;
            case "dif":
                e.diffMm = w instanceof Number ? ((Number) w).doubleValue() : 0;
                break;
            case "datum":
                e.datum = w == null ? "" : w.toString().trim();
                break;
            case "bemerk":
                e.bemerkung = w == null ? "" : w.toString().trim();
                break;
            default:
                break;
        }
    }

    /** Jet-4-Text: UCS-2 oder komprimiert (Kennung FF FE, Umschaltung mit 00). */
    private String text(int von, int bis) {
        StringBuilder sb = new StringBuilder();
        if (bis - von >= 2 && u8(von) == 0xFF && u8(von + 1) == 0xFE) {
            boolean komprimiert = true;
            int i = von + 2;
            while (i < bis) {
                if (u8(i) == 0) {
                    komprimiert = !komprimiert;
                    i++;
                } else if (komprimiert) {
                    sb.append((char) u8(i));
                    i++;
                } else if (i + 1 < bis) {
                    sb.append((char) u16(i));
                    i += 2;
                } else {
                    break;
                }
            }
        } else {
            for (int i = von; i + 1 < bis; i += 2) {
                sb.append((char) u16(i));
            }
        }
        return reparieren(sb.toString());
    }

    /** Als ANSI gespeicherter UTF-8-Text ("HÃ¶he") wird zurückgewandelt. */
    static String reparieren(String s) {
        if (s.indexOf('Ã') < 0 && s.indexOf('Â') < 0) {
            return s;
        }
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        String u = new String(b, StandardCharsets.UTF_8);
        return u.indexOf('�') >= 0 ? s : u;
    }
}
