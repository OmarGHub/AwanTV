package app.awanpro.tv;

/**
 * The screens' languages, one list for everything that names them: Settings' language row, the settings file and the
 * editor's choices (SettingsFile), the import's check and the language decision (Screen.language). A language is
 * added here only with its resources complete (res/values-xx/texts.xml, checked by checkTexts): one not yet ready
 * shows half in English (languages plan). Each name is written in its own language, so one who opened the app in a
 * language they cannot read still finds theirs.
 */
final class Languages {
    private Languages() {}

    /** The setting's values after "system", in the order Settings shows them. */
    static final String[] TAGS = {"ar", "en"};
    /** Each in its own language; not translated. */
    static final String[] NAMES = {"العربية", "English"};

    /** The setting's values: "system" (the TV's language), then each language. */
    static String[] values() {
        String[] out = new String[TAGS.length + 1];
        out[0] = "system";
        System.arraycopy(TAGS, 0, out, 1, TAGS.length);
        return out;
    }

    static boolean known(String tag) {
        for (String t : TAGS) if (t.equals(tag)) return true;
        return false;
    }
}
