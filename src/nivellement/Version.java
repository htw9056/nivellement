package nivellement;

/**
 * Programmname, Version und Urheber.
 */
public final class Version {

    public static final String NAME = "Nivellement";
    public static final String VERSION = "V1.00";
    public static final String URHEBER = "Dipl.-Ing. (FH) Stefan Klein";

    private Version() {
    }

    public static String titel() {
        return NAME + " " + VERSION + " © " + URHEBER;
    }
}
