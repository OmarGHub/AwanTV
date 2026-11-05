package app.awanpro.tv;

import android.content.Context;
import android.view.KeyEvent;
import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.Madhab;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Every text the app writes, in both languages and both kinds of digits, as one sorted file (test builds only:
 * SettingsTransfer TEXT_DUMP, tools/AwanTV-textdump.ps1). The languages plan (claude/awan-languages-plan.md, phase 0):
 * the file from a build before the strings move to Android's resources is the golden one; every later test build
 * must give each of its lines unchanged.
 *
 * <p>Frozen: its inputs and its line format do not change once the golden file is taken, or what is compared changes.
 * Only {@link #make} (how a Text is built) follows the app, and each change to it is reviewed on its own.
 *
 * <p>Line: section, then what was called and with what, a tab, "=>", a tab, the result (or "!" and the error). Anything
 * that does not print (no-break space, direction marks, line breaks) is written as \\uXXXX, so a whitespace change
 * cannot hide.
 */
final class TextDump {
    private TextDump() {}

    static final String FILE = "text-dump.txt";
    /** Combinations above this use the short lists of numbers and texts. */
    private static final int MAX_COMBINATIONS = 4000;

    // ---------------------------------------------------------------- how a Text is built (follows the app)

    /** The only part that changes with the app: phase 1 builds Text from resources, phase 2 from a Lang. */
    static Text make(Context c, boolean ar, boolean eastern) {
        Text t = Text.of(c, ar, eastern);
        t.cityName = ar ? "جدة" : "Jeddah";
        return t;
    }

    // ---------------------------------------------------------------- the inputs (frozen)

    private static final int[] INTS;
    private static final int[] SHORT_INTS = {-1, 0, 1, 2, 3, 10, 11, 59, 60, 99, 100, 101, 103, 1000};
    private static final long[] BIG = {1440, 2000, 10_000, 99_999, 100_000, 999_999, 1_000_000, 1_048_576, 1_500_000,
        5_242_880, 10_000_000, 104_857_600, 999_999_999, 1_073_741_824, 2_147_483_647L, 5_368_709_120L, 1_099_511_627_776L};
    private static final double[] DOUBLES;
    private static final double[] SHORT_DOUBLES = {-33.9249, -0.5, 0, 0.25, 1.5, 21.4858, 39.8262, 90, 180, 359.9};
    private static final String[] SHORT_STRINGS = {"‹S›", ""};

    static {
        List<Integer> ints = new ArrayList<>();
        for (int i = -5; i <= 130; i++) ints.add(i);
        for (int i : new int[] {199, 200, 201, 202, 203, 210, 211, 299, 300, 301, 999, 1000, 1001, 1002, 1003, 1010, 1011})
            ints.add(i);
        INTS = new int[ints.size()];
        for (int i = 0; i < INTS.length; i++) INTS[i] = ints.get(i);
        List<Double> d = new ArrayList<>();
        for (int i = 0; i < 360; i++) d.add((double) i);
        for (double x : new double[] {-180, -90.5, -33.9249, -0.5, 0.25, 1.5, 21.4858, 39.8262, 89.99, 179.99, 359.9, 360, 361})
            d.add(x);
        DOUBLES = new double[d.size()];
        for (int i = 0; i < DOUBLES.length; i++) DOUBLES[i] = d.get(i);
    }

    private static boolean numeric(Class<?> type) {
        return type == int.class || type == long.class || type == double.class;
    }

    /**
     * The values tried for one parameter. single: the method's only number (or only parameter), which gets the whole
     * range; else the short lists. A text gets the version list only when it is the only parameter.
     */
    private static List<Object> values(Class<?> type, boolean single, boolean only, int position) {
        List<Object> out = new ArrayList<>();
        if (type == int.class) {
            if (single) {
                for (int i : INTS) out.add(i);
                for (long b : BIG) if (b <= Integer.MAX_VALUE) out.add((int) b);
            } else {
                for (int i : SHORT_INTS) out.add(i);
            }
        } else if (type == long.class) {
            if (single) {
                for (int i : INTS) out.add((long) i);
                for (long b : BIG) out.add(b);
            } else {
                for (int i : SHORT_INTS) out.add((long) i);
                out.add(1_048_576L);
                out.add(1_073_741_824L);
            }
        } else if (type == double.class) {
            if (single) {
                for (double x : DOUBLES) out.add(x);
            } else {
                for (double x : SHORT_DOUBLES) out.add(x);
            }
        } else if (type == boolean.class) {
            out.add(false);
            out.add(true);
        } else if (type == String.class || type == CharSequence.class) {
            if (only) {
                out.add("‹S›");
                out.add("");
                out.add("05:12");
                for (String v : NewsActivity.VERSIONS) out.add(v);
                out.add("1.14.2");
                out.add("1.15.0a");
            } else {
                out.add("‹S" + position + "›");
                out.add("");
            }
        } else if (type == Radio.Station.class) {
            out.addAll(Arrays.asList(Radio.STATIONS));
            out.add(Radio.Station.reciter("dump", "قارئ", "Reciter"));
        } else if (type == app.awanpro.tv.Method.class) {
            out.addAll(methods());
        } else if (type == Madhab.class) {
            out.addAll(Arrays.asList(Madhab.values()));
        } else if (type == int[].class) {
            out.add(new int[] {0, 0, 0, 0, 0, 0});
            out.add(new int[] {2, 0, 0, 0, 0, 0});
            out.add(new int[] {0, -3, 0, 0, 1, 0});
            out.add(new int[] {1, 1, 1, 1, 1, 1});
            out.add(new int[] {-30, 30, 0, 0, 0, -1});
        } else {
            return null;
        }
        return out;
    }

    private static List<Object> methods() {
        List<Object> out = new ArrayList<>();
        for (CalculationMethod m : CalculationMethod.values()) out.add(app.awanpro.tv.Method.of(m));
        for (Field f : app.awanpro.tv.Method.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != app.awanpro.tv.Method.class) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(null);
                if (v != null && !out.contains(v)) out.add(v);
            } catch (IllegalAccessException ignored) {
                // Not readable: left out.
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- the dump

    static void write(Context c, java.io.File file) throws java.io.IOException {
        List<String> lines = new ArrayList<>();
        boolean[][] variants = {{true, false}, {true, true}, {false, false}, {false, true}};
        for (boolean[] v : variants) {
            Text t = make(c, v[0], v[1]);
            String tag = (v[0] ? "ar" : "en") + (v[1] ? "-eastern" : "-western");
            textMethods(t, tag, lines);
            helpers(c, t, tag, lines);
            dates(t, tag, lines);
            order(t, tag, lines);
        }
        language(lines);
        settings(c, lines);
        // Sorted, and a repeated key numbered, so each line is found by its key whatever the order of reflection.
        java.util.Collections.sort(lines);
        Map<String, Integer> seen = new LinkedHashMap<>();
        StringBuilder b = new StringBuilder();
        for (String line : lines) {
            int tab = line.indexOf("\t=>\t");
            String key = tab < 0 ? line : line.substring(0, tab);
            Integer n = seen.get(key);
            seen.put(key, n == null ? 1 : n + 1);
            b.append(n == null ? line : key + " #" + (n + 1) + line.substring(Math.max(tab, 0))).append('\n');
        }
        java.io.File tmp = new java.io.File(file.getPath() + ".tmp");
        try (java.io.OutputStream os = new java.io.FileOutputStream(tmp)) {
            os.write(b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(file)) throw new java.io.IOException("Could not write " + file);
    }

    /** Every method of Text (not private, not made by the compiler), static ones too, with every input. */
    private static void textMethods(Text t, String tag, List<String> lines) {
        Method[] all = Text.class.getDeclaredMethods();
        Arrays.sort(all, Comparator.comparing(TextDump::signature));
        for (Method m : all) {
            if (m.isSynthetic() || Modifier.isPrivate(m.getModifiers())) continue;
            Class<?>[] types = m.getParameterTypes();
            int numbers = 0;
            for (Class<?> type : types) if (numeric(type)) numbers++;
            List<List<Object>> lists = new ArrayList<>();
            long combinations = 1;
            boolean unknown = false;
            // The whole range for a method's one number; the short lists when that makes too many combinations.
            for (int pass = 0; pass < 2; pass++) {
                lists.clear();
                combinations = 1;
                unknown = false;
                for (int i = 0; i < types.length; i++) {
                    boolean whole = pass == 0 && (types.length == 1 || (numbers == 1 && numeric(types[i])));
                    List<Object> vs = values(types[i], whole, types.length == 1, i);
                    if (vs == null) { unknown = true; break; }
                    lists.add(vs);
                    combinations *= vs.size();
                }
                if (unknown || combinations <= MAX_COMBINATIONS) break;
            }
            String sig = signature(m);
            if (unknown) {
                lines.add("text|" + sig + "|" + tag + "\t=>\t!not dumped: a parameter type the dump does not know");
                continue;
            }
            if (combinations > MAX_COMBINATIONS) {
                lines.add("text|" + sig + "|" + tag + "\t=>\t!not dumped: " + combinations + " combinations");
                continue;
            }
            m.setAccessible(true);
            Object target = Modifier.isStatic(m.getModifiers()) ? null : t;
            List<String> results = new ArrayList<>();
            combine(lists, 0, new Object[types.length], args -> {
                String result;
                try {
                    result = show(m.invoke(target, args));
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    result = "!" + cause.getClass().getSimpleName() + ": " + escape(String.valueOf(cause.getMessage()));
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    result = "!" + e.getClass().getSimpleName();
                }
                results.add(result);
                lines.add("text|" + sig + "|" + tag + "|" + showArgs(args) + "\t=>\t" + result);
            });
            // One result for every input: the inputs may not reach the method's branches.
            if (results.size() > 1 && new java.util.HashSet<>(results).size() == 1) {
                lines.add("text|" + sig + "|" + tag + "|(note)\t=>\tsame result for all " + results.size() + " inputs");
            }
        }
    }

    private interface Call { void with(Object[] args); }

    private static void combine(List<List<Object>> lists, int i, Object[] args, Call call) {
        if (i == lists.size()) {
            call.with(args.clone());
            return;
        }
        for (Object v : lists.get(i)) {
            args[i] = v;
            combine(lists, i + 1, args, call);
        }
    }

    /** Texts written outside Text: the remote's buttons, the adhan voices, countries, apps' Arabic names. */
    private static void helpers(Context c, Text t, String tag, List<String> lines) {
        int[] codes = {KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_PROG_YELLOW,
            KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_BOOKMARK, KeyEvent.KEYCODE_ALL_APPS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_MEDIA_RECORD, KeyEvent.KEYCODE_TV, 4020, 4062};
        for (int code : codes) {
            for (int scan : new int[] {0, 377}) {
                lines.add("helper|Buttons.name|" + tag + "|" + code + "," + scan + "\t=>\t" + call(() -> Buttons.name(t, code, scan)));
            }
        }
        for (String[] v : Adhans.VOICES) {
            lines.add("helper|Adhans.name|" + tag + "|" + v[0] + "\t=>\t" + call(() -> Adhans.name(t, v[0])));
        }
        for (String cc : new String[] {"SA", "EG", "PS", "IL", "MA", "DZ", "AE", "ID", "MY", "PK", "IN", "BD", "TR", "IR",
            "GB", "US", "FR", "DE", "CN", "NG", "", "XX"}) {
            lines.add("helper|Places.country|" + tag + "|" + cc + "\t=>\t" + call(() -> Places.country(cc, t.ar)));
        }
        try {
            Field f = Apps.class.getDeclaredField("AR_NAMES");
            f.setAccessible(true);
            Map<?, ?> names = (Map<?, ?>) f.get(null);
            List<String> pkgs = new ArrayList<>();
            for (Object k : names.keySet()) pkgs.add(String.valueOf(k));
            java.util.Collections.sort(pkgs);
            for (String pkg : pkgs) {
                lines.add("helper|Apps.name|" + tag + "|" + pkg + "\t=>\t" + call(() -> Apps.name(c, t, pkg)));
            }
        } catch (ReflectiveOperationException e) {
            lines.add("helper|Apps.name|" + tag + "\t=>\t!" + e.getClass().getSimpleName());
        }
    }

    /** The Gregorian dates the screens write: a fixed day in each month, each day of the week. */
    private static void dates(Text t, String tag, List<String> lines) {
        String[] patterns = {"MMMM", "MMM", "d MMMM yyyy", "EEEE", "EEEE d MMMM", "EEEE d MMMM yyyy"};
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        for (String pattern : patterns) {
            SimpleDateFormat f = new SimpleDateFormat(pattern, Screen.dateLocale(t));
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            for (int month = 0; month < 12; month++) {
                cal.clear();
                cal.set(2026, month, 15, 12, 0, 0);
                lines.add("date|" + pattern + "|" + tag + "|2026-" + (month + 1) + "-15\t=>\t" + escape(t.d(f.format(cal.getTime()))));
            }
            for (int day = 0; day < 7; day++) {
                cal.clear();
                cal.set(2026, Calendar.NOVEMBER, 1 + day, 12, 0, 0);
                lines.add("date|" + pattern + "|" + tag + "|2026-11-" + (1 + day) + "\t=>\t" + escape(t.d(f.format(cal.getTime()))));
            }
        }
    }

    /** The order of a fixed list of names (apps, countries). */
    private static void order(Text t, String tag, List<String> lines) {
        List<String> names = new ArrayList<>(Arrays.asList("يوتيوب", "نتفليكس", "شاهد", "YouTube", "Netflix", "Shahid",
            "prime video", "Play Store", "الإعدادات", "Settings", "أذان", "إذاعة", "آخر", "zebra", "Ångström", "123", "السعودية",
            "مصر", "الأردن", "Égypte"));
        Collator col = Apps.collator(t);
        names.sort(col::compare);
        lines.add("order|Apps.collator|" + tag + "\t=>\t" + escape(String.join(" | ", names)));
    }

    /** The language and digits for every setting and TV language (Screen.language). */
    private static void language(List<String> lines) {
        for (String locale : new String[] {"system", "ar", "en", "id", "xx", ""}) {
            for (String tv : new String[] {"ar", "en", "fr", "in", "ur", ""}) {
                for (String digits : new String[] {"", "western", "eastern", "xx"}) {
                    boolean[] r = Screen.language(locale, tv, digits);
                    lines.add("language|" + locale + "|" + tv + "|" + digits + "\t=>\t" + (r[0] ? "ar" : "en") + (r[1] ? "-eastern" : "-western"));
                }
            }
        }
    }

    /** The settings editor's descriptions in both languages, and this TV's settings file (without what changes by itself). */
    private static void settings(Context c, List<String> lines) {
        for (boolean ar : new boolean[] {true, false}) {
            String tag = ar ? "ar" : "en";
            try {
                JSONObject schema = new JSONObject(SettingsFile.schema(c, "VERSION", ar));
                schema.remove("version");
                schema.remove("apps");
                String[] out = schema.toString(1).split("\n");
                for (int i = 0; i < out.length; i++) {
                    lines.add("schema|" + tag + "|" + String.format(java.util.Locale.US, "%05d", i) + "\t=>\t" + escape(out[i]));
                }
            } catch (Exception e) {
                lines.add("schema|" + tag + "\t=>\t!" + e.getClass().getSimpleName() + ": " + escape(String.valueOf(e.getMessage())));
            }
        }
        // In this TV's own language and with its own settings: unchanged between two dumps if the settings are.
        try {
            String[] out = SettingsFile.write(c, "VERSION").split("\n");
            int n = 0;
            for (String line : out) {
                String trimmed = line.trim();
                if (trimmed.startsWith("\"version\":") || trimmed.startsWith("\"buttons\":")) continue;
                lines.add("file|" + String.format(java.util.Locale.US, "%05d", n++) + "\t=>\t" + escape(line));
            }
        } catch (Exception e) {
            lines.add("file\t=>\t!" + e.getClass().getSimpleName() + ": " + escape(String.valueOf(e.getMessage())));
        }
    }

    // ---------------------------------------------------------------- printing

    private interface Text0 { Object get() throws Exception; }

    private static String call(Text0 f) {
        try {
            return show(f.get());
        } catch (Exception e) {
            return "!" + e.getClass().getSimpleName() + ": " + escape(String.valueOf(e.getMessage()));
        }
    }

    private static String signature(Method m) {
        StringBuilder b = new StringBuilder(m.getName()).append('(');
        Class<?>[] types = m.getParameterTypes();
        for (int i = 0; i < types.length; i++) b.append(i == 0 ? "" : ",").append(types[i].getSimpleName());
        return b.append(')').toString();
    }

    private static String showArgs(Object[] args) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < args.length; i++) b.append(i == 0 ? "" : ",").append(showArg(args[i]));
        return b.toString();
    }

    private static String showArg(Object a) {
        if (a instanceof Radio.Station) return "station:" + ((Radio.Station) a).id;
        if (a instanceof app.awanpro.tv.Method) return "method:" + ((app.awanpro.tv.Method) a).id;
        if (a instanceof int[]) return Arrays.toString((int[]) a).replace(" ", "");
        if (a instanceof String) return "\"" + escape((String) a) + "\"";
        return String.valueOf(a);
    }

    private static String show(Object r) {
        if (r == null) return "null";
        if (r instanceof Object[]) {
            StringBuilder b = new StringBuilder("[");
            Object[] a = (Object[]) r;
            for (int i = 0; i < a.length; i++) b.append(i == 0 ? "" : " | ").append(show(a[i]));
            return b.append(']').toString();
        }
        if (r instanceof int[]) return Arrays.toString((int[]) r);
        if (r instanceof CharSequence) return "\"" + escape(r.toString()) + "\"";
        return escape(String.valueOf(r));
    }

    /** Backslash, and everything that does not print as itself (controls, format marks, spaces other than " "), as \\uXXXX. */
    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int type = Character.getType(ch);
            boolean hidden = Character.isISOControl(ch) || type == Character.FORMAT
                || (Character.isSpaceChar(ch) && ch != ' ') || type == Character.SURROGATE || type == Character.UNASSIGNED;
            if (ch == '\\') b.append("\\\\");
            else if (hidden) b.append(String.format(java.util.Locale.US, "\\u%04X", (int) ch));
            else b.append(ch);
        }
        return b.toString();
    }
}
