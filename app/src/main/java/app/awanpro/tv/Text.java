package app.awanpro.tv;

import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Madhab;

/** Interface strings for both locales, taken from the `t` tables in design/afnon-mihrab/design/Home.dc.html. */
final class Text {
    final boolean ar;
    /** Eastern Arabic digits (٠١٢٣). Its own setting; defaults to on in Arabic. */
    final boolean eastern;

    Text(boolean ar, boolean eastern) {
        this.ar = ar;
        this.eastern = eastern;
    }

    String d(String s) {
        if (!eastern) return s;
        // "·" beside Eastern digits reads as a zero (٠); Arabic punctuation takes its own comma instead. Awan TV
        // English with Eastern digits takes the Latin comma ("Cairo, Friday", not "Cairo، Friday").
        s = s.replace(" · ", ar ? "، " : ", ");
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c >= '0' && c <= '9' ? (char) ('٠' + (c - '0')) : c);
        }
        return b.toString();
    }

    /** The " · " between parts of a line; a comma where "·" would sit beside Eastern digits. */
    String sep() { return d(" · "); }

    /** The arch verse's reference (An-Nisa 4:103), in the screen's digits like every other number. */
    String ref() { return ar ? d("النساء 103") : d("An-Nisa 4:103"); }
    String nextPrayer() { return ar ? "الصلاة القادمة" : "NEXT PRAYER"; }
    String today() { return ar ? "مواقيت اليوم والقبلة" : "Today & Qibla"; }
    String settings() { return ar ? "الإعدادات" : "Settings"; }
    /** The saved city in this language; set by {@link Screen#loadText}. */
    String cityName = "";
    String city() { return cityName; }
    String fav() { return ar ? "المفضلة" : "FAVOURITES"; }
    String more() { return ar ? "المزيد" : "MORE"; }
    /** Home's tile: the label over the count. */
    String all(int n) { return d((ar ? "كل التطبيقات" : "All apps") + "\n" + n); }
    String ampm(boolean am) { return ar ? (am ? "ص" : "م") : (am ? "AM" : "PM"); }
    String adhanAt() { return ar ? "الأذان" : "Adhan at"; }
    String nowTag() { return ar ? "الآن" : "NOW"; }
    String nextTag() { return ar ? "التالية" : "NEXT"; }
    String jumuahLabel() { return ar ? "صلاة الجمعة" : "JUMU'AH"; }
    String jumuah() { return ar ? "الجمعة" : "Jumu'ah"; }
    String kahf() { return ar ? "سورة الكهف اليوم" : "Surat Al-Kahf today"; }
    String iftarIn() { return ar ? "الإفطار بعد" : "IFTAR IN"; }
    String iftar() { return ar ? "الإفطار" : "Iftar"; }
    String iftarTag() { return ar ? "الإفطار" : "IFTAR"; }
    String suhoorTag() { return ar ? "الإمساك" : "SUHOOR"; }
    String suhoorEnds(String time) { return d(ar ? "السحور حتى " + time : "Suhoor ends " + time); }
    String comingSoon() { return ar ? "هذه الشاشة قادمة في التحديث التالي" : "This screen comes in the next update"; }

    String hintOk(String what) { return "OK  " + what; }
    String hintApps() { return ar ? "◀  التطبيقات" : "▶  Apps"; }
    String hintMore() { return ar ? "▼  المزيد" : "▼  More"; }
    String hintFavs() { return ar ? "▲  المفضلة" : "▲  Favourites"; }
    String open(String app) { return ar ? "فتح " + app : "Open " + app; }
    String network() { return ar ? "إعدادات الشبكة" : "Network settings"; }
    // Home's network button, in its hint.
    String netWired() { return ar ? "متصل بالكابل" : "Connected by cable"; }
    String netOff() { return ar ? "غير متصل" : "Not connected"; }
    String netNoInternet() { return ar ? "متصل بلا إنترنت" : "Connected, no internet"; }

    // Screensaver (design/afnon-mihrab/design/Screensaver.dc.html)
    /**
     * "in 27 min". Arabic counts take their proper forms: دقيقة واحدة، دقيقتين، ٣–١٠ دقائق، ١١+ دقيقة. An hour
     * or more reads as h:mm, as on Prayer day.
     */
    String inMinutes(long minutes, String hoursAndMinutes) {
        if (minutes >= 60) return in(hoursAndMinutes);
        if (!ar) return d("in " + minutes + " min");
        if (minutes <= 1) return "بعد دقيقة واحدة";
        if (minutes == 2) return "بعد دقيقتين";
        return d("بعد " + minutes + (minutes <= 10 ? " دقائق" : " دقيقة"));
    }

    // Reminder and call to prayer (design/afnon-mihrab/design/Home.dc.html approach/now, design/afnon-mihrab/design/ApproachVideo.dc.html)
    String apLabel() { return ar ? "اقتربت الصلاة" : "PRAYER APPROACHING"; }
    /** "Asr in 13 minutes". */
    String apTitle(String prayer, long minutes, String hoursAndMinutes) {
        if (!ar && minutes < 60) return d(prayer + " in " + minutes + (minutes == 1 ? " minute" : " minutes"));
        return prayer + " " + inMinutes(minutes, hoursAndMinutes);
    }
    String apSub(CharSequence time) { return d((ar ? "الأذان " : "Adhan at ") + time + (ar ? " · رجوع للإغلاق" : " · Back to dismiss")); }
    String apOk() { return ar ? "حسنًا" : "Got it"; }
    String bannerSub(CharSequence time, int seconds) {
        return d((ar ? "الأذان " : "Adhan ") + time + (ar ? " · يختفي خلال " + seconds + " ث" : " · hides in " + seconds + " s"));
    }
    String nowLabel() { return ar ? "حان الآن وقت صلاة" : "IT'S TIME FOR"; }
    String play() { return ar ? "تشغيل الأذان" : "Play adhan"; }
    String stop() { return ar ? "إيقاف الأذان" : "Stop adhan"; }
    String dismiss() { return ar ? "إغلاق" : "Dismiss"; }
    // Radio: Home's pill and card, the screen-off view, the screensaver line and the Settings section.
    String radio() { return ar ? "الإذاعة" : "Radio"; }
    String station(Radio.Station s) { return d(ar ? s.ar : s.en); }
    String stationWhere(Radio.Station s) { return d(ar ? s.whereAr : s.whereEn); }
    /** A station in a list: its name and place, since several are called إذاعة القرآن الكريم. */
    String stationChoice(Radio.Station s) { return s.reciter ? station(s) : station(s) + sep() + stationWhere(s); }
    String stationRow() { return ar ? "المحطة" : "Station"; }
    String[] stationGroups() {
        // Awan TV adds the recordings saved on the TV (Library).
        return ar ? new String[] {"إذاعات القرآن الكريم", "القرّاء", "المحفوظة على التلفاز"}
                  : new String[] {"Quran radio stations", "Reciters", "Saved on the TV"};
    }
    String libraryRow() { return ar ? "التلاوات المحفوظة" : "Saved recitations"; }
    String libraryHelp(int recordings, String size) {
        if (recordings == 0) return ar ? "حمّل تلاوات لتُسمع دون إنترنت" : "Save recitations to listen without the internet";
        return d(ar ? recordings + " من القرّاء · " + size : recordings + (recordings == 1 ? " recording · " : " recordings · ") + size);
    }
    String libraryIntro() {
        return ar ? "تُحمَّل من mp3quran.net لتُسمع دون إنترنت، من الإذاعة ← المحطة ← المحفوظة على التلفاز"
                  : "Downloaded from mp3quran.net to play without the internet: Radio > Station > Saved on the TV";
    }
    String reserveNote() { return ar ? d("يُترك 2 جيجابايت فارغة للتلفاز دائمًا") : "2 GB are always left free for the TV"; }
    String savedSize(String s) { return (ar ? "المحفوظ " : "Saved ") + s; }
    String freeSize(String s) { return (ar ? "المتاح " : "Free ") + s; }
    String searchReciter() { return ar ? "ابحث عن قارئ" : "Search for a reciter"; }
    String loadingReciters() { return ar ? "تُحمَّل قائمة القرّاء…" : "Loading the reciters…"; }
    String recitersOffline() {
        return ar ? "تعذّر الوصول إلى mp3quran.net · تحقق من الإنترنت ثم افتح الشاشة مرة أخرى"
                  : "Could not reach mp3quran.net · check the internet and open this screen again";
    }
    String noReciter() { return ar ? "لا قارئ بهذا الاسم" : "No reciter by that name"; }
    String complete() { return ar ? "✓ كامل" : "✓ Complete"; }
    String someOf(int have, int of) { return ar ? have + " من " + of : have + " of " + of; }
    String surahCount(int n) { return ar ? surahsAr(n) : n + (n == 1 ? " surah" : " surahs"); }
    /** "سورة واحدة", "سورتان", "3 سور", "11 سورة", "103 سور": Arabic counts by the last two digits. */
    static String surahsAr(int n) {
        if (n == 1) return "سورة واحدة";
        if (n == 2) return "سورتان";
        int m = n % 100;
        return n + (m >= 3 && m <= 10 ? " سور" : " سورة");
    }
    String noDownloads() {
        return ar ? "التحميل متوقف: مدير التحميل في التلفاز لا يستجيب (قد يكون معطّلًا في إعدادات التطبيقات)"
                  : "Downloads stopped: the TV's download manager does not answer (it may be turned off in Apps)";
    }
    String downloadAll(int n) { return ar ? "تحميل الكل (" + surahCount(n) + ")" : "Download all (" + surahCount(n) + ")"; }
    String downloadRest(int n) { return ar ? "تحميل الباقي (" + surahCount(n) + ")" : "Download the rest (" + surahCount(n) + ")"; }
    String deleteSaved() { return ar ? "حذف المحفوظ" : "Delete saved"; }
    String waitingSpace() {
        return ar ? d("متوقف: المساحة المتاحة أقل من 2 جيجابايت · احذف شيئًا ليكمل")
                  : "Waiting: less than 2 GB free · delete something to go on";
    }
    String noStorage() { return ar ? "لا مساحة تخزين متاحة لهذا التطبيق على التلفاز" : "This TV has no storage for the app"; }
    String downloadingNow(String surah, int pct, int waiting) {
        String p = pct >= 0 ? " " + pct + "٪" : "";
        if (!ar) p = p.replace("٪", "%");
        String then = waiting > 0 ? (ar ? " · ثم " + waiting + " في الانتظار" : " · then " + waiting + " waiting") : "";
        return (ar ? "يُحمَّل الآن: " : "Downloading: ") + surah + p + then;
    }
    String queuedOnly(int waiting) { return ar ? waiting + " في الانتظار بعد تلاوة أخرى" : waiting + " waiting after another recording"; }
    String allSaved() { return ar ? "كل السور محفوظة · تُسمع من الإذاعة" : "Every surah is saved · play it from Radio"; }
    String deleteAsk() { return ar ? "حذف؟" : "Delete?"; }
    String pickReciter() { return ar ? "اختر قارئًا من القائمة" : "Pick a reciter from the list"; }
    String libraryLegend() {
        return ar ? "✓ محفوظة · ↓ في الانتظار · OK على سورة: تحميلها أو إلغاؤها، ومرتين لحذفها"
                  : "✓ saved · ↓ waiting · OK on a surah: download or cancel it, twice to delete it";
    }
    String hintChooseWord() { return ar ? "اختيار" : "Choose"; }
    String hintToSurahs() { return ar ? "◀  إلى السور" : "▶  To the surahs"; }
    String hintBackSettings() { return ar ? "رجوع  الإعدادات" : "Back  Settings"; }
    String recitationsCredit() { return ar ? "التلاوات: mp3quran.net · ملك قرّائها" : "Recitations: mp3quran.net · their reciters'"; }
    String recitationsCreditLong() {
        return ar ? "التلاوات: mp3quran.net (يحق لأي مطور استخدام موادها) · ملك قرّائها"
                  : "Recitations: mp3quran.net (open to any developer) · their reciters'";
    }
    String adhanCredit() {
        return ar ? "التسجيلات: github.com/abodehq/Athan-MP3 و github.com/meypod/audio_files · يُحذف أي تسجيل بطلب صاحب حقه"
                  : "Recordings: github.com/abodehq/Athan-MP3 and github.com/meypod/audio_files · any one removed at its rights holder's request";
    }
    String nothingSaved() { return ar ? "لا تلاوات محفوظة بعد · من الإذاعة ← التلاوات المحفوظة" : "Nothing saved yet · Radio > Saved recitations"; }
    /** The decimal point: the Arabic one (٫) with Eastern digits, a point with Western ones. */
    private String decimal(String v) { return eastern ? v.replace('.', '٫') : v; }
    /** A size in MB or GB, in the screen's digits. */
    String bytes(long b) {
        double gb = b / (1024.0 * 1024 * 1024);
        if (gb >= 1) {
            String v = String.format(java.util.Locale.US, "%.1f", gb);
            return d(ar ? decimal(v) + " جيجابايت" : v + " GB");
        }
        long mb = Math.round(b / (1024.0 * 1024));
        return d(ar ? mb + " ميجابايت" : mb + " MB");
    }
    String radioStatus(int s) {
        switch (s) {
            case Radio.PLAYING: return ar ? "على الهواء" : "On air";
            case Radio.CONNECTING: return ar ? "جارٍ الاتصال…" : "Connecting…";
            case Radio.RETRYING: return ar ? "انقطع البث · نعيد المحاولة" : "Signal lost · retrying";
            case Radio.PAUSED: return ar ? "متوقفة للأذان" : "Paused for the adhan";
            default: return ar ? "متوقفة" : "Off";
        }
    }
    String radioPlay() { return ar ? "تشغيل" : "Play"; }
    String radioStop() { return ar ? "إيقاف" : "Stop"; }
    String screenOff() { return ar ? "إطفاء الشاشة" : "Screen off"; }
    String radioNote() {
        return d(ar ? "تستمر في الرئيسية وشاشة الاستراحة · تتوقف إذا شغّل تطبيق آخر صوتًا"
                    : "Keeps playing on Home and the screensaver · stops when another app plays sound");
    }
    String screenOffNote() {
        return d(ar ? "الشاشة مطفأة والإذاعة تعمل · اضغط أي زر للعودة" : "Screen off, radio playing · press any key to come back");
    }
    String hintClose() { return ar ? "رجوع  إغلاق" : "Back  Close"; }
    String sectionRadio() { return ar ? "الإذاعة" : "Radio"; }
    String radioRowHelp(Radio.Station s) {
        // A saved recording plays without the internet.
        if (s.url == null) return stationWhere(s);
        return stationWhere(s) + (ar ? " · تحتاج إلى الإنترنت" : " · needs the internet");
    }
    String afterAdhanRow() { return ar ? "بعد الأذان" : "After the adhan"; }
    String afterAdhanHelp() { return ar ? "تتوقف الإذاعة أثناء الأذان ثم…" : "The radio pauses for the adhan, then…"; }
    String[] afterAdhanChoices() { return ar ? new String[] {"تُستأنف", "تبقى متوقفة"} : new String[] {"Resumes", "Stays off"}; }
    String radioDarkRow() { return ar ? "إطفاء الشاشة أثناء الاستماع" : "Screen off while listening"; }
    String radioDarkHelp() {
        return d(ar ? "توفير الكهرباء: تنطفئ الشاشة وتستمر الإذاعة · أي زر يعيدها"
                    : "Saves power: the screen goes dark, the radio keeps playing · any key brings it back");
    }
    String[] radioDarkChoices() {
        return ar ? new String[] {"مع شاشة الاستراحة", "بعدها بـ 15 د", "أبدًا"} : new String[] {"With screensaver", "15 min later", "Never"};
    }

    /** The call screen's hint: how it closes by itself (Settings → Reminders); 0 is when the adhan ends. */
    String nowHint(int minutes) {
        if (minutes == 0) return d(ar ? "رجوع للإغلاق · يُغلق تلقائيًا بعد الأذان" : "Back to dismiss · closes by itself after the adhan");
        return d(ar ? "رجوع للإغلاق · يُغلق تلقائيًا بعد " + (minutes == 1 ? "دقيقة" : minutes + " دقائق")
                    : "Back to dismiss · closes by itself after " + minutes + " min");
    }
    String callCloseRow() { return ar ? "إغلاق شاشة الأذان" : "Close the call screen"; }
    String callCloseHelp() { return ar ? "ثم تعود إلى ما كنت تشاهده" : "Then back to what you were watching"; }
    /** In the order of the row's minutes: 0 (after the adhan), 1, 5, 10. */
    String[] callCloseChoices() {
        return ar ? new String[] {"بعد الأذان", "1 د", "5 د", "10 د"} : new String[] {"After adhan", "1 min", "5 min", "10 min"};
    }
    String voiceRow() { return ar ? "صوت الأذان" : "Adhan voice"; }
    String voiceHelp() { return ar ? "لكل الصلوات عدا الفجر · يُسمع عند التنقل في القائمة" : "Every prayer but Fajr · plays as you move through the list"; }
    String fajrVoiceRow() { return ar ? "أذان الفجر" : "Fajr adhan"; }
    String fajrVoiceHelp() { return ar ? "الصوت ثم مستواه · يُفضَّل أذان فيه «الصلاة خير من النوم»" : "The voice, then its level · best with “prayer is better than sleep”"; }

    // All apps (design/afnon-mihrab/design/AllApps.dc.html)
    String allTitle() { return ar ? "كل التطبيقات" : "All apps"; }
    String back() { return ar ? "رجوع" : "Back"; }
    /** "Asr in", followed by the countdown. */
    String chip(String prayer) { return ar ? prayer + " بعد" : prayer + " in"; }
    String favNote(int position) {
        // Six favourites now (Apps.FAVOURITE_SLOTS); the fifth and sixth crashed All apps.
        return ar ? "في المفضلة · " + ordinal(position) : d("In favourites · position " + position);
    }
    /** "In More · 3rd". */
    String moreNote(int position) { return ar ? "في المزيد · " + ordinal(position) : d("In More · position " + position); }
    private String ordinal(int position) {
        String[] ord = {"الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس"};
        return position >= 1 && position <= ord.length ? ord[position - 1] : d(String.valueOf(position));
    }
    String notFav() { return ar ? "اضغط مطولًا على OK لوضعه في الرئيسية" : "Hold OK to put it on Home"; }
    String notOnHome() { return ar ? "ليس في الرئيسية" : "Not on Home"; }
    String moveEarlierMore() { return ar ? "تقديمه في المزيد" : "Move earlier in More"; }
    String moveLaterMore() { return ar ? "تأخيره في المزيد" : "Move later in More"; }
    String moveToMore() { return ar ? "نقله إلى المزيد…" : "Move to More…"; }
    String moveToFav() { return ar ? "نقله إلى المفضلة…" : "Move to favourites…"; }
    String putInFav() { return ar ? "ضعه في المفضلة…" : "Put in favourites…"; }
    String putInMore() { return ar ? "ضعه في المزيد…" : "Put in More…"; }
    String inPlaceOf(String app) { return ar ? "مكان " + app : "Instead of " + app; }
    String chooseFavSlot() { return ar ? "اختر مكانه في المفضلة" : "Pick its place in favourites"; }
    String chooseMoreSlot() { return ar ? "اختر مكانه في المزيد" : "Pick its place in More"; }
    String hintOpen() { return ar ? "OK  فتح" : "OK  Open"; }
    String hintHold() { return ar ? "اضغط مطولًا  ترتيب الرئيسية" : "Hold OK  Arrange Home"; }
    String moveEarlier() { return ar ? "تقديمه في المفضلة" : "Move earlier in favourites"; }
    String moveLater() { return ar ? "تأخيره في المفضلة" : "Move later in favourites"; }
    String cancel() { return ar ? "إلغاء" : "Cancel"; }
    String favourite() { return ar ? "مفضل" : "Favourite"; }

    // Prayer day (design/afnon-mihrab/design/PrayerDay.dc.html)
    String dayTitle() { return ar ? "مواقيت اليوم" : "Today's prayers"; }
    String home() { return ar ? "الرئيسية" : "Home"; }
    String school(Madhab m) {
        if (m == Madhab.HANAFI) return ar ? "العصر: الحنفي" : "Asr: Hanafi";
        return ar ? "العصر: الجمهور (شافعي)" : "Asr: standard (Shafi‘i)";
    }

    String methodName(Method m) {
        if (m == Method.UMM_AL_QURA) return ar ? "أم القرى، مكة المكرمة" : "Umm al-Qura, Makkah";
        if (m == Method.MUSLIM_WORLD_LEAGUE) return ar ? "رابطة العالم الإسلامي" : "Muslim World League";
        if (m == Method.KARACHI) return ar ? "جامعة العلوم الإسلامية، كراتشي" : "University of Islamic Sciences, Karachi";
        if (m == Method.DUBAI) return ar ? "دبي" : "Dubai";
        if (m == Method.KUWAIT) return ar ? "الكويت" : "Kuwait";
        if (m == Method.QATAR) return ar ? "قطر" : "Qatar";
        if (m == Method.NORTH_AMERICA) return ar ? "الجمعية الإسلامية لأمريكا الشمالية" : "ISNA, North America";
        if (m == Method.MOON_SIGHTING_COMMITTEE) return ar ? "لجنة رؤية الهلال" : "Moonsighting Committee";
        if (m == Method.SINGAPORE) return ar ? "سنغافورة" : "Singapore";
        if (m == Method.OMAN_AWQAF) return ar ? "وزارة الأوقاف والشؤون الدينية، سلطنة عُمان" : "Ministry of Awqaf, Oman";
        if (m == Method.MOROCCO_HABOUS) return ar ? "وزارة الأوقاف والشؤون الإسلامية، المملكة المغربية" : "Ministry of Habous, Morocco";
        return ar ? "الهيئة المصرية العامة للمساحة" : "Egyptian General Authority of Survey";
    }

    String methodAngles(Method m) {
        CalculationParameters p = Prefs.parameters(m);
        String fajr = (ar ? "زاوية الفجر " : "Fajr angle ") + trim(p.fajrAngle) + "°";
        String isha = p.ishaInterval > 0
            ? (ar ? "العشاء بعد المغرب بـ " + p.ishaInterval + " دقيقة" : "Isha " + p.ishaInterval + " min after Maghrib")
            : (ar ? "العشاء " : "Isha ") + trim(p.ishaAngle) + "°";
        return d(fajr + " · " + isha);
    }

    /** Morocco's method under its name: in Morocco by the ministry's tables, elsewhere at its coastal horizon. */
    String habousAngles(boolean table) {
        return d(table ? (ar ? "الفجر 19° · العشاء 17° · كما تحسب الوزارة جداولها" : "Fajr 19° · Isha 17° · as the ministry computes its tables")
                       : (ar ? "خارج المغرب: الفجر 19° · العشاء 17° · باحتياط الوزارة" : "Outside Morocco: Fajr 19° · Isha 17° · with the ministry's margins"));
    }

    /** Settings > Prayer times > Ministry table (Morocco): which of the ministry's 191 timetables. */
    String habousRow() { return ar ? "جدول الوزارة" : "Ministry table"; }
    String habousHelp(int km, boolean chosen) {
        return d(chosen ? (ar ? "اخترته أنت، على بعد " + km + " كم من موقعك" : "Chosen by you, " + km + " km from your location")
                        : (ar ? "أقرب مدن الوزارة إلى موقعك، على بعد " + km + " كم" : "The ministry's nearest locality, " + km + " km away"));
    }
    String habousChooser() { return ar ? "الوزارة تنشر جدولًا لكل مدينة من 191 مدينة ومركزًا. هذه أقربها إلى موقعك." : "The ministry publishes a table for each of 191 localities. These are the nearest to your location."; }
    String habousItem(String name, int km) { return d(name + "  ·  " + km + (ar ? " كم" : " km")); }
    String habousAuto() { return ar ? "تلقائي: الأقرب إلى الموقع" : "Automatic: the nearest"; }
    String habousFileHelp() { return ar ? "أي جداول وزارة الأوقاف المغربية تتبعه الشاشة في المغرب" : "Which of the Moroccan ministry's tables the TV follows in Morocco"; }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    // Settings (design/afnon-mihrab/design/Settings.dc.html)
    String sectionPrayer() { return ar ? "مواقيت الصلاة" : "Prayer times"; }
    String sectionReminders() { return ar ? "التنبيهات والأذان" : "Reminders & adhan"; }
    String sectionDisplay() { return ar ? "العرض واللغة" : "Display & language"; }
    String sectionNetwork(String ssid) { return ssid == null ? (ar ? "الشبكة" : "Network") : (ar ? "الشبكة · " : "Network · ") + ssid; }
    String sectionDevice() { return ar ? "إعدادات الجهاز" : "Device settings"; }
    String preview() { return ar ? "العصر اليوم بهذه الإعدادات" : "Asr today with these settings"; }
    String location() { return ar ? "الموقع" : "Location"; }
    // The first run's location and Home's reminder while none is set.
    String chooseLocation() { return ar ? "اختر موقعك" : "Choose your location"; }
    String otherLocation() { return ar ? "موقع آخر" : "Another location"; }
    String locating() { return ar ? "جارٍ تحديد الموقع…" : "Finding your location…"; }
    String near(String place) { return ar ? "قرب " + place : "Near " + place; }
    String locationAskTitle() { return ar ? "لم تحدد موقعك بعد" : "Your location isn't set yet"; }
    String locationAskBody(String guess) {
        return ar ? "المواقيت الآن لموقع «" + guess + "» حسب المنطقة الزمنية للتلفاز، وقد تختلف عن موقعك. والأذان يُرفع عليها."
                  : "The times are now for “" + guess + "”, by the TV's time zone, and may differ from your location. The adhan is called on them.";
    }
    String setLocation() { return ar ? "تحديد الموقع" : "Set location"; }
    String later() { return ar ? "لاحقًا" : "Later"; }
    String hintBackLater() { return ar ? "رجوع  لاحقًا" : "Back  Later"; }
    String hintBackLocate() { return ar ? "رجوع  تحديد الموقع" : "Back  Set location"; }
    String noLocation() { return ar ? "لم يُحدَّد موقعك بعد" : "Your location isn't set yet"; }
    String setYourLocation() { return ar ? "حدّد موقعك" : "Set your location"; }
    String country() { return ar ? "الدولة" : "Country"; }
    String region() { return ar ? "المنطقة" : "Region"; }
    /** Location's first step, in the order of Prefs.REGIONS. */
    String[] regions() {
        return ar ? new String[] {"الدول العربية", "آسيا", "أوروبا", "الأمريكتان", "أفريقيا", "أوقيانوسيا"}
            : new String[] {"Arab world", "Asia", "Europe", "Americas", "Africa", "Oceania"};
    }
    String locationHelp(double lat, double lng, boolean byCoordinates) {
        return coordinates(lat, lng) + (byCoordinates ? (ar ? " · بالإحداثيات" : " · by coordinates")
                                                      : (ar ? " · يُختار من القائمة" : " · chosen from the list"));
    }
    /** "21.42° N · 39.83° E" in the screen's digits. */
    String coordinates(double lat, double lng) {
        String ns = lat >= 0 ? (ar ? "ش" : "N") : (ar ? "ج" : "S");
        String ew = lng >= 0 ? (ar ? "ق" : "E") : (ar ? "غ" : "W");
        return d(String.format(java.util.Locale.US, "%.2f° %s · %.2f° %s", Math.abs(lat), ns, Math.abs(lng), ew));
    }
    String place(String city, String country) { return city + (ar ? "، " : ", ") + country; }
    String method() { return ar ? "طريقة الحساب" : "Calculation method"; }
    String asrSchool() { return ar ? "مذهب العصر" : "Asr school"; }
    String asrSchoolHelp() { return ar ? "يغيّر وقت العصر فقط" : "Only changes the Asr time"; }
    String[] asrSchools() { return ar ? new String[] {"الجمهور", "الحنفي"} : new String[] {"Standard", "Hanafi"}; }
    String hijriAdjust() { return ar ? "تعديل التاريخ الهجري" : "Hijri date adjustment"; }
    /** About > Sources and licences (SettingsActivity.creditRows). */
    String creditsRow() { return ar ? "المصادر والتراخيص" : "Sources and licences"; }
    String creditsHelp() { return ar ? "أفنون، والطقس، والمدن، والتلاوات، والأذان" : "Afnon, the weather, the cities, the recitations, the adhan"; }
    String creditsSection() { return ar ? "حول التطبيق ← المصادر والتراخيص" : "About › Sources and licences"; }

    /** What's new (NewsActivity): after an update, and from About. */
    String newsRow() { return ar ? "ما الجديد" : "What's new"; }
    String newsRowHelp() { return ar ? "ما أضافه كل إصدار" : "What each version added"; }
    String newsAll() { return ar ? "ما الجديد" : "What's new"; }
    String newsTitle(String version) { return ar ? "الجديد في Awan TV " + version : "New in Awan TV " + version; }
    String newsAlso(String version) { return ar ? "ومن " + version : "And from " + version; }
    String newsLater() { return ar ? "تجده لاحقًا في الإعدادات ← حول التطبيق ← ما الجديد." : "Find it later in Settings › About › What's new."; }
    String newsOk() { return ar ? "حسنًا" : "OK"; }
    /** Each version's lines: {name, where it is or what it does}; NewsActivity.VERSIONS lists the versions. */
    String[][] news(String version) {
        switch (version) {
            case "1.14.2":
                return ar ? new String[][] {
                    {"أسماء التلاوات المحفوظة لا تضيع", "بعد حذف التطبيق وإعادة تثبيته تعود التلاوات المحفوظة بأسماء قرّائها ومصاحفها، ولو دون إنترنت."}}
                  : new String[][] {
                    {"Saved recitations keep their names", "After the app is removed and installed again, saved recitations come back with their reciters' and riwayahs' names, even without internet."}};
            case "1.14.1":
                return ar ? new String[][] {
                    {"الألوان تتبع الوقت", "لازوردي في النهار وفحمي بعد المغرب، أخف على العين في الليل. ولون واحد طوال اليوم من الإعدادات ← العرض واللغة ← ألوان الواجهة."}}
                  : new String[][] {
                    {"Colours that follow the time", "Lapis by day and Charcoal after Maghrib, easier on the eyes at night. One colour all day in Settings › Display & language › Colours."}};
            case "1.14.0":
                return ar ? new String[][] {
                    {"عدّاد الإقامة فوق التطبيقات", "من الأذان حتى الإقامة تظهر بطاقة صغيرة في زاوية الشاشة فوق أي تطبيق: كم بقي على الإقامة. تُطفأ من الإعدادات ← الإقامة."},
                    {"صور في شاشة الاستراحة", "صورة كل عشر دقائق خلف المواقيت، والداكنة فقط من العشاء حتى الفجر. تُطفأ من الإعدادات ← العرض واللغة."}}
                  : new String[][] {
                    {"The iqamah countdown over apps", "From the adhan until the iqamah, a small card in a corner over any app shows how long is left. Turn it off in Settings › Iqamah."},
                    {"Photos in the screensaver", "A photo every ten minutes behind the times, only dark ones from Isha until Fajr. Turn them off in Settings › Display & language."}};
            case "1.13.0":
                return ar ? new String[][] {
                    {"شاشة أمان إن توقف", "إن توقف Awan TV على غير المتوقع تظهر شاشة تعيد تشغيله أو تفتح شاشة التلفاز الأصلية. وإن تكرر التوقف يترك التلفاز يعمل بشاشته نصف ساعة، فلا يتعطل."},
                    {"إصلاح الشاشة من الجوال أو الحاسب", "حدّد شاشة محدّثة في «شاشاتي» بالمثبّت أو في «تحديث الشاشات» بالمحرر، فيُعاد إعدادها دون إرسال التطبيق من جديد."}}
                  : new String[][] {
                    {"A safety screen if it stops", "If Awan TV stops unexpectedly, a screen restarts it or opens the TV's own home. If it stops again, the TV runs on its own home for half an hour, so it stays usable."},
                    {"Put a TV right from the phone or the computer", "Tick an up-to-date TV in the installer's My TVs or the editor's Update TVs, and it is set up again without sending the app anew."}};
            case "1.10.0":
                return ar ? new String[][] {
                    {"الإذاعة على شاشاتك من الحاسب", "من محرر الإعدادات: ترى ما تبثه كل شاشة الآن، وتشغّل الإذاعة أو توقفها على شاشة أو على كل الشاشات. ولا تُقطع شاشة يُسمع عليها شيء آخر إلا إن اخترت ذلك."}}
                  : new String[][] {
                    {"The radio on your TVs from the computer", "From the settings editor: see what each TV is playing now, and start or stop the radio on one TV or on all of them. A TV playing something else is not cut off unless you choose to."}};
            case "1.9.0":
                return ar ? new String[][] {
                    {"التلاوات والمحطة من الحاسب", "من محرر الإعدادات على Windows: اختر تلاوات تحمّلها شاشة أو أكثر بنفسها، وغيّر المحطة، دون الذهاب إلى كل شاشة."}}
                  : new String[][] {
                    {"Recitations and the station from the computer", "From the settings editor on Windows: choose recitations for one or more TVs to download by themselves, and change the station, without going to each TV."}};
            case "1.8.0":
                return ar ? new String[][] {
                    {"مواقيت المغرب كما تنشرها الوزارة", "طريقة وزارة الأوقاف والشؤون الإسلامية المغربية بجدول كل مدينة من مدنها الـ 191، وتختار الأقرب إلى موقعك من الإعدادات ← مواقيت الصلاة ← جدول الوزارة."}}
                  : new String[][] {
                    {"Morocco's times as the ministry publishes them", "The Moroccan Ministry of Habous' method, with the table of each of its 191 localities; pick the one near you in Settings › Prayer times › Ministry table."}};
            case "1.7.0":
                return ar ? new String[][] {
                    {"التحديث بضغطة", "حين يصدر إصدار أحدث، اضغط «حدّث الآن» في نافذته، فينزّله التطبيق ويثبّته على الشاشة نفسها، دون جوال ولا حاسب."}}
                  : new String[][] {
                    {"Update with one press", "When a newer version is out, press Update now in its window: the app downloads it and installs it on the TV itself, with no phone or computer."}};
            case "1.5.0":
                return ar ? new String[][] {
                    {"تنبيه بالإصدار الجديد", "حين يصدر إصدار أحدث تخبرك الرئيسية به برمز تمسحه بالجوال، وتبقى نقطة ذهبية على زر الإعدادات حتى تحدّث."}}
                  : new String[][] {
                    {"Told of a new version", "When a newer version is out, Home tells you with a code to scan with a phone, and a gold mark stays on the Settings button until you update."}};
            case "1.4.0":
                return ar ? new String[][] {
                    {"المحطة من الرئيسية", "في بطاقة الإذاعة زر «المحطة»: بدّل الإذاعة أو التلاوة دون أن تفتح الإعدادات."},
                    {"الضغطة المطوّلة", "لكل زر خصصته في أزرار الريموت ضغطة مطوّلة تفتح شيئًا آخر."},
                    {"صوتان للأذان", "أذان المسجد النبوي، وأذان الشيخ راغب مصطفى غلوش، في الإعدادات ← الأذان."}}
                  : new String[][] {
                    {"The station from Home", "The radio card has a Station button: change the station or recitation without opening Settings."},
                    {"Hold a button", "Each button you customised in Remote buttons can open something else when held."},
                    {"Two adhan voices", "The adhan of Al-Masjid an-Nabawi, and Sheikh Ragheb Mustafa Ghalwash's, in Settings › Adhan."}};
            case "1.3.0":
                return ar ? new String[][] {
                    {"طريقة وزارة الأوقاف العُمانية", "مواقيت سلطنة عُمان بطريقة وزارة الأوقاف والشؤون الدينية، تُختار تلقائيًا لمدنها."},
                    {"ثلاث إذاعات", "إذاعة القرآن الكريم من سلطنة عُمان، وإذاعة القرآن الكريم ونداء الإسلام من السعودية، في الإعدادات ← الإذاعة."},
                    {"المثبّت على الجوال", "يخبرك بالمثبّت الأحدث، ويقول لماذا لم يصل إلى الشاشة وماذا تفعل."}}
                  : new String[][] {
                    {"Oman's Ministry of Awqaf method", "Oman's prayer times by its Ministry of Awqaf, chosen for its cities by themselves."},
                    {"Three radio stations", "Oman's Holy Quran Radio, and Saudi Arabia's Quran Radio and Nida Al-Islam, in Settings › Radio."},
                    {"The phone installer", "It tells you of a newer installer, and says why it couldn't reach the TV and what to do."}};
            case "1.2.0":
                return ar ? new String[][] {
                    {"تعديل المواقيت بالدقائق", d("الإعدادات ← مواقيت الصلاة ← تعديل المواقيت والتاريخ: كل وقت حتى 30 دقيقة قبله أو بعده، ليطابق مسجدك.")},
                    {"الإصدار الجديد برمز QR", "حين يصدر إصدار أحدث، يفتح «حول التطبيق» نافذة برمز تمسحه بالجوال لتصل إلى صفحته."},
                    {"ما الجديد", "هذه النافذة، مرة بعد كل تحديث، وتجدها في «حول التطبيق»."}}
                  : new String[][] {
                    {"Adjust the times by minutes", d("Settings › Prayer times › Adjust the times and the date: each time up to 30 minutes earlier or later, to match your masjid.")},
                    {"A new version's QR code", "When a newer version is out, About opens a window with a code to scan with a phone to reach its page."},
                    {"What's new", "This window, once after each update, and in About."}};
            case "1.1.0":
                return ar ? new String[][] {
                    {"أزرار الريموت", "الإعدادات ← إعدادات الجهاز ← أزرار الريموت: اجعل زرًا في أي ريموت يفتح ما تختار."}}
                  : new String[][] {
                    {"Remote buttons", "Settings › Device settings › Remote buttons: make a button on any remote open what you choose."}};
            default:
                return new String[0][];
        }
    }

    /** The new version's window (SettingsActivity.openUpdate). */
    String updateTitle(String version) { return ar ? "إصدار جديد: Awan TV " + version : "New version: Awan TV " + version; }
    String updateHave(String version) { return ar ? "على هذه الشاشة " + version + "." : "This TV has " + version + "."; }
    String updateHow() {
        return ar ? "امسح الرمز بكاميرا الجوال لتفتح صفحة التحميل، ثم حدّث بالمثبّت على الجوال أو من حاسب Windows. الإعدادات والتلاوات المحفوظة تبقى."
                  : "Scan the code with a phone's camera to open the download page, then update with the phone installer or from a Windows computer. The settings and saved recitations stay.";
    }
    /** Over Home (UpdateNotice): why to update, when it comes again, and the version-only mute. */
    String updateWhy() {
        return ar ? "يوجد إصدار أحدث للتطبيق من الذي تستخدمه حاليًا. حدّث التطبيق لتحصل على الميزات الجديدة والإصلاحات، ونوصي بالتحديث دائمًا إلى أحدث إصدار متوفر."
                  : "There is a newer version of the app than the one you are using. Update it to get the new features and fixes; we recommend always updating to the latest version available.";
    }
    String updateAgain() {
        return ar ? "نذكّرك كل أسبوع حتى تحدّث، وتجده في الإعدادات ← حول التطبيق." : "We'll remind you every week until you update; it is also in Settings › About.";
    }
    String updateMute() { return ar ? "لا تذكّرني بهذا الإصدار" : "Don't remind me of this version"; }
    /** Update now (SelfUpdate), over Home and in Settings > About's window. */
    String updateNow() { return ar ? "حدّث الآن" : "Update now"; }
    String updateLater() { return ar ? "لاحقًا" : "Later"; }
    String updateRetry() { return ar ? "حاول مرة أخرى" : "Try again"; }
    String updateHowSelf() {
        return ar ? "«حدّث الآن» ينزّل الإصدار ويثبّته على هذه الشاشة، والإعدادات والتلاوات المحفوظة تبقى. أو امسح الرمز بالجوال لتفتح صفحة التحميل."
                  : "Update now downloads the version and installs it on this TV; the settings and saved recitations stay. Or scan the code with a phone to open the download page.";
    }
    /** Without the install appop (installed by hand): what Update now needs. */
    String updateManual() {
        return ar ? "للاستفادة من كل ميزات التطبيق، ومنها التحديث بضغطة من الشاشة، ثبّته بالمثبّت على الجوال أو من حاسب Windows، وهما الطريقتان 1 و2 في صفحة التحميل."
                  : "To get every feature of the app, updating with one press on the TV among them, install it with the phone installer or from a Windows computer, ways 1 and 2 on the download page.";
    }
    String updateDownloading(String version, int percent) {
        return d((ar ? "جارٍ تنزيل الإصدار " : "Downloading version ") + version + (ar ? "… " : "… ") + percent + (ar ? "٪" : "%"));
    }
    String updateLeave() { return ar ? "يمكنك ترك هذه النافذة، ويكمل التنزيل." : "You can leave this window; the download carries on."; }
    String updateThen() {
        return ar ? "بعده يسألك Android «هل تريد تحديث هذا التطبيق؟»: انتقل إلى «تحديث» واضغط OK، وتعود الشاشة الرئيسية الجديدة خلال ثوانٍ."
                  : "Then Android asks whether to update this app: move to Update and press OK, and the new Home comes back within seconds.";
    }
    String updateInstalling() { return ar ? "جارٍ التثبيت…" : "Installing…"; }
    String updateContinue() { return ar ? "متابعة التثبيت" : "Continue installing"; }
    String updateConfirm() {
        return ar ? "إن سألك Android «هل تريد تحديث هذا التطبيق؟»، فانتقل إلى «تحديث» واضغط OK. ثم تعود الشاشة الرئيسية الجديدة."
                  : "If Android asks whether to update this app, move to Update and press OK. The new Home then comes back.";
    }
    String updateFailed() {
        return ar ? "تعذّر التحديث. تأكد من اتصال الشاشة بالإنترنت ثم حاول مرة أخرى، أو امسح الرمز وحدّث من صفحة التحميل."
                  : "The update didn't go through. Check the TV's internet connection and try again, or scan the code and update from the download page.";
    }
    String updateOpen() { return ar ? "فتح صفحة الإصدار" : "Open the release page"; }
    String hintBackClose() { return ar ? "رجوع  إغلاق" : "Back  Close"; }
    /** Prayer times > Adjust the times and the date (Prefs.adjustments, Prefs.hijriOffset). */
    String adjustRow() { return ar ? "تعديل المواقيت والتاريخ" : "Adjust the times and the date"; }
    String adjustRowHelp() { return d(ar ? "كل وقت حتى 30 دقيقة قبله أو بعده، والتاريخ الهجري" : "Each time up to 30 minutes earlier or later, and the Hijri date"); }
    String adjustSection() { return ar ? "مواقيت الصلاة ← تعديل المواقيت والتاريخ" : "Prayer times › Adjust the times and the date"; }
    String hintBackPrayer() { return ar ? "رجوع  مواقيت الصلاة" : "Back  Prayer times"; }
    /** The row's value: what is adjusted ("الفجر +2، المغرب +1"), the first two and "…" after; "بلا تعديل" for none. */
    String adjustSummary(int[] minutes, int hijriDays) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        String[] names = prayerNames();
        for (int i = 0; i < minutes.length; i++) if (minutes[i] != 0) parts.add(names[i] + " " + minutesSigned(minutes[i]));
        if (hijriDays != 0) parts.add((ar ? "الهجري " : "Hijri ") + minutesSigned(hijriDays));
        if (parts.isEmpty()) return ar ? "بلا تعديل" : "None";
        String s = parts.get(0) + (parts.size() > 1 ? (ar ? "، " : ", ") + parts.get(1) : "");
        return parts.size() > 2 ? s + " …" : s;
    }
    /** A time's adjustment between its − and + buttons: "+2", "0", "-3" (minutes). */
    String minutesSigned(int n) {
        // Left-to-right embedding: in an Arabic line the sign would otherwise land after the number ("2+").
        return "\u202A" + d(n > 0 ? "+" + n : String.valueOf(n)) + "\u202C";
    }
    /** Under each time's name: what it is with the adjustment (Fajr is tomorrow's after Isha), and how far it goes. */
    String adjustTimeHelp(String time, boolean tomorrow) {
        return d(ar ? (tomorrow ? "غدًا " : "اليوم ") + time + " · بالدقائق، حتى 30 قبله أو بعده"
                    : (tomorrow ? "Tomorrow " : "Today ") + time + " · in minutes, up to 30 earlier or later");
    }
    String adjustNote() {
        return ar ? "لمن يتبع مسجدًا يختلف قليلًا عن الحساب. الأذان والإقامة والتنبيهات وشاشة الاستراحة كلها تتبع الوقت بعد التعديل."
                  : "For a masjid a little off the calculation. The adhan, the iqamah, the reminders and the screensaver all follow the adjusted time.";
    }
    String adjustFileLabel(String prayer) { return ar ? "تعديل " + prayer + " بالدقائق" : prayer + " adjustment (minutes)"; }
    String adjustFileHelp() { return ar ? "دقائق تُضاف إلى الوقت المحسوب، وبالسالب قبله" : "Minutes added to the calculated time; negative is earlier"; }
    String hijriToday(String date) { return (ar ? "اليوم: " : "Today: ") + date; }
    String days(int n) {
        // No adjustment reads as such, not "0 يوم".
        if (n == 0) return ar ? "بلا تعديل" : "None";
        String sign = n > 0 ? "+" : "";
        if (!ar) return d(sign + n + (Math.abs(n) == 1 ? " day" : " days"));
        return d(sign + n + (Math.abs(n) == 2 ? " يومان" : " يوم"));
    }
    String roomRow() { return roomTitle(); }
    String roomHelp() { return ar ? "لمعرفة القبلة بالنسبة لمكان جلوسك" : "So the Qibla is shown from where you sit"; }
    String notSet() { return ar ? "لم يُحدَّد" : "Not set"; }
    String remind() { return ar ? "التنبيه قبل الصلاة" : "Remind me before prayer"; }
    String remindHelp() { return ar ? "على الرئيسية وفوق الفيديو" : "On Home and over video"; }
    String[] remindChoices() {
        return ar ? new String[] {"5 د", "10 د", "15 د", "20 د", "30 د", "إيقاف"}
                  : new String[] {"5 min", "10 min", "15 min", "20 min", "30 min", "Off"};
    }
    String remindVoiceRow() { return ar ? "تذكير صوتي" : "Spoken reminder"; }
    String remindVoiceHelp() { return ar ? "«اقترب موعد صلاة …» مع التذكير" : "Says «اقترب موعد صلاة …» with the reminder"; }
    String announceRow() { return ar ? "إعلان دخول الوقت" : "Announce the time"; }
    String announceHelp() {
        return ar ? "«حان الآن موعد صلاة …» للصلاة التي أذانها متوقف" : "Says «حان الآن موعد صلاة …» when a prayer's adhan is off";
    }
    String adhanRow() { return ar ? "الأذان" : "Adhan"; }
    String adhanHelp() { return ar ? "يُشغَّل عند دخول الوقت · يمكن إيقافه لكل صلاة" : "Plays when the time begins · can be muted per prayer"; }
    String language() { return ar ? "اللغة" : "Language"; }
    String languageHelp() { return ar ? "«التلفاز» يتبع لغة التلفاز" : "“TV” follows the TV's language"; }
    String[] languages() { return new String[] {ar ? "التلفاز" : "TV", "العربية", "English"}; }
    String digits() { return ar ? "الأرقام" : "Digits"; }
    String clockRow() { return ar ? "الساعة" : "Clock"; }
    String clockHelp() { return ar ? "«التلفاز» يتبع إعداد الوقت في التلفاز" : "“TV” follows the TV's time setting"; }
    String[] clocks() { return ar ? new String[] {"التلفاز", "12 س", "24 س"} : new String[] {"TV", "12 h", "24 h"}; }
    String networkRow() { return ar ? "إعدادات الشبكة في التلفاز" : "TV network settings"; }
    String networkHelp() { return ar ? "واي فاي وإيثرنت" : "Wi-Fi and Ethernet"; }
    String deviceRow() { return ar ? "إعدادات التلفاز" : "TV settings"; }
    String deviceHelp() { return ar ? "الصورة والصوت والتطبيقات والنظام" : "Picture, sound, apps and system"; }
    /** Home's button on an HDMI source box (Prefs.homeButton). */
    String homeButtonRow() { return ar ? "زر Home يفتح Awan TV مباشرة" : "Home button opens Awan TV directly"; }
    String homeButtonHelp() {
        return ar ? "فعّله إن صمت مكبّر الصوت بعد Home · الضغط المطوّل: واجهة الجهاز"
                  : "On if the soundbar goes silent after Home · hold it: the box's own home";
    }
    /** Settings > Device settings > Remote buttons (Buttons). */
    String buttonsRow() { return ar ? "أزرار الريموت" : "Remote buttons"; }
    String buttonsHelp(int n) {
        if (n == 0) return ar ? "اجعل زرًا في أي ريموت يفتح ما تختار" : "Make a button on any remote open what you choose";
        if (!ar) return d(n == 1 ? "1 button customised" : n + " buttons customised");
        if (n == 1) return "زر واحد مخصص";
        if (n == 2) return "زران مخصصان";
        return d(n <= 10 ? n + " أزرار مخصصة" : n + " زرًا مخصصًا");
    }
    String buttonsSection() { return ar ? "إعدادات الجهاز ← أزرار الريموت" : "Device settings › Remote buttons"; }
    String hintBackDevice() { return ar ? "رجوع  إعدادات الجهاز" : "Back  Device settings"; }
    String buttonFrom(String remote) {
        if (remote == null || remote.isEmpty()) return "";
        return ar ? "من «" + remote + "»" : "From \u201C" + remote + "\u201D";
    }
    String addButtonRow() { return ar ? "إضافة زر" : "Add a button"; }
    String addButtonHelp() { return ar ? "اضغط بعدها أي زر في أي ريموت متصل بالشاشة" : "Then press any button on any remote connected to the TV"; }
    String buttonsInfoRow() { return ar ? "عن تخصيص الأزرار" : "About custom buttons"; }
    String buttonsInfoHelp() {
        return ar ? "ما دام زر واحد مخصصًا تمر ضغطات الريموت كلها عبر Awan TV، فالضغط المطوّل على الصوت يرفعه درجة واحدة. ولا تُخصَّص هذه الأزرار: الأسهم، OK، رجوع، Home، الصوت، التشغيل."
                  : "While one button is customised every remote press passes through Awan TV, so holding Volume moves it one step. These buttons are never customised: the arrows, OK, Back, Home, Volume, Power.";
    }
    String buttonsInfoHelpTcl() {
        return ar ? "على هذه الشاشة تُخصَّص الأزرار بنظام TCL نفسه، فلا تمر ضغطات الريموت عبر Awan TV ويبقى زر الصوت كما هو. ولا تُخصَّص هذه الأزرار: الأسهم، OK، رجوع، Home، الصوت، التشغيل."
                  : "On this TV the buttons are customised in TCL's own remapping, so remote presses do not pass through Awan TV and Volume is as it was. These buttons are never customised: the arrows, OK, Back, Home, Volume, Power.";
    }
    String buttonsServiceOff() { return ar ? "خدمة Awan TV متوقفة: لا تعمل الأزرار المخصصة حتى تشغّلها" : "The Awan TV service is off: custom buttons work once it is on"; }
    String buttonOpens(String name) { return ar ? name + " يفتح" : name + " opens"; }
    /** A custom button's press and hold (Buttons.Entry.longAction). */
    String buttonPressIs(String action) { return (ar ? "ضغطة: " : "Press: ") + action; }
    String buttonLongIs(String action) { return (ar ? "ضغطة مطوّلة: " : "Hold: ") + action; }
    String buttonLongNone() { return ar ? "بلا ضغطة مطوّلة" : "No hold"; }
    String buttonLongNote() { return ar ? "الضغطة المطوّلة نصف ثانية أو أكثر" : "A hold is half a second or more"; }
    String buttonLongShort(String action) { return (ar ? "مطوّلًا: " : "Held: ") + action; }
    String buttonLongTitle(String name) { return ar ? name + " مطوّلًا" : name + ", held"; }
    String buttonOriginal() { return ar ? "عمله الأصلي (بلا تخصيص)" : "What it always did (not customised)"; }
    String buttonNone() { return ar ? "لا شيء (يعطّل الزر)" : "Nothing (turns the button off)"; }
    String buttonOff() { return ar ? "لا شيء (معطّل)" : "Nothing (off)"; }
    String buttonAwan(String screen) { return "Awan TV · " + screen; }
    String buttonRadio() { return ar ? "الإذاعة (تشغيل وإيقاف)" : "Radio (play and stop)"; }
    String pressTitle() { return ar ? "اضغط الزر الذي تريد تخصيصه" : "Press the button to customise"; }
    String pressHelp() { return ar ? "في الريموت الأصلي أو أي ريموت بلوتوث متصل بالشاشة." : "On the TV's own remote or any Bluetooth remote connected to it."; }
    String pressWaiting(int seconds) {
        if (!ar) return d("Waiting " + seconds + (seconds == 1 ? " second" : " seconds"));
        if (seconds == 1) return "ينتظر ثانية واحدة";
        if (seconds == 2) return "ينتظر ثانيتين";
        return d("ينتظر " + seconds + " ثوانٍ");
    }
    String noneTitle() { return ar ? "لم يصل أي زر" : "No button came through"; }
    String noneBody() { return d(ar ? "مرّت 10 ثوانٍ دون أن يصل شيء إلى Awan TV." : "10 seconds went by and nothing reached Awan TV."); }
    String noneWhy() {
        return ar ? "بعض الأزرار يأخذها التلفاز قبل أي تطبيق، فلا يمكن تخصيصها هنا. جرّب زرًا آخر."
                  : "The TV takes some buttons before any app sees them, so they cannot be customised here. Try another one.";
    }
    String tryAgain() { return ar ? "حاول مرة أخرى" : "Try again"; }
    String close() { return ar ? "إغلاق" : "Close"; }

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
    String lastCrashRow() { return ar ? "آخر توقف للتطبيق" : "Last time the app stopped"; }
    String lastCrashHelp(String when, String what) { return d(when) + sep() + what; }
    String noCrash() { return ar ? "لا توقف مسجّل" : "No stop recorded"; }
    String warnTitle() { return ar ? "قبل أول تخصيص" : "Before the first button"; }
    String warnBody() {
        return ar ? "لكي يعرف Awan TV الزر الذي ضغطته، تمر ضغطات الريموت كلها عبره ما دام زر واحد مخصصًا."
                  : "So that Awan TV knows which button you pressed, every remote press passes through it while one button is customised.";
    }
    String warnVolume() {
        return ar ? "على أغلب الشاشات لا تشعر بفرق. لكن الضغط المطوّل على زر الصوت يرفعه درجة واحدة فقط، فاضغطه مرات متتالية."
                  : "On most TVs you feel no difference. But holding Volume moves it only one step, so press it several times instead.";
    }
    String warnUndo() { return ar ? "إن حذفت كل الأزرار المخصصة عاد الريموت كما كان." : "Remove every custom button and the remote is as it was."; }
    String hintMove() { return ar ? "▲▼  تنقل" : "▲▼  Move"; }
    String hintChange() { return ar ? "◀▶  تغيير القيمة" : "◀▶  Change value"; }
    String hintChoose() { return ar ? "OK  اختيار" : "OK  Choose"; }
    String hintBackMenu() { return ar ? "رجوع  القائمة" : "Back  Menu"; }
    String hintEdit() { return ar ? "OK  تعديل" : "OK  Edit"; }
    String hintSwitch() { return ar ? "OK  تشغيل/إيقاف" : "OK  On/Off"; }
    String hintSave() { return ar ? "OK  حفظ" : "OK  Save"; }
    String hintBackCancel() { return ar ? "رجوع  إلغاء" : "Back  Cancel"; }
    String saved() { return ar ? "✓  تم الحفظ" : "✓  Saved"; }
    String qibla() { return ar ? "القبلة" : "QIBLA"; }
    String north() { return ar ? "الشمال ▲" : "▲ North"; }
    String qiblaHint() { return ar ? "وجّه الشمال على البوصلة نحو الشمال الحقيقي" : "Line up the compass north with true north"; }
    String bellToggle() { return ar ? "تشغيل/إيقاف الأذان لهذه الصلاة" : "Adhan on/off for this prayer"; }
    String hintBackHome() { return ar ? "رجوع  الرئيسية" : "Back  Home"; }
    String bell(boolean on) { return ar ? (on ? "الأذان مفعّل" : "الأذان متوقف") : (on ? "Adhan on" : "Adhan off"); }
    String rowNow() { return ar ? "الآن" : "now"; }
    /** Sunrise is masculine in Arabic (الشروق مضى); the prayers are feminine (الصلاة مضت). */
    String passed(boolean sunrise) { return ar ? (sunrise ? "مضى" : "مضت") : "passed"; }
    String in(String duration) { return d(ar ? "بعد " + duration : "in " + duration); }
    /** "الفجر بعد" / "Fajr in": the screensaver's line above its countdown. */
    String nextIn(String prayer) { return ar ? prayer + " بعد" : prayer + " in"; }

    /** "South-east · from Cairo" for a bearing in degrees from true north. */
    String qiblaDirection(double bearing) {
        return octant(bearing) + (ar ? " · من " : " · from ") + city();
    }

    String octant(double bearing) {
        String[] names = ar ? new String[] {"شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب"}
                            : new String[] {"North", "North-east", "East", "South-east", "South", "South-west", "West", "North-west"};
        return names[sector(bearing)];
    }

    /**
     * Where the Qibla is for someone facing the TV, from its angle clockwise from straight ahead. Right and left
     * are the viewer's own, so they never mirror with the layout.
     */
    String qiblaRelative(double clockwise) {
        String[] where = ar
            ? new String[] {"أمامك", "أمامك إلى اليمين", "على يمينك", "خلفك إلى اليمين", "خلفك", "خلفك إلى اليسار", "على يسارك", "أمامك إلى اليسار"}
            : new String[] {"ahead of you", "ahead, to your right", "to your right", "behind, to your right", "behind you",
                            "behind, to your left", "to your left", "ahead, to your left"};
        return (ar ? "القبلة " : "Qibla is ") + where[sector(clockwise)];
    }

    /** "North-west · 320°". */
    String bearingLabel(int degrees) { return d(octant(degrees) + " · " + degrees + "°"); }

    String facingLine(int facing) { return (ar ? "تجلس مواجهًا: " : "You face: ") + bearingLabel(facing); }
    String tvMarker() { return ar ? "التلفاز ▲" : "▲ TV"; }
    String roomButton(boolean set) { return ar ? (set ? "تغيير اتجاه الغرفة" : "حدّد اتجاه الغرفة") : (set ? "Change room direction" : "Set room direction"); }
    String roomTitle() { return ar ? "اتجاه الغرفة" : "Room direction"; }
    String roomTip() {
        return ar ? "قف مواجهًا للتلفاز، وافتح تطبيق البوصلة في هاتفك وأمسكه مستويًا أمامك، ثم اضبط الرقم هنا على ما تقرؤه. يكفي ذلك مرة واحدة ما دام التلفاز في مكانه."
                  : "Stand facing the TV, open the compass app on your phone and hold it flat in front of you, then set the number here to what it reads. Once is enough while the TV stays where it is.";
    }
    String roomKeys() { return ar ? "◀ ▶  ٥°     ▲ ▼  ٤٥°     OK  حفظ     رجوع  إلغاء" : "◀ ▶  5°     ▲ ▼  45°     OK  Save     Back  Cancel"; }
    String hintQibla() { return ar ? "◀  القبلة" : "▶  Qibla"; }
    String hintTimes() { return ar ? "▶  المواقيت" : "◀  Times"; }

    private static int sector(double degrees) {
        return (int) Math.round(((degrees % 360) + 360) % 360 / 45) % 8;
    }

    /** Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha. */
    String[] prayerNames() {
        return ar ? new String[] {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"}
                  : new String[] {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    }

    String[] hijriMonths() {
        return ar ? new String[] {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
                                  "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"}
                  : new String[] {"Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula",
                                  "Jumada al-Akhirah", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qadah",
                                  "Dhu al-Hijjah"};
    }

    String hijriSuffix() { return ar ? " هـ" : " AH"; }

    // ----------------------------------------------------------------

    // Iqamah (Settings, screensaver)
    String sectionIqama() { return ar ? "الإقامة" : "Iqamah"; }
    String iqama() { return ar ? "الإقامة" : "Iqamah"; }
    String iqamaAt(CharSequence time) { return (ar ? "الإقامة " : "Iqamah ") + time; }
    String noIqama() { return ar ? "لا إقامة" : "No iqamah"; }
    String khutbah() { return ar ? "الخطبة" : "Khutbah"; }
    // Friday's khutbah and Ramadan's imsak (Jumuah; Settings > Prayer times; the screensaver)
    String khutbahAt(CharSequence time) { return (ar ? "الخطبة " : "Khutbah ") + time; }
    String khutbahIn() { return ar ? "الخطبة بعد" : "Khutbah in"; }
    String suhoorEndsIn() { return ar ? "السحور ينتهي بعد" : "Suhoor ends in"; }
    String fridayLine() { return ar ? "سورة الكهف · الصلاة على النبي ﷺ" : "Surat Al-Kahf · Salawat on the Prophet ﷺ"; }
    String fridayEveLine() { return ar ? "ليلة الجمعة · سورة الكهف · الصلاة على النبي ﷺ" : "Friday night · Al-Kahf · Salawat ﷺ"; }
    String kahfTonight() { return ar ? "ليلة الجمعة · سورة الكهف" : "Friday night · Surat Al-Kahf"; }
    String ramadanDay(int day) { return d(ar ? "اليوم " + day + " من رمضان" : "Day " + day + " of Ramadan"); }
    String imsakAt(CharSequence time) { return (ar ? "الإمساك " : "Imsak ") + time; }
    String fajrAt(CharSequence time) { return (ar ? "الفجر " : "Fajr ") + time; }
    String fridayRow() { return ar ? "صلاة الجمعة" : "Friday prayer"; }
    String[] fridayChoices() { return ar ? new String[] {"حسب الدولة", "عند الظهر", "موعد ثابت"} : new String[] {"By country", "At Dhuhr", "Fixed time"}; }
    /** preset: the country's own time ("12:45 PM"), or null when the country's khutbah is at Dhuhr. */
    String fridayHelp(int mode, CharSequence preset, String country) {
        if (mode == Prefs.FRIDAY_FIXED) return ar ? "موعد الخطبة أدناه" : "The khutbah's time below";
        if (mode == Prefs.FRIDAY_DHUHR || preset == null) {
            return mode == Prefs.FRIDAY_DHUHR ? (ar ? "الخطبة عند أذان الظهر" : "The khutbah at the Dhuhr adhan")
                                              : (ar ? "حسب الدولة: الخطبة عند أذان الظهر" : "By country: the khutbah at the Dhuhr adhan");
        }
        return ar ? "حسب الدولة: " + country + " " + preset + " · الهيئة العامة للشؤون الإسلامية"
                  : "By country: " + country + " " + preset + " · the Islamic Affairs authority";
    }
    String fridayTimeRow() { return ar ? "موعد الخطبة" : "Khutbah time"; }
    String fridayTimeHelp() { return ar ? "كل جمعة · العد التنازلي يوم الجمعة إليه" : "Every Friday · Friday's countdown is to it"; }
    String imsakRow() { return ar ? "الإمساك في رمضان" : "Imsak in Ramadan"; }
    String[] imsakChoices() { return ar ? new String[] {"بلا", "5 د", "10 د", "15 د", "20 د"} : new String[] {"None", "5 min", "10 min", "15 min", "20 min"}; }
    String imsakHelp() { return ar ? "السحور ينتهي عند الفجر · الإمساك احتياط قبله" : "Suhoor ends at Fajr · imsak is a precaution before it"; }
    String iqamaRow() { return ar ? "وقت الإقامة" : "Iqamah time"; }
    String[] iqamaModes() {
        return ar ? new String[] {"إيقاف", "الدولة", "دقائق", "وقت ثابت"} : new String[] {"Off", "Country", "Minutes", "Fixed"};
    }
    /** The mode row's help line: what the mode does, or why Country is not on offer. */
    String iqamaModeHelp(int mode, String country, boolean hasPreset) {
        switch (mode) {
            case Iqama.COUNTRY: return ar ? "المعتمد رسميًا في " + country : "The official wait in " + country;
            case Iqama.MINUTES: return ar ? "دقائق بعد الأذان، لكل صلاة" : "Minutes after the adhan, per prayer";
            case Iqama.FIXED: return ar ? "وقت ثابت لكل صلاة، كما في مسجدك" : "A set time per prayer, as at your mosque";
            default:
                if (hasPreset) return ar ? "لا يظهر وقت الإقامة" : "No iqamah shown";
                return ar ? "لا معيار رسمي مسجَّل لهذه الدولة بعد" : "No official standard for this country yet";
        }
    }
    /** "25 minutes", with the Arabic count forms. */
    String minutesAfter(int n) {
        if (!ar) return d(n + " min");
        if (n == 0) return "مع الأذان";
        if (n == 1) return "دقيقة";
        if (n == 2) return "دقيقتان";
        return d(n + (n <= 10 ? " دقائق" : " دقيقة"));
    }
    String adhanToday(CharSequence time) { return (ar ? "الأذان اليوم " : "Adhan today ") + time; }
    String iqamaToday(CharSequence time) { return (ar ? "الإقامة اليوم " : "Iqamah today ") + time; }
    String fridayNote() { return ar ? "وفي الجمعة الخطبة بدل الإقامة" : "On Fridays the khutbah instead"; }

    // Settings > Awan TV: what runs (Prefs.run), the screensaver, the TV's own home screen, removing the app
    String sectionRun() { return ar ? "تشغيل Awan TV" : "Awan TV"; }
    String runRow() { return ar ? "يعمل" : "Runs"; }
    String[] runModes() {
        return ar ? new String[] {"الكل", "الرئيسية", "الاستراحة", "إيقاف"} : new String[] {"All", "Home", "Screensaver", "Off"};
    }
    /** The run row's help: what the choice runs, or what it still needs. */
    String runHelp(int run, boolean serviceOn) {
        if (!serviceOn && (run == Prefs.RUN_ALL || run == Prefs.RUN_HOME)) {
            return ar ? "الرئيسية تحتاج تشغيل خدمة Awan TV (إمكانية الوصول، أدناه)"
                      : "Home needs the Awan TV service on (Accessibility, below)";
        }
        switch (run) {
            case Prefs.RUN_ALL: return ar ? "الرئيسية فوق شاشة التلفاز، وشاشة الاستراحة بالمواقيت والأذكار"
                                          : "Home over the TV's own, and the screensaver with times and adhkar";
            case Prefs.RUN_HOME: return ar ? "الرئيسية فقط · شاشة الاستراحة والأذكار متوقفة"
                                           : "Home only · no screensaver or adhkar";
            case Prefs.RUN_SAVER: return ar ? "شاشة الاستراحة فقط · رئيسية التلفاز كما هي"
                                            : "Screensaver only · the TV's own home screen";
            default: return ar ? "متوقف · الأذان والتذكير حسب إعداداتهما"
                               : "Off · the adhan and reminders keep their own settings";
        }
    }
    String saverIdleRow() { return ar ? "شاشة الاستراحة بعد" : "Screensaver after"; }
    String[] saverIdleChoices() {
        return ar ? new String[] {"5 دقائق", "10 دقائق", "15 دقيقة", "30 دقيقة"} : new String[] {"5 min", "10 min", "15 min", "30 min"};
    }
    /** ours: the TV's screensaver is Awan TV's (set over adb), so the TV starts it after its own timeout. */
    String saverIdleHelp(boolean ours) {
        return ours ? (ar ? "يبدؤها التلفاز بنفسه حسب مهلته · هذا للرئيسية فقط" : "The TV starts it after its own timeout · this is for Home")
                    : (ar ? "على الرئيسية دون ضغط زر · ثم تنطفئ حسب إعداد التلفاز" : "On Home with no key pressed · then off as the TV is set");
    }
    String stockRow() { return ar ? "الشاشة الرئيسية الأصلية" : "The TV's own home screen"; }
    String stockHelp() { return ar ? "تبقى حتى تفتح تطبيقًا آخر" : "Stays until you open another app"; }
    String removeRow() { return ar ? "إزالة Awan TV" : "Remove Awan TV"; }
    String removeHelp() {
        return ar ? "يعيد شاشة الاستراحة السابقة، ثم يحذف التطبيق" : "Puts the old screensaver back, then uninstalls";
    }
    String removeRecitationsTitle() { return ar ? "حذف التلاوات المحفوظة أيضًا؟" : "Delete the saved recitations too?"; }
    String removeRecitationsSub(String size) {
        return ar ? size + " في Music/Awan TV · تبقى للتثبيت القادم إن أبقيتها" : size + " in Music/Awan TV · kept for the next install if you keep them";
    }
    String[] removeRecitationsChoices() {
        return ar ? new String[] {"أبقِها", "احذفها"} : new String[] {"Keep them", "Delete them"};
    }
    String[] removeChoices() { return ar ? new String[] {"إزالة الآن", "إلغاء"} : new String[] {"Remove now", "Cancel"}; }
    String accessibilityRow() { return ar ? "إعدادات إمكانية الوصول" : "Accessibility settings"; }
    String accessibilityHelp() { return ar ? "لتشغيل خدمة Awan TV: الشاشة الرئيسية والأذان" : "To turn on the Awan TV service: Home and the adhan"; }

    // Adhkar (screensaver, Settings > Reminders)
    String morningAdhkar() { return ar ? "أذكار الصباح" : "Morning adhkar"; }
    String eveningAdhkar() { return ar ? "أذكار المساء" : "Evening adhkar"; }
    String adhkarRow() { return ar ? "أذكار الصباح والمساء" : "Morning and evening adhkar"; }
    String[] adhkarModes() { return ar ? new String[] {"إيقاف", "ممتد", "الأفضل"} : new String[] {"Off", "Extended", "Preferred"}; }
    String adhkarHelp(int mode) {
        switch (mode) {
            case Adhkar.EXTENDED: return ar ? "من الفجر إلى الظهر، ومن العصر إلى العشاء" : "On the screensaver: Fajr to Dhuhr, Asr to Isha";
            case Adhkar.PREFERRED: return ar ? "من الفجر إلى الشروق، ومن العصر إلى المغرب" : "On the screensaver: Fajr to sunrise, Asr to Maghrib";
            default: return ar ? "لا تظهر على شاشة الاستراحة" : "No adhkar on the screensaver";
        }
    }

    // Weather by the clock (Weather; Settings > Display)
    String weatherRow() { return ar ? "الطقس" : "Weather"; }
    String[] weatherChoices() { return ar ? new String[] {"إيقاف", "°م", "°ف"} : new String[] {"Off", "°C", "°F"}; }
    String weatherHelp(boolean on) {
        return on ? (ar ? "بجانب الساعة · من Open-Meteo كل 30 دقيقة، ويختفي دون إنترنت"
                        : "By the clock · from Open-Meteo every 30 minutes, hidden when offline")
                  : (ar ? "لا يتصل التطبيق لأجل الطقس" : "No connection for the weather");
    }
    String weatherCredit() { return ar ? "بيانات الطقس: Open-Meteo.com (ترخيص CC BY 4.0)" : "Weather data: Open-Meteo.com (CC BY 4.0)"; }
    /** The sky's name, for screen readers. */
    String sky(int sky) {
        String[] ar = {"صافٍ", "غائم جزئيًا", "غائم", "ضباب", "رذاذ", "مطر", "ثلج", "عاصفة رعدية"};
        String[] en = {"Clear", "Partly cloudy", "Cloudy", "Fog", "Drizzle", "Rain", "Snow", "Thunderstorm"};
        return (this.ar ? ar : en)[Math.max(0, Math.min(7, sky))];
    }

    // Another place, by coordinates (Settings > Location, PlaceActivity)
    String otherPlace() { return ar ? "أي مكان في العالم (بحث أو إحداثيات)" : "Any place in the world (search or coordinates)"; }
    String placeTitle() { return ar ? "أي مكان في العالم" : "Any place in the world"; }
    String placeIntro() {
        return ar ? "ابحث بالاسم بالعربية أو الإنجليزية، ثم احفظ · أو اكتب الإحداثيات مباشرة لمكان أصغر"
                  : "Search by name in Arabic or English, then save · or type the coordinates for a smaller place";
    }
    String searchCity() { return ar ? "ابحث عن مدينة" : "Search for a city"; }
    String loadingCities() { return ar ? "تُحمَّل قائمة المدن…" : "Loading the cities…"; }
    String noCity() {
        return ar ? "لا نتائج · جرّب الاسم بالإنجليزية، أو اكتب الإحداثيات في المكان المختار"
                  : "No results · try another spelling, or type the coordinates";
    }
    /** "4.7 million", "195 thousand": a city's size in the results. */
    String population(long n) {
        if (n >= 1_000_000) {
            String m = String.format(java.util.Locale.US, "%.1f", n / 1_000_000.0).replace(".0", "");
            return d(ar ? decimal(m) + " مليون" : m + " million");
        }
        long k = Math.max(1, Math.round(n / 1000.0));
        return d(ar ? k + " ألف" : k + " thousand");
    }
    String chosenPlace() { return ar ? "المكان المختار" : "Chosen place"; }
    String placeName() { return ar ? "اسم المكان (يمكنك تعديله)" : "Place name (you can change it)"; }
    String latitude() { return ar ? "خط العرض" : "Latitude"; }
    String longitude() { return ar ? "خط الطول" : "Longitude"; }
    /** The method a place gets: its country's, or the one set now when its country's is not known. */
    String placeMethod(String method, String country) {
        if (country == null) return ar ? "طريقة الحساب: " + method + " · راجعها بعد الحفظ"
                                       : "Method: " + method + " · check it after saving";
        // A ministry's name already says its country (Oman, Morocco): the line stays one line.
        if (method.contains(country)) return (ar ? "طريقة الحساب: " : "Method: ") + method;
        return ar ? "طريقة الحساب: " + method + " · المتبعة في " + country : "Method: " + method + " · as in " + country;
    }
    String placeIqama(String country) {
        return ar ? "والإقامة حسب المعيار الرسمي لـ" + country : "Iqamah: the official wait in " + country;
    }
    String hintPick() { return ar ? "OK  اختيار المدينة" : "OK  Pick the city"; }
    String hintToChosen() { return ar ? "◀  إلى المكان المختار" : "▶  To the chosen place"; }
    String hintCancel() { return ar ? "رجوع  إلغاء" : "Back  Cancel"; }
    String citiesCredit() { return ar ? "المدن: GeoNames.org" : "Cities: GeoNames.org"; }
    String habousCreditRow() { return ar ? "مواقيت المغرب" : "Morocco's times"; }
    String habousCredit() { return ar ? "مستخرجة من جداول وزارة الأوقاف والشؤون الإسلامية المغربية (habous.gov.ma)" : "Fitted to the timetables of Morocco's Ministry of Habous and Islamic Affairs (habous.gov.ma)"; }
    String citiesCreditLong() { return ar ? "قائمة المدن: GeoNames.org (ترخيص CC BY 4.0)" : "City list: GeoNames.org (CC BY 4.0)"; }
    String placeSave() { return ar ? "حفظ" : "Save"; }
    String placeCancel() { return ar ? "إلغاء" : "Cancel"; }
    String placeBad() { return ar ? "تحقق من الأرقام: خط العرض بين −90 و90، وخط الطول بين −180 و180" : "Check the numbers: latitude −90 to 90, longitude −180 to 180"; }
    String placeNoName() { return ar ? "اكتب اسمًا للمكان" : "Give the place a name"; }
    String placeAfter() {
        return ar ? "ثم اختر طريقة الحساب المتبعة في مساجد المكان" : "Then pick the calculation method of the place's mosques";
    }

    // Screensaver now (Settings > Awan TV, and Home's button)
    String saverNowRow() { return ar ? "شاشة الاستراحة الآن" : "Screensaver now"; }
    String saverNowHelp() { return ar ? "تظهر فورًا · أي زر يغلقها" : "Shows at once · any key closes it"; }

    // Iqamah in Ramadan (Settings > Iqamah)
    String iqamaPeriodRow() { return ar ? "الإقامة في" : "Iqamah for"; }
    String[] iqamaPeriods() { return ar ? new String[] {"الأيام العادية", "رمضان"} : new String[] {"Other days", "Ramadan"}; }
    String iqamaPeriodHelp(boolean ramadan) {
        return ramadan ? (ar ? "دقائق بعد الأذان في رمضان · «كالمعتاد» يتبع بقية الأيام" : "Minutes after the adhan in Ramadan · “As usual” follows the other days")
                       : (ar ? "لرمضان ضبط مستقل" : "Ramadan has its own setting");
    }
    String asUsual() { return ar ? "كالمعتاد" : "As usual"; }

    // About
    String sectionAbout() { return ar ? "حول التطبيق" : "About"; }
    String version(String v) { return d((ar ? "الإصدار " : "Version ") + v); }
    String byAwan() { return ar ? "تطوير عمر أحمد الحويرث" : "Developed by OMAR A ALHWAIRTH"; }
    String sourceRow() { return ar ? "المصدر والتحديثات" : "Source and updates"; }
    String newVersion(String v) { return d((ar ? "إصدار جديد متاح: " : "New version out: ") + v); }
    String qrHint() { return ar ? "امسح الرمز بكاميرا هاتفك" : "Scan a code with your phone's camera"; }
    String siteTitle() { return "AwanPro"; }
    String checkNowRow() { return ar ? "ابحث عن تحديث الآن" : "Check for updates now"; }
    String checkNowHelp() {
        return ar ? "يسأل GitHub الآن، دون انتظار سؤاله اليومي" : "Asks GitHub now, without waiting for its daily question";
    }
    String checking() { return ar ? "جارٍ البحث…" : "Checking…"; }
    String upToDate() { return ar ? "لديك أحدث إصدار" : "You have the latest version"; }
    String checkFailed() { return ar ? "تعذّر الوصول إلى GitHub" : "Couldn't reach GitHub"; }
    String basedOn() { return ar ? "مبني على تطبيقي أفنون وMasjid TV" : "Built on Afnon and Masjid TV"; }
    String mitLicence() { return ar ? "(ترخيص MIT)" : "(MIT licence)"; }

    // Colour themes (Settings > Display & language), in Tokens' order
    String themeRow() { return ar ? "ألوان الواجهة" : "Colours"; }
    String themeHelp() {
        return ar ? "للرئيسية والإعدادات · «تلقائي»: لازوردي نهارًا وفحمي بعد المغرب"
                  : "Home and Settings · Automatic: lapis by day, charcoal after Maghrib";
    }
    String[] themes() {
        return ar ? new String[] {"لازوردي", "زمردي", "عنّابي", "فحمي", "رقّ", "تلقائي"}
                  : new String[] {"Lapis", "Emerald", "Burgundy", "Charcoal", "Parchment", "Automatic"};
    }

    // Home's month (Hijri) and the coming occasion (Home's arch, the screensaver's Hijri line)
    String calendarSource() { return ar ? "تقويم أم القرى" : "Umm al-Qura"; }
    /** Sunday first, as Umm al-Qura's printed calendar. */
    String[] weekdaysShort() {
        return ar ? new String[] {"أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت"}
                  : new String[] {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    }

    /** "Ramadan in 23 days", "Eid al-Fitr tomorrow", "Eid Mubarak" (Hijri.RAMADAN, FITR, ARAFAH, ADHA). */
    String occasion(int kind, int days) {
        if (days == 0) {
            if (ar) return new String[] {"رمضان مبارك", "عيد فطر مبارك", "اليوم يوم عرفة", "عيد أضحى مبارك"}[kind];
            return new String[] {"Ramadan Mubarak", "Eid Mubarak", "The Day of Arafah", "Eid Mubarak"}[kind];
        }
        String name = ar ? new String[] {"رمضان", "عيد الفطر", "يوم عرفة", "عيد الأضحى"}[kind]
                         : new String[] {"Ramadan", "Eid al-Fitr", "The Day of Arafah", "Eid al-Adha"}[kind];
        if (!ar) return d(days == 1 ? name + " tomorrow" : name + " in " + days + " days");
        if (days == 1) return name + " غدًا";
        if (days == 2) return name + " بعد يومين";
        return d(name + " بعد " + days + (days <= 10 ? " أيام" : " يومًا"));
    }

    /** Tomorrow's sunnah fast (Hijri.MONDAY, THURSDAY, WHITE, TASUA, ASHURA). */
    String fastTomorrow(int kind) {
        return ar ? new String[] {"غدًا صيام الاثنين", "غدًا صيام الخميس", "غدًا من الأيام البيض", "غدًا صيام تاسوعاء", "غدًا صيام عاشوراء"}[kind]
                  : new String[] {"Tomorrow: the Monday fast", "Tomorrow: the Thursday fast", "Tomorrow: one of the White Days",
                                  "Tomorrow: the fast of Tasu'a", "Tomorrow: the fast of Ashura"}[kind];
    }
    String fastRow() { return ar ? "تذكير الصيام المسنون" : "Sunnah fast reminder"; }
    String fastHelp() {
        return ar ? "مساء اليوم السابق: الاثنين والخميس والأيام البيض وتاسوعاء وعاشوراء"
                  : "The evening before: Mondays, Thursdays, the White Days, Tasu'a and Ashura";
    }
    String middleRow() { return ar ? "وسط شاشة الاستراحة" : "Screensaver's middle"; }
    String middleHelp() {
        return d(ar ? "حديث يتغير كل 10 دقائق، وأذكار بعد الصلاة 10 دقائق بعد كل صلاة"
                    : "A hadith every 10 min, and the adhkar for 10 min after each prayer");
    }
    String[] middleChoices() {
        return ar ? new String[] {"الاثنان", "الحديث", "الأذكار", "لا شيء"} : new String[] {"Both", "Hadith", "Adhkar", "Nothing"};
    }
    String afterPrayerLabel() { return ar ? "أذكار بعد الصلاة" : "Adhkar after the prayer"; }
    String afterAdhanLabel() { return ar ? "الدعاء بعد الأذان" : "Supplication after the adhan"; }
    String midnight() { return ar ? "منتصف الليل" : "Midnight"; }
    String lastThird() { return ar ? "الثلث الأخير" : "Last third"; }

    // The screensaver's hadith (assets/hadith/hadith.json)
    String hadithSource(int bukhari, int muslim) {
        StringBuilder b = new StringBuilder();
        if (bukhari > 0) b.append(ar ? "البخاري " : "Al-Bukhari ").append(bukhari);
        if (muslim > 0) b.append(b.length() > 0 ? " · " : "").append(ar ? "مسلم " : "Muslim ").append(muslim);
        return d(b.toString());
    }
    // Background photos (Photos)
    String backgroundRow() { return ar ? "خلفية الرئيسية" : "Home background"; }
    String backgroundHelp() { return ar ? "الصور: من الحرمين والأقصى والمساجد والطبيعة، تحت لون الواجهة" : "Photos: the Haramain, al-Aqsa, mosques and nature, under the theme's colour"; }
    String[] backgrounds() { return ar ? new String[] {"النقش", "صور"} : new String[] {"Pattern", "Photos"}; }
    String saverPhotosRow() { return ar ? "صور في الاستراحة" : "Screensaver photos"; }
    String saverPhotosHelp() { return ar ? "والداكنة فقط من العشاء حتى الفجر" : "Only the dark ones from Isha until Fajr"; }
    String photoEveryRow() { return ar ? "تغيير الصورة" : "Photo change"; }
    String photoEveryHelp() { return ar ? "للرئيسية والاستراحة · «تلقائي»: الرئيسية كل يوم، والاستراحة كل 10 دقائق" : "Home and the screensaver · Automatic: Home daily, the screensaver every 10 minutes"; }
    String[] photoEveryChoices() {
        return ar ? new String[] {"تلقائي", "كل 10 دقائق", "كل نصف ساعة", "كل ساعة", "مع كل صلاة", "كل يوم"}
                  : new String[] {"Automatic", "Every 10 minutes", "Every half hour", "Every hour", "With each prayer", "Every day"};
    }
    String nightDimRow() { return ar ? "تخفيف الإضاءة ليلًا" : "Dim at night"; }
    String nightDimHelp() { return ar ? "شاشة الاستراحة بعد العشاء بساعتين حتى الفجر" : "The screensaver, from two hours after Isha until Fajr"; }

    // Fajr's adhan level, the iqamah countdown over other apps, App status
    String percent(int n) { return d(ar ? n + "٪" : n + "%"); }
    String fullLevel() { return ar ? "كامل (كبقية الأذان)" : "Full (as the other adhans)"; }
    String fajrLevelTitle() { return ar ? "مستوى أذان الفجر" : "Fajr adhan level"; }
    String fajrLevelHelp() { return ar ? "نسبة من صوت التلفاز · يُسمع وأنت تتنقل" : "A share of the TV's sound · heard as you move"; }
    String iqamaCounterRow() { return ar ? "عدّاد الإقامة فوق التطبيقات" : "Iqamah countdown over apps"; }
    /** Under the countdown card its first times (PrayerWatch.HINT_TIMES): where it is turned off. */
    String iqamaCounterOff() { return ar ? "تُطفأ من الإعدادات ← الإقامة" : "Turn it off in Settings › Iqamah"; }
    // The settings file's last part (SettingsFile.write, 1.14.0).
    String buttonsFileSection() { return ar ? "أزرار الريموت (لهذه الشاشة وحدها)" : "Remote buttons (this TV only)"; }
    String buttonsFileNote() {
        return ar ? "تعود إلى هذه الشاشة وحدها، وتُتجاهل إن أُرسل الملف إلى شاشة أخرى، فلكل ريموت أرقامه."
            : "They come back to this TV only and are passed over when the file goes to another TV: each remote has its own codes.";
    }
    String iqamaCounterHelp() { return ar ? "بعد الأذان، في زاوية الشاشة حتى الإقامة" : "After the adhan, in a corner of the screen until the iqamah"; }
    /** "الإقامة بعد 8 د" / "Iqamah in 8 min"; under a minute "الإقامة الآن". */
    String iqamaInMinutes(long minutes) {
        if (minutes <= 0) return ar ? "الإقامة الآن" : "Iqamah now";
        return d(ar ? "الإقامة بعد " + minutes + " د" : "Iqamah in " + minutes + " min");
    }
    String statusRow() { return ar ? "حالة التطبيق" : "App status"; }
    String statusHelp() { return ar ? "هل الخدمة والاستراحة والأذان تعمل على هذا الجهاز" : "Whether the service, the screensaver and the adhan work on this device"; }
    String statusSection() { return ar ? "حول التطبيق ← حالة التطبيق" : "About › App status"; }
    String hintBackAbout() { return ar ? "رجوع  حول التطبيق" : "Back  About"; }
    String serviceRow() { return ar ? "خدمة Awan TV" : "Awan TV service"; }
    String serviceHelp() { return ar ? "الرئيسية فوق واجهة الجهاز، والأذان فوق أي تطبيق" : "Home over the device's own, and the adhan over any app"; }
    String running(boolean on) { return ar ? (on ? "تعمل" : "متوقفة") : (on ? "Running" : "Stopped"); }
    String serviceOffHelp() { return ar ? "شغّلها من إعدادات الجهاز ← إمكانية الوصول ← Awan TV" : "Turn it on in the device's Settings › Accessibility › Awan TV"; }
    String saverStatusRow() { return ar ? "شاشة الاستراحة" : "Screensaver"; }
    String saverStatusHelp(int state) {
        return ar ? new String[] {"شاشة استراحة الجهاز هي Awan TV", "يفتحها Awan TV بنفسه من الرئيسية (لا صلاحية لضبط شاشة الجهاز)", "مطفأة في «تشغيل Awan TV»"}[state]
                  : new String[] {"The device's screensaver is Awan TV", "Awan TV opens it itself from Home (no permission to set the device's)", "Off in Run Awan TV"}[state];
    }
    String off() { return ar ? "مطفأ" : "Off"; }
    String fromHome() { return ar ? "من الرئيسية" : "From Home"; }
    String lastAdhanRow() { return ar ? "آخر أذان" : "Last adhan"; }
    String noAdhanYet() { return ar ? "لم يُشغَّل أذان بعد على هذا الجهاز" : "No adhan has played on this device yet"; }
    String played(boolean ok) { return ar ? (ok ? "شُغِّل" : "تعذّر التشغيل") : (ok ? "Played" : "Could not play"); }
    String none() { return ar ? "لا شيء" : "None"; }
    String nextAdhanRow() { return ar ? "الأذان القادم" : "Next adhan"; }
    String adhanOn(boolean on) { return ar ? (on ? "مفعّل" : "مطفأ") : (on ? "On" : "Off"); }
    String permissionRow() { return ar ? "صلاحية الإعدادات" : "Settings permission"; }
    String permissionHelp() { return ar ? "لضبط شاشة الاستراحة وإعادة ربط الخدمة (تُمنح عند التثبيت)" : "To set the screensaver and bind the service again (granted at install)"; }
    String granted(boolean on) { return ar ? (on ? "ممنوحة" : "غير ممنوحة") : (on ? "Granted" : "Not granted"); }
    String weatherStatusHelp(CharSequence at) { return ar ? "آخر تحديث " + at + " · Open-Meteo" : "Last update " + at + " · Open-Meteo"; }
    String weatherNone() { return ar ? "لم يصل تحديث خلال 3 ساعات (الشبكة أو الموقع)" : "No update within 3 hours (network or place)"; }
    String works(boolean ok) { return ar ? (ok ? "يعمل" : "لا يصل") : (ok ? "Working" : "Not reaching"); }
    String versionRow() { return ar ? "النسخة" : "Version"; }
    String todayWord() { return ar ? "اليوم" : "today"; }

    // The settings file (SettingsFile), its comments; western digits there (values are JSON numbers)
    String sepPlain() { return " · "; }
    String[] fileHeader() {
        return ar ? new String[] {
            "إعدادات Awan TV: عدّل القيم ثم أرسلها إلى الشاشة:",
            "  powershell -ExecutionPolicy Bypass -File tools\\AwanTV-settings.ps1 -Import -File <هذا الملف> -Device <الشاشة>",
            "فوق كل إعداد: ما هو، وقيمه، وقيمته الافتراضية. الأسطر التي تبدأ بـ // شروح لا تُقرأ.",
            "حذف إعداد من الملف يُبقيه على الشاشة كما هو. والملف يُفحص كاملًا: أي خطأ يوقف الاستيراد ولا يتغير شيء.",
            "قبل الاستيراد تُحفظ إعدادات الشاشة نسخة احتياطية في state\\settings على الحاسب."}
            : new String[] {
            "Awan TV settings: change the values, then send them to the TV:",
            "  powershell -ExecutionPolicy Bypass -File tools\\AwanTV-settings.ps1 -Import -File <this file> -Device <the TV>",
            "Above each setting: what it is, its values and its default. Lines starting with // are notes, not read.",
            "A setting left out of the file stays as it is on the TV. The whole file is checked: one mistake stops the import and nothing changes.",
            "Before importing, the TV's settings are kept as a backup in state\\settings on the computer."};
    }
    String boolValues() { return ar ? "القيم: true = نعم، false = لا" : "Values: true = on, false = off"; }
    String rangeValues(int lo, int hi) { return ar ? "القيم: من " + lo + " إلى " + hi : "Values: " + lo + " to " + hi; }
    String timeOrEmpty() { return ar ? "القيم: وقت \"HH:MM\" بنظام 24 ساعة، أو \"\" بلا وقت" : "Values: a time \"HH:MM\" (24-hour), or \"\" for none"; }
    String timeBetween(String a, String b) { return ar ? "القيم: وقت \"HH:MM\" من " + a + " إلى " + b : "Values: a time \"HH:MM\" from " + a + " to " + b; }
    String listValues() { return ar ? "القيم: قائمة أسماء حزم التطبيقات بالترتيب" : "Values: a list of the apps' package names, in order"; }
    String cityValues() { return ar ? "يُنقل كما هو بين الشاشات؛ لتغيير الموقع اختره من التلفاز" : "Copied as it is between TVs; to change the location, pick it on the TV"; }
    String textValue() { return ar ? "القيم: نص بين علامتي تنصيص" : "Values: text in quotes"; }
    String defaultIs(String v) { return (ar ? "الافتراضي: " : "Default: ") + v; }
    String cityFileHelp() { return ar ? "المدينة أو المكان بالإحداثيات" : "The city, or a place by its coordinates"; }
    String byCity() { return ar ? "حسب الموقع" : "by the location"; }
    String byCountry() { return ar ? "حسب الدولة إن كان لها معيار رسمي، وإلا 0 (إيقاف)" : "the country's, if it has an official one, else 0 (off)"; }
    String byCountryOr(int n) { return ar ? "حسب الدولة، وإلا " + n : "the country's, else " + n; }
    String byTvCountry() { return ar ? "\"f\" في الدول التي تقيس بالفهرنهايت، وإلا \"c\"" : "\"f\" in countries that use Fahrenheit, else \"c\""; }
    String facingNote() { return ar ? "بالدرجات من الشمال، و-1 غير محدد" : "degrees from north, -1 not set"; }
    String fridayFixedNote() { return ar ? "يُستعمل حين تكون صلاة الجمعة «موعد ثابت» (2)" : "Used when Friday prayer is Fixed time (2)"; }
    String iqamaMinutesRow(String prayer) { return ar ? "دقائق إقامة " + prayer : prayer + " iqamah minutes"; }
    String iqamaMinutesHelp() { return ar ? "بعد الأذان، حين تكون الإقامة «دقائق» (2)" : "After the adhan, when Iqamah is Minutes (2)"; }
    String iqamaFixedRow(String prayer) { return ar ? "موعد إقامة " + prayer + " الثابت" : prayer + " fixed iqamah"; }
    String iqamaFixedHelp() { return ar ? "حين تكون الإقامة «وقت ثابت» (3)" : "When Iqamah is Fixed (3)"; }
    String iqamaRamadanRow(String prayer) { return ar ? "إقامة " + prayer + " في رمضان" : prayer + " iqamah in Ramadan"; }
    String iqamaRamadanHelp() { return ar ? "دقائق بعد الأذان، و-1 كبقية الأيام" : "Minutes after the adhan, -1 as on other days"; }
    String bellRow(String prayer) { return ar ? "تشغيل أذان " + prayer : prayer + " adhan on"; }
    String notInstalledFile() { return ar ? "(غير مثبَّت على هذه الشاشة)" : "(not installed on this TV)"; }
    String fajrVoiceFile() { return ar ? "صوت أذان الفجر" : "Fajr adhan voice"; }
    String valuesWord() { return ar ? "القيم: " : "Values: "; }
    String radioResumeRowFile() { return ar ? "الإذاعة بعد الأذان" : "The radio after the adhan"; }
    String stationSavedFile() { return ar ? "تلاوة محفوظة على هذه الشاشة، فلا تُنقل؛ اخترها من التلفاز" : "a recitation saved on this TV, so not copied; pick it on the TV"; }
    String voiceHelpFile() { return ar ? "لكل الصلوات عدا الفجر" : "Every prayer but Fajr"; }
    String fajrLevelHelpFile() { return ar ? "نسبة من صوت التلفاز" : "A share of the TV's sound"; }
    String stationFileHelp() { return ar ? "تُرسل إلى الشاشات المختارة؛ الشاشة التي لا توجد عليها تبقى محطتها" : "Sent to the chosen TVs; a TV that doesn't have it keeps its own"; }
    String[] digitChoicesFile() { return ar ? new String[] {"حسب اللغة", "0123", "٠١٢٣"} : new String[] {"By language", "0123", "٠١٢٣"}; }
    String hadithRowFile() { return ar ? "حديث في وسط شاشة الاستراحة" : "A hadith in the screensaver's middle"; }
    String homeAppsFile() { return ar ? "تطبيقات الرئيسية والجهاز" : "Home's apps and the device"; }
    String homeSectionFile() { return ar ? "الرئيسية" : "Home"; }
    String homeAppsNoteFile() { return ar ? "في التلفاز تُرتَّب من الرئيسية نفسها" : "On the TV they are arranged on Home itself"; }
    String bellsNoteFile() { return ar ? "في التلفاز: مواقيت اليوم والقبلة، زر الجرس بجانب كل صلاة" : "On the TV: Today & Qibla, the bell by each prayer"; }
    String favouritesFile() { return ar ? "التطبيقات المفضلة في الرئيسية" : "Home's favourite apps"; }
    String moreFile() { return ar ? "صف «المزيد» بترتيبك (فارغ: تلقائي)" : "The More row in your order (empty: automatic)"; }
    String packagesHelp() { return ar ? "غير المثبَّت منها على هذه الشاشة يُتجاهل" : "Ones not installed on this TV are skipped"; }
    String homeButtonFileHelp() { return ar ? "لأجهزة البث الموصولة بشاشة فقط (Google TV Streamer)" : "Only on boxes connected to a TV (Google TV Streamer)"; }

    // Settings > About > Reset settings
    String resetRow() { return ar ? "إعادة ضبط الإعدادات" : "Reset settings"; }
    String resetHelp() { return ar ? "كل إعدادات Awan TV كما كانت عند التثبيت، ثم يبدأ من اختيار الموقع" : "Every Awan TV setting as on a new install, then it starts from the location"; }
    String resetTitle() { return ar ? "إعادة ضبط الإعدادات؟" : "Reset settings?"; }
    String resetGoesHead() { return ar ? "يعود إلى ما كان عليه عند التثبيت:" : "Back to a new install's:"; }
    String[] resetGoes() {
        return ar ? new String[] {"الموقع وطريقة الحساب والإقامة والجمعة", "التنبيهات وأصوات الأذان ومستوى الفجر",
                                  "الألوان والصور واللغة والطقس", "التطبيقات المفضلة وصف «المزيد»", "أزرار الريموت المخصصة"}
                  : new String[] {"The location, the method, the iqamah and Friday", "Reminders, adhan voices and Fajr's level",
                                  "Colours, photos, language and weather", "Favourite apps and the More row", "Custom remote buttons"};
    }
    String resetStaysHead() { return ar ? "يبقى كما هو:" : "Stays as it is:"; }
    String[] resetStays() {
        return ar ? new String[] {"التلاوات المحفوظة", "شاشة استراحة التلفاز السابقة (لإزالة Awan TV لاحقًا)", "خدمة Awan TV وصلاحياتها"}
                  : new String[] {"Saved recitations", "The TV's earlier screensaver (to remove Awan TV later)", "The Awan TV service and its permissions"};
    }
    String resetContinue() { return ar ? "متابعة" : "Continue"; }
    String resetHoldTitle() { return ar ? "اضغط OK مطوّلًا للتأكيد" : "Hold OK to confirm"; }
    String resetHoldHelp() { return ar ? "ثلاث ثوانٍ · رفع الإصبع قبلها يعيد العدّ، وأي زر آخر يلغي" : "Three seconds · letting go starts again, any other button cancels"; }
    String resetBackupNote() { return ar ? "تُحفظ الإعدادات الحالية على الشاشة قبل المسح،\nويمكن استعادتها من الحاسب بالأداة:" : "The current settings are kept on the TV first,\nand can be brought back from a computer with:"; }
    String hintHoldOk() { return ar ? "OK  اضغط مطوّلًا" : "OK  Hold"; }

    // The editor's Identify on the TV
    String identifyLabel() { return ar ? "هذه الشاشة" : "This TV"; }
    String identifyDevice(String name) { return (ar ? "اسم الجهاز: " : "Device name: ") + name; }
    String identifyNoName() { return ar ? "لم تُسمَّ في الحاسب بعد" : "Not named on the computer yet"; }
    String identifyFoot(int seconds) { return d(ar ? "طلبها محرر الإعدادات من الحاسب · تختفي بعد " + seconds + " ثوانٍ" : "Asked for by the settings editor · goes in " + seconds + " seconds"); }
}
