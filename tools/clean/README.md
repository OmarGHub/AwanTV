# تنظيف الشاشة من الإعلانات والتتبّع (AwanTV-clean)

تعطّل هذه الأدوات إعلانات الشاشة وتتبّعها والتطبيقات المثبتة معها مسبقًا، عبر adb من الحاسوب. والتعطيل للمستخدم فقط
(`pm disable-user --user 0`)، فلا يُحذف شيء من الشاشة، وتبقى بيانات كل تطبيق فيه، ويعود كل تطبيق بأمر واحد.

| الأداة / Tool | العمل / What it does |
|---|---|
| `tools\AwanTV-tv-backup.ps1` | نسخة احتياطية قبل أي تغيير، ولا تغيّر شيئًا / Backup before any change; changes nothing |
| `tools\AwanTV-clean.ps1` | التعطيل حسب قائمة في `tools\clean\` / Turns packages off from a list |
| `tools\AwanTV-clean-undo.ps1` | إرجاع ما عطّلته أداة التنظيف / Turns back on what the clean tool turned off |
| `tools\clean\tcl-smart-tv-pro.txt` | قائمة شاشة TCL «Smart TV Pro» / The list for TCL "Smart TV Pro" |
| `tools\clean\extra.txt` | قائمتك الخاصة لشاشة TCL: تطبيقات اختيارية، كلها معلّقة بـ `#` / Optional apps for the TCL, all commented out |
| `tools\clean\google-tv-streamer.txt` | قائمة جهاز Google TV Streamer / The list for the Google TV Streamer |
| `tools\clean\extra-google-tv-streamer.txt` | التطبيقات الاختيارية لجهاز Streamer، كلها معلّقة / Optional apps for the Streamer, all commented out |
| `tools\clean\tcl-beyondtv-android11.txt` | قائمة شاشة TCL BeyondTV بنظام Android 11 / The list for TCL BeyondTV on Android 11 |
| `tools\clean\extra-tcl-beyondtv-android11.txt` | التطبيقات الاختيارية لشاشة BeyondTV، كلها معلّقة / Optional apps for the BeyondTV, all commented out |
| `tools\clean\google-chromecast-gtv.txt` | قائمة Chromecast with Google TV (4K وHD) / The list for the Chromecast with Google TV (4K and HD) |
| `tools\clean\extra-google-chromecast-gtv.txt` | التطبيقات الاختيارية لجهاز Chromecast، كلها معلّقة / Optional apps for the Chromecast, all commented out |

كل الأوامر تُكتب في PowerShell داخل مجلد المشروع، وكل أمر في سطر وحده.

---

## 1. تجهيز الشاشة (مرة واحدة)

1. في الشاشة: الإعدادات ← النظام ← حول ← اضغط على «إصدار نظام Android TV» (Build) سبع مرات حتى يظهر «أصبحت مطوّرًا».
2. الإعدادات ← النظام ← خيارات المطوّر ← فعّل **Wireless debugging** (تصحيح الأخطاء اللاسلكي).
3. داخل Wireless debugging اختر **Pair device with pairing code**، فيظهر عنوان ومنفذ ورمز من ستة أرقام.

## 2. الاقتران والاتصال

1. الاقتران، بالعنوان والمنفذ الظاهرين في نافذة الاقتران (Pair):

   ```powershell
   adb pair 192.168.1.20:<منفذ الاقتران>
   ```

   ثم اكتب الرمز عند السؤال. يجب أن يظهر `Successfully paired`.

2. تأكد من الاتصال:

   ```powershell
   adb devices
   ```

   تظهر الشاشة غالبًا باسم مثل `adb-1A2B3C4D5E6F7G-AbC123._adb-tls-connect._tcp`، وقد تظهر مرتين، مرة منهما
   بلاحقة ` (2)`، وكلاهما الشاشة نفسها.

3. إن لم تظهر خلال عشر ثوانٍ: خذ العنوان والمنفذ من صفحة Wireless debugging نفسها (وهو غير منفذ الاقتران)، ثم:

   ```powershell
   adb connect 192.168.1.20:<المنفذ>
   ```

> **اسم الجهاز في الأدوات:** يكفي جزؤه الثابت، مثل `-Device 1A2B3C4D5E6F7G`، فلا تتأثر الأدوات باللاحقة ` (2)`
> التي تتغير كلما أعيد تشغيل adb. أما إن اتصلت بالعنوان (`adb connect`)، فاسم الجهاز هو العنوان نفسه، فاكتب
> `-Device 192.168.1.20`.

## 3. النسخة الاحتياطية

```powershell
powershell -ExecutionPolicy Bypass -File tools\AwanTV-tv-backup.ps1 -Device 1A2B3C4D5E6F7G
```

تحفظ في `state\backup\<رقم الشاشة>\<التاريخ والوقت>\`:

1. قوائم التطبيقات كلها: مع إصدار كل تطبيق ومساره، والنظامية، والمثبتة، والمفعّلة، والمعطّلة.
2. تطبيق الشاشة الرئيسية، وخدمات إمكانية الوصول، وشاشة التوقف.
3. الإعدادات (system و secure و global)، وخصائص الجهاز.
4. ملفات APK للتطبيقات التي ثبّتّها، وكل ملف مطابَق لحجمه على الشاشة (`ok`، وإلا `FAIL`).

لا يمكن حفظ بيانات التطبيقات وتسجيلات الدخول دون root، لكن التعطيل لا يمسّها.

لإعادة تثبيت تطبيق من النسخة:

```powershell
adb install-multiple (Get-ChildItem "<مجلد النسخة>\apks\<اسم الحزمة>\*.apk").FullName
```

## 4. التجربة دون تغيير

```powershell
powershell -ExecutionPolicy Bypass -File tools\AwanTV-clean.ps1 -Device 1A2B3C4D5E6F7G -DryRun
```

تعرض قائمة مرقّمة بما ستعطّله، وما ستتخطاه ولماذا: `protected` (محمي)، `not on this TV` (غير موجود)،
`already off` (معطّل من قبل). لا تغيّر شيئًا.

القائمة تُختار تلقائيًا من طراز الجهاز، أو تُحدَّد بـ `-List <ملف>`:

| الطراز / Model (`ro.product.model`) | القائمة / List | الاختيارية / Optional |
|---|---|---|
| Smart TV Pro (TCL) | `tcl-smart-tv-pro.txt` | `extra.txt` |
| Google TV Streamer | `google-tv-streamer.txt` | `extra-google-tv-streamer.txt` |
| BeyondTV (TCL) على Android 11 / on Android 11 | `tcl-beyondtv-android11.txt` | `extra-tcl-beyondtv-android11.txt` |

اسم «BeyondTV» تحمله شاشات TCL بنظام Android 9 أيضًا، وقائمتها غير هذه؛ لذلك تُختار القائمة بالاسم ورقم إصدار
Android معًا، وعلى Android 9 تحتاج `-List`.

## 5. التنظيف

```powershell
powershell -ExecutionPolicy Bypass -File tools\AwanTV-clean.ps1 -Device 1A2B3C4D5E6F7G
```

1. تعطّل كل تطبيق بأمر مستقل، ثم تتأكد أنه تعطّل فعلًا (`off`، وإلا `FAIL` مع السبب).
2. تكتب ما عطّلته، أولًا بأول، في `state\clean\<رقم الشاشة>\clean-<التاريخ والوقت>.txt`.
3. في النهاية تتأكد أن الشاشة ما زالت تستجيب، وأن تطبيق الشاشة الرئيسية لم يتغير. إن تغيّر شيء من ذلك تظهر
   `WARNING` ومعها أمر الإرجاع.

## 6. بعد التنظيف

1. أعد تشغيل الشاشة.
2. تأكد من: الصورة، ومدخلات HDMI (زر Input)، والشبكة، وميكروفون جهاز التحكم، وزر Home وظهور Awan TV، وشاشة
   الاستراحة، والأذان.
3. تأكد أن التعطيل بقي بعد إعادة التشغيل:

   ```powershell
   adb -s "<اسم الجهاز من adb devices>" shell pm list packages -d
   ```

4. أطفئ Wireless debugging بعد الانتهاء: على شاشة TCL «Smart TV Pro» **لا يعمل زر Input في جهاز التحكم ما دام
   Wireless debugging مفعّلًا**، ويعود يعمل حين يُطفأ (من الشاشة نفسها، لا من التنظيف). وإطفاؤه أسلم أيضًا. والشاشة تطفئه وحدها بعد قليل من عدم الاستخدام، فيعود الزر دون أن تفعل شيئًا. وحين
   تحتاجه للتراجع أو لتثبيت إصدار جديد، فعّله من جديد: الاقتران يبقى محفوظًا عادةً، فتظهر الشاشة في `adb devices`
   دون اقتران جديد؛ وإن لم تظهر فأعد الخطوة 2.

## 7. تعطيل تطبيقات اختيارية (extra.txt)

القائمة الأولى لا تلمس ما قد تستعمله. أما `tools\clean\extra.txt` (لشاشة TCL) و`extra-google-tv-streamer.txt` (لجهاز
Streamer، فاكتب اسمه بعد `-List` بدل `extra.txt`) ففيهما تطبيقات المشاهدة والبث من الجوال وغيرها،
وكل سطر فيهما يبدأ بـ `#` فلا يُعطَّل شيء منها حتى تختار:

1. افتح `extra.txt`، واحذف `#` من أول سطر كل تطبيق تريد تعطيله. مثال:

   ```
   # com.amazon.amazonvideo.livingroom    # Prime Video          <- يبقى كما هو
   com.amazon.amazonvideo.livingroom      # Prime Video          <- سيُعطَّل
   ```

   ما كُتب بجانبه «together with the next line» يُعطَّل مع ما بعده أو لا يُعطَّل كلاهما (Netflix وخدمته،
   AirPlay وخدمتاه، مشغّل USB ومعرض صوره).

2. جرّب ثم نفّذ:

   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\AwanTV-clean.ps1 -Device 1A2B3C4D5E6F7G -List extra.txt -DryRun
   ```

   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\AwanTV-clean.ps1 -Device 1A2B3C4D5E6F7G -List extra.txt
   ```

3. يمكن تشغيلها متى شئت: ما عُطّل من قبل يُتخطّى (`already off`)، وما ليس على الشاشة يُتخطّى
   (`not on this TV`)، فلا تكرار.

4. إعادة `#` إلى سطر لا تُرجع التطبيق؛ للإرجاع استعمل أداة التراجع بـ `-Package` (الخطوة 8).

5. أزرار التطبيقات في جهاز التحكم (Netflix، Prime Video، YouTube) لا تعمل إذا عُطّل تطبيقها.

## 8. التراجع

1. إرجاع كل ما عطّلته الأداة:

   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\AwanTV-clean-undo.ps1 -Device 1A2B3C4D5E6F7G
   ```

   يقرأ سجلات `state\clean\<رقم الشاشة>\`، ويعيد تفعيل ما فيها واحدًا واحدًا، ثم يغيّر اسم السجل من `clean-` إلى
   `undone-` حتى لا يُعاد مرة ثانية. ما كان معطّلًا قبل التنظيف لا يمسّه.

2. إرجاع تطبيق واحد فقط:

   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\AwanTV-clean-undo.ps1 -Device 1A2B3C4D5E6F7G -Package com.netflix.ninja
   ```

3. إرجاع سجل واحد فقط: `-Log <ملف السجل>`.

4. الحل الأخير إن تعذّر كل شيء: إعادة ضبط المصنع من إعدادات الشاشة، فتعود كما اشتريتها (ويلزم بعدها تثبيت Awan
   TV من جديد).

---

## ما لا تلمسه الأداة أبدًا

ترفضه ولو وُضع في القائمة خطأً:

1. **النظام:** `android`، `com.android.systemui`، `com.android.settings`، `com.android.tv.settings`، مزوّدو
   الإعدادات والوسائط والتنزيل، مثبّت الحزم، متحكّم الأذونات، الشبكة والبلوتوث، و`com.google.android.overlay.modules*`.
   تعطيل بعضها قد يمنع الشاشة من الإقلاع.
2. **Google:** Play Store و Play Services (`com.android.vending`، `com.google.android.gms`، `com.google.android.gsf`).
3. **TCL:** الصورة والمدخلات (`com.tcl.tv`، `com.tcl.tvinput`)، لوحة المدخلات لزر Input (`com.tcl.suspension`)،
   الإعدادات السريعة (`com.tcl.dashboard`)، الإقران (`com.tcl.autopair`)، و`com.tcl.providers.config` و
   `com.tcl.initsetup` و`com.tcl.factory.view` (تعطيلها معًا قطع الشبكة عن شاشة من اللوحة نفسها)، والصوت
   (`com.tcl.eva`، `com.tcl.hearaid`)، و`tv.mopa.ginga` (البث)، ولوحتا المفاتيح، وخدمات MediaTek و Dolby.
4. **جهاز التحكم:** البحث الصوتي (`com.google.android.katniss`) وميكروفون جهاز التحكم
   (`com.google.android.tv.remote.service`).
5. **ما يعمل عليه Awan TV:** واجهة Google TV (`launcherx`)، شاشة التوقف الأصلية (`dreamx`)، الإعداد
   (`setupwraith`)، TalkBack والنطق، و Awan TV نفسه. وأي تطبيق تفتحه الشاشة زرَّ Home.

## جهاز Google TV Streamer

1. جهاز من Google نفسها، فلا إعلانات ولا تتبّع لشركة مصنّعة؛ لذلك قائمته قصيرة (7 تطبيقات).
2. **معالج الإعداد `com.google.android.tungsten.setupwraith` لا يُعطَّل عليه أبدًا** (والأداة ترفضه): فيه شهادات
   Chromecast، ويملك شبكة Wi-Fi المحفوظة؛ تعطيله في CleanYourTV أوقف البث من الجوال وقد يقطع الشبكة. ولا حاجة إليه
   أصلًا، لأن Awan TV يعمل فوق واجهة Google TV.
3. محميّ أيضًا: تحديث برمجيات جهاز التحكم (`com.google.android.tv.dfuservice`)، وإقرانه وصوته
   (`com.google.android.tv.axel`)، وشاشة Ambient (`com.google.android.backdrop`)، والبلوتوث والشبكة.
4. وحدات Privacy Sandbox الأربع (الإعلانات المخصّصة) في الملف الاختياري، لأن إعادة تفعيلها تلزم إن أساء Play Store
   أو المساعد الصوتي التصرف.
5. إن وُجد فيه شيء معطّل من قبل (تنظيف سابق، أو ما يأتي معطّلًا من المصنع مثل `tvrecommendations` و
   `cast2class`)، فالأداة تتخطاه (`already off`)، وأداة التراجع لا تلمسه لأنه ليس في سجلاتها.

## شاشة TCL BeyondTV (Android TV 11)

1. **الاتصال:** هذه الشاشة لا تعرض Wireless debugging بالاقتران. فعّل **USB debugging** في خيارات المطوّر، ثم
   `adb connect <عنوان الشاشة>:5555`، واقبل على الشاشة «Allow USB debugging?» مع «Always allow». إن ظهر
   `failed to authenticate` فالرسالة تنتظرك على الشاشة. وفي الأدوات اكتب العنوان: `-Device 192.168.1.30`.
2. **بعد الانتهاء أطفئ USB debugging:** المنفذ 5555 غير مشفّر، ومن على الشبكة نفسها يصل إليه.
3. **أول سطر في القائمة هو الأهم:** `com.tcl.overseasappshow`. خدمة من TCL تعيد تفعيل تطبيقات الإعلانات عند كل
   إعادة تشغيل وبعد كل تثبيت؛ إن بقيت عادت تلك التطبيقات.
4. **ليست في القائمة عمدًا، مع أنها تُعطَّل في شاشات TCL الأحدث:** `com.tcl.partnercustomizer` (يتولى هنا أزرار جهاز
   التحكم) و`com.tcl.usercenter` (شاشات الإعداد والحساب).
5. **شاشة التوقف الأصلية هنا `com.google.android.backdrop`:** محمية، فإليها يعيد Awan TV الشاشة إن أُزيل.
6. **الصوت (المساعد وميكروفون جهاز التحكم) في الملف الاختياري:** أربعة تطبيقات تُعطَّل معًا أو لا تُعطَّل.
7. **محميّ أيضًا في هذه المنصة:** قائمة المدخلات، و HDMI، وأزرار جهاز التحكم وبلوتوثه، والصورة والصوت، وتوثيق الجهاز،
   ومُحدِّث النظام (`com.tcl.versionUpdateApp`) لتصل التحديثات الأمنية.

## قائمة لشاشة أخرى

1. انسخ `tcl-smart-tv-pro.txt` باسم جديد في `tools\clean\` (تطبيق في كل سطر، و`#` لبدء تعليق).
2. خذ الأسماء من قائمة مجرّبة لطراز الشاشة نفسه في
   [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (مجلد `examples/`)، ثم احذف منها ما تستعمله.
3. شغّل بـ `-List <اسم الملف> -DryRun` أولًا، ثم دون `-DryRun`.
4. لاختيار القائمة تلقائيًا: أضف الطراز (من `adb shell getprop ro.product.model`) إلى `$Lists` في أول
   `AwanTV-clean.ps1`.

## المصدر

قائمة BeyondTV من CleanYourTV (`examples/tcl-beyondtv2-android-11`)، لشاشة BeyondTV بنظام Android 11 بالتطبيقات
المسبقة نفسها وبالمعطّل من المصنع نفسه، جُرّبت على جهاز حقيقي مع إعادة التشغيل؛ وتُرك ما اختاره صاحبها من الصوت والبث
وشاشة التوقف للملف الاختياري.

قائمة جهاز Streamer من CleanYourTV (`examples/google-tv-streamer-4k-kirkwood`)، مع سطرين تشترك فيهما أكثر قوائمه
(`partnersetup` في 76 قائمة، ومزامنة التقويم في 63)، دون `setupwraith`.

قائمة TCL مأخوذة من CleanYourTV (ترخيص MIT)، من قائمتي `examples/tcl-c755-google-tv` و
`examples/tcl-qm6k-google-tv`، وهما لشاشتين «Smart TV Pro» من اللوحة نفسها (G08) جُرّبتا على أجهزة حقيقية. أُخذ ما
تتفقان عليه من التتبّع والإعلانات ووضع العرض والتطبيقات المسبقة، وتُرك ما قد تستعمله (البث من الجوال، AirPlay،
Miracast، ملفات USB، شريط الألعاب، تطبيقات المشاهدة، YouTube Music).
