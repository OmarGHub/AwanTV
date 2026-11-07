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
    static final String[] TAGS = {"ar", "en", "ur", "tr", "fr", "ms"};
    /** Each in its own language; not translated. */
    static final String[] NAMES = {"العربية", "English", "اردو", "Türkçe", "Français", "Bahasa Melayu"};

    /** The setting's values: "system" (the TV's language), then each language. */
    static String[] values() {
        String[] out = new String[TAGS.length + 1];
        out[0] = "system";
        System.arraycopy(TAGS, 0, out, 1, TAGS.length);
        return out;
    }

    /** A language's own name (NAMES); the tag itself for one this version does not know. */
    static String name(String tag) {
        for (int i = 0; i < TAGS.length; i++) if (TAGS[i].equals(tag)) return NAMES[i];
        return tag;
    }

    static boolean known(String tag) {
        for (String t : TAGS) if (t.equals(tag)) return true;
        return false;
    }

    /**
     * A language code as Android gives it (Locale.getLanguage), as the setting writes it: Android keeps three old
     * codes for compatibility (Indonesian "in", Hebrew "iw", Yiddish "ji"), and the resources' folders follow them
     * (values-in), but the setting is "id".
     */
    static String fromAndroid(String code) {
        if (code == null) return "";
        switch (code) {
            case "in": return "id";
            case "iw": return "he";
            case "ji": return "yi";
            default: return code;
        }
    }

    /**
     * The language's code at mp3quran.net (its API's ?language=, mp3quran.net/api): its own scheme ("eng", "cn"),
     * so reciters, surahs and reciter channels are named in the screen's language. Null for one it does not have
     * (Malay, Omar's one exception): those names then show in English.
     */
    static String mp3quran(String tag) {
        switch (tag) {
            case "en": return "eng";
            case "zh": return "cn";
            case "ar": case "fr": case "ru": case "de": case "es": case "tr": case "th": case "ur": case "bn": case "bs":
            case "ug": case "fa": case "tg": case "ml": case "tl": case "id": case "pt": case "ha": case "sw":
                return tag;
            default: return null;
        }
    }

    /** The screens' languages besides Arabic and English that mp3quran.net names things in: their own lists. */
    static java.util.List<String> moreLists() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String t : TAGS) if (!"ar".equals(t) && !"en".equals(t) && mp3quran(t) != null) out.add(t);
        return out;
    }

    /** Written right to left: Arabic, Urdu, Persian, Uyghur (of mp3quran.net's languages, the upper limit). */
    static boolean rtl(String tag) {
        return arabicScript(tag);
    }

    /** Written in the Arabic script: Amiri's bold for names, no capitals or letter spacing, the Arabic comma. */
    static boolean arabicScript(String tag) {
        return "ar".equals(tag) || "ur".equals(tag) || "fa".equals(tag) || "ug".equals(tag);
    }

    /**
     * The zero of the language's own digits when Eastern digits are on: Urdu and Persian write ۰۱۲۳ (from U+06F0),
     * every other language Arabic's ٠١٢٣ (from U+0660).
     */
    static char easternZero(String tag) {
        return "ur".equals(tag) || "fa".equals(tag) ? '\u06f0' : '\u0660';
    }

    /**
     * A text written once per language in a JSON item, as prefix + the language with a capital ("countAr",
     * "countEn", "countUr"): language -> text (adhkar.json, hadith.json; languages plan, phase 3).
     */
    static java.util.Map<String, String> fields(org.json.JSONObject o, String prefix) {
        java.util.Map<String, String> out = new java.util.HashMap<>();
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (k.length() <= prefix.length() || !k.startsWith(prefix)) continue;
            String lang = k.substring(prefix.length());
            if (!Character.isUpperCase(lang.charAt(0))) continue;
            out.put(lang.toLowerCase(java.util.Locale.ROOT), o.optString(k));
        }
        return out;
    }

    /**
     * The screen's language's text from fields(): Arabic and English their own, even empty, as before 1.18.0; any
     * other language its own, else English.
     */
    static String pick(java.util.Map<String, String> texts, Text t) {
        String own = texts.get(t.lang);
        if (t.ar || "en".equals(t.lang)) return own == null ? "" : own;
        if (own != null && !own.isEmpty()) return own;
        String en = texts.get("en");
        return en == null ? "" : en;
    }

    /** "0123" in the language's Eastern digits: Settings' digits row. */
    static String easternDigits(String tag) {
        char z = easternZero(tag);
        return new String(new char[] {z, (char) (z + 1), (char) (z + 2), (char) (z + 3)});
    }
}
