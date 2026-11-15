package app.awanpro.tv;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BuildJakim - Version 1.0.0 (run by tools/AwanTV-build-jakim.ps1).
 *
 * Works out JAKIM's zone times (Malaysia's e-Solat timetables) as Awan TV computes them ({@link Jakim}): for every zone
 * and prayer, up to three points and a margin, the time being the latest raw time over the points (the earliest, for
 * sunrise, which ends Fajr) plus the margin, its minute rounded up. That is how JAKIM is described to make one time
 * serve a whole zone (its reference at the zone's far side, a precaution of up to two minutes); the points and
 * margins themselves are read off the timetables here.
 *
 * Input: tools/jakim/zones.tsv (zone, state, its towns name|lat|lon as e-Solat names them) and
 * state/jakim/<year>/<zone>.json (tools/AwanTV-fetch-jakim). Fitted on every year but the earliest, which checks it.
 * Output: app/src/main/assets/jakim/zones.tsv. The raw times are {@link JakimSun}'s, the app's own code.
 */
public final class BuildJakim {
    static final String[] NAMES = {"fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"};
    static final int MAX_POINTS = 3;
    /** How far around the zone's towns its points are looked for, degrees. */
    static final double REACH = 1.0;

    static final class Day {
        final int year, month, day;
        final int[] table = new int[6];
        Day(int y, int m, int d) { year = y; month = m; day = d; }
    }

    static final class Zone {
        final String code, state, towns;
        final double lat0, lat1, lon0, lon1;
        final List<Day> days = new ArrayList<>();
        Zone(String line) {
            String[] p = line.split("\t");
            code = p[0];
            state = p[1];
            StringBuilder t = new StringBuilder();
            double a0 = 90, a1 = -90, o0 = 180, o1 = -180;
            for (int i = 2; i < p.length; i++) {
                String[] q = p[i].split("\\|");
                double la = Double.parseDouble(q[1]), lo = Double.parseDouble(q[2]);
                a0 = Math.min(a0, la); a1 = Math.max(a1, la); o0 = Math.min(o0, lo); o1 = Math.max(o1, lo);
                if (t.length() > 0) t.append(';');
                t.append(p[i]);
            }
            towns = t.toString();
            lat0 = a0 - REACH; lat1 = a1 + REACH; lon0 = o0 - REACH; lon1 = o1 + REACH;
        }
    }

    /** One prayer's fit. */
    static final class Fit {
        int variant;
        double margin;
        final List<double[]> points = new ArrayList<>();
        int fitExact;
        double[] envelope;
    }

    private static final Pattern DAY = Pattern.compile(
        "\"date\":\"(\\d+)-(\\w+)-(\\d+)\"[^}]*?\"fajr\":\"(\\d+):(\\d+)[^\"]*\",\"syuruk\":\"(\\d+):(\\d+)[^}]*?"
        + "\"dhuhr\":\"(\\d+):(\\d+)[^\"]*\",\"asr\":\"(\\d+):(\\d+)[^\"]*\",\"maghrib\":\"(\\d+):(\\d+)[^\"]*\",\"isha\":\"(\\d+):(\\d+)");
    private static final String MONTHS = "Jan Feb Mac Apr Mei Jun Jul Ogos Sep Okt Nov Dis";
    private static final String MONTHS_EN = "Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec";

    static int month(String m) {
        String[] a = MONTHS.split(" "), b = MONTHS_EN.split(" ");
        for (int i = 0; i < 12; i++) if (a[i].equals(m) || b[i].equals(m)) return i + 1;
        throw new IllegalArgumentException("month " + m);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("BuildJakim v1.0.0");
        File root = new File(args.length > 0 ? args[0] : ".");
        File state = new File(root, "state/jakim");
        Map<String, Zone> zones = new TreeMap<>();
        for (String line : Files.readAllLines(new File(root, "tools/jakim/zones.tsv").toPath(), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            Zone z = new Zone(line);
            zones.put(z.code, z);
        }
        int first = Integer.MAX_VALUE;
        File[] years = state.listFiles(File::isDirectory);
        if (years == null || years.length < 2) throw new IOException("Need two years or more in " + state + " (AwanTV-fetch-jakim)");
        for (File y : years) {
            first = Math.min(first, Integer.parseInt(y.getName()));
            for (Zone z : zones.values()) {
                File f = new File(y, z.code + ".json");
                if (!f.exists()) continue;
                Matcher m = DAY.matcher(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                while (m.find()) {
                    Day d = new Day(Integer.parseInt(m.group(3)), month(m.group(2)), Integer.parseInt(m.group(1)));
                    for (int i = 0; i < 6; i++) {
                        int h = Integer.parseInt(m.group(4 + 2 * i)), mi = Integer.parseInt(m.group(5 + 2 * i));
                        // A few tables print the afternoon in a twelve-hour clock.
                        if (i >= 3 && h < 12) h += 12;
                        d.table[i] = h * 60 + mi;
                    }
                    z.days.add(d);
                }
            }
        }
        final int checkYear = first;
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
        Map<String, Future<String>> jobs = new TreeMap<>();
        for (Zone z : zones.values()) jobs.put(z.code, pool.submit(() -> fitZone(z, checkYear)));
        File out = new File(root, "app/src/main/assets/jakim/zones.tsv");
        out.getParentFile().mkdirs();
        long[] totals = new long[4];
        try (PrintWriter w = new PrintWriter(out, "UTF-8")) {
            w.println("# JAKIM's zones (Malaysia), from tools/AwanTV-build-jakim: zone, state, towns (name|lat|lon), then per");
            w.println("# prayer (Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha) variant margin lat,lon[ lat,lon...]; see Jakim.java.");
            for (Map.Entry<String, Future<String>> e : jobs.entrySet()) {
                String[] r = e.getValue().get().split("\n");
                w.println(r[0]);
                String[] n = r[1].split(" ");
                for (int i = 0; i < 4; i++) totals[i] += Long.parseLong(n[i]);
                System.out.println(r[2]);
            }
        }
        pool.shutdown();
        System.out.printf(Locale.ROOT, "All zones: fitted years %.1f%% on the minute; %d (not fitted) %.1f%% on the minute%n",
            100.0 * totals[0] / Math.max(1, totals[1]), checkYear, 100.0 * totals[2] / Math.max(1, totals[3]));
        System.out.println("Written: " + out);
    }

    static String fitZone(Zone z, int checkYear) {
        int n = z.days.size();
        boolean[] fit = new boolean[n];
        for (int i = 0; i < n; i++) fit[i] = z.days.get(i).year != checkYear;
        // Raw times at a point, computed once per point.
        StringBuilder line = new StringBuilder(z.code + "\t" + z.state + "\t" + z.towns);
        StringBuilder report = new StringBuilder(z.code);
        long fitEx = 0, fitN = 0, chkEx = 0, chkN = 0;
        for (int pr = 0; pr < 6; pr++) {
            int[] variants = pr == 3 ? new int[] {JakimSun.ASR, JakimSun.ASR_AT_0H} : new int[] {pr};
            Fit best = null;
            for (int v : variants) {
                Fit f = greedy(z, fit, pr, v);
                if (best == null || f.fitExact > best.fitExact) best = f;
            }
            int fe = 0, fn = 0, ce = 0, cn = 0;
            for (int i = 0; i < n; i++) {
                int got = (int) Math.ceil(best.envelope[i] + best.margin - 1e-9);
                boolean hit = got == z.days.get(i).table[pr];
                if (fit[i]) { fn++; if (hit) fe++; } else { cn++; if (hit) ce++; }
            }
            fitEx += fe; fitN += fn; chkEx += ce; chkN += cn;
            line.append('\t').append(best.variant).append(' ').append(String.format(Locale.ROOT, "%.2f", best.margin));
            for (double[] p : best.points) line.append(String.format(Locale.ROOT, " %.2f,%.2f", p[0], p[1]));
            report.append(String.format(Locale.ROOT, "  %s %.0f%%", NAMES[pr], 100.0 * fe / Math.max(1, fn)));
            if (cn > 0) report.append(String.format(Locale.ROOT, "/%.0f%%", 100.0 * ce / cn));
        }
        return line + "\n" + fitEx + " " + fitN + " " + chkEx + " " + chkN + "\n" + report;
    }

    static double raw(double lat, double lon, Day d, int variant) {
        return JakimSun.time(variant, lat, lon, d.year, d.month, d.day, 8, 1);
    }

    /** Points one at a time (each the best of a 0.1° grid, then 0.02° around it), while each adds enough days. */
    static Fit greedy(Zone z, boolean[] fit, int pr, int variant) {
        int n = z.days.size();
        boolean earliest = pr == JakimSun.SUNRISE;
        Fit res = new Fit();
        res.variant = variant;
        double[] env = null;
        for (int k = 0; k < MAX_POINTS; k++) {
            double bestScore = -1, bestMargin = 0;
            double[] bestEnv = null, bestPoint = null;
            for (double step : new double[] {0.1, 0.02}) {
                double a0, a1, o0, o1;
                if (step == 0.1) { a0 = z.lat0; a1 = z.lat1; o0 = z.lon0; o1 = z.lon1; }
                else { a0 = bestPoint[0] - 0.1; a1 = bestPoint[0] + 0.1; o0 = bestPoint[1] - 0.1; o1 = bestPoint[1] + 0.1; }
                for (double la = a0; la <= a1 + 1e-9; la += step) {
                    for (double lo = o0; lo <= o1 + 1e-9; lo += step) {
                        double[] e = new double[n];
                        for (int i = 0; i < n; i++) {
                            double r = raw(la, lo, z.days.get(i), variant);
                            e[i] = env == null ? r : earliest ? Math.min(env[i], r) : Math.max(env[i], r);
                        }
                        double[] mm = bestMargin(e, z, fit, pr);
                        if (mm[1] > bestScore) { bestScore = mm[1]; bestEnv = e; bestMargin = mm[0]; bestPoint = new double[] {la, lo}; }
                    }
                }
            }
            if (env != null && bestScore < res.fitExact + Math.max(3, 0.003 * n)) break;
            env = bestEnv;
            res.envelope = env;
            res.margin = bestMargin;
            res.fitExact = (int) bestScore;
            res.points.add(new double[] {Math.round(bestPoint[0] * 100) / 100.0, Math.round(bestPoint[1] * 100) / 100.0});
        }
        // The points as written (two decimals), so the margin and the count are what the app will compute.
        double[] e = new double[n];
        for (int i = 0; i < n; i++) {
            double v = earliest ? Double.MAX_VALUE : -Double.MAX_VALUE;
            for (double[] p : res.points) {
                double r = raw(p[0], p[1], z.days.get(i), variant);
                v = earliest ? Math.min(v, r) : Math.max(v, r);
            }
            e[i] = v;
        }
        double[] mm = bestMargin(e, z, fit, pr);
        res.envelope = e;
        res.margin = Math.round(mm[0] * 100) / 100.0;
        res.fitExact = (int) mm[1];
        return res;
    }

    /** The margin m that puts most fitted days on their minute: day i wants ceil(e + m) = t, m in (t - 1 - e, t - e]. */
    static double[] bestMargin(double[] e, Zone z, boolean[] fit, int pr) {
        List<double[]> ev = new ArrayList<>();
        for (int i = 0; i < e.length; i++) {
            if (!fit[i] || Double.isNaN(e[i])) continue;
            double t = z.days.get(i).table[pr];
            ev.add(new double[] {t - 1 - e[i], 1});
            ev.add(new double[] {t - e[i], -1});
        }
        ev.sort((x, y) -> x[0] != y[0] ? Double.compare(x[0], y[0]) : Double.compare(x[1], y[1]));
        int cur = 0, best = -1;
        double m = 0;
        for (int i = 0; i < ev.size(); i++) {
            cur += (int) ev.get(i)[1];
            if (ev.get(i)[1] > 0 && i + 1 < ev.size() && cur > best) {
                best = cur;
                // Two decimals inside the interval, away from its open end.
                double lo = ev.get(i)[0], hi = ev.get(i + 1)[0];
                m = Math.min(hi, Math.max(lo + 0.005, Math.round((lo + hi) / 2 * 100) / 100.0));
            }
        }
        return new double[] {m, best};
    }
}
