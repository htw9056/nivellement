package nivellement;

import java.util.Locale;

/**
 * Kleiner Formelauswerter für Fehlergrenzen, z. B. "2 + 3*Sqrt(R)" oder "2 + 3*Sqrt(R/2)".
 * Erlaubt: Zahlen, R, + - * / ^, Klammern, Sqrt(), Abs().
 */
public final class Formel {

    private final String s;
    private final double r;
    private int pos;

    private Formel(String s, double r) {
        this.s = s.replace(',', '.').replace(" ", "").toLowerCase(Locale.ROOT);
        this.r = r;
    }

    public static double berechne(String formel, double rKm) {
        Formel f = new Formel(formel, rKm);
        double w = f.ausdruck();
        if (f.pos != f.s.length()) {
            throw new IllegalArgumentException("Formel fehlerhaft bei Zeichen " + (f.pos + 1) + ": " + formel);
        }
        return w;
    }

    /** Prüft eine Formel, liefert null bei Erfolg, sonst die Fehlermeldung. */
    public static String pruefe(String formel) {
        try {
            berechne(formel, 1.0);
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    private double ausdruck() {
        double w = term();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '+') {
                pos++;
                w += term();
            } else if (c == '-') {
                pos++;
                w -= term();
            } else {
                break;
            }
        }
        return w;
    }

    private double term() {
        double w = potenz();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '*') {
                pos++;
                w *= potenz();
            } else if (c == '/') {
                pos++;
                w /= potenz();
            } else {
                break;
            }
        }
        return w;
    }

    private double potenz() {
        double w = faktor();
        if (pos < s.length() && s.charAt(pos) == '^') {
            pos++;
            w = Math.pow(w, potenz());
        }
        return w;
    }

    private double faktor() {
        if (pos >= s.length()) {
            throw new IllegalArgumentException("Formel unvollständig: " + s);
        }
        char c = s.charAt(pos);
        if (c == '-') {
            pos++;
            return -faktor();
        }
        if (c == '+') {
            pos++;
            return faktor();
        }
        if (c == '(') {
            pos++;
            double w = ausdruck();
            erwarte(')');
            return w;
        }
        if (s.startsWith("sqrt(", pos)) {
            pos += 5;
            double w = ausdruck();
            erwarte(')');
            return Math.sqrt(w);
        }
        if (s.startsWith("abs(", pos)) {
            pos += 4;
            double w = ausdruck();
            erwarte(')');
            return Math.abs(w);
        }
        if (c == 'r') {
            pos++;
            return r;
        }
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("Unerwartetes Zeichen '" + c + "' in Formel: " + s);
        }
        return Double.parseDouble(s.substring(start, pos));
    }

    private void erwarte(char c) {
        if (pos >= s.length() || s.charAt(pos) != c) {
            throw new IllegalArgumentException("'" + c + "' erwartet in Formel: " + s);
        }
        pos++;
    }
}
