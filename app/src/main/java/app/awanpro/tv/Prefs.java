package app.awanpro.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateFormat;

import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.Madhab;

/**
 * The settings (SharedPreferences {@link #FILE}), read by every screen and the screensaver. Language and digits
 * live in {@link Screen#loadText}; the room direction in {@link Screen#facing()}.
 */
final class Prefs {
    private Prefs() {}

    static final String FILE = "awantv";
    private static volatile boolean moved;

    static SharedPreferences of(Context c) {
        SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (!moved) move(c, p);
        return p;
    }

    /**
     * Once: the settings of an install that kept them under Afnon's file name ("afnon") are copied into {@link #FILE}
     * and the old file is emptied, so an update keeps every setting.
     */
    private static synchronized void move(Context c, SharedPreferences into) {
        if (moved) return;
        moved = true;
        SharedPreferences old = c.getSharedPreferences("afnon", Context.MODE_PRIVATE);
        java.util.Map<String, ?> all = old.getAll();
        if (all.isEmpty() || !into.getAll().isEmpty()) return;
        SharedPreferences.Editor e = into.edit();
        for (java.util.Map.Entry<String, ?> en : all.entrySet()) {
            Object v = en.getValue();
            String k = en.getKey();
            if (v instanceof String) e.putString(k, (String) v);
            else if (v instanceof Integer) e.putInt(k, (Integer) v);
            else if (v instanceof Long) e.putLong(k, (Long) v);
            else if (v instanceof Float) e.putFloat(k, (Float) v);
            else if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
            else if (v instanceof java.util.Set) {
                java.util.Set<String> set = new java.util.HashSet<>();
                for (Object o : (java.util.Set<?>) v) set.add(String.valueOf(o));
                e.putStringSet(k, set);
            }
        }
        if (e.commit()) old.edit().clear().apply();
    }

    // ---------------------------------------------------------------- location

    static final class City {
        final String id, ar, en, countryAr, countryEn;
        final double lat, lng;
        /** The method that city's mosques follow; picked for it when the city changes. */
        final Method method;
        /** The Asr school most of its mosques follow, also picked when the city changes. */
        final Madhab madhab;

        City(String id, String ar, String en, String countryAr, String countryEn, double lat, double lng, Method method) {
            this(id, ar, en, countryAr, countryEn, lat, lng, method, Madhab.SHAFI);
        }

        City(String id, String ar, String en, String countryAr, String countryEn, double lat, double lng, Method method, Madhab madhab) {
            this.id = id;
            this.ar = ar;
            this.en = en;
            this.countryAr = countryAr;
            this.countryEn = countryEn;
            this.lat = lat;
            this.lng = lng;
            this.method = method;
            this.madhab = madhab;
        }
    }

    private static final Method EG = Method.EGYPTIAN;
    private static final Method MWL = Method.MUSLIM_WORLD_LEAGUE;
    private static final Method IN = Method.KARACHI;
    private static final Method SG = Method.SINGAPORE;
    /** Oman's Ministry of Awqaf (Method.OMAN_AWQAF). */
    private static final Method OM = Method.OMAN_AWQAF;
    private static final Method NA = Method.NORTH_AMERICA;
    /** Hanafi Asr (about an hour later), the usual school in Pakistan, Bangladesh and most of North India. */
    private static final Madhab HANAFI = Madhab.HANAFI;
    static final City[] CITIES = {
        new City("cairo", "القاهرة", "Cairo", "مصر", "Egypt", 30.0444, 31.2357, EG),
        new City("giza", "الجيزة", "Giza", "مصر", "Egypt", 30.0131, 31.2089, EG),
        new City("alexandria", "الإسكندرية", "Alexandria", "مصر", "Egypt", 31.2001, 29.9187, EG),
        new City("mansoura", "المنصورة", "Mansoura", "مصر", "Egypt", 31.0409, 31.3785, EG),
        new City("tanta", "طنطا", "Tanta", "مصر", "Egypt", 30.7865, 31.0004, EG),
        new City("portsaid", "بورسعيد", "Port Said", "مصر", "Egypt", 31.2653, 32.3019, EG),
        new City("suez", "السويس", "Suez", "مصر", "Egypt", 29.9668, 32.5498, EG),
        new City("asyut", "أسيوط", "Asyut", "مصر", "Egypt", 27.1783, 31.1859, EG),
        new City("luxor", "الأقصر", "Luxor", "مصر", "Egypt", 25.6872, 32.6396, EG),
        new City("aswan", "أسوان", "Aswan", "مصر", "Egypt", 24.0889, 32.8998, EG),
        new City("makkah", "مكة المكرمة", "Makkah", "السعودية", "Saudi Arabia", 21.3891, 39.8579, Method.UMM_AL_QURA),
        new City("madinah", "المدينة المنورة", "Madinah", "السعودية", "Saudi Arabia", 24.5247, 39.5692, Method.UMM_AL_QURA),
        new City("riyadh", "الرياض", "Riyadh", "السعودية", "Saudi Arabia", 24.7136, 46.6753, Method.UMM_AL_QURA),
        new City("jeddah", "جدة", "Jeddah", "السعودية", "Saudi Arabia", 21.5292, 39.1611, Method.UMM_AL_QURA),
        new City("dammam", "الدمام", "Dammam", "السعودية", "Saudi Arabia", 26.4333, 50.1000, Method.UMM_AL_QURA),
        new City("taif", "الطائف", "Taif", "السعودية", "Saudi Arabia", 21.2667, 40.4167, Method.UMM_AL_QURA),
        new City("tabuk", "تبوك", "Tabuk", "السعودية", "Saudi Arabia", 28.3972, 36.5789, Method.UMM_AL_QURA),
        new City("abha", "أبها", "Abha", "السعودية", "Saudi Arabia", 18.2167, 42.5000, Method.UMM_AL_QURA),
        new City("dubai", "دبي", "Dubai", "الإمارات", "UAE", 25.2048, 55.2708, Method.DUBAI),
        new City("abudhabi", "أبو ظبي", "Abu Dhabi", "الإمارات", "UAE", 24.4511, 54.3969, Method.DUBAI),
        new City("sharjah", "الشارقة", "Sharjah", "الإمارات", "UAE", 25.3375, 55.4119, Method.DUBAI),
        new City("alain", "العين", "Al Ain", "الإمارات", "UAE", 24.2075, 55.7447, Method.DUBAI),
        new City("kuwait", "الكويت", "Kuwait City", "الكويت", "Kuwait", 29.3759, 47.9774, Method.KUWAIT),
        new City("doha", "الدوحة", "Doha", "قطر", "Qatar", 25.2854, 51.5310, Method.QATAR),
        new City("amman", "عمّان", "Amman", "الأردن", "Jordan", 31.9539, 35.9106, MWL),
        new City("irbid", "إربد", "Irbid", "الأردن", "Jordan", 32.5556, 35.8500, MWL),
        new City("zarqa", "الزرقاء", "Zarqa", "الأردن", "Jordan", 32.0833, 36.1000, MWL),
        new City("aqaba", "العقبة", "Aqaba", "الأردن", "Jordan", 29.5322, 35.0061, MWL),
        new City("jerusalem", "القدس", "Jerusalem", "فلسطين", "Palestine", 31.7789, 35.2256, MWL),
        new City("ramallah", "رام الله", "Ramallah", "فلسطين", "Palestine", 31.8969, 35.2017, MWL),
        new City("nablus", "نابلس", "Nablus", "فلسطين", "Palestine", 32.2211, 35.2608, MWL),
        new City("hebron", "الخليل", "Hebron", "فلسطين", "Palestine", 31.5286, 35.0969, MWL),
        new City("bethlehem", "بيت لحم", "Bethlehem", "فلسطين", "Palestine", 31.7044, 35.2064, MWL),
        new City("jenin", "جنين", "Jenin", "فلسطين", "Palestine", 32.4611, 35.3000, MWL),
        new City("tulkarm", "طولكرم", "Tulkarm", "فلسطين", "Palestine", 32.3111, 35.0308, MWL),
        new City("qalqilya", "قلقيلية", "Qalqilya", "فلسطين", "Palestine", 32.1904, 34.9685, MWL),
        new City("salfit", "سلفيت", "Salfit", "فلسطين", "Palestine", 32.0819, 35.1822, MWL),
        new City("tubas", "طوباس", "Tubas", "فلسطين", "Palestine", 32.3222, 35.3686, MWL),
        new City("jericho", "أريحا", "Jericho", "فلسطين", "Palestine", 31.8561, 35.4600, MWL),
        new City("gaza", "غزة", "Gaza", "فلسطين", "Palestine", 31.5075, 34.4597, MWL),
        new City("jabalia", "جباليا", "Jabalia", "فلسطين", "Palestine", 31.5281, 34.4831, MWL),
        new City("deiralbalah", "دير البلح", "Deir al-Balah", "فلسطين", "Palestine", 31.4167, 34.3500, MWL),
        new City("khanyunis", "خان يونس", "Khan Yunis", "فلسطين", "Palestine", 31.3444, 34.3031, MWL),
        new City("rafah", "رفح", "Rafah", "فلسطين", "Palestine", 31.2725, 34.2586, MWL),
        new City("beirut", "بيروت", "Beirut", "لبنان", "Lebanon", 33.8869, 35.5131, MWL),
        new City("tripoli", "طرابلس", "Tripoli", "لبنان", "Lebanon", 34.4367, 35.8344, MWL),
        new City("sidon", "صيدا", "Sidon", "لبنان", "Lebanon", 33.5606, 35.3758, MWL),
        new City("tyre", "صور", "Tyre", "لبنان", "Lebanon", 33.2667, 35.2000, MWL),
        new City("baalbek", "بعلبك", "Baalbek", "لبنان", "Lebanon", 34.0061, 36.2086, MWL),
        new City("damascus", "دمشق", "Damascus", "سوريا", "Syria", 33.5131, 36.2919, MWL),
        new City("aleppo", "حلب", "Aleppo", "سوريا", "Syria", 36.2000, 37.1600, MWL),
        new City("homs", "حمص", "Homs", "سوريا", "Syria", 34.7333, 36.7167, MWL),
        new City("hama", "حماة", "Hama", "سوريا", "Syria", 35.1350, 36.7500, MWL),
        new City("latakia", "اللاذقية", "Latakia", "سوريا", "Syria", 35.5200, 35.7781, MWL),
        new City("deirezzor", "دير الزور", "Deir ez-Zor", "سوريا", "Syria", 35.3333, 40.1500, MWL),
        new City("baghdad", "بغداد", "Baghdad", "العراق", "Iraq", 33.3153, 44.3661, MWL),
        new City("basra", "البصرة", "Basra", "العراق", "Iraq", 30.5150, 47.8100, MWL),
        new City("mosul", "الموصل", "Mosul", "العراق", "Iraq", 36.3417, 43.1292, MWL),
        new City("erbil", "أربيل", "Erbil", "العراق", "Iraq", 36.1911, 44.0094, MWL),
        new City("najaf", "النجف", "Najaf", "العراق", "Iraq", 32.0290, 44.3396, MWL),
        new City("karbala", "كربلاء", "Karbala", "العراق", "Iraq", 32.6167, 44.0333, MWL),
        new City("kirkuk", "كركوك", "Kirkuk", "العراق", "Iraq", 35.4667, 44.4000, MWL),
        new City("sulaymaniyah", "السليمانية", "Sulaymaniyah", "العراق", "Iraq", 35.5500, 45.4333, MWL),
        new City("manama", "المنامة", "Manama", "البحرين", "Bahrain", 26.2167, 50.5833, MWL),
        new City("muharraq", "المحرق", "Muharraq", "البحرين", "Bahrain", 26.2533, 50.6083, MWL),
        // Muscat at the point the ministry's own Muscat timetable matches (Afnon 0.7).
        new City("muscat", "مسقط", "Muscat", "عُمان", "Oman", 23.6139, 58.2800, OM),
        new City("salalah", "صلالة", "Salalah", "عُمان", "Oman", 17.0197, 54.0897, OM),
        new City("sohar", "صحار", "Sohar", "عُمان", "Oman", 24.3420, 56.7299, OM),
        new City("nizwa", "نزوى", "Nizwa", "عُمان", "Oman", 22.9333, 57.5333, OM),
        new City("sanaa", "صنعاء", "Sanaa", "اليمن", "Yemen", 15.3500, 44.2000, MWL),
        new City("aden", "عدن", "Aden", "اليمن", "Yemen", 12.8000, 45.0333, MWL),
        new City("taiz", "تعز", "Taiz", "اليمن", "Yemen", 13.5789, 44.0219, MWL),
        new City("hodeidah", "الحديدة", "Hodeidah", "اليمن", "Yemen", 14.8022, 42.9511, MWL),
        new City("mukalla", "المكلا", "Mukalla", "اليمن", "Yemen", 14.5333, 49.1333, MWL),
        // United States & Canada
        new City("newyork", "نيويورك", "New York", "الولايات المتحدة", "United States", 40.7128, -74.0060, NA),
        new City("chicago", "شيكاغو", "Chicago", "الولايات المتحدة", "United States", 41.8781, -87.6298, NA),
        new City("dallas", "دالاس", "Dallas", "الولايات المتحدة", "United States", 32.7767, -96.7970, NA),
        new City("houston", "هيوستن", "Houston", "الولايات المتحدة", "United States", 29.7604, -95.3698, NA),
        new City("losangeles", "لوس أنجلوس", "Los Angeles", "الولايات المتحدة", "United States", 34.0522, -118.2437, NA),
        new City("toronto", "تورونتو", "Toronto", "كندا", "Canada", 43.6532, -79.3832, NA),

        // India
        new City("bangalore", "بنغالور", "Bengaluru", "الهند", "India", 12.9716, 77.5946, IN, HANAFI),
        new City("kozhikode", "كوزيكود", "Kozhikode", "الهند", "India", 11.2588, 75.7804, IN),
        new City("mumbai", "مومباي", "Mumbai", "الهند", "India", 19.0760, 72.8777, IN, HANAFI),
        new City("delhi", "دلهي", "Delhi", "الهند", "India", 28.6139, 77.2090, IN, HANAFI),
        new City("hyderabad", "حيدر أباد", "Hyderabad", "الهند", "India", 17.3850, 78.4867, IN, HANAFI),
        new City("chennai", "تشيناي", "Chennai", "الهند", "India", 13.0827, 80.2707, IN),
        new City("kolkata", "كولكاتا", "Kolkata", "الهند", "India", 22.5726, 88.3639, IN, HANAFI),
        new City("ahmedabad", "أحمد أباد", "Ahmedabad", "الهند", "India", 23.0225, 72.5714, IN, HANAFI),
        new City("pune", "بونه", "Pune", "الهند", "India", 18.5204, 73.8567, IN, HANAFI),
        new City("lucknow", "لكهنؤ", "Lucknow", "الهند", "India", 26.8467, 80.9462, IN, HANAFI),
        new City("srinagar", "سرينغار", "Srinagar", "الهند", "India", 34.0837, 74.7973, IN, HANAFI),
        new City("kochi", "كوتشي", "Kochi", "الهند", "India", 9.9312, 76.2673, IN),
        new City("malappuram", "ملابورام", "Malappuram", "الهند", "India", 11.0510, 76.0711, IN),
        new City("bhopal", "بوبال", "Bhopal", "الهند", "India", 23.2599, 77.4126, IN, HANAFI),
        new City("patna", "باتنا", "Patna", "الهند", "India", 25.5941, 85.1376, IN, HANAFI),

        // Pakistan
        new City("karachi", "كراتشي", "Karachi", "باكستان", "Pakistan", 24.8607, 67.0011, IN, HANAFI),
        new City("lahore", "لاهور", "Lahore", "باكستان", "Pakistan", 31.5204, 74.3587, IN, HANAFI),
        new City("islamabad", "إسلام أباد", "Islamabad", "باكستان", "Pakistan", 33.6844, 73.0479, IN, HANAFI),
        new City("rawalpindi", "راولبندي", "Rawalpindi", "باكستان", "Pakistan", 33.5651, 73.0169, IN, HANAFI),
        new City("peshawar", "بيشاور", "Peshawar", "باكستان", "Pakistan", 34.0151, 71.5249, IN, HANAFI),

        // Bangladesh
        new City("dhaka", "دكا", "Dhaka", "بنغلاديش", "Bangladesh", 23.8103, 90.4125, IN, HANAFI),
        new City("chittagong", "شيتاغونغ", "Chittagong", "بنغلاديش", "Bangladesh", 22.3569, 91.7832, IN, HANAFI),

        // Sri Lanka & Maldives (Shafi'i, as are Kerala and Tamil Nadu above)
        new City("colombo", "كولمبو", "Colombo", "سريلانكا", "Sri Lanka", 6.9271, 79.8612, IN),
        new City("male", "ماليه", "Malé", "المالديف", "Maldives", 4.1755, 73.5093, MWL),

        // Southeast Asia. Indonesia (Kemenag), Singapore (MUIS) and Brunei use 20°/18°. Malaysia (JAKIM) moved Subuh to
        // 18° in 2020; adhan-java has no JAKIM method, and Moon Sighting Committee came within 2 minutes of every
        // e-solat time for Kuala Lumpur (2026-10-22), where Singapore's 20° put Fajr 10 minutes early.
        new City("jakarta", "جاكرتا", "Jakarta", "إندونيسيا", "Indonesia", -6.2088, 106.8456, SG),
        new City("surabaya", "سورابايا", "Surabaya", "إندونيسيا", "Indonesia", -7.2575, 112.7521, SG),
        new City("bandung", "باندونغ", "Bandung", "إندونيسيا", "Indonesia", -6.9175, 107.6191, SG),
        new City("medan", "ميدان", "Medan", "إندونيسيا", "Indonesia", 3.5952, 98.6722, SG),
        new City("kualalumpur", "كوالالمبور", "Kuala Lumpur", "ماليزيا", "Malaysia", 3.1390, 101.6869, Method.MOON_SIGHTING_COMMITTEE),
        new City("penang", "بينانغ", "Penang", "ماليزيا", "Malaysia", 5.4164, 100.3327, Method.MOON_SIGHTING_COMMITTEE),
        new City("singapore", "سنغافورة", "Singapore", "سنغافورة", "Singapore", 1.3521, 103.8198, SG),
        new City("brunei", "بندر سري بكاوان", "Bandar Seri Begawan", "بروناي", "Brunei", 4.9031, 114.9398, SG),
        new City("bangkok", "بانكوك", "Bangkok", "تايلاند", "Thailand", 13.7563, 100.5018, SG),

        // East Asia
        new City("tokyo", "طوكيو", "Tokyo", "اليابان", "Japan", 35.6762, 139.6503, MWL),
        new City("seoul", "سيول", "Seoul", "كوريا الجنوبية", "South Korea", 37.5665, 126.9780, MWL),

        // Turkey: Diyanet uses MWL's angles (18°/17°) and the first-shadow Asr (asr-ı evvel), plus safety minutes
        // (temkin) adhan-java can't add. Against Diyanet's Istanbul table (2026-10-22) Fajr and Isha match within a
        // minute; Dhuhr, Asr and Maghrib come 4 to 8 minutes before the mosques'.
        new City("istanbul", "إسطنبول", "Istanbul", "تركيا", "Turkey", 41.0082, 28.9784, MWL),
        new City("ankara", "أنقرة", "Ankara", "تركيا", "Turkey", 39.9334, 32.8597, MWL),

        // Europe
        new City("london", "لندن", "London", "المملكة المتحدة", "United Kingdom", 51.5074, -0.1278, MWL),
        new City("birmingham", "برمنغهام", "Birmingham", "المملكة المتحدة", "United Kingdom", 52.4862, -1.8904, MWL),
        new City("manchester", "مانشستر", "Manchester", "المملكة المتحدة", "United Kingdom", 53.4808, -2.2426, MWL),
        new City("paris", "باريس", "Paris", "فرنسا", "France", 48.8566, 2.3522, MWL),
        new City("berlin", "برلين", "Berlin", "ألمانيا", "Germany", 52.5200, 13.4050, MWL),
        new City("frankfurt", "فرانكفورت", "Frankfurt", "ألمانيا", "Germany", 50.1109, 8.6821, MWL),
        new City("amsterdam", "أمستردام", "Amsterdam", "هولندا", "Netherlands", 52.3676, 4.9041, MWL),
        new City("brussels", "بروكسل", "Brussels", "بلجيكا", "Belgium", 50.8503, 4.3517, MWL),
        new City("vienna", "فيينا", "Vienna", "النمسا", "Austria", 48.2082, 16.3738, MWL),
        new City("zurich", "زيورخ", "Zurich", "سويسرا", "Switzerland", 47.3769, 8.5417, MWL),
        new City("rome", "روما", "Rome", "إيطاليا", "Italy", 41.9028, 12.4964, MWL),
        new City("madrid", "مدريد", "Madrid", "إسبانيا", "Spain", 40.4168, -3.7038, MWL),
        new City("sarajevo", "سراييفو", "Sarajevo", "البوسنة والهرسك", "Bosnia and Herzegovina", 43.8563, 18.4131, MWL),

        // Africa
        new City("nairobi", "نيروبي", "Nairobi", "كينيا", "Kenya", -1.2921, 36.8219, MWL),
        new City("lagos", "لاغوس", "Lagos", "نيجيريا", "Nigeria", 6.5244, 3.3792, MWL),
        new City("johannesburg", "جوهانسبرغ", "Johannesburg", "جنوب أفريقيا", "South Africa", -26.2041, 28.0473, MWL),

        // Oceania
        new City("sydney", "سيدني", "Sydney", "أستراليا", "Australia", -33.8688, 151.2093, MWL),
        new City("melbourne", "ملبورن", "Melbourne", "أستراليا", "Australia", -37.8136, 144.9631, MWL),
        new City("auckland", "أوكلاند", "Auckland", "نيوزيلندا", "New Zealand", -36.8485, 174.7633, MWL),
    };

    /** The id of a place set by its coordinates (Settings > Location > Another place). */
    static final String CUSTOM = "custom";

    /** The index in {@link #CITIES}; -1 for a place set by its coordinates. */
    static int cityIndex(Context c) {
        String id = of(c).getString("city", null);
        if (id == null) return guessCity();
        if (CUSTOM.equals(id)) return -1;
        for (int i = 0; i < CITIES.length; i++) if (CITIES[i].id.equals(id)) return i;
        return 0;
    }

    /** False until the viewer has picked a city; Home then opens the city list once. */
    static boolean citySet(Context c) {
        return of(c).contains("city");
    }

    /**
     * Until a city is picked, the first listed city of the TV's own country (from its time zone), so a
     * new install is roughly right anywhere instead of always in Cairo. Cairo when the country is not listed.
     */
    private static int guessCity() {
        String country = null;
        try {
            country = listCountry(android.icu.util.TimeZone.getRegion(java.util.TimeZone.getDefault().getID()));
        } catch (RuntimeException unknownZone) {
            // No region for this zone (UTC, say): fall through to Cairo.
        }
        if (country != null) for (int i = 0; i < CITIES.length; i++) if (CITIES[i].countryEn.equals(country)) return i;
        return 0;
    }

    /** A country's English name as CITIES writes it, from its ISO code (Android's name, except where CITIES differs). */
    private static String listCountry(String cc) {
        switch (cc) {
            case "AE": return "UAE";
            case "BA": return "Bosnia and Herzegovina";
            case "PS": return "Palestine";
            case "TR": return "Turkey";
            case "KR": return "South Korea";
            default: return new java.util.Locale("", cc).getDisplayCountry(java.util.Locale.ENGLISH);
        }
    }

    /** A country by its ISO code, as the city list names it ("السعودية") when it has it; else Android's name. */
    static String countryName(String cc, boolean ar) {
        if (cc == null || cc.isEmpty()) return "";
        String en = listCountry(cc);
        for (City city : CITIES) if (city.countryEn.equals(en)) return ar ? city.countryAr : city.countryEn;
        return Places.country(cc, ar);
    }

    /** A country in the screen's language: the list's names in Arabic and English, Android's in any other. */
    static String countryName(String cc, Text t) {
        if (t.ar || "en".equals(t.lang)) return countryName(cc, t.ar);
        return Places.country(cc, t);
    }

    /** A listed or saved city's country in the screen's language (any other language: by its code, else English). */
    static String countryOf(City city, Text t) {
        if (t.ar) return city.countryAr;
        if ("en".equals(t.lang)) return city.countryEn;
        String cc = codeOf(city.countryEn);
        return cc == null ? city.countryEn : Places.country(cc, t);
    }

    private static volatile java.util.Map<String, String> codes;

    /** The code of a country as the city list names it in English ("UAE": AE); null when none matches. */
    private static String codeOf(String countryEn) {
        java.util.Map<String, String> m = codes;
        if (m == null) {
            m = new java.util.HashMap<>();
            for (String cc : java.util.Locale.getISOCountries()) m.put(listCountry(cc), cc);
            codes = m;
        }
        return m.get(countryEn);
    }

    static City city(Context c) {
        int i = cityIndex(c);
        return i >= 0 ? CITIES[i] : custom(c);
    }

    /**
     * A place anywhere, by its coordinates and a name the viewer gives it. It has no country (so no iqamah
     * preset) and no method of its own: it keeps the method and Asr school chosen when it was set, which their rows
     * still change.
     */
    private static City custom(Context c) {
        SharedPreferences p = of(c);
        String name = p.getString("custom_name", "");
        double lat = Double.longBitsToDouble(p.getLong("custom_lat", Double.doubleToLongBits(0)));
        double lng = Double.longBitsToDouble(p.getLong("custom_lng", Double.doubleToLongBits(0)));
        Method m = MWL;
        String saved = p.getString("method", null);
        if (saved != null) for (Method x : METHODS) if (methodId(x).equals(saved)) m = x;
        // Picked from the world list: its country (Android's names), so the iqamah preset and the place line know it.
        String cc = p.getString("custom_cc", "");
        return new City(CUSTOM, name, name, countryName(cc, true), countryName(cc, false), lat, lng, m,
            "hanafi".equals(p.getString("madhab", null)) ? Madhab.HANAFI : Madhab.SHAFI);
    }

    /**
     * Sets a place anywhere. With its country (ISO code, from the world list) it takes the method and Asr
     * school that country's mosques follow, where {@link Places#method} knows them; otherwise it keeps today's.
     */
    static void setCustom(Context c, String name, double lat, double lng, String cc) {
        Method known = Places.method(cc);
        Method m = known != null ? known : method(c);
        Madhab h = known != null ? Places.madhab(cc) : madhab(c);
        of(c).edit().putString("city", CUSTOM).putString("custom_name", name).putString("custom_cc", cc == null ? "" : cc)
            .putLong("custom_lat", Double.doubleToLongBits(lat)).putLong("custom_lng", Double.doubleToLongBits(lng))
            .putString("method", methodId(m)).putString("madhab", h == Madhab.HANAFI ? "hanafi" : "shafi")
            .remove(Habous.PREF).apply();   // a new place: Morocco's table found again for it
    }

    /** The region of the other places, after the six of {@link #REGIONS} in the first list. */
    static final int REGION_CUSTOM = 6; // REGIONS.length

    /** A new city also takes that city's usual method and Asr school; their rows can still override them after. */
    static void setCity(Context c, int index) {
        City city = CITIES[index];
        of(c).edit().putString("city", city.id).putString("method", methodId(city.method))
            .putString("madhab", city.madhab == Madhab.HANAFI ? "hanafi" : "shafi").remove(Habous.PREF).apply();
    }

    /**
     * Location's first step: each region's countries (English names, as in {@link #CITIES}), in the order of
     * {@link Text#regions()}. Every country in CITIES must be in exactly one region, or its cities can't be picked.
     */
    static final String[][] REGIONS = {
        {"Egypt", "Saudi Arabia", "UAE", "Kuwait", "Qatar", "Jordan", "Palestine", "Lebanon", "Syria", "Iraq", "Bahrain",
            "Oman", "Yemen"},
        {"India", "Pakistan", "Bangladesh", "Sri Lanka", "Maldives", "Indonesia", "Malaysia", "Singapore", "Brunei",
            "Thailand", "Japan", "South Korea", "Turkey"},
        {"United Kingdom", "France", "Germany", "Netherlands", "Belgium", "Austria", "Switzerland", "Italy", "Spain",
            "Bosnia and Herzegovina"},
        {"United States", "Canada"},
        {"Kenya", "Nigeria", "South Africa"},
        {"Australia", "New Zealand"},
    };

    /** The region (index into {@link #REGIONS}) a city's country is in; -1 if it is in none. */
    static int region(City city) {
        if (CUSTOM.equals(city.id)) return REGION_CUSTOM;
        for (int r = 0; r < REGIONS.length; r++) for (String n : REGIONS[r]) if (n.equals(city.countryEn)) return r;
        return -1;
    }

    static Coordinates where(Context c) {
        City city = city(c);
        return new Coordinates(city.lat, city.lng);
    }

    // ---------------------------------------------------------------- calculation

    /** Settings' list: adhan-java's methods, with Oman's and Morocco's ministries after Qatar. */
    static final Method[] METHODS = {
        Method.EGYPTIAN, Method.UMM_AL_QURA, Method.MUSLIM_WORLD_LEAGUE,
        Method.KARACHI, Method.DUBAI, Method.KUWAIT, Method.QATAR,
        Method.OMAN_AWQAF, Method.MOROCCO_HABOUS,
        Method.NORTH_AMERICA, Method.MOON_SIGHTING_COMMITTEE, Method.SINGAPORE};

    /** What Settings saves for a method (the settings file and the editor show it). */
    static String methodId(Method m) {
        return m.id;
    }

    /** Fresh parameters for a method (the caller sets the Asr school and adjustments on them). */
    static com.batoulapps.adhan.CalculationParameters parameters(Method m) {
        return m.parameters();
    }

    static Method method(Context c) {
        String name = of(c).getString("method", null);
        if (name != null) for (Method m : METHODS) if (methodId(m).equals(name)) return m;
        return city(c).method;
    }

    static void setMethod(Context c, Method m) {
        of(c).edit().putString("method", methodId(m)).apply();
    }

    static Madhab madhab(Context c) {
        String m = of(c).getString("madhab", null);
        // Until set, the (guessed) city's usual school, as setCity would pick.
        if (m == null) return city(c).madhab;
        return "hanafi".equals(m) ? Madhab.HANAFI : Madhab.SHAFI;
    }

    static void setMadhab(Context c, Madhab m) {
        of(c).edit().putString("madhab", m == Madhab.HANAFI ? "hanafi" : "shafi").apply();
    }

    /** Days added to the computed Hijri date, for when the local moon sighting differs: −2 to +2. */
    static int hijriOffset(Context c) {
        return of(c).getInt("hijri_offset", 0);
    }

    static void setHijriOffset(Context c, int days) {
        of(c).edit().putInt("hijri_offset", Math.max(-2, Math.min(2, days))).apply();
    }

    /**
     * Minutes added to Fajr, Sunrise, Dhuhr, Asr, Maghrib and Isha (Settings > Prayer times > Adjust the times and the
     * date, as in Afnon 0.8 and meypod/al-azan): -30 to +30, for a masjid a little off the calculation.
     */
    static int[] adjustments(Context c) {
        SharedPreferences p = of(c);
        int[] a = new int[6];
        for (int i = 0; i < 6; i++) a[i] = p.getInt("adjust_" + i, 0);
        return a;
    }

    static void setAdjustment(Context c, int prayer, int minutes) {
        of(c).edit().putInt("adjust_" + prayer, Math.max(-30, Math.min(30, minutes))).apply();
    }

    // ---------------------------------------------------------------- reminders

    /** Minutes before each prayer to show the reminder; 0 is off. */
    static int remindMinutes(Context c) {
        return of(c).getInt("remind", 15);
    }

    static void setRemindMinutes(Context c, int minutes) {
        of(c).edit().putInt("remind", minutes).apply();
    }

    /** Say «اقترب موعد صلاة …» with the reminder card or banner (Announce, on by default). */
    static boolean remindVoice(Context c) {
        return of(c).getBoolean("remind_voice", true);
    }

    static void setRemindVoice(Context c, boolean on) {
        of(c).edit().putBoolean("remind_voice", on).apply();
    }

    /** Say «حان الآن موعد صلاة …» when a prayer's time begins with its adhan off (Announce, on by default). */
    static boolean announce(Context c) {
        return of(c).getBoolean("announce", true);
    }

    static void setAnnounce(Context c, boolean on) {
        of(c).edit().putBoolean("announce", on).apply();
    }

    /**
     * When the call screen closes by itself: 1, 5 or 10 minutes after it opens ({@link Tokens#MOTION_CALL_AUTO_DISMISS_MS}
     * by default), or 0 when the adhan ends.
     */
    static int callCloseMinutes(Context c) {
        return of(c).getInt("call_close", (int) (Tokens.MOTION_CALL_AUTO_DISMISS_MS / 60_000));
    }

    static void setCallCloseMinutes(Context c, int minutes) {
        of(c).edit().putInt("call_close", minutes).apply();
    }

    /** The adhan as a whole; each prayer's bell (Prayer day) still applies under it. */
    static boolean adhan(Context c) {
        return of(c).getBoolean("adhan", true);
    }

    static void setAdhan(Context c, boolean on) {
        of(c).edit().putBoolean("adhan", on).apply();
    }

    /** Each prayer's own adhan bell (Prayer day); on by default except Sunrise, which has no adhan. */
    static boolean bell(Context c, int prayer) {
        return of(c).getBoolean("bell_" + prayer, prayer != PrayerDay.SUNRISE);
    }

    /** The recording (Adhans id) for every prayer but Fajr, and the one for Fajr, whose adhan has its own line. */
    static String voice(Context c, boolean fajr) {
        return fajr ? of(c).getString("voice_fajr", "chebae_fajr") : of(c).getString("voice", "alafasy");
    }

    static void setVoice(Context c, boolean fajr, String id) {
        of(c).edit().putString(fajr ? "voice_fajr" : "voice", id).apply();
    }

    /** Fajr's adhan level, in percent of the TV's sound (100, 75, 50, 25); 100 by default. */
    static final int[] FAJR_LEVELS = {100, 75, 50, 25};

    static int fajrLevel(Context c) {
        return of(c).getInt("fajr_level", 100);
    }

    static void setFajrLevel(Context c, int percent) {
        of(c).edit().putInt("fajr_level", percent).apply();
    }

    /**
     * The iqamah countdown over other apps, from the call until the iqamah. On by default since 1.14.0 (Omar: off,
     * hardly anyone would find it; on, anyone can turn it off), so a TV that never chose gets it with the update; one
     * turned off stays off. Its first {@link PrayerWatch#HINT_TIMES} times say where it is turned off.
     */
    static boolean iqamaCounter(Context c) {
        return of(c).getBoolean("iqama_counter", true);
    }

    static void setIqamaCounter(Context c, boolean on) {
        of(c).edit().putBoolean("iqama_counter", on).apply();
    }

    /**
     * The last adhan the call screen started, for Settings > About > App status: "prayer:time:voice"
     * of the call, when it started, and whether the recording played (false: it could not be read).
     */
    static void setLastAdhan(Context c, int prayer, long time, String voice, boolean played) {
        of(c).edit().putString("last_adhan", prayer + ":" + time + ":" + voice)
            .putLong("last_adhan_at", System.currentTimeMillis()).putBoolean("last_adhan_ok", played).apply();
    }

    // ---------------------------------------------------------------- radio

    /** After the adhan paused the radio: true resumes it (the default), false leaves it off. */
    static boolean radioResume(Context c) {
        return of(c).getBoolean("radio_resume", true);
    }

    static void setRadioResume(Context c, boolean on) {
        of(c).edit().putBoolean("radio_resume", on).apply();
    }

    /** Minutes of screensaver before the screen goes dark while the radio plays: 0 at once, 15 (the default), -1 never. */
    static int radioScreenOff(Context c) {
        // 15 minutes after the screensaver by default (Afnon: at once), so the screensaver is seen
        // first; "with the screensaver" stays a choice in Settings > Radio.
        return of(c).getInt("radio_dark", 15);
    }

    static void setRadioScreenOff(Context c, int minutes) {
        of(c).edit().putInt("radio_dark", minutes).apply();
    }

    // ---------------------------------------------------------------- display

    /**
     * What Awan TV runs (Settings > Awan TV > Run). Home over the TV's own home screen ({@link HomeGuard})
     * and the screensaver, each can be off; the adhan and reminders keep their own settings whatever is chosen.
     */
    static final int RUN_ALL = 0, RUN_HOME = 1, RUN_SAVER = 2, RUN_OFF = 3;

    static int run(Context c) {
        int r = of(c).getInt("run", -1);
        if (r >= RUN_ALL && r <= RUN_OFF) return r;
        // Not chosen yet: the "guard" switch (Home over the TV's home screen), if it was turned off.
        return of(c).getBoolean("guard", true) ? RUN_ALL : RUN_SAVER;
    }

    static void setRun(Context c, int run) {
        of(c).edit().putInt("run", run).apply();
    }

    /** Home over the TV's own home screen. */
    static boolean guard(Context c) {
        int r = run(c);
        return r == RUN_ALL || r == RUN_HOME;
    }

    /** Awan TV's screensaver (the TV's, or Awan TV's own after the idle minutes), and the adhkar on it. */
    static boolean saver(Context c) {
        int r = run(c);
        return r == RUN_ALL || r == RUN_SAVER;
    }

    /**
     * Minutes without a key on Home before Awan TV opens its screensaver itself, where the TV's own
     * screensaver is not Awan TV's (Google TV without the one-time adb step).
     */
    static int saverIdle(Context c) {
        return of(c).getInt("saver_idle", 10);
    }

    static void setSaverIdle(Context c, int minutes) {
        of(c).edit().putInt("saver_idle", minutes).apply();
    }

    /** A hadith in the screensaver's middle outside the adhkar times (SaverScreen). On by default. */
    static boolean hadith(Context c) {
        return of(c).getBoolean("saver_hadith", true);
    }

    static void setHadith(Context c, boolean on) {
        of(c).edit().putBoolean("saver_hadith", on).apply();
    }

    /** The colour theme (Tokens.LAPIS … Tokens.AUTO); Settings > Display & language. */
    /** The colours; Auto by default since 1.14.1 (Omar: Lapis by day, Charcoal after Maghrib), a choice made kept. */
    static int theme(Context c) {
        int t = of(c).getInt("theme", Tokens.AUTO);
        return t >= Tokens.LAPIS && t <= Tokens.AUTO ? t : Tokens.AUTO;
    }

    static void setTheme(Context c, int t) {
        of(c).edit().putInt("theme", t).apply();
    }

    /** The adhkar after the prayer in the screensaver's middle for ten minutes (Hadith). On by default. */
    static boolean afterPrayer(Context c) {
        return of(c).getBoolean("saver_after_prayer", true);
    }

    static void setAfterPrayer(Context c, boolean on) {
        of(c).edit().putBoolean("saver_after_prayer", on).apply();
    }

    /** The evening reminder of a sunnah fast tomorrow, in the gold line (Hijri.notice). On by default. */
    static boolean fastReminder(Context c) {
        return of(c).getBoolean("fast_reminder", true);
    }

    static void setFastReminder(Context c, boolean on) {
        of(c).edit().putBoolean("fast_reminder", on).apply();
    }

    /** The screensaver dimmed from two hours after Isha until Fajr. On by default. */
    /**
     * Home's background, the pattern ({@link Photos#PATTERN}) or the photos ({@link Photos#PHOTOS}, the default: from
     * the first start Home looks unlike Afnon, whose pattern it otherwise shares).
     */
    static int background(Context c) {
        return of(c).getInt("background", Photos.PHOTOS) == Photos.PATTERN ? Photos.PATTERN : Photos.PHOTOS;
    }

    static void setBackground(Context c, int b) {
        of(c).edit().putInt("background", b).apply();
    }

    /** Photos behind the screensaver (Photos); off by default, so it stays black. */
    /** Photos behind the screensaver; on by default since 1.14.0 (Omar: few found it), off kept where it was turned off. */
    static boolean saverPhotos(Context c) {
        return of(c).getBoolean("saver_photos", true);
    }

    static void setSaverPhotos(Context c, boolean on) {
        of(c).edit().putBoolean("saver_photos", on).apply();
    }

    /**
     * Friday's khutbah (Settings > Prayer times, Jumuah): by the country (Dhuhr unless it sets one
     * time), at Dhuhr, or a fixed time of the viewer's (minutes after midnight, 12:30 to start from).
     */
    static final int FRIDAY_COUNTRY = 0, FRIDAY_DHUHR = 1, FRIDAY_FIXED = 2;

    static int fridayMode(Context c) {
        int m = of(c).getInt("friday_mode", FRIDAY_COUNTRY);
        return m >= FRIDAY_COUNTRY && m <= FRIDAY_FIXED ? m : FRIDAY_COUNTRY;
    }

    static void setFridayMode(Context c, int mode) {
        of(c).edit().putInt("friday_mode", mode).apply();
    }

    static int fridayFixed(Context c) {
        return of(c).getInt("friday_fixed", 12 * 60 + 30);
    }

    static void setFridayFixed(Context c, int minutes) {
        of(c).edit().putInt("friday_fixed", minutes).apply();
    }

    /**
     * Ramadan's imsak, minutes before Fajr (0, 5, 10, 15, 20). Off by default: suhoor ends at Fajr,
     * and no official body found publishes a fixed number (Egypt's Dar al-Ifta: stop just before the Fajr adhan;
     * Dubai's fatwa department: about ten minutes before is a recommended precaution, not an obligation).
     */
    static int imsak(Context c) {
        return of(c).getInt("imsak", 0);
    }

    static void setImsak(Context c, int minutes) {
        of(c).edit().putInt("imsak", minutes).apply();
    }

    /**
     * On an HDMI source box (HomeGuard.hdmiSource), the service takes Home's button and opens Home
     * itself, so Android sends no HDMI-CEC One Touch Play (Settings > Device settings). Off by default: Android's own
     * Home, which also switches the TV to the box's input.
     */
    static boolean homeButton(Context c) {
        return of(c).getBoolean("home_button", false);
    }

    static void setHomeButton(Context c, boolean on) {
        of(c).edit().putBoolean("home_button", on).apply();
    }

    /** How often the background photo changes (Photos.EVERY_*); Automatic by default. */
    static int photoEvery(Context c) {
        int e = of(c).getInt("photo_every", Photos.EVERY_AUTO);
        return e >= Photos.EVERY_AUTO && e <= Photos.EVERY_DAY ? e : Photos.EVERY_AUTO;
    }

    static void setPhotoEvery(Context c, int e) {
        of(c).edit().putInt("photo_every", e).apply();
    }

    static boolean nightDim(Context c) {
        return of(c).getBoolean("saver_night_dim", true);
    }

    static void setNightDim(Context c, boolean on) {
        of(c).edit().putBoolean("saver_night_dim", on).apply();
    }

    /** The weather by the clock: "off", "c" or "f". By default on, in °F where the TV's country uses it. */
    static String weather(Context c) {
        return of(c).getString("weather", fahrenheitCountry() ? "f" : "c");
    }

    static void setWeather(Context c, String v) {
        of(c).edit().putString("weather", v).apply();
    }

    /** The countries that read the temperature in °F, by the TV's time zone. */
    private static boolean fahrenheitCountry() {
        try {
            String region = android.icu.util.TimeZone.getRegion(java.util.TimeZone.getDefault().getID());
            return java.util.Arrays.asList("US", "PR", "GU", "VI", "AS", "MP", "UM", "BS", "BZ", "KY", "PW", "FM", "MH", "LR")
                .contains(region);
        } catch (RuntimeException unknownZone) {
            return false;
        }
    }

    /** "system" (the TV's own Date & time setting, the default), "12" or "24". */
    static String clock(Context c) {
        return of(c).getString("clock", "system");
    }

    static boolean clock24(Context c) {
        String s = clock(c);
        return "system".equals(s) ? DateFormat.is24HourFormat(c) : "24".equals(s);
    }

    /** Everything a built screen depends on; a screen whose key changed while it was away rebuilds. */
    static String key(Context c) {
        SharedPreferences p = of(c);
        return p.getString("locale", "") + "|" + p.getString("digits", "") + "|" + clock24(c) + "|" + city(c).id + "|" + city(c).lat + "," + city(c).lng
            + "|" + city(c).ar
            + "|" + method(c) + "|" + p.getInt(Habous.PREF, 0) + "|" + madhab(c) + "|" + hijriOffset(c) + "|" + java.util.Arrays.toString(adjustments(c))
            + "|" + p.getInt("facing", -1) + "|" + theme(c)
            + "|" + background(c) + "|" + photoEvery(c)
            // Set or still guessed: Home's reminder comes and goes with it (the guessed city may be the one chosen).
            + "|" + citySet(c);
    }
}
