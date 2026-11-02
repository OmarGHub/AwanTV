package app.awanpro.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every setting in one commented file (JSON with // comments, .jsonc) that the viewer edits on a
 * computer and sends back with tools/AwanTV-settings.ps1 (through {@link SettingsTransfer}). Above each setting: what
 * it is (the Settings screen's own words, in the app's language), its values and its default.
 *
 * <p>{@link #items} is the one table of the settings. A new setting must be added here too, or it is not in the
 * file (and not copied between TVs). What belongs to one TV is not here: the screensaver it replaced, downloads,
 * saved recitations, the weather read, the reminders done (SettingsTransfer.LOCAL).
 *
 * <p>Import checks the whole file first: one wrong value and nothing changes, and every mistake is listed (English,
 * for the terminal). A setting left out of the file stays as it is; one equal to its default is stored as unset,
 * so a later default change still reaches it.
 */
final class SettingsFile {
    static final int FORMAT = 2;

    private static final int BOOL = 0, INT_CHOICE = 1, STR_CHOICE = 2, INT_RANGE = 3, TIME = 4, LIST = 5, STRING = 6, CITY = 7;

    /** Reads the current value (as the file shows it) where it is not simply the stored one or the default. */
    private interface Reader {
        Object read(Context c);
    }

    /** One setting: its key, how its value is written, what the comment says. */
    private static final class Item {
        final String key, label;
        final int type;
        String help = "";
        Object[] values;
        String[] labels;
        int min, max;
        /** The value when nothing is stored; null when it depends (on the city, the TV): see defNote. */
        Object def;
        String defNote;
        Reader reader;
        /** A section heading to write before it, and its id for the editor (the TV's Settings sections). */
        String section, sectionId;
        /**
         * For the editor (tools/AwanTV-editor.ps1), as on the TV: shown only while another setting
         * has one of these values ({@code whenKey} in / not in {@code whenIn}); one side of a view switch (the Iqamah
         * rows: "iqama:days" or "iqama:ramadan"); one of a pair shown as one row ({@code combo}); read only there;
         * where it is on the TV when not in Settings.
         */
        String whenKey, view, combo, note;
        Object[] whenIn;
        boolean whenNot, readonly;

        Item(String key, int type, String label) {
            this.key = key;
            this.type = type;
            this.label = label;
        }

        Item help(String h) { help = h == null ? "" : h; return this; }
        Item choices(Object[] v, String[] l) { values = v; labels = l; return this; }
        Item range(int lo, int hi) { min = lo; max = hi; return this; }
        Item def(Object d) { def = d; return this; }
        Item depends(String note, Reader r) { def = null; defNote = note; reader = r; return this; }
        Item reader(Reader r) { reader = r; return this; }
        Item section(String id, String label) { sectionId = id; section = label; return this; }
        Item when(String key, Object... in) { whenKey = key; whenIn = in; return this; }
        Item whenNot(String key, Object... in) { whenKey = key; whenIn = in; whenNot = true; return this; }
        Item view(String v) { view = v; return this; }
        Item combo(String g) { combo = g; return this; }
        Item readonly() { readonly = true; return this; }
        Item note(String n) { note = n; return this; }
    }

    // ---------------------------------------------------------------- the table

    private static List<Item> items(Context c, Text t) {
        List<Item> out = new ArrayList<>();
        String[] names = t.prayerNames();
        int[] prayers = Iqama.PRAYERS;

        // Prayer times
        Item city = new Item("city", CITY, t.location()).help(t.cityFileHelp()).section("prayer", t.sectionPrayer()).readonly();
        city.reader = SettingsFile::cityNow;
        out.add(city);
        String[] methods = new String[Prefs.METHODS.length], methodNames = new String[Prefs.METHODS.length];
        for (int i = 0; i < methods.length; i++) {
            methods[i] = Prefs.METHODS[i].name();
            methodNames[i] = t.methodName(Prefs.METHODS[i]);
        }
        out.add(new Item("method", STR_CHOICE, t.method()).choices(methods, methodNames)
            .depends(t.byCity(), x -> Prefs.method(x).name()));
        out.add(new Item("madhab", STR_CHOICE, t.asrSchool()).help(t.asrSchoolHelp())
            .choices(new String[] {"shafi", "hanafi"}, t.asrSchools())
            .depends(t.byCity(), x -> Prefs.madhab(x) == com.batoulapps.adhan.Madhab.HANAFI ? "hanafi" : "shafi"));
        out.add(new Item("hijri_offset", INT_RANGE, t.hijriAdjust()).range(-2, 2).def(0));
        out.add(new Item("facing", INT_RANGE, t.roomRow()).help(t.roomHelp() + t.sepPlain() + t.facingNote()).range(-1, 359).def(-1));
        out.add(new Item("friday_mode", INT_CHOICE, t.fridayRow())
            .choices(new Integer[] {Prefs.FRIDAY_COUNTRY, Prefs.FRIDAY_DHUHR, Prefs.FRIDAY_FIXED}, t.fridayChoices()).def(Prefs.FRIDAY_COUNTRY));
        out.add(new Item("friday_fixed", TIME, t.fridayTimeRow()).help(t.fridayTimeHelp() + t.sepPlain() + t.fridayFixedNote())
            .range(11 * 60, 14 * 60).def(12 * 60 + 30).when("friday_mode", Prefs.FRIDAY_FIXED));
        out.add(new Item("imsak", INT_CHOICE, t.imsakRow()).help(t.imsakHelp())
            .choices(new Integer[] {0, 5, 10, 15, 20}, t.imsakChoices()).def(0));

        // Iqamah
        Item mode = new Item("iqama_mode", INT_CHOICE, t.iqamaRow())
            .choices(new Integer[] {Iqama.OFF, Iqama.COUNTRY, Iqama.MINUTES, Iqama.FIXED}, t.iqamaModes())
            .depends(t.byCountry(), Iqama::mode).section("iqama", t.sectionIqama());
        out.add(mode);
        // As on the TV: the other days' rows by the mode (minutes or a fixed time), Ramadan's behind the view switch.
        for (int p : prayers) {
            out.add(new Item("iqama_min_" + p, INT_RANGE, t.iqamaMinutesRow(names[p])).help(t.iqamaMinutesHelp()).range(0, 60)
                .depends(t.byCountryOr(startMinutes(p)), x -> Iqama.minutes(x, p)).when("iqama_mode", Iqama.MINUTES).view("iqama:days"));
        }
        for (int p : prayers) {
            out.add(new Item("iqama_at_" + p, TIME, t.iqamaFixedRow(names[p])).help(t.iqamaFixedHelp()).range(0, 24 * 60 - 1).def(-1)
                .when("iqama_mode", Iqama.FIXED).view("iqama:days"));
        }
        for (int p : prayers) {
            out.add(new Item("iqama_ramadan_" + p, INT_RANGE, t.iqamaRamadanRow(names[p])).help(t.iqamaRamadanHelp()).range(-1, 60).def(-1)
                .whenNot("iqama_mode", Iqama.OFF).view("iqama:ramadan"));
        }
        out.add(new Item("iqama_counter", BOOL, t.iqamaCounterRow()).help(t.iqamaCounterHelp()).def(false).whenNot("iqama_mode", Iqama.OFF));

        // Reminders and adhan
        Item remind = new Item("remind", INT_CHOICE, t.remind()).help(t.remindHelp())
            .choices(new Integer[] {5, 10, 15, 20, 30, 0}, t.remindChoices()).def(15).section("reminders", t.sectionReminders());
        out.add(remind);
        out.add(new Item("remind_voice", BOOL, t.remindVoiceRow()).help(t.remindVoiceHelp()).def(true));
        out.add(new Item("adhan", BOOL, t.adhanRow()).help(t.adhanHelp()).def(true));
        for (int p : prayers) out.add(new Item("bell_" + p, BOOL, t.bellRow(names[p])).def(true).whenNot("adhan", false).note(t.bellsNoteFile()));
        out.add(new Item("announce", BOOL, t.announceRow()).help(t.announceHelp()).def(true));
        out.add(new Item("call_close", INT_CHOICE, t.callCloseRow()).help(t.callCloseHelp())
            .choices(new Integer[] {0, 1, 5, 10}, t.callCloseChoices()).def((int) (Tokens.MOTION_CALL_AUTO_DISMISS_MS / 60_000)));
        String[] voices = new String[Adhans.VOICES.length], voiceNames = new String[Adhans.VOICES.length];
        for (int i = 0; i < voices.length; i++) {
            voices[i] = Adhans.VOICES[i][0];
            voiceNames[i] = Adhans.name(t, voices[i]);
        }
        out.add(new Item("voice", STR_CHOICE, t.voiceRow()).help(t.voiceHelpFile()).choices(voices, voiceNames).def("alafasy"));
        out.add(new Item("voice_fajr", STR_CHOICE, t.fajrVoiceFile()).choices(voices, voiceNames).def("chebae_fajr"));
        Integer[] levels = new Integer[Prefs.FAJR_LEVELS.length];
        String[] levelNames = new String[levels.length];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = Prefs.FAJR_LEVELS[i];
            levelNames[i] = i == 0 ? t.fullLevel() : t.percent(levels[i]);
        }
        out.add(new Item("fajr_level", INT_CHOICE, t.fajrLevelTitle()).help(t.fajrLevelHelpFile()).choices(levels, levelNames).def(100));
        out.add(new Item("adhkar_mode", INT_CHOICE, t.adhkarRow())
            .choices(new Integer[] {Adhkar.OFF, Adhkar.EXTENDED, Adhkar.PREFERRED}, t.adhkarModes()).def(Adhkar.EXTENDED));
        out.add(new Item("fast_reminder", BOOL, t.fastRow()).help(t.fastHelp()).def(true));

        // Radio
        Item station = new Item("station", STRING, t.stationRow()).help(t.stationFileHelp()).def("cairo").section("radio", t.sectionRadio())
            .readonly();
        out.add(station);
        out.add(new Item("radio_resume", BOOL, t.radioResumeRowFile()).choices(null, t.afterAdhanChoices()).def(true));
        out.add(new Item("radio_dark", INT_CHOICE, t.radioDarkRow())
            .choices(new Integer[] {0, 15, -1}, t.radioDarkChoices()).def(15));

        // Display and language
        Item locale = new Item("locale", STR_CHOICE, t.language()).help(t.languageHelp())
            .choices(new String[] {"system", "ar", "en"}, t.languages()).def("system").section("display", t.sectionDisplay());
        out.add(locale);
        out.add(new Item("digits", STR_CHOICE, t.digits())
            .choices(new String[] {"", "western", "eastern"}, t.digitChoicesFile()).def(""));
        out.add(new Item("clock", STR_CHOICE, t.clockRow()).help(t.clockHelp())
            .choices(new String[] {"system", "12", "24"}, t.clocks()).def("system"));
        out.add(new Item("theme", INT_CHOICE, t.themeRow())
            .choices(new Integer[] {Tokens.LAPIS, Tokens.EMERALD, Tokens.BURGUNDY, Tokens.CHARCOAL, Tokens.PARCHMENT, Tokens.AUTO}, t.themes())
            .def(Tokens.LAPIS));
        out.add(new Item("background", INT_CHOICE, t.backgroundRow()).choices(new Integer[] {Photos.PATTERN, Photos.PHOTOS}, t.backgrounds())
            .def(Photos.PHOTOS));
        out.add(new Item("saver_photos", BOOL, t.saverPhotosRow()).help(t.saverPhotosHelp()).def(false));
        out.add(new Item("photo_every", INT_CHOICE, t.photoEveryRow())
            .choices(new Integer[] {Photos.EVERY_AUTO, Photos.EVERY_10, Photos.EVERY_30, Photos.EVERY_HOUR, Photos.EVERY_PRAYER, Photos.EVERY_DAY},
                t.photoEveryChoices()).def(Photos.EVERY_AUTO));
        out.add(new Item("weather", STR_CHOICE, t.weatherRow()).choices(new String[] {"off", "c", "f"}, t.weatherChoices())
            .depends(t.byTvCountry(), Prefs::weather));

        // Awan TV
        Item run = new Item("run", INT_CHOICE, t.runRow())
            .choices(new Integer[] {Prefs.RUN_ALL, Prefs.RUN_HOME, Prefs.RUN_SAVER, Prefs.RUN_OFF}, t.runModes()).def(Prefs.RUN_ALL)
            .reader(Prefs::run).section("run", t.sectionRun());
        out.add(run);
        // The screensaver's rows while it runs (All or Screensaver), as on the TV; its middle is one row there.
        out.add(new Item("saver_idle", INT_CHOICE, t.saverIdleRow()).choices(new Integer[] {5, 10, 15, 30}, t.saverIdleChoices()).def(10)
            .when("run", Prefs.RUN_ALL, Prefs.RUN_SAVER));
        out.add(new Item("saver_hadith", BOOL, t.hadithRowFile()).def(true).combo("middle").when("run", Prefs.RUN_ALL, Prefs.RUN_SAVER));
        out.add(new Item("saver_after_prayer", BOOL, t.afterPrayerLabel()).def(true).combo("middle").when("run", Prefs.RUN_ALL, Prefs.RUN_SAVER));
        out.add(new Item("saver_night_dim", BOOL, t.nightDimRow()).help(t.nightDimHelp()).def(true).when("run", Prefs.RUN_ALL, Prefs.RUN_SAVER));

        // Network (its other row opens the TV's own Wi-Fi settings)
        out.add(new Item("update_check", BOOL, t.updateCheckRow()).help(t.updateCheckFileHelp()).def(true)
            .section("network", t.sectionNetwork(null)));

        // Device settings, then Home's apps (arranged on Home itself on the TV)
        out.add(new Item("home_button", BOOL, t.homeButtonRow()).help(t.homeButtonFileHelp()).def(false).section("device", t.sectionDevice()));
        Item favs = new Item("favourites", LIST, t.favouritesFile()).help(t.packagesHelp())
            .reader(x -> Apps.favourites(x)).section("home", t.homeSectionFile()).note(t.homeAppsNoteFile());
        out.add(favs);
        out.add(new Item("more", LIST, t.moreFile()).help(t.packagesHelp()).reader(Apps::moreSaved).note(t.homeAppsNoteFile()));
        return out;
    }

    private static int startMinutes(int prayer) {
        return new int[] {25, 0, 20, 20, 10, 20}[prayer];
    }

    // ---------------------------------------------------------------- export

    /** The whole file, every setting with its comment, in the app's language. */
    static String write(Context c, String version) throws JSONException {
        Text t = plainText(c);
        SharedPreferences p = Prefs.of(c);
        StringBuilder b = new StringBuilder();
        for (String line : t.fileHeader()) b.append("// ").append(line).append('\n');
        b.append("{\n");
        b.append("  \"app\": ").append(JSONObject.quote(c.getPackageName())).append(",\n");
        b.append("  \"format\": ").append(FORMAT).append(",\n");
        b.append("  \"version\": ").append(JSONObject.quote(version)).append(",\n");
        List<Item> all = items(c, t);
        for (int i = 0; i < all.size(); i++) {
            Item it = all.get(i);
            Object now = current(c, p, it);
            if (it.section != null) b.append("\n  // ═══ ").append(it.section).append(" ═══\n");
            b.append("\n");
            // A saved recitation plays on this TV only: a note instead of the line.
            if (it.type == STRING && now instanceof String && ((String) now).startsWith("saved:")) {
                b.append("  // ").append(it.label).append(": ").append(t.stationSavedFile()).append('\n');
                continue;
            }
            for (String line : comment(t, it)) b.append("  // ").append(line).append('\n');
            // Package names say little: each app's name as Home shows it (not installed: so marked).
            if (it.type == LIST && now instanceof List) {
                for (Object pkg : (List<?>) now) {
                    String name = Apps.name(c, t, String.valueOf(pkg));
                    b.append("  //   ").append(JSONObject.quote(String.valueOf(pkg))).append(" = ")
                        .append(name.equals(pkg) ? t.notInstalledFile() : name).append('\n');
                }
            }
            b.append("  ").append(JSONObject.quote(it.key)).append(": ").append(json(it, now));
            b.append(i == all.size() - 1 ? "\n" : ",\n");
        }
        b.append("}\n");
        return b.toString();
    }

    /** Western digits and the plain " · " in the file: the values are numbers JSON reads. */
    private static Text plainText(Context c) {
        Text shown = Screen.loadText(c);
        Text t = new Text(shown.ar, false);
        t.cityName = shown.cityName;
        return t;
    }

    private static List<String> comment(Text t, Item it) {
        List<String> out = new ArrayList<>();
        out.add(it.help.isEmpty() ? it.label : it.label + ": " + it.help);
        String values;
        switch (it.type) {
            case BOOL:
                values = it.labels == null ? t.boolValues() : t.valuesWord() + "true = " + it.labels[0] + (t.ar ? "، " : ", ")
                    + "false = " + it.labels[1];
                break;
            case INT_CHOICE:
            case STR_CHOICE: {
                List<String> parts = new ArrayList<>();
                for (int i = 0; i < it.values.length; i++) parts.add(json(it, it.values[i]) + " = " + it.labels[i]);
                values = t.valuesWord() + TextUtils.join(t.ar ? "، " : ", ", parts);
                // A long list (methods, voices): one value a line, the default after them.
                if (values.length() > 110) {
                    out.add(t.valuesWord().trim());
                    for (String part : parts) out.add("  " + part);
                    values = null;
                }
                break;
            }
            case INT_RANGE: values = t.rangeValues(it.min, it.max); break;
            case TIME: values = it.def != null && it.def.equals(-1) ? t.timeOrEmpty() : t.timeBetween(hhmm(it.min), hhmm(it.max)); break;
            case LIST: values = t.listValues(); break;
            case CITY: values = t.cityValues(); break;
            default: values = t.textValue();
        }
        String def;
        if (it.def == null && it.defNote != null) def = it.defNote;
        else if (it.type == LIST || it.type == CITY) def = null;
        else def = json(it, it.def);
        if (values == null) {
            if (def != null) out.add(t.defaultIs(def));
        } else {
            out.add(def == null ? values : values + t.sepPlain() + t.defaultIs(def));
        }
        return out;
    }

    // ---------------------------------------------------------------- schema

    /**
     * What the editor (tools/AwanTV-editor.ps1) needs to show the settings as the TV does, written beside the file on
     * export: the sections in the TV's order with their settings, each with its kind, values and labels, range,
     * default, when it shows, Iqamah's view switch and the screensaver's middle as one row; and the TV's apps by name
     * for Home's lists. The editor writes values back as this file's .jsonc does. In a given language (Arabic or
     * English), whatever the TV's: the editor's language is its user's choice, not the TV's (a TV may be in a language
     * its owner does not read).
     */
    static String schema(Context c, String version, boolean ar) throws JSONException {
        Text t = new Text(ar, false);
        t.cityName = Screen.loadText(c).cityName;
        return schema(c, version, t);
    }

    private static String schema(Context c, String version, Text t) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("app", c.getPackageName());
        out.put("format", FORMAT);
        out.put("version", version);
        out.put("lang", t.ar ? "ar" : "en");
        JSONArray sections = new JSONArray();
        JSONObject section = null;
        for (Item it : items(c, t)) {
            if (it.sectionId != null) {
                section = new JSONObject();
                section.put("id", it.sectionId);
                section.put("label", it.section);
                section.put("items", new JSONArray());
                sections.put(section);
            }
            JSONObject o = new JSONObject();
            o.put("key", it.key);
            o.put("kind", new String[] {"bool", "choice", "choice", "range", "time", "list", "string", "city"}[it.type]);
            o.put("label", it.label);
            o.put("help", it.help);
            if (it.values != null) o.put("values", new JSONArray(Arrays.asList(it.values)));
            if (it.labels != null) o.put("labels", new JSONArray(Arrays.asList(it.labels)));
            if (it.type == INT_RANGE || it.type == TIME) {
                o.put("min", it.min);
                o.put("max", it.max);
            }
            if (it.def != null && it.type != LIST && it.type != CITY) o.put("default", jsonValue(it, it.def));
            if (it.defNote != null) o.put("defaultNote", it.defNote);
            if (it.whenKey != null) {
                JSONObject w = new JSONObject();
                w.put("key", it.whenKey);
                w.put(it.whenNot ? "not" : "in", new JSONArray(Arrays.asList(it.whenIn)));
                o.put("when", w);
            }
            if (it.view != null) o.put("view", it.view);
            if (it.combo != null) o.put("combo", it.combo);
            if (it.readonly) o.put("readonly", true);
            if (it.note != null) o.put("note", it.note);
            section.getJSONArray("items").put(o);
        }
        out.put("sections", sections);

        JSONObject views = new JSONObject();
        JSONObject iqama = new JSONObject();
        iqama.put("label", t.iqamaPeriodRow());
        iqama.put("values", new JSONArray(Arrays.asList("days", "ramadan")));
        iqama.put("labels", new JSONArray(Arrays.asList(t.iqamaPeriods())));
        views.put("iqama", iqama);
        out.put("views", views);

        JSONObject combos = new JSONObject();
        JSONObject middle = new JSONObject();
        middle.put("label", t.middleRow());
        middle.put("help", t.middleHelp());
        middle.put("keys", new JSONArray(Arrays.asList("saver_hadith", "saver_after_prayer")));
        middle.put("values", new JSONArray("[[true,true],[true,false],[false,true],[false,false]]"));
        middle.put("labels", new JSONArray(Arrays.asList(t.middleChoices())));
        combos.put("middle", middle);
        out.put("combos", combos);

        // Home's lists by name: every app the TV can open.
        JSONArray apps = new JSONArray();
        for (String pkg : Apps.sorted(c, t)) {
            JSONObject a = new JSONObject();
            a.put("pkg", pkg);
            a.put("name", Apps.name(c, t, pkg));
            apps.put(a);
        }
        out.put("apps", apps);
        return out.toString(1);
    }

    /** A value as the file writes it, as a JSON value (a time as "HH:MM", "" for none). */
    private static Object jsonValue(Item it, Object v) {
        if (it.type == TIME) return v == null || ((Integer) v) < 0 ? "" : hhmm((Integer) v);
        return v;
    }

    // ---------------------------------------------------------------- values

    /** What the file shows for a setting now: the stored value, else its default (or what it depends on). */
    private static Object current(Context c, SharedPreferences p, Item it) {
        if (it.reader != null && (it.def == null || it.type == LIST || it.type == CITY || !p.contains(it.key))) {
            Object v = it.reader.read(c);
            if (v != null) return v;
        }
        if (!p.contains(it.key)) return it.def;
        Object v = p.getAll().get(it.key);
        if (it.type == TIME || it.type == INT_RANGE || it.type == INT_CHOICE) return v instanceof Integer ? v : it.def;
        if (it.type == BOOL) return v instanceof Boolean ? v : it.def;
        return v instanceof String ? v : it.def;
    }

    private static String json(Item it, Object v) {
        try {
            switch (it.type) {
                case TIME: return JSONObject.quote(v == null || ((Integer) v) < 0 ? "" : hhmm((Integer) v));
                case LIST: return new JSONArray((List<?>) v).toString();
                case CITY: return ((JSONObject) v).toString(2).replace("\n", "\n  ");
                case STR_CHOICE:
                case STRING: return JSONObject.quote(String.valueOf(v));
                default: return String.valueOf(v);
            }
        } catch (JSONException | ClassCastException e) {
            return "null";
        }
    }

    private static String hhmm(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private static JSONObject cityNow(Context c) {
        try {
            Prefs.City city = Prefs.city(c);
            JSONObject o = new JSONObject();
            o.put("id", city.id);
            if (Prefs.CUSTOM.equals(city.id)) {
                SharedPreferences p = Prefs.of(c);
                o.put("name", p.getString("custom_name", ""));
                o.put("country", p.getString("custom_cc", ""));
                o.put("lat", city.lat);
                o.put("lng", city.lng);
            } else {
                // In the app's language, as the editor shows it (only the id is compared on import).
                o.put("name", Screen.loadText(c).ar ? city.ar : city.en);
            }
            return o;
        } catch (JSONException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- import

    /** The file's text without its // comments and trailing commas (a comma before } or ]), for JSONObject. */
    static String strip(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean string = false, escape = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (string) {
                b.append(ch);
                if (escape) escape = false;
                else if (ch == '\\') escape = true;
                else if (ch == '"') string = false;
            } else if (ch == '"') {
                string = true;
                b.append(ch);
            } else if (ch == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i + 1 < s.length() && s.charAt(i + 1) != '\n') i++;
            } else if (ch == ',') {
                int j = next(s, i + 1);
                if (j >= s.length() || (s.charAt(j) != '}' && s.charAt(j) != ']')) b.append(ch);
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    /** The next character after whitespace and // comments. */
    private static int next(String s, int i) {
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) i++;
            else if (ch == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
            } else break;
        }
        return i;
    }

    /**
     * Checks the whole file, then saves what differs from now in one commit. Returns how many settings changed;
     * throws with every mistake listed (" | " between them) and nothing saved when one is wrong.
     */
    static int read(Context c, String text) throws java.io.IOException {
        JSONObject in;
        try {
            in = new JSONObject(strip(text));
        } catch (JSONException e) {
            throw new java.io.IOException("Not valid JSON: " + e.getMessage());
        }
        if (!c.getPackageName().equals(in.optString("app"))) throw new java.io.IOException("Not an Awan TV settings file");
        int format = in.optInt("format");
        if (format < FORMAT) throw new java.io.IOException("An older file (format " + format + "); export it again");
        if (format > FORMAT) throw new java.io.IOException("Made by a newer Awan TV (" + in.optString("version") + "); update this TV first");

        Text t = plainText(c);
        SharedPreferences p = Prefs.of(c);
        Map<String, Item> table = new LinkedHashMap<>();
        for (Item it : items(c, t)) table.put(it.key, it);
        List<String> errors = new ArrayList<>();
        SharedPreferences.Editor ed = p.edit();
        int changed = 0;
        for (Iterator<String> keys = in.keys(); keys.hasNext(); ) {
            String key = keys.next();
            if (key.equals("app") || key.equals("format") || key.equals("version")) continue;
            Item it = table.get(key);
            if (it == null) {
                errors.add(key + ": not a setting" + (SettingsTransfer.local(key) ? " that is copied (it belongs to one TV)" : ""));
                continue;
            }
            Object raw = in.opt(key);
            String bad = check(it, raw);
            if (bad != null) {
                errors.add(key + ": " + bad);
                continue;
            }
            Object v = normalise(it, raw);
            if (same(it, v, current(c, p, it))) continue;
            put(ed, it, v);
            changed++;
        }
        if (!errors.isEmpty()) throw new java.io.IOException(errors.size() + " mistake(s), nothing changed | " + TextUtils.join(" | ", errors));
        if (changed > 0 && !ed.commit()) throw new java.io.IOException("The settings could not be saved");
        return changed;
    }

    private static final Pattern TIME_RE = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
    private static final Pattern PACKAGE_RE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$");

    /** Why a value cannot be saved, or null when it can. */
    private static String check(Item it, Object v) {
        switch (it.type) {
            case BOOL:
                return v instanceof Boolean ? null : "use true or false";
            case INT_CHOICE: {
                Integer n = integer(v);
                if (n != null) for (Object x : it.values) if (x.equals(n)) return null;
                return v + " is not one of " + Arrays.toString(it.values);
            }
            case STR_CHOICE:
                if (v instanceof String) for (Object x : it.values) if (x.equals(v)) return null;
                return quoteOf(v) + " is not one of " + quotedList(it.values);
            case INT_RANGE: {
                Integer n = integer(v);
                if (n == null) return v + " is not a whole number";
                return n < it.min || n > it.max ? n + " is outside " + it.min + " to " + it.max : null;
            }
            case TIME: {
                if (!(v instanceof String)) return "write a time as \"HH:MM\"";
                if (((String) v).isEmpty()) return it.def != null && it.def.equals(-1) ? null : "a time is needed here";
                int m = minutes((String) v);
                if (m < 0) return quoteOf(v) + " is not a time (\"HH:MM\", 00:00 to 23:59)";
                return m < it.min || m > it.max ? quoteOf(v) + " is outside " + hhmm(it.min) + " to " + hhmm(it.max) : null;
            }
            case LIST: {
                if (!(v instanceof JSONArray)) return "write a list: [\"com.example.app\", ...]";
                JSONArray a = (JSONArray) v;
                for (int i = 0; i < a.length(); i++) {
                    Object x = a.opt(i);
                    if (!(x instanceof String) || !PACKAGE_RE.matcher((String) x).matches()) return quoteOf(x) + " is not an app's package name";
                }
                return null;
            }
            case STRING:
                if (!(v instanceof String) || ((String) v).trim().isEmpty()) return "write a name in quotes";
                return ((String) v).startsWith("saved:") ? "a saved recitation stays on its own TV" : null;
            case CITY: {
                if (!(v instanceof JSONObject)) return "write it as the file gave it: { \"id\": ... }";
                JSONObject o = (JSONObject) v;
                String id = o.optString("id");
                if (Prefs.CUSTOM.equals(id)) {
                    double lat = o.optDouble("lat", Double.NaN), lng = o.optDouble("lng", Double.NaN);
                    if (Double.isNaN(lat) || lat < -90 || lat > 90) return "lat must be -90 to 90";
                    if (Double.isNaN(lng) || lng < -180 || lng > 180) return "lng must be -180 to 180";
                    return o.optString("name").trim().isEmpty() ? "a place by coordinates needs a name" : null;
                }
                for (Prefs.City x : Prefs.CITIES) if (x.id.equals(id)) return null;
                return quoteOf(id) + " is not one of the app's cities; pick the city on the TV";
            }
            default:
                return "unknown kind";
        }
    }

    /** The checked value in the form {@link #current} gives. */
    private static Object normalise(Item it, Object v) {
        switch (it.type) {
            case INT_CHOICE:
            case INT_RANGE: return integer(v);
            case TIME: return ((String) v).isEmpty() ? -1 : minutes((String) v);
            case LIST: {
                JSONArray a = (JSONArray) v;
                List<String> out = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
                return out;
            }
            default: return v;
        }
    }

    private static boolean same(Item it, Object v, Object now) {
        if (now == null) return false;
        if (it.type == CITY) {
            JSONObject a = (JSONObject) v, b = (JSONObject) now;
            if (!a.optString("id").equals(b.optString("id"))) return false;
            if (!Prefs.CUSTOM.equals(a.optString("id"))) return true;
            return a.optString("name").equals(b.optString("name")) && a.optString("country").equals(b.optString("country"))
                && Math.abs(a.optDouble("lat") - b.optDouble("lat")) < 1e-6 && Math.abs(a.optDouble("lng") - b.optDouble("lng")) < 1e-6;
        }
        return v.equals(now);
    }

    private static void put(SharedPreferences.Editor ed, Item it, Object v) {
        String k = it.key;
        switch (it.type) {
            case CITY: {
                // Only the place: the method and Asr school are their own lines in the file.
                JSONObject o = (JSONObject) v;
                String id = o.optString("id");
                ed.putString("city", id);
                if (Prefs.CUSTOM.equals(id)) {
                    ed.putString("custom_name", o.optString("name").trim()).putString("custom_cc", o.optString("country").trim())
                        .putLong("custom_lat", Double.doubleToLongBits(o.optDouble("lat")))
                        .putLong("custom_lng", Double.doubleToLongBits(o.optDouble("lng")));
                }
                return;
            }
            case LIST:
                ed.putString(k, TextUtils.join(",", (List<?>) v));
                return;
            default:
        }
        // Equal to a fixed default: unset, so a later change of the default reaches it.
        if (it.def != null && it.def.equals(v)) {
            ed.remove(k);
            return;
        }
        if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
        else if (v instanceof Integer) ed.putInt(k, (Integer) v);
        else ed.putString(k, String.valueOf(v));
    }

    private static Integer integer(Object v) {
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Long && (Long) v == ((Long) v).intValue()) return ((Long) v).intValue();
        if (v instanceof Double && (Double) v == Math.rint((Double) v) && Math.abs((Double) v) < 1e9) return ((Double) v).intValue();
        return null;
    }

    private static int minutes(String s) {
        Matcher m = TIME_RE.matcher(s.trim());
        if (!m.matches()) return -1;
        int h = Integer.parseInt(m.group(1)), mm = Integer.parseInt(m.group(2));
        return h < 24 && mm < 60 ? h * 60 + mm : -1;
    }

    private static String quoteOf(Object v) {
        return v instanceof String ? "\"" + v + "\"" : String.valueOf(v);
    }

    private static String quotedList(Object[] values) {
        List<String> out = new ArrayList<>();
        for (Object v : values) out.add("\"" + v + "\"");
        return "[" + TextUtils.join(", ", out) + "]";
    }
}
