package app.awanpro.tv;

import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Madhab;

/**
 * Interface strings for both locales, first taken from the `t` tables in design/afnon-mihrab/design/Home.dc.html.
 *
 * <p>The words are in res/values/texts.xml (English, also what any missing text falls back to) and
 * res/values-ar/texts.xml (languages plan, phase 1): read in the language chosen in Settings, whatever the TV's own,
 * from one Resources per language (`of`). The methods still decide everything else: which text, the digits (d()),
 * the Arabic counting forms, still in code until phase 2. The crash screen's texts stay in code (`crash...`, `back`):
 * it must open even when the resources cannot be read, so `new Text(ar, eastern)` (no resources) serves it alone.
 */
final class Text {
    /**
     * The language (Languages.TAGS): its resources' folder (values-ar, values for English), direction, script,
     * dates, order and digits (languages plan, phase 3: any language, not Arabic and English only).
     */
    final String lang;
    /**
     * Arabic itself. Besides the Arabic counting forms kept in code, it chooses the Arabic side of what is written in
     * Arabic and English (cities, countries, stations, adhan voices, apps' Arabic names, the reciters' kept names):
     * any other language takes the English side. Direction and script are rtl() and arabicScript(), not this.
     */
    final boolean ar;
    /** Eastern digits (Arabic's ٠١٢٣, Urdu's ۰۱۲۳). Its own setting; defaults to on in Arabic. */
    final boolean eastern;
    private final Res res;

    /** Where the words come from: Android's resources in one language, or (the text dump's check) any table. */
    interface Res {
        String string(int id);
        String[] array(int id);
    }

    /** Without resources: the crash screen's fallback only (its texts are in code). */
    Text(boolean ar, boolean eastern) {
        this(null, ar, eastern);
    }

    Text(Res res, boolean ar, boolean eastern) {
        this(res, ar ? "ar" : "en", eastern);
    }

    Text(Res res, String lang, boolean eastern) {
        this.res = res;
        this.lang = lang;
        this.ar = "ar".equals(lang);
        this.eastern = eastern;
    }

    /** Written right to left: the screen's layout and its text direction. */
    boolean rtl() {
        return Languages.rtl(lang);
    }

    /** Written in the Arabic script: Amiri's bold for names; no capitals, no letter spacing, the Arabic comma. */
    boolean arabicScript() {
        return Languages.arabicScript(lang);
    }

    /**
     * Month and day names in the screen's language, Western digits (d() decides the digits). Arabic and English as
     * before 1.18.0 (other Arabic regions name the months otherwise); any other language its own, Latin digits.
     */
    java.util.Locale dateLocale() {
        if (ar) return java.util.Locale.forLanguageTag("ar-EG-u-nu-latn");
        if ("en".equals(lang)) return java.util.Locale.UK;
        return java.util.Locale.forLanguageTag(lang + "-u-nu-latn");
    }

    /** The order of names in the screen's language (apps, countries). */
    java.util.Locale sortLocale() {
        if (ar) return new java.util.Locale("ar");
        if ("en".equals(lang)) return java.util.Locale.ENGLISH;
        return new java.util.Locale(lang);
    }

    private static final java.util.Map<String, Res> RES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The texts in Arabic or English, whatever the TV's own language: the application's resources with only the
     * locale set, made once per language and kept (screens, the screensaver, the services and the adb receiver may
     * ask from different threads). Words only: no size, image or font is read through it.
     */
    static Text of(android.content.Context c, boolean ar, boolean eastern) {
        return of(c, ar ? "ar" : "en", eastern);
    }

    /** The texts in one language (Languages.TAGS); a missing text is English's (values). */
    static Text of(android.content.Context c, String tag, boolean eastern) {
        Res r = RES.get(tag);
        if (r == null) {
            android.content.Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
            android.content.res.Configuration cfg = new android.content.res.Configuration(app.getResources().getConfiguration());
            cfg.setLocale(new java.util.Locale(tag));
            android.content.res.Resources words = app.createConfigurationContext(cfg).getResources();
            r = new Res() {
                @Override public String string(int id) { return words.getString(id); }
                @Override public String[] array(int id) { return words.getStringArray(id); }
            };
            RES.put(tag, r);
        }
        return new Text(r, tag, eastern);
    }

    /** A text: %% in it is a percent sign (every string is written for fill). */
    /**
     * The singular of a count (xxxOne, else xxxOther): 1 in English, Urdu and Turkish; 0 and 1 in French (CLDR's
     * "one", "0 minute"). Arabic keeps its own forms in code.
     */
    private boolean one(long n) {
        return n == 1 || (n == 0 && "fr".equals(lang));
    }

    private String s(int id) {
        return degreeSide(fill(res.string(id)));
    }

    /** A text with its parts: %1$s, %2$s… by position, %% a percent sign. */
    private String s(int id, Object... parts) {
        return degreeSide(fill(res.string(id), parts));
    }

    /**
     * Fills %1$s… in one pass (a part that itself holds "%2$s" stays as it is). Not String.format: that would write
     * the numbers in the resources' locale, and the digits are d()'s alone. Each part as string concatenation wrote it.
     */
    static String fill(String template, Object... parts) {
        StringBuilder b = new StringBuilder(template.length() + 16);
        int n = template.length();
        for (int i = 0; i < n; i++) {
            char c = template.charAt(i);
            if (c == '%' && i + 1 < n) {
                char next = template.charAt(i + 1);
                if (next == '%') { b.append('%'); i++; continue; }
                int j = i + 1;
                while (j < n && Character.isDigit(template.charAt(j))) j++;
                if (j > i + 1 && j + 1 < n && template.charAt(j) == '$' && template.charAt(j + 1) == 's') {
                    int k = Integer.parseInt(template.substring(i + 1, j)) - 1;
                    b.append(k >= 0 && k < parts.length ? String.valueOf(parts[k]) : template.substring(i, j + 2));
                    i = j + 1;
                    continue;
                }
            }
            b.append(c);
        }
        return b.toString();
    }

    private String[] a(int id) {
        String[] items = res.array(id), out = items;
        for (int i = 0; i < items.length; i++) {
            String v = degreeSide(items[i]);
            if (v == items[i]) continue;
            if (out == items) out = items.clone();
            out[i] = v;
        }
        return out;
    }

    /** An array written row after row (title, line, title, line…) as rows of width. */
    private static String[][] pairs(String[] flat, int width) {
        String[][] out = new String[flat.length / width][];
        for (int i = 0; i < out.length; i++) out[i] = java.util.Arrays.copyOfRange(flat, i * width, i * width + width);
        return out;
    }

    String d(String s) {
        return degreeSide(digits(s));
    }

    /**
     * A number with its degree sign, in a right-to-left line. Unicode's bidi puts the sign by the kind of digits and by
     * what comes before them ("°18" after a word, "18°" at the start of a line with Western digits), so the same screen
     * had it on both sides. Arabic: always left of the number, as Saudi Arabia's National Center for Meteorology writes
     * it (the number in a left-to-right isolate, the sign after it outside: a minus stays before the digits). Persian and
     * Urdu: always right, as Persian Calendar and CLDR's Urdu (the number and its sign in one isolate). Other languages
     * as written. Once only: a number already marked doesn't match again.
     */
    String degreeSide(String s) {
        if (s == null || s.indexOf('°') < 0 || !rtl()) return s;
        return DEGREES.matcher(s).replaceAll(ar ? "\u2066$1\u2069°" : "\u2066$1°\u2069");
    }

    private static final java.util.regex.Pattern DEGREES = java.util.regex.Pattern.compile(
        "(?<![\\u2066\\-0-9\\u0660-\\u0669\\u06F0-\\u06F9.\\u066B])"
        + "(-?[0-9\\u0660-\\u0669\\u06F0-\\u06F9]+(?:[.\\u066B][0-9\\u0660-\\u0669\\u06F0-\\u06F9]+)?)°");

    /** The screen's digits and the punctuation that goes with them. */
    private String digits(String s) {
        if (!eastern) return s;
        // "·" beside Eastern digits reads as a zero (٠); Arabic punctuation takes its own comma instead. Awan TV
        // English with Eastern digits takes the Latin comma ("Cairo, Friday", not "Cairo، Friday").
        // Punctuation of the script, not a word: in code, so the crash screen's fallback (no resources) has it too.
        s = s.replace(" · ", arabicScript() ? "، " : ", ");
        // The language's own Eastern digits: Arabic's ٠١٢٣, Urdu's ۰۱۲۳ (Languages.easternZero).
        char zero = Languages.easternZero(lang);
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c >= '0' && c <= '9' ? (char) (zero + (c - '0')) : c);
        }
        return b.toString();
    }

    /** The " · " between parts of a line; a comma where "·" would sit beside Eastern digits. */
    String sep() { return d(" · "); }

    /** The arch verse's reference (An-Nisa 4:103), in the screen's digits like every other number. */
    String ref() { return d(s(R.string.ref)); }
    String nextPrayer() { return s(R.string.nextPrayer); }
    String today() { return s(R.string.today); }
    String settings() { return s(R.string.settings); }
    /** The saved city in this language; set by {@link Screen#loadText}. */
    String cityName = "";
    String city() { return cityName; }
    String fav() { return s(R.string.fav); }
    String more() { return s(R.string.more); }
    /** Home's tile: the label over the count. */
    String all(int n) { return d((s(R.string.all)) + "\n" + n); }
    String ampm(boolean am) { return (am ? s(R.string.ampm) : s(R.string.ampm_2)); }
    String adhanAt() { return s(R.string.adhanAt); }
    String nowTag() { return s(R.string.nowTag); }
    String nextTag() { return s(R.string.nextTag); }
    String jumuahLabel() { return s(R.string.jumuahLabel); }
    String jumuah() { return s(R.string.jumuah); }
    String kahf() { return s(R.string.kahf); }
    String iftarIn() { return s(R.string.iftarIn); }
    String iftar() { return s(R.string.iftar); }
    String iftarTag() { return s(R.string.iftarTag); }
    String suhoorTag() { return s(R.string.suhoorTag); }
    String suhoorEnds(String time) { return d(s(R.string.suhoorEnds, time)); }
    String comingSoon() { return s(R.string.comingSoon); }

    String hintOk(String what) { return "OK  " + what; }
    String hintApps() { return s(R.string.hintApps); }
    String hintMore() { return s(R.string.hintMore); }
    String hintFavs() { return s(R.string.hintFavs); }
    String open(String app) { return s(R.string.open, app); }
    String network() { return s(R.string.network); }
    // Home's network button, in its hint.
    String netWired() { return s(R.string.netWired); }
    String netOff() { return s(R.string.netOff); }
    String netNoInternet() { return s(R.string.netNoInternet); }

    // Screensaver (design/afnon-mihrab/design/Screensaver.dc.html)
    /**
     * "in 27 min". Arabic counts take their proper forms: دقيقة واحدة، دقيقتين، ٣–١٠ دقائق، ١١+ دقيقة. An hour
     * or more reads as h:mm, as on Prayer day.
     */
    String inMinutes(long minutes, String hoursAndMinutes) {
        if (minutes >= 60) return in(hoursAndMinutes);
        if (!ar) return d(s(R.string.inMinutesOther, minutes));
        if (minutes <= 1) return "بعد دقيقة واحدة";
        if (minutes == 2) return "بعد دقيقتين";
        return d("بعد " + minutes + (minutes <= 10 ? " دقائق" : " دقيقة"));
    }

    // Reminder and call to prayer (design/afnon-mihrab/design/Home.dc.html approach/now, design/afnon-mihrab/design/ApproachVideo.dc.html)
    String apLabel() { return s(R.string.apLabel); }
    /** "Asr in 13 minutes". */
    String apTitle(String prayer, long minutes, String hoursAndMinutes) {
        if (!ar && minutes < 60) return d(s(one(minutes) ? R.string.apTitleOne : R.string.apTitleOther, prayer, minutes));
        return prayer + " " + inMinutes(minutes, hoursAndMinutes);
    }
    String apSub(CharSequence time) { return d((s(R.string.apSub)) + time + (s(R.string.apSub_2))); }
    String apOk() { return s(R.string.apOk); }
    String bannerSub(CharSequence time, int seconds) {
        return d((s(R.string.bannerSub)) + time + (s(R.string.bannerSub_2, seconds)));
    }
    String nowLabel() { return s(R.string.nowLabel); }
    String play() { return s(R.string.play); }
    String stop() { return s(R.string.stop); }
    String dismiss() { return s(R.string.dismiss); }
    // Radio: Home's pill and card, the screen-off view, the screensaver line and the Settings section.
    String radio() { return s(R.string.radio); }
    /** A station's name: Arabic or English as written; another language mp3quran.net's name for it, else English. */
    String station(Radio.Station s) {
        if (ar || "en".equals(lang)) return d(ar ? s.ar : s.en);
        String own = s.more.get(lang);
        return d(own != null ? own : s.en);
    }
    /** Under a station's name: where it broadcasts (English in another language), or what a reciter's channel is. */
    String stationWhere(Radio.Station s) {
        if (ar || "en".equals(lang)) return d(ar ? s.whereAr : s.whereEn);
        if (s.savedSurahs >= 0) return d(s(R.string.stationSaved) + " · " + surahCount(s.savedSurahs));
        if (s.reciter) return d(s(R.string.stationAroundClock));
        return d(s.whereEn);
    }
    /** A station in a list: its name and place, since several are called إذاعة القرآن الكريم. */
    String stationChoice(Radio.Station s) { return s.reciter ? station(s) : station(s) + sep() + stationWhere(s); }
    String stationRow() { return s(R.string.stationRow); }
    String[] stationGroups() {
        // Awan TV adds the recordings saved on the TV (Library).
        return a(R.array.stationGroups);
    }
    String libraryRow() { return s(R.string.libraryRow); }
    String libraryHelp(int recordings, String size) {
        if (recordings == 0) return s(R.string.libraryHelp);
        return d(s(one(recordings) ? R.string.libraryHelpOne : R.string.libraryHelpMany, recordings, size));
    }
    String libraryIntro() {
        return s(R.string.libraryIntro);
    }
    /** The Arabic line puts its digits through d(), the English one never did. */
    String reserveNote() { String v = s(R.string.reserveNote); return arabicScript() ? d(v) : v; }
    String savedSize(String s) { return (s(R.string.savedSize)) + s; }
    String freeSize(String s) { return (s(R.string.freeSize)) + s; }
    String searchReciter() { return s(R.string.searchReciter); }
    String loadingReciters() { return s(R.string.loadingReciters); }
    String recitersOffline() {
        return s(R.string.recitersOffline);
    }
    String noReciter() { return s(R.string.noReciter); }
    String complete() { return s(R.string.complete); }
    String someOf(int have, int of) { return s(R.string.someOf, have, of); }
    /** Arabic counts by surahsAr (its grammar in code until the plural forms move, phase 2); the Arabic values are unused. */
    String surahCount(int n) { return ar ? surahsAr(n) : s(one(n) ? R.string.surahOne : R.string.surahMany, n); }
    /** "سورة واحدة", "سورتان", "3 سور", "11 سورة", "103 سور": Arabic counts by the last two digits. */
    static String surahsAr(int n) {
        if (n == 1) return "سورة واحدة";
        if (n == 2) return "سورتان";
        int m = n % 100;
        return n + (m >= 3 && m <= 10 ? " سور" : " سورة");
    }
    String noDownloads() {
        return s(R.string.noDownloads);
    }
    String downloadAll(int n) { return s(R.string.downloadAll, surahCount(n)); }
    String downloadRest(int n) { return s(R.string.downloadRest, surahCount(n)); }
    String deleteSaved() { return s(R.string.deleteSaved); }
    String waitingSpace() {
        // The Arabic line puts its digits through d(), the English one never did.
        String v = s(R.string.waitingSpace);
        return arabicScript() ? d(v) : v;
    }
    String noStorage() { return s(R.string.noStorage); }
    String downloadingNow(String surah, int pct, int waiting) {
        String p = pct >= 0 ? " " + s(R.string.downloadingPercent, pct) : "";
        String then = waiting > 0 ? (s(R.string.downloadingNow, waiting)) : "";
        return (s(R.string.downloadingNow_2)) + surah + p + then;
    }
    String queuedOnly(int waiting) { return s(R.string.queuedOnly, waiting); }
    String allSaved() { return s(R.string.allSaved); }
    String deleteAsk() { return s(R.string.deleteAsk); }
    String pickReciter() { return s(R.string.pickReciter); }
    String libraryLegend() {
        return s(R.string.libraryLegend);
    }
    String hintChooseWord() { return s(R.string.hintChooseWord); }
    String hintToSurahs() { return s(R.string.hintToSurahs); }
    String hintBackSettings() { return s(R.string.hintBackSettings); }
    String recitationsCredit() { return s(R.string.recitationsCredit); }
    String recitationsCreditLong() {
        return s(R.string.recitationsCreditLong);
    }
    String adhanCredit() {
        return s(R.string.adhanCredit);
    }
    String nothingSaved() { return s(R.string.nothingSaved); }
    /** The decimal point: the Arabic one (٫) with Eastern digits, a point with Western ones. */
    private String decimal(String v) { return eastern ? v.replace('.', '٫') : v; }
    /** A size in MB or GB, in the screen's digits. */
    String bytes(long b) {
        double gb = b / (1024.0 * 1024 * 1024);
        if (gb >= 1) {
            String v = String.format(java.util.Locale.US, "%.1f", gb);
            return d(s(R.string.bytesGb, arabicScript() ? decimal(v) : v));
        }
        long mb = Math.round(b / (1024.0 * 1024));
        return d(s(R.string.bytes, mb));
    }
    String radioStatus(int s) {
        switch (s) {
            case Radio.PLAYING: return s(R.string.radioStatus);
            case Radio.CONNECTING: return s(R.string.radioStatus_2);
            case Radio.RETRYING: return s(R.string.radioStatus_3);
            case Radio.PAUSED: return s(R.string.radioStatus_4);
            default: return s(R.string.radioStatus_5);
        }
    }
    String radioPlay() { return s(R.string.radioPlay); }
    String radioStop() { return s(R.string.radioStop); }
    String screenOff() { return s(R.string.screenOff); }
    String radioNote() {
        return d(s(R.string.radioNote));
    }
    String screenOffNote() {
        return d(s(R.string.screenOffNote));
    }
    String hintClose() { return s(R.string.hintClose); }
    String sectionRadio() { return s(R.string.sectionRadio); }
    String radioRowHelp(Radio.Station s) {
        // A saved recording plays without the internet.
        if (s.url == null) return stationWhere(s);
        return stationWhere(s) + (s(R.string.radioRowHelp));
    }
    String afterAdhanRow() { return s(R.string.afterAdhanRow); }
    String afterAdhanHelp() { return s(R.string.afterAdhanHelp); }
    String[] afterAdhanChoices() { return a(R.array.afterAdhanChoices); }
    String radioDarkRow() { return s(R.string.radioDarkRow); }
    String radioDarkHelp() {
        return d(s(R.string.radioDarkHelp));
    }
    String[] radioDarkChoices() {
        return a(R.array.radioDarkChoices);
    }

    /** The call screen's hint: how it closes by itself (Settings → Reminders); 0 is when the adhan ends. */
    String nowHint(int minutes) {
        if (minutes == 0) return d(s(R.string.nowHint));
        return d(minutes == 1 ? s(R.string.nowHintOne) : s(R.string.nowHintMany, minutes));
    }
    String callCloseRow() { return s(R.string.callCloseRow); }
    String callCloseHelp() { return s(R.string.callCloseHelp); }
    /** In the order of the row's minutes: 0 (after the adhan), 1, 5, 10. */
    String[] callCloseChoices() {
        return a(R.array.callCloseChoices);
    }
    String voiceRow() { return s(R.string.voiceRow); }
    String voiceHelp() { return s(R.string.voiceHelp); }
    String fajrVoiceRow() { return s(R.string.fajrVoiceRow); }
    String fajrVoiceHelp() { return s(R.string.fajrVoiceHelp); }

    // All apps (design/afnon-mihrab/design/AllApps.dc.html)
    String allTitle() { return s(R.string.allTitle); }
    String back() { return ar ? "رجوع" : "Back"; }
    /** "Asr in", followed by the countdown. */
    String chip(String prayer) { return s(R.string.chip, prayer); }
    String favNote(int position) {
        // Six favourites now (Apps.FAVOURITE_SLOTS); the fifth and sixth crashed All apps.
        return ar ? s(R.string.favNote, ordinal(position)) : d(s(R.string.favNote, position));
    }
    /** "In More · 3rd". */
    String moreNote(int position) { return ar ? s(R.string.moreNote, ordinal(position)) : d(s(R.string.moreNote, position)); }
    private String ordinal(int position) {
        String[] ord = {"الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس"};
        return position >= 1 && position <= ord.length ? ord[position - 1] : d(String.valueOf(position));
    }
    String notFav() { return s(R.string.notFav); }
    String notOnHome() { return s(R.string.notOnHome); }
    String moveEarlierMore() { return s(R.string.moveEarlierMore); }
    String moveLaterMore() { return s(R.string.moveLaterMore); }
    String moveToMore() { return s(R.string.moveToMore); }
    String moveToFav() { return s(R.string.moveToFav); }
    String putInFav() { return s(R.string.putInFav); }
    String putInMore() { return s(R.string.putInMore); }
    String inPlaceOf(String app) { return s(R.string.inPlaceOf, app); }
    String chooseFavSlot() { return s(R.string.chooseFavSlot); }
    String chooseMoreSlot() { return s(R.string.chooseMoreSlot); }
    String hintOpen() { return s(R.string.hintOpen); }
    String hintHold() { return s(R.string.hintHold); }
    String moveEarlier() { return s(R.string.moveEarlier); }
    String moveLater() { return s(R.string.moveLater); }
    String cancel() { return s(R.string.cancel); }
    String favourite() { return s(R.string.favourite); }

    // Prayer day (design/afnon-mihrab/design/PrayerDay.dc.html)
    String dayTitle() { return s(R.string.dayTitle); }
    String home() { return s(R.string.home); }
    String school(Madhab m) {
        if (m == Madhab.HANAFI) return s(R.string.school);
        return s(R.string.school_2);
    }

    String methodName(Method m) {
        if (m == Method.UMM_AL_QURA) return s(R.string.methodName);
        if (m == Method.MUSLIM_WORLD_LEAGUE) return s(R.string.methodName_2);
        if (m == Method.KARACHI) return s(R.string.methodName_3);
        if (m == Method.DUBAI) return s(R.string.methodName_4);
        if (m == Method.KUWAIT) return s(R.string.methodName_5);
        if (m == Method.QATAR) return s(R.string.methodName_6);
        if (m == Method.NORTH_AMERICA) return s(R.string.methodName_7);
        if (m == Method.MOON_SIGHTING_COMMITTEE) return s(R.string.methodName_8);
        if (m == Method.SINGAPORE) return s(R.string.methodName_9);
        if (m == Method.OMAN_AWQAF) return s(R.string.methodName_10);
        if (m == Method.MOROCCO_HABOUS) return s(R.string.methodName_11);
        if (m == Method.TEHRAN) return s(R.string.methodName_13);
        return s(R.string.methodName_12);
    }

    String methodAngles(Method m) {
        CalculationParameters p = Prefs.parameters(m);
        String fajr = (s(R.string.methodAngles)) + trim(p.fajrAngle) + "°";
        String isha = p.ishaInterval > 0
            ? (s(R.string.methodAngles_2, p.ishaInterval))
            : (s(R.string.methodAngles_3)) + trim(p.ishaAngle) + "°";
        if (m.maghribAngle() > 0) return d(fajr + " · " + s(R.string.methodAngles_4) + trim(m.maghribAngle()) + "° · " + isha);
        return d(fajr + " · " + isha);
    }

    /** Morocco's method under its name: in Morocco by the ministry's tables, elsewhere at its coastal horizon. */
    String habousAngles(boolean table) {
        return d(table ? (s(R.string.habousAngles))
                       : (s(R.string.habousAngles_2)));
    }

    /** Settings > Prayer times > Ministry table (Morocco): which of the ministry's 191 timetables. */
    String habousRow() { return s(R.string.habousRow); }
    String habousHelp(int km, boolean chosen) {
        return d(chosen ? (s(R.string.habousHelp, km))
                        : (s(R.string.habousHelp_2, km)));
    }
    String habousChooser() { return s(R.string.habousChooser); }
    String habousItem(String name, int km) { return d(name + "  ·  " + km + (s(R.string.habousItem))); }
    String habousAuto() { return s(R.string.habousAuto); }
    String habousFileHelp() { return s(R.string.habousFileHelp); }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    // Settings (design/afnon-mihrab/design/Settings.dc.html)
    String sectionPrayer() { return s(R.string.sectionPrayer); }
    String sectionReminders() { return s(R.string.sectionReminders); }
    String sectionDisplay() { return s(R.string.sectionDisplay); }
    String sectionNetwork(String ssid) { return ssid == null ? (s(R.string.sectionNetwork)) : (s(R.string.sectionNetwork_2)) + ssid; }
    String sectionDevice() { return s(R.string.sectionDevice); }
    String preview() { return s(R.string.preview); }
    String location() { return s(R.string.location); }
    // The first run's location and Home's reminder while none is set.
    String chooseLocation() { return s(R.string.chooseLocation); }
    String otherLocation() { return s(R.string.otherLocation); }
    String locating() { return s(R.string.locating); }
    String near(String place) { return s(R.string.near, place); }
    String locationAskTitle() { return s(R.string.locationAskTitle); }
    String locationAskBody(String guess) {
        return s(R.string.locationAskBody, guess);
    }
    String setLocation() { return s(R.string.setLocation); }
    String later() { return s(R.string.later); }
    String hintBackLater() { return s(R.string.hintBackLater); }
    String hintBackLocate() { return s(R.string.hintBackLocate); }
    String noLocation() { return s(R.string.noLocation); }
    String setYourLocation() { return s(R.string.setYourLocation); }
    String country() { return s(R.string.country); }
    String region() { return s(R.string.region); }
    /** Location's first step, in the order of Prefs.REGIONS. */
    String[] regions() {
        return a(R.array.regions);
    }
    String locationHelp(double lat, double lng, boolean byCoordinates) {
        return coordinates(lat, lng) + (byCoordinates ? (s(R.string.locationHelp))
                                                      : (s(R.string.locationHelp_2)));
    }
    /** "21.42° N · 39.83° E" in the screen's digits. */
    String coordinates(double lat, double lng) {
        String ns = lat >= 0 ? (s(R.string.coordinates)) : (s(R.string.coordinates_2));
        String ew = lng >= 0 ? (s(R.string.coordinates_3)) : (s(R.string.coordinates_4));
        return d(String.format(java.util.Locale.US, "%.2f° %s · %.2f° %s", Math.abs(lat), ns, Math.abs(lng), ew));
    }
    String place(String city, String country) { return city + (s(R.string.place)) + country; }
    /** Palestine in a language whose country names come from Android (Places.country): ours, as in Arabic and English. */
    String palestine() { return s(R.string.palestine); }
    String method() { return s(R.string.method); }
    String asrSchool() { return s(R.string.asrSchool); }
    String asrSchoolHelp() { return s(R.string.asrSchoolHelp); }
    String[] asrSchools() { return a(R.array.asrSchools); }
    String hijriAdjust() { return s(R.string.hijriAdjust); }
    /** About > Sources and licences (SettingsActivity.creditRows). */
    String creditsRow() { return s(R.string.creditsRow); }
    String creditsHelp() { return s(R.string.creditsHelp); }
    String creditsSection() { return s(R.string.creditsSection); }

    /** What's new (NewsActivity): after an update, and from About. */
    String newsRow() { return s(R.string.newsRow); }
    String newsRowHelp() { return s(R.string.newsRowHelp); }
    String newsAll() { return s(R.string.newsAll); }
    String newsTitle(String version) { return s(R.string.newsTitle, version); }
    String newsAlso(String version) { return s(R.string.newsAlso, version); }
    String newsLater() { return s(R.string.newsLater); }
    String newsOk() { return s(R.string.newsOk); }
    /** Each version's lines: {name, where it is or what it does}; NewsActivity.VERSIONS lists the versions. */
    String[][] news(String version) {
        switch (version) {
            case "1.25.0": {
                // Its degrees in the viewer's digits.
                String[] lines = a(R.array.news_1_25_0);
                for (int i = 0; i < lines.length; i++) lines[i] = d(lines[i]);
                return pairs(lines, 2);
            }
            case "1.24.0": {
                // Its degrees and years in the viewer's digits.
                String[] lines = a(R.array.news_1_24_0);
                for (int i = 0; i < lines.length; i++) lines[i] = d(lines[i]);
                return pairs(lines, 2);
            }
            case "1.22.0":
                return pairs(a(R.array.news_1_22_0), 2);
            case "1.21.0":
                return pairs(a(R.array.news_1_21_0), 2);
            case "1.20.0":
                return pairs(a(R.array.news_1_20_0), 2);
            case "1.19.0":
                return pairs(a(R.array.news_1_19_0), 2);
            case "1.18.0":
                return pairs(a(R.array.news_1_18_0), 2);
            case "1.17.0":
                return pairs(a(R.array.news_1_17_0), 2);
            case "1.14.2":
                return pairs(a(R.array.news_1_14_2), 2);
            case "1.14.1":
                return pairs(a(R.array.news_1_14_1), 2);
            case "1.14.0":
                return pairs(a(R.array.news_1_14_0), 2);
            case "1.13.0":
                return pairs(a(R.array.news_1_13_0), 2);
            case "1.10.0":
                return pairs(a(R.array.news_1_10_0), 2);
            case "1.9.0":
                return pairs(a(R.array.news_1_9_0), 2);
            case "1.8.0":
                return pairs(a(R.array.news_1_8_0), 2);
            case "1.7.0":
                return pairs(a(R.array.news_1_7_0), 2);
            case "1.5.0":
                return pairs(a(R.array.news_1_5_0), 2);
            case "1.4.0":
                return pairs(a(R.array.news_1_4_0), 2);
            case "1.3.0":
                return pairs(a(R.array.news_1_3_0), 2);
            case "1.2.0":
            {
                // The first line's digits through d(), as before.
                String[][] p = pairs(a(R.array.news_1_2_0), 2);
                p[0][1] = d(p[0][1]);
                return p;
            }
            case "1.1.0":
                return pairs(a(R.array.news_1_1_0), 2);
            default:
                return new String[0][];
        }
    }

    /** The new version's window (SettingsActivity.openUpdate). */
    String updateTitle(String version) { return s(R.string.updateTitle, version); }
    String updateHave(String version) { return s(R.string.updateHave, version); }
    String updateHow() {
        return s(R.string.updateHow);
    }
    /** Over Home (UpdateNotice): why to update, when it comes again, and the version-only mute. */
    String updateWhy() {
        return s(R.string.updateWhy);
    }
    String updateAgain() {
        return s(R.string.updateAgain);
    }
    String updateMute() { return s(R.string.updateMute); }
    /** Update now (SelfUpdate), over Home and in Settings > About's window. */
    String updateNow() { return s(R.string.updateNow); }
    String updateLater() { return s(R.string.updateLater); }
    String updateRetry() { return s(R.string.updateRetry); }
    String updateHowSelf() {
        return s(R.string.updateHowSelf);
    }
    /** Without the install appop (installed by hand): what Update now needs. */
    String updateManual() {
        return s(R.string.updateManual);
    }
    String updateDownloading(String version, int percent) {
        return d((s(R.string.updateDownloading)) + version + (s(R.string.updateDownloading_2)) + percent + (s(R.string.updateDownloading_3)));
    }
    String updateLeave() { return s(R.string.updateLeave); }
    String updateThen() {
        return s(R.string.updateThen);
    }
    String updateInstalling() { return s(R.string.updateInstalling); }
    String updateContinue() { return s(R.string.updateContinue); }
    String updateConfirm() {
        return s(R.string.updateConfirm);
    }
    String updateFailed() {
        return s(R.string.updateFailed);
    }
    String updateOpen() { return s(R.string.updateOpen); }
    String hintBackClose() { return s(R.string.hintBackClose); }
    /** Prayer times > Adjust the times and the date (Prefs.adjustments, Prefs.hijriOffset). */
    String adjustRow() { return s(R.string.adjustRow); }
    String adjustRowHelp() { return d(s(R.string.adjustRowHelp)); }
    String adjustSection() { return s(R.string.adjustSection); }
    String hintBackPrayer() { return s(R.string.hintBackPrayer); }
    /** The row's value: what is adjusted ("الفجر +2، المغرب +1"), the first two and "…" after; "بلا تعديل" for none. */
    String adjustSummary(int[] minutes, int hijriDays) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        String[] names = prayerNames();
        for (int i = 0; i < minutes.length; i++) if (minutes[i] != 0) parts.add(names[i] + " " + minutesSigned(minutes[i]));
        if (hijriDays != 0) parts.add((s(R.string.adjustSummary)) + minutesSigned(hijriDays));
        if (parts.isEmpty()) return s(R.string.adjustSummary_2);
        String s = parts.get(0) + (parts.size() > 1 ? (s(R.string.adjustSummary_3)) + parts.get(1) : "");
        return parts.size() > 2 ? s + " …" : s;
    }
    /** A time's adjustment between its − and + buttons: "+2", "0", "-3" (minutes). */
    String minutesSigned(int n) {
        // Left-to-right embedding: in an Arabic line the sign would otherwise land after the number ("2+").
        return "\u202A" + d(n > 0 ? "+" + n : String.valueOf(n)) + "\u202C";
    }
    /** Under each time's name: what it is with the adjustment (Fajr is tomorrow's after Isha), and how far it goes. */
    String adjustTimeHelp(String time, boolean tomorrow) {
        return d(s(tomorrow ? R.string.adjustTimeHelpTomorrow : R.string.adjustTimeHelpToday, time));
    }
    String adjustNote() {
        return s(R.string.adjustNote);
    }
    String adjustFileLabel(String prayer) { return s(R.string.adjustFileLabel, prayer); }

    // Prayer times > Calculation method > Custom calculation (1.24.0, Prefs.Calc)
    String calcItem() { return s(R.string.calcItem); }
    String calcName() { return s(R.string.calcName); }
    String calcSection() { return s(R.string.calcSection); }
    String calcRow() { return s(R.string.calcRow); }
    String calcRowHelp() { return s(R.string.calcRowHelp); }
    /** Off: what turning it on starts from. */
    String calcOffNote(String method) { return d(s(R.string.calcOffNote, method)); }
    String calcBaseRow() { return s(R.string.calcBaseRow); }
    String calcBaseHelp() { return s(R.string.calcBaseHelp); }
    String calcFajrRow() { return s(R.string.calcFajrRow); }
    /** Under an angle: the time it gives today (Fajr is tomorrow's after Isha), and the step. */
    String calcAngleHelp(String time, boolean tomorrow) {
        return d(s(tomorrow ? R.string.calcAngleHelpTomorrow : R.string.calcAngleHelp, time));
    }
    String calcIshaRow() { return s(R.string.calcIshaRow); }
    String calcIshaRowHelp() { return s(R.string.calcIshaRowHelp); }
    String[] calcIshaModes() { return a(R.array.calcIshaModes); }
    String calcIshaAngleRow() { return s(R.string.calcIshaAngleRow); }
    String calcIshaMinutesRow() { return s(R.string.calcIshaMinutesRow); }
    /** Under Isha's minutes: today's Isha and the step; Umm al-Qura's Ramadan half hour when it starts from it. */
    String calcMinutesHelp(String time, boolean ummAlQura) {
        return d(s(R.string.calcMinutesHelp, time) + (ummAlQura ? s(R.string.calcRamadan) : ""));
    }
    /** A stepper's minutes: "90 min". */
    String calcMinutes(int n) {
        // Arabic: 105 and 110 count as 5 and 10 do ("١٠٥ دقائق"); 60 to 100 and the rest take دقيقة.
        if (ar) return d(n + (n % 100 >= 3 && n % 100 <= 10 ? " دقائق" : " دقيقة"));
        return d(s(R.string.minutesAfterOther, n));
    }
    /** An angle in tenths of a degree: "18°", "18.5°" (the language's decimal sign; the Arabic one with Eastern digits). */
    String degrees(int tenths) {
        String v = tenths % 10 == 0 ? String.valueOf(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
        if (arabicScript()) v = decimal(v);
        else v = v.replace('.', new java.text.DecimalFormatSymbols(java.util.Locale.forLanguageTag(lang)).getDecimalSeparator());
        return d(v + "°");
    }
    String calcHighRow() { return s(R.string.calcHighRow); }
    String[] calcHighNames() {
        String[] names = a(R.array.calcHighNames);
        for (int i = 0; i < names.length; i++) names[i] = d(names[i]);
        return names;
    }
    /** Each rule's one line: what it does and whose it is (HighLatitude). */
    String[] calcHighAbout() {
        String[] about = a(R.array.calcHighAbout);
        for (int i = 0; i < about.length; i++) about[i] = d(about[i]);
        return about;
    }
    String calcHighHere() { return d(s(R.string.calcHighHere)); }
    String calcHighMsc() { return s(R.string.calcHighMsc); }
    String calcKeepRow() { return s(R.string.calcKeepRow); }
    String calcKeepHelp() { return s(R.string.calcKeepHelp); }
    /** The method row while it is on: its angles (as a method's), and "kept" when a new location leaves it. */
    String calcSummary(Prefs.Calc k) {
        String fajr = s(R.string.methodAngles) + degrees(k.fajrTenths);
        String isha = k.ishaAfterMaghrib ? s(R.string.methodAngles_2, k.ishaMinutes) : s(R.string.methodAngles_3) + degrees(k.ishaTenths);
        return d(fajr + " · " + isha + (k.keep ? " · " + s(R.string.calcKept) : ""));
    }
    /** The method row once a new location took the custom calculation or the minutes away. */
    String calcReset() { return s(R.string.calcReset); }
    /** The page's note: today's six times with these settings. */
    String calcTimesNote(java.util.List<String> times) {
        StringBuilder b = new StringBuilder();
        for (String time : times) b.append(b.length() == 0 ? "" : " · ").append(time);
        return d(s(R.string.calcTimesNote, b.toString()));
    }
    String calcFileHelp() { return s(R.string.calcFileHelp); }
    String calcTenthsHelp() { return s(R.string.calcTenthsHelp); }
    String calcFromMethod() { return s(R.string.calcFromMethod); }
    String adjustFileHelp() { return s(R.string.adjustFileHelp); }
    String hijriToday(String date) { return (s(R.string.hijriToday)) + date; }
    String days(int n) {
        // No adjustment reads as such, not "0 يوم".
        if (n == 0) return s(R.string.days);
        String sign = n > 0 ? "+" : "";
        if (!ar) return d(s(one(Math.abs(n)) ? R.string.daysOne : R.string.daysOther, sign + n));
        return d(sign + n + (Math.abs(n) == 2 ? " يومان" : " يوم"));
    }
    String roomRow() { return roomTitle(); }
    String roomHelp() { return s(R.string.roomHelp); }
    String notSet() { return s(R.string.notSet); }
    String remind() { return s(R.string.remind); }
    String remindHelp() { return s(R.string.remindHelp); }
    String[] remindChoices() {
        return a(R.array.remindChoices);
    }
    String remindVoiceRow() { return s(R.string.remindVoiceRow); }
    String remindVoiceHelp() { return s(R.string.remindVoiceHelp); }
    String announceRow() { return s(R.string.announceRow); }
    String announceHelp() {
        return s(R.string.announceHelp);
    }
    String adhanRow() { return s(R.string.adhanRow); }
    String adhanHelp() { return s(R.string.adhanHelp); }
    String language() { return s(R.string.language); }
    String languageHelp() { return s(R.string.languageHelp); }
    /** "TV" in the screen's language, then each language by its own name (Languages). */
    String[] languages() {
        String[] out = new String[Languages.NAMES.length + 1];
        out[0] = s(R.string.languages);
        System.arraycopy(Languages.NAMES, 0, out, 1, Languages.NAMES.length);
        return out;
    }
    /**
     * The language button's and chooser's name: the word in the screen's language, then "Language", so one who
     * cannot read the screen's language still finds it ("اللغة · Language"); English says it once.
     */
    String languageBoth() {
        String w = language();
        return "Language".equals(w) ? w : w + " · Language";
    }
    /** The chooser's first item: the TV's language, named by its own name ("TV (English now)"). */
    String languageTv(String name) { return s(R.string.languageTv, name); }
    String languageKeepTitle() { return s(R.string.languageKeepTitle); }
    String languageKeep() { return s(R.string.languageKeep); }
    String languageUndo() { return s(R.string.languageUndo); }
    /** "Back to العربية in 7 s": the language before, by its own name, and the seconds left. */
    String languageGoesBack(int seconds, String name) { return s(R.string.languageGoesBack, d(String.valueOf(seconds)), name); }
    String hintBackUndo() { return s(R.string.hintBackUndo); }
    String digits() { return s(R.string.digits); }
    String clockRow() { return s(R.string.clockRow); }
    String clockHelp() { return s(R.string.clockHelp); }
    String[] clocks() { return a(R.array.clocks); }
    String networkRow() { return s(R.string.networkRow); }
    String networkHelp() { return s(R.string.networkHelp); }
    String deviceRow() { return s(R.string.deviceRow); }
    String deviceHelp() { return s(R.string.deviceHelp); }
    /** Home's button on an HDMI source box (Prefs.homeButton). */
    String homeButtonRow() { return s(R.string.homeButtonRow); }
    String homeButtonHelp() {
        return s(R.string.homeButtonHelp);
    }
    /** Settings > Device settings > Remote buttons (Buttons). */
    String buttonsRow() { return s(R.string.buttonsRow); }
    String buttonsHelp(int n) {
        if (n == 0) return s(R.string.buttonsHelp);
        if (!ar) return d(s(one(n) ? R.string.buttonsHelpOne : R.string.buttonsHelpOther, n));
        if (n == 1) return "زر واحد مخصص";
        if (n == 2) return "زران مخصصان";
        return d(n <= 10 ? n + " أزرار مخصصة" : n + " زرًا مخصصًا");
    }
    String buttonsSection() { return s(R.string.buttonsSection); }
    String hintBackDevice() { return s(R.string.hintBackDevice); }
    String buttonFrom(String remote) {
        if (remote == null || remote.isEmpty()) return "";
        return s(R.string.buttonFrom, remote);
    }
    String addButtonRow() { return s(R.string.addButtonRow); }
    String addButtonHelp() { return s(R.string.addButtonHelp); }
    String buttonsInfoRow() { return s(R.string.buttonsInfoRow); }
    String buttonsInfoHelp() {
        return s(R.string.buttonsInfoHelp);
    }
    String buttonsInfoHelpTcl() {
        return s(R.string.buttonsInfoHelpTcl);
    }
    String buttonsServiceOff() { return s(R.string.buttonsServiceOff); }
    String buttonOpens(String name) { return s(R.string.buttonOpens, name); }
    /** A custom button's press and hold (Buttons.Entry.longAction). */
    String buttonPressIs(String action) { return (s(R.string.buttonPressIs)) + action; }
    String buttonLongIs(String action) { return (s(R.string.buttonLongIs)) + action; }
    String buttonLongNone() { return s(R.string.buttonLongNone); }
    String buttonLongNote() { return s(R.string.buttonLongNote); }
    String buttonLongShort(String action) { return (s(R.string.buttonLongShort)) + action; }
    String buttonLongTitle(String name) { return s(R.string.buttonLongTitle, name); }
    String buttonOriginal() { return s(R.string.buttonOriginal); }
    String buttonNone() { return s(R.string.buttonNone); }
    String buttonOff() { return s(R.string.buttonOff); }
    String buttonAwan(String screen) { return "Awan TV · " + screen; }
    String buttonRadio() { return s(R.string.buttonRadio); }
    String pressTitle() { return s(R.string.pressTitle); }
    String pressHelp() { return s(R.string.pressHelp); }
    String pressWaiting(int seconds) {
        if (!ar) return d(s(one(seconds) ? R.string.pressWaitingOne : R.string.pressWaitingOther, seconds));
        if (seconds == 1) return "ينتظر ثانية واحدة";
        if (seconds == 2) return "ينتظر ثانيتين";
        return d("ينتظر " + seconds + " ثوانٍ");
    }
    String noneTitle() { return s(R.string.noneTitle); }
    String noneBody() { return d(s(R.string.noneBody)); }
    String noneWhy() {
        return s(R.string.noneWhy);
    }
    String tryAgain() { return s(R.string.tryAgain); }
    String close() { return s(R.string.close); }

    // The safety net's screen (Crash, CrashActivity, 1.13.0).
    String crashTitle() { return ar ? "توقف Awan TV على غير المتوقع" : "Awan TV stopped unexpectedly"; }
    String crashLead() {
        return ar ? "حدث خطأ في التطبيق فأُوقف. إعداداتك وتلاواتك المحفوظة لم تتأثر."
            : "Something went wrong in the app, so it was stopped. Your settings and saved recitations are untouched.";
    }
    String crashHow() {
        return ar ? "«أعد التشغيل» يفتح الرئيسية من جديد، وفي الغالب يكفي. إن تكرر التوقف خلال دقيقتين يترك Awan TV شاشة التلفاز الأصلية تعمل نصف ساعة حتى لا يتعطل التلفاز."
            : "Restart opens Home again, which is usually enough. If it stops again within two minutes, Awan TV leaves the TV's own home screen on for half an hour, so the TV stays usable.";
    }
    String crashAgainTitle() { return ar ? "تكرر توقف Awan TV" : "Awan TV stopped again"; }
    String crashAgainLead() {
        return ar ? "توقف مرتين خلال دقيقتين، فأوقفنا فتح رئيسية Awan TV نصف ساعة، وتعمل شاشة التلفاز الأصلية. إعداداتك محفوظة."
            : "It stopped twice within two minutes, so Awan TV's Home stays closed for half an hour and the TV's own home screen is on. Your settings are kept.";
    }
    String crashAgainHow() {
        return ar ? "جرّب «أعد التشغيل» الآن، أو اتركه يعود وحده بعد نصف ساعة. وإن استمر فأبلغنا من «تفاصيل للإبلاغ»."
            : "Try Restart now, or leave it to come back by itself in half an hour. If it goes on, tell us from Details to report.";
    }
    String crashCloses() { return d(ar ? "تُغلق هذه الشاشة وحدها بعد 10 دقائق دون ضغط." : "This screen closes by itself after 10 minutes without a key."); }
    String crashRestart() { return ar ? "أعد التشغيل" : "Restart"; }
    String crashStock() { return ar ? "الشاشة الأصلية للتلفاز" : "The TV's own home"; }
    String crashDetails() { return ar ? "تفاصيل للإبلاغ" : "Details to report"; }
    String crashDetailsTitle() { return ar ? "تفاصيل التوقف" : "What stopped it"; }
    String crashDetailsLead() {
        return ar ? "امسح الرمز بالجوال لتفتح بلاغًا على صفحة المشروع، ثم الصق فيه هذه الأسطر أو صوّرها. لا تحمل شيئًا عنك ولا عن شبكتك."
            : "Scan the code with a phone to open a report on the project's page, then paste these lines or a photo of them. They carry nothing about you or your network.";
    }
    String lastCrashRow() { return s(R.string.lastCrashRow); }
    String lastCrashHelp(String when, String what) { return d(when) + sep() + what; }
    String noCrash() { return s(R.string.noCrash); }
    String warnTitle() { return s(R.string.warnTitle); }
    String warnBody() {
        return s(R.string.warnBody);
    }
    String warnVolume() {
        return s(R.string.warnVolume);
    }
    String warnUndo() { return s(R.string.warnUndo); }
    String hintMove() { return s(R.string.hintMove); }
    String hintChange() { return s(R.string.hintChange); }
    String hintChoose() { return s(R.string.hintChoose); }
    String hintBackMenu() { return s(R.string.hintBackMenu); }
    String hintEdit() { return s(R.string.hintEdit); }
    String hintSwitch() { return s(R.string.hintSwitch); }
    String hintSave() { return s(R.string.hintSave); }
    String hintBackCancel() { return s(R.string.hintBackCancel); }
    String saved() { return s(R.string.saved); }
    String qibla() { return s(R.string.qibla); }
    String north() { return s(R.string.north); }
    String qiblaHint() { return s(R.string.qiblaHint); }
    String bellToggle() { return s(R.string.bellToggle); }
    String hintBackHome() { return s(R.string.hintBackHome); }
    String bell(boolean on) { return (on ? s(R.string.bell) : s(R.string.bell_2)); }
    String rowNow() { return s(R.string.rowNow); }
    /** Sunrise is masculine in Arabic (الشروق مضى); the prayers are feminine (الصلاة مضت). */
    String passed(boolean sunrise) { return s(sunrise ? R.string.passedSunrise : R.string.passed); }
    String in(String duration) { return d(s(R.string.in, duration)); }
    /** "الفجر بعد" / "Fajr in": the screensaver's line above its countdown. */
    String nextIn(String prayer) { return s(R.string.nextIn, prayer); }

    /** "South-east · from Cairo" for a bearing in degrees from true north. */
    String qiblaDirection(double bearing) {
        return octant(bearing) + (s(R.string.qiblaDirection)) + city();
    }

    String octant(double bearing) {
        String[] names = a(R.array.octant);
        return names[sector(bearing)];
    }

    /**
     * Where the Qibla is for someone facing the TV, from its angle clockwise from straight ahead. Right and left
     * are the viewer's own, so they never mirror with the layout.
     */
    String qiblaRelative(double clockwise) {
        String[] where = a(R.array.qiblaRelative);
        return (s(R.string.qiblaRelative_2)) + where[sector(clockwise)];
    }

    /** "North-west · 320°". */
    String bearingLabel(int degrees) { return d(octant(degrees) + " · " + degrees + "°"); }

    String facingLine(int facing) { return (s(R.string.facingLine)) + bearingLabel(facing); }
    String tvMarker() { return s(R.string.tvMarker); }
    String roomButton(boolean set) { return (set ? s(R.string.roomButton) : s(R.string.roomButton_2)); }
    String roomTitle() { return s(R.string.roomTitle); }
    String roomTip() {
        return s(R.string.roomTip);
    }
    String roomKeys() { return s(R.string.roomKeys); }
    String hintQibla() { return s(R.string.hintQibla); }
    String hintTimes() { return s(R.string.hintTimes); }

    private static int sector(double degrees) {
        return (int) Math.round(((degrees % 360) + 360) % 360 / 45) % 8;
    }

    /** Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha. */
    String[] prayerNames() {
        return a(R.array.prayerNames);
    }

    String[] hijriMonths() {
        return a(R.array.hijriMonths);
    }

    String hijriSuffix() { return s(R.string.hijriSuffix); }

    // ----------------------------------------------------------------

    // Iqamah (Settings, screensaver)
    String sectionIqama() { return s(R.string.sectionIqama); }
    String iqama() { return s(R.string.iqama); }
    String iqamaAt(CharSequence time) { return (s(R.string.iqamaAt)) + time; }
    String noIqama() { return s(R.string.noIqama); }
    String khutbah() { return s(R.string.khutbah); }
    // Friday's khutbah and Ramadan's imsak (Jumuah; Settings > Prayer times; the screensaver)
    String khutbahAt(CharSequence time) { return (s(R.string.khutbahAt)) + time; }
    String khutbahIn() { return s(R.string.khutbahIn); }
    String suhoorEndsIn() { return s(R.string.suhoorEndsIn); }
    String fridayLine() { return s(R.string.fridayLine); }
    String fridayEveLine() { return s(R.string.fridayEveLine); }
    String kahfTonight() { return s(R.string.kahfTonight); }
    String ramadanDay(int day) { return d(s(R.string.ramadanDay, day)); }
    String imsakAt(CharSequence time) { return (s(R.string.imsakAt)) + time; }
    String fajrAt(CharSequence time) { return (s(R.string.fajrAt)) + time; }
    String fridayRow() { return s(R.string.fridayRow); }
    String[] fridayChoices() { return a(R.array.fridayChoices); }
    /** preset: the country's own time ("12:45 PM"), or null when the country's khutbah is at Dhuhr. */
    String fridayHelp(int mode, CharSequence preset, String country) {
        if (mode == Prefs.FRIDAY_FIXED) return s(R.string.fridayHelp);
        if (mode == Prefs.FRIDAY_DHUHR || preset == null) {
            return mode == Prefs.FRIDAY_DHUHR ? (s(R.string.fridayHelp_2))
                                              : (s(R.string.fridayHelp_3));
        }
        return s(R.string.fridayHelp_4, country, preset);
    }
    String fridayTimeRow() { return s(R.string.fridayTimeRow); }
    String fridayTimeHelp() { return s(R.string.fridayTimeHelp); }
    String imsakRow() { return s(R.string.imsakRow); }
    String[] imsakChoices() { return a(R.array.imsakChoices); }
    String imsakHelp() { return s(R.string.imsakHelp); }
    String iqamaRow() { return s(R.string.iqamaRow); }
    String[] iqamaModes() {
        return a(R.array.iqamaModes);
    }
    /** The mode row's help line: what the mode does, or why Country is not on offer. */
    String iqamaModeHelp(int mode, String country, boolean hasPreset) {
        switch (mode) {
            case Iqama.COUNTRY: return s(R.string.iqamaModeHelp, country);
            case Iqama.MINUTES: return s(R.string.iqamaModeHelp_2);
            case Iqama.FIXED: return s(R.string.iqamaModeHelp_3);
            default:
                if (hasPreset) return s(R.string.iqamaModeHelp_4);
                return s(R.string.iqamaModeHelp_5);
        }
    }
    /** "25 minutes", with the Arabic count forms. */
    String minutesAfter(int n) {
        if (!ar) return d(s(R.string.minutesAfterOther, n));
        if (n == 0) return "مع الأذان";
        if (n == 1) return "دقيقة";
        if (n == 2) return "دقيقتان";
        return d(n + (n <= 10 ? " دقائق" : " دقيقة"));
    }
    String adhanToday(CharSequence time) { return (s(R.string.adhanToday)) + time; }
    String iqamaToday(CharSequence time) { return (s(R.string.iqamaToday)) + time; }
    String fridayNote() { return s(R.string.fridayNote); }

    // Settings > Awan TV: what runs (Prefs.run), the screensaver, the TV's own home screen, removing the app
    String sectionRun() { return s(R.string.sectionRun); }
    String runRow() { return s(R.string.runRow); }
    String[] runModes() {
        return a(R.array.runModes);
    }
    /** The run row's help: what the choice runs, or what it still needs. */
    String runHelp(int run, boolean serviceOn) {
        if (!serviceOn && (run == Prefs.RUN_ALL || run == Prefs.RUN_HOME)) {
            return s(R.string.runHelp);
        }
        switch (run) {
            case Prefs.RUN_ALL: return s(R.string.runHelp_2);
            case Prefs.RUN_HOME: return s(R.string.runHelp_3);
            case Prefs.RUN_SAVER: return s(R.string.runHelp_4);
            default: return s(R.string.runHelp_5);
        }
    }
    String saverIdleRow() { return s(R.string.saverIdleRow); }
    String[] saverIdleChoices() {
        return a(R.array.saverIdleChoices);
    }
    /** ours: the TV's screensaver is Awan TV's (set over adb), so the TV starts it after its own timeout. */
    String saverIdleHelp(boolean ours) {
        return ours ? (s(R.string.saverIdleHelp))
                    : (s(R.string.saverIdleHelp_2));
    }
    String stockRow() { return s(R.string.stockRow); }
    String stockHelp() { return s(R.string.stockHelp); }
    String removeRow() { return s(R.string.removeRow); }
    String removeHelp() {
        return s(R.string.removeHelp);
    }
    String removeRecitationsTitle() { return s(R.string.removeRecitationsTitle); }
    String removeRecitationsSub(String size) {
        return s(R.string.removeRecitationsSub, size);
    }
    String[] removeRecitationsChoices() {
        return a(R.array.removeRecitationsChoices);
    }
    String[] removeChoices() { return a(R.array.removeChoices); }
    String accessibilityRow() { return s(R.string.accessibilityRow); }
    String accessibilityHelp() { return s(R.string.accessibilityHelp); }

    // Adhkar (screensaver, Settings > Reminders)
    String morningAdhkar() { return s(R.string.morningAdhkar); }
    String eveningAdhkar() { return s(R.string.eveningAdhkar); }
    String adhkarRow() { return s(R.string.adhkarRow); }
    String[] adhkarModes() { return a(R.array.adhkarModes); }
    String adhkarHelp(int mode) {
        switch (mode) {
            case Adhkar.EXTENDED: return s(R.string.adhkarHelp);
            case Adhkar.PREFERRED: return s(R.string.adhkarHelp_2);
            default: return s(R.string.adhkarHelp_3);
        }
    }

    // Weather by the clock (Weather; Settings > Display)
    String weatherRow() { return s(R.string.weatherRow); }
    String[] weatherChoices() { return a(R.array.weatherChoices); }
    String weatherHelp(boolean on) {
        return on ? (s(R.string.weatherHelp))
                  : (s(R.string.weatherHelp_2));
    }
    String weatherCredit() { return s(R.string.weatherCredit); }
    /** The sky's name, for screen readers. */
    String sky(int sky) {
        return a(R.array.sky)[Math.max(0, Math.min(7, sky))];
    }

    // Another place, by coordinates (Settings > Location, PlaceActivity)
    String otherPlace() { return s(R.string.otherPlace); }
    String placeTitle() { return s(R.string.placeTitle); }
    String placeIntro() {
        return s(R.string.placeIntro);
    }
    String searchCity() { return s(R.string.searchCity); }
    String loadingCities() { return s(R.string.loadingCities); }
    String noCity() {
        return s(R.string.noCity);
    }
    /** "4.7 million", "195 thousand": a city's size in the results. */
    String population(long n) {
        if (n >= 1_000_000) {
            String m = String.format(java.util.Locale.US, "%.1f", n / 1_000_000.0).replace(".0", "");
            return d(s(R.string.populationMillion, arabicScript() ? decimal(m) : m));
        }
        long k = Math.max(1, Math.round(n / 1000.0));
        return d(s(R.string.population, k));
    }
    String chosenPlace() { return s(R.string.chosenPlace); }
    String placeName() { return s(R.string.placeName); }
    String latitude() { return s(R.string.latitude); }
    String longitude() { return s(R.string.longitude); }
    /** The method a place gets: its country's, or the one set now when its country's is not known. */
    String placeMethod(String method, String country) {
        if (country == null) return s(R.string.placeMethod, method);
        // A ministry's name already says its country (Oman, Morocco): the line stays one line.
        if (method.contains(country)) return (s(R.string.placeMethod_2)) + method;
        return s(R.string.placeMethod_3, method, country);
    }
    String placeIqama(String country) {
        return s(R.string.placeIqama, country);
    }
    String hintPick() { return s(R.string.hintPick); }
    String hintToChosen() { return s(R.string.hintToChosen); }
    String hintCancel() { return s(R.string.hintCancel); }
    String citiesCredit() { return s(R.string.citiesCredit); }
    String habousCreditRow() { return s(R.string.habousCreditRow); }
    String habousCredit() { return s(R.string.habousCredit); }
    String citiesCreditLong() { return s(R.string.citiesCreditLong); }
    String placeSave() { return s(R.string.placeSave); }
    String placeCancel() { return s(R.string.placeCancel); }
    String placeBad() { return s(R.string.placeBad); }
    String placeNoName() { return s(R.string.placeNoName); }
    String placeAfter() {
        return s(R.string.placeAfter);
    }

    // Screensaver now (Settings > Awan TV, and Home's button)
    String saverNowRow() { return s(R.string.saverNowRow); }
    String saverNowHelp() { return s(R.string.saverNowHelp); }

    // Iqamah in Ramadan (Settings > Iqamah)
    String iqamaPeriodRow() { return s(R.string.iqamaPeriodRow); }
    String[] iqamaPeriods() { return a(R.array.iqamaPeriods); }
    String iqamaPeriodHelp(boolean ramadan) {
        return ramadan ? (s(R.string.iqamaPeriodHelp))
                       : (s(R.string.iqamaPeriodHelp_2));
    }
    String asUsual() { return s(R.string.asUsual); }

    // About
    String sectionAbout() { return s(R.string.sectionAbout); }
    String version(String v) { return d((s(R.string.version)) + v); }
    String byAwan() { return s(R.string.byAwan); }
    String sourceRow() { return s(R.string.sourceRow); }
    String newVersion(String v) { return d((s(R.string.newVersion)) + v); }
    String qrHint() { return s(R.string.qrHint); }
    String siteTitle() { return "AwanPro"; }
    String checkNowRow() { return s(R.string.checkNowRow); }
    String checkNowHelp() {
        return s(R.string.checkNowHelp);
    }
    String checking() { return s(R.string.checking); }
    String upToDate() { return s(R.string.upToDate); }
    String checkFailed() { return s(R.string.checkFailed); }
    String basedOn() { return s(R.string.basedOn); }
    String mitLicence() { return s(R.string.mitLicence); }

    // Colour themes (Settings > Display & language), in Tokens' order
    String themeRow() { return s(R.string.themeRow); }
    String themeHelp() {
        return s(R.string.themeHelp);
    }
    String[] themes() {
        return a(R.array.themes);
    }

    // Home's month (Hijri) and the coming occasion (Home's arch, the screensaver's Hijri line)
    String calendarSource() { return s(R.string.calendarSource); }
    /** Sunday first, as Umm al-Qura's printed calendar. */
    String[] weekdaysShort() {
        return a(R.array.weekdaysShort);
    }

    /** "Ramadan in 23 days", "Eid al-Fitr tomorrow", "Eid Mubarak" (Hijri.RAMADAN, FITR, ARAFAH, ADHA). */
    String occasion(int kind, int days) {
        if (days == 0) {
            return a(R.array.occasionToday)[kind];
        }
        String name = a(R.array.occasion)[kind];
        if (!ar) return d(days == 1 ? s(R.string.occasionTomorrow, name) : s(R.string.occasionInDays, name, days));
        if (days == 1) return name + " غدًا";
        if (days == 2) return name + " بعد يومين";
        return d(name + " بعد " + days + (days <= 10 ? " أيام" : " يومًا"));
    }

    /** Tomorrow's sunnah fast (Hijri.MONDAY, THURSDAY, WHITE, TASUA, ASHURA). */
    String fastTomorrow(int kind) {
        return a(R.array.fastTomorrow)[kind];
    }
    String fastRow() { return s(R.string.fastRow); }
    String fastHelp() {
        return s(R.string.fastHelp);
    }
    String middleRow() { return s(R.string.middleRow); }
    String middleHelp() {
        return d(s(R.string.middleHelp));
    }
    String[] middleChoices() {
        return a(R.array.middleChoices);
    }
    String afterPrayerLabel() { return s(R.string.afterPrayerLabel); }
    String afterAdhanLabel() { return s(R.string.afterAdhanLabel); }
    String midnight() { return s(R.string.midnight); }
    String lastThird() { return s(R.string.lastThird); }

    // The screensaver's hadith (assets/hadith/hadith.json)
    // The remote's buttons by Android's names (Buttons.name)
    String keyRed() { return s(R.string.keyRed); }
    String keyGreen() { return s(R.string.keyGreen); }
    String keyYellow() { return s(R.string.keyYellow); }
    String keyBlue() { return s(R.string.keyBlue); }
    String keySettings() { return s(R.string.keySettings); }
    String keyInput() { return s(R.string.keyInput); }
    String keyGuide() { return s(R.string.keyGuide); }
    String keyInfo() { return s(R.string.keyInfo); }
    String keyBookmark() { return s(R.string.keyBookmark); }
    String keyApps() { return s(R.string.keyApps); }
    String keyPlayPause() { return s(R.string.keyPlayPause); }
    String keyChannelUp() { return s(R.string.keyChannelUp); }
    String keyChannelDown() { return s(R.string.keyChannelDown); }
    /** A button known only by its number ("Button 4020"); the caller puts the digits through d(). */
    String keyNumbered(String number) { return s(R.string.keyNumbered, number); }
    /** A digit key ("Number 5"); the caller puts the digits through d(). */
    String keyDigit(String digit) { return (s(R.string.keyDigit)) + digit; }
    /** Any other key by Android's name, underscores as spaces ("MEDIA RECORD button"). */
    String keyNamed(String name) { return (s(R.string.keyNamed)) + name + (s(R.string.keyNamed_2)); }
    /** A surah downloading, how far ("45٪"): the Arabic percent sign in both languages, as before. */
    String percentDone(int percent) { return d(percent + "٪"); }

    String hadithSource(int bukhari, int muslim) {
        StringBuilder b = new StringBuilder();
        if (bukhari > 0) b.append(s(R.string.hadithSource)).append(bukhari);
        if (muslim > 0) b.append(b.length() > 0 ? " · " : "").append(s(R.string.hadithSource_2)).append(muslim);
        return d(b.toString());
    }
    // Background photos (Photos)
    String backgroundRow() { return s(R.string.backgroundRow); }
    String backgroundHelp() { return s(R.string.backgroundHelp); }
    String[] backgrounds() { return a(R.array.backgrounds); }
    String saverPhotosRow() { return s(R.string.saverPhotosRow); }
    String saverPhotosHelp() { return s(R.string.saverPhotosHelp); }
    String photoEveryRow() { return s(R.string.photoEveryRow); }
    String photoEveryHelp() { return s(R.string.photoEveryHelp); }
    String[] photoEveryChoices() {
        return a(R.array.photoEveryChoices);
    }
    String nightDimRow() { return s(R.string.nightDimRow); }
    String nightDimHelp() { return s(R.string.nightDimHelp); }

    // Fajr's adhan level, the iqamah countdown over other apps, App status
    String percent(int n) { return d(s(R.string.percent, n)); }
    String fullLevel() { return s(R.string.fullLevel); }
    String fajrLevelTitle() { return s(R.string.fajrLevelTitle); }
    String fajrLevelHelp() { return s(R.string.fajrLevelHelp); }
    String iqamaCounterRow() { return s(R.string.iqamaCounterRow); }
    /** Under the countdown card its first times (PrayerWatch.HINT_TIMES): where it is turned off. */
    String iqamaCounterOff() { return s(R.string.iqamaCounterOff); }
    // The settings file's last part (SettingsFile.write, 1.14.0).
    String buttonsFileSection() { return s(R.string.buttonsFileSection); }
    String buttonsFileNote() {
        return s(R.string.buttonsFileNote);
    }
    String iqamaCounterHelp() { return s(R.string.iqamaCounterHelp); }
    /** "الإقامة بعد 8 د" / "Iqamah in 8 min"; under a minute "الإقامة الآن". */
    String iqamaInMinutes(long minutes) {
        if (minutes <= 0) return s(R.string.iqamaInMinutes);
        return d(s(R.string.iqamaInMinutes_2, minutes));
    }
    String statusRow() { return s(R.string.statusRow); }
    String statusHelp() { return s(R.string.statusHelp); }
    String statusSection() { return s(R.string.statusSection); }
    String hintBackAbout() { return s(R.string.hintBackAbout); }
    String serviceRow() { return s(R.string.serviceRow); }
    String serviceHelp() { return s(R.string.serviceHelp); }
    String running(boolean on) { return (on ? s(R.string.running) : s(R.string.running_2)); }
    String serviceOffHelp() { return s(R.string.serviceOffHelp); }
    String saverStatusRow() { return s(R.string.saverStatusRow); }
    String saverStatusHelp(int state) {
        return a(R.array.saverStatusHelp)[state];
    }
    String off() { return s(R.string.off); }
    String fromHome() { return s(R.string.fromHome); }
    String lastAdhanRow() { return s(R.string.lastAdhanRow); }
    String noAdhanYet() { return s(R.string.noAdhanYet); }
    String played(boolean ok) { return (ok ? s(R.string.played) : s(R.string.played_2)); }
    String none() { return s(R.string.none); }
    String nextAdhanRow() { return s(R.string.nextAdhanRow); }
    String adhanOn(boolean on) { return (on ? s(R.string.adhanOn) : s(R.string.adhanOn_2)); }
    String permissionRow() { return s(R.string.permissionRow); }
    String permissionHelp() { return s(R.string.permissionHelp); }
    String granted(boolean on) { return (on ? s(R.string.granted) : s(R.string.granted_2)); }
    String weatherStatusHelp(CharSequence at) { return s(R.string.weatherStatusHelp, at); }
    String weatherNone() { return s(R.string.weatherNone); }
    String works(boolean ok) { return (ok ? s(R.string.works) : s(R.string.works_2)); }
    String versionRow() { return s(R.string.versionRow); }
    String todayWord() { return s(R.string.todayWord); }

    // The settings file (SettingsFile), its comments; western digits there (values are JSON numbers)
    String sepPlain() { return " · "; }
    String[] fileHeader() {
        return a(R.array.fileHeader);
    }
    String boolValues() { return s(R.string.boolValues); }
    String rangeValues(int lo, int hi) { return s(R.string.rangeValues, lo, hi); }
    String timeOrEmpty() { return s(R.string.timeOrEmpty); }
    String timeBetween(String a, String b) { return s(R.string.timeBetween, a, b); }
    String listValues() { return s(R.string.listValues); }
    String cityValues() { return s(R.string.cityValues); }
    String textValue() { return s(R.string.textValue); }
    String defaultIs(String v) { return (s(R.string.defaultIs)) + v; }
    String cityFileHelp() { return s(R.string.cityFileHelp); }
    String byCity() { return s(R.string.byCity); }
    String byCountry() { return s(R.string.byCountry); }
    String byCountryOr(int n) { return s(R.string.byCountryOr, n); }
    String byTvCountry() { return s(R.string.byTvCountry); }
    String facingNote() { return s(R.string.facingNote); }
    String fridayFixedNote() { return s(R.string.fridayFixedNote); }
    String iqamaMinutesRow(String prayer) { return s(R.string.iqamaMinutesRow, prayer); }
    String iqamaMinutesHelp() { return s(R.string.iqamaMinutesHelp); }
    String iqamaFixedRow(String prayer) { return s(R.string.iqamaFixedRow, prayer); }
    String iqamaFixedHelp() { return s(R.string.iqamaFixedHelp); }
    String iqamaRamadanRow(String prayer) { return s(R.string.iqamaRamadanRow, prayer); }
    String iqamaRamadanHelp() { return s(R.string.iqamaRamadanHelp); }
    String bellRow(String prayer) { return s(R.string.bellRow, prayer); }
    String notInstalledFile() { return s(R.string.notInstalledFile); }
    String fajrVoiceFile() { return s(R.string.fajrVoiceFile); }
    String valuesWord() { return s(R.string.valuesWord); }
    String radioResumeRowFile() { return s(R.string.radioResumeRowFile); }
    String stationSavedFile() { return s(R.string.stationSavedFile); }
    String voiceHelpFile() { return s(R.string.voiceHelpFile); }
    String fajrLevelHelpFile() { return s(R.string.fajrLevelHelpFile); }
    String stationFileHelp() { return s(R.string.stationFileHelp); }
    String[] digitChoicesFile() { return a(R.array.digitChoicesFile); }
    String hadithRowFile() { return s(R.string.hadithRowFile); }
    String homeAppsFile() { return s(R.string.homeAppsFile); }
    String homeSectionFile() { return s(R.string.homeSectionFile); }
    String homeAppsNoteFile() { return s(R.string.homeAppsNoteFile); }
    String bellsNoteFile() { return s(R.string.bellsNoteFile); }
    String favouritesFile() { return s(R.string.favouritesFile); }
    String moreFile() { return s(R.string.moreFile); }
    String packagesHelp() { return s(R.string.packagesHelp); }
    String homeButtonFileHelp() { return s(R.string.homeButtonFileHelp); }

    // Settings > About > Reset settings
    String resetRow() { return s(R.string.resetRow); }
    String resetHelp() { return s(R.string.resetHelp); }
    String resetTitle() { return s(R.string.resetTitle); }
    String resetGoesHead() { return s(R.string.resetGoesHead); }
    String[] resetGoes() {
        return a(R.array.resetGoes);
    }
    String resetStaysHead() { return s(R.string.resetStaysHead); }
    String[] resetStays() {
        return a(R.array.resetStays);
    }
    String resetContinue() { return s(R.string.resetContinue); }
    String resetHoldTitle() { return s(R.string.resetHoldTitle); }
    String resetHoldHelp() { return s(R.string.resetHoldHelp); }
    String resetBackupNote() { return s(R.string.resetBackupNote); }
    String hintHoldOk() { return s(R.string.hintHoldOk); }

    // The editor's Identify on the TV
    String identifyLabel() { return s(R.string.identifyLabel); }
    String identifyDevice(String name) { return (s(R.string.identifyDevice)) + name; }
    String identifyNoName() { return s(R.string.identifyNoName); }
    String identifyFoot(int seconds) { return d(s(R.string.identifyFoot, seconds)); }
}
