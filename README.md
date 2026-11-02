<div dir="rtl">

# Awan TV

**مواقيت الصلاة والأذكار والمزيد: شاشة رئيسية وشاشة استراحة لما يعمل بنظام Android TV أو Google TV من شاشات تلفزيون ذكية وأجهزة TV Box وأجهزة بث.**

هل عندك شاشة تلفزيون ذكية تعمل بنظام Android TV أو Google TV، أو جهاز بهذا النظام كـ Chromecast أو Google TV Streamer أو أجهزة
TV Box مثل Xiaomi Mi Box، وشاشته الرئيسية صامتة لا تقول ولا تقدّم شيئًا؟

ماذا لو صار يذكّرك بموعد كل صلاة وإقامتها، ويريك تاريخ اليوم بالهجري والميلادي في تقويم شهري يجمعهما، ويعرض عليك
أذكار الصباح والمساء في أوقاتها، ويرفع الأذان وأنت تشاهد أي برنامج، ويخبرك كم بقي على إقامة الصلاة؟

هذا ما يقدّمه لك **Awan TV**، والمزيد غيره.

[English](#awan-tv-english)

| | |
|:---:|:---:|
| ![الرئيسية بسمة لازوردي](docs/images/home-photo.webp) | ![الرئيسية بسمة فحمي](docs/images/home-charcoal.webp) |
| الرئيسية بسمة لازوردي | الرئيسية بسمة فحمي |
| ![شاشة الاستراحة وفي وسطها حديث](docs/images/saver-photo.webp) | ![شاشة الاستراحة في وقت أذكار الصباح](docs/images/saver-adhkar.webp) |
| شاشة الاستراحة: حديث من الصحيحين | شاشة الاستراحة: أذكار الصباح |
| ![عداد الإقامة فوق تطبيق آخر](docs/images/iqamah-overlay.webp) | ![الإعدادات](docs/images/settings.webp) |
| عدّاد الإقامة فوق أي تطبيق (اختياري) | الإعدادات بالريموت |
| ![أول تشغيل: اختر موقعك](docs/images/first-location.webp) | ![التلاوات المحفوظة](docs/images/recitations.webp) |
| أول تشغيل: اختر موقعك، ويقترحه التطبيق | التلاوات المحفوظة للاستماع بلا إنترنت |

<p align="center"><img src="docs/images/editor.webp" alt="محرر الإعدادات على Windows" width="80%"></p>
<p align="center">محرر الإعدادات على Windows: يجلبها من شاشة، ويرسلها إلى شاشة أو أكثر</p>

> رسوم توضيحية من تصميم التطبيق نفسه؛ التطبيقات والمواقيت والطقس على شاشتك تختلف. الخلفية (نقش أو صور) والسمة (ست سمات)
> من الإعدادات ← العرض واللغة، وعدّاد الإقامة من الإعدادات ← الإقامة.

## باختصار

1. **شاشة رئيسية فوق الشاشة الرئيسية الأصلية للجهاز**، ولا يُعطَّل فيه شيء.
2. **شاشة استراحة** بالمواقيت الستة والإقامة والطقس والتاريخين، وفيها أذكار الصباح والمساء وأحاديث الصحيحين بنصها ومصدرها.
3. **الأذان فوق أي تطبيق**، والتذكير قبل الصلاة، وعدّاد الإقامة.
4. **أي موقع في العالم**، والمواقيت تُحسب على التلفاز دون إنترنت.
5. **إذاعة القرآن الكريم**، والتلاوات المحفوظة للاستماع دون إنترنت.
6. **العربية والإنجليزية**، وست سمات للألوان.

## التثبيت

ثلاث طرق، اختر واحدة منها. الملفات كلها في [صفحة الإصدارات](../../releases/latest).

**على التلفاز أولًا (للطريقتين 1 و2):**
1. فعّل خيارات المطوّر: الإعدادات ← النظام ← حول، واضغط OK سبع مرات على «إصدار نظام تشغيل Android TV»
   حتى تظهر رسالة أنك أصبحت مطوّرًا. في الشاشات الأقدم: الإعدادات ← إعدادات الجهاز ← حول ← الإصدار.
2. من خيارات المطوّر فعّل «تصحيح الأخطاء اللاسلكي»، وفي الشاشات الأقدم التي ليس فيها: «تصحيح أخطاء USB».

### 1. من الجوال (الأسهل)
1. ثبّت [AwanTVInstaller.apk](../../releases/latest/download/AwanTVInstaller.apk) على جوالك الأندرويد، وتأكد أن الجوال
   والتلفاز على شبكة الواي فاي نفسها.
2. افتح المثبّت، واختر شاشتك، ثم «ثبّت Awan TV». إن طلبت الشاشة السماح أو ظهر عليها رمز، فوافق أو اكتب الرمز في المثبّت.

### 2. من حاسب Windows 10 أو 11
1. نزّل [AwanTV-Windows.zip](../../releases/latest/download/AwanTV-Windows.zip) وفُكّه في مجلد.
2. انقر نقرتين على `AwanTV-install.cmd`. يطلب عنوان التلفاز ويتصل به، وإن لم يجد adb عرض تنزيله من Google.
   إن ظهر تحذير أمان من Windows فاختر «تشغيل»: يظهر مع كل ملف نُزّل من الإنترنت. والتفاصيل في `README.txt` داخل المجلد.

### 3. يدويًا، دون جوال ولا حاسب
1. ثبّت [AwanTV.apk](../../releases/latest/download/AwanTV.apk) على التلفاز بأي طريقة (يكفي السماح بالتثبيت من مصادر غير معروفة).
2. شغّل الخدمة: إعدادات التلفاز ← النظام ← إمكانية الوصول ← Awan TV. (بعض أجهزة Android 13 وما بعده تمنع ذلك لتطبيق
   مثبت من خارج المتجر؛ عندها استعمل الطريقة 1 أو 2.)
3. شاشة الاستراحة: يفتحها Awan TV بنفسه بعد دقائق دون ضغط زر وأنت على الرئيسية. أما جعلها شاشة استراحة التلفاز نفسه
   في كل التطبيقات فيحتاج الطريقة 1 أو 2 على Google TV.

**بعد التثبيت:** اضغط Home في الريموت. في أول تشغيل يقترح التطبيق موقع الشاشة، فتؤكده أو تختار موقعًا آخر. ولك أن تطفئ
التصحيح من خيارات المطوّر، وتشغّله مرة أخرى عند التحديث.

**التحديث:** الطريقة نفسها بالإصدار الجديد، وتبقى الإعدادات والتلاوات المحفوظة. ويخبرك التطبيق بالإصدار الجديد في
الإعدادات ← حول التطبيق (ويُوقف من هناك)، أو ابحث بنفسك متى شئت: الإعدادات ← حول التطبيق ← ابحث عن تحديث الآن.

**الإزالة:** الإعدادات ← تشغيل Awan TV ← **إزالة Awan TV**: يعيد شاشة الاستراحة السابقة، ويسألك إن كانت عندك تلاوات
محفوظة: تبقى أم تُحذف، ثم يفتح حذف التطبيق. أو من الحاسب: `AwanTV-restore.cmd`. ولو حُذف التطبيق من إعدادات التلفاز مباشرة
يعود التلفاز إلى شاشة استراحته الافتراضية بنفسه.

## ماذا يقدم

1. **شاشة رئيسية فوق شاشة التلفاز الأصلية**: كلما ظهرت الشاشة الرئيسية للتلفاز يظهر Awan TV فوقها. **لا يُعطَّل أي شيء**، فيبقى الكاست من الجوال وكل خصائص التلفاز تعمل.
2. **صور خلفية:** 26 صورة للحرمين والمسجد الأقصى ومساجد والليل والطبيعة، داخل التطبيق دون إنترنت. تحت لون
   الواجهة، وتتغير كل 10 دقائق أو نصف ساعة أو ساعة أو مع كل صلاة أو كل يوم (تلقائيًا: الرئيسية كل يوم والاستراحة كل 10
   دقائق)، والداكنة فقط من العشاء حتى الفجر. في الرئيسية تبدأ بها، والنقش بديلها؛ وفي الاستراحة اختيارية (الإعدادات ← العرض).
3. **شاشة استراحة بكل المواقيت**: الساعة والطقس والتاريخان والصلاة القادمة، وفي الأسفل المواقيت الستة مع الإقامة. وزر "شاشة الاستراحة الآن" في الرئيسية.
4. **أذكار الصباح والمساء** على شاشة الاستراحة، من حصن المسلم بعدد كل ذكر ومصدره: في الوقت الممتد (الفجر إلى الظهر، والعصر إلى العشاء) أو الوقت الأفضل.
5. **تقويم الشهر الهجري** في الرئيسية من تقويم أم القرى، مع اليوم الميلادي تحت كل يوم، والعد التنازلي لرمضان والعيدين ويوم عرفة قبل 30 يومًا.
6. **حديث من الصحيحين** على شاشة الاستراحة خارج أوقات الأذكار، يتغير كل 10 دقائق، و**أذكار ما بعد الصلاة** 10 دقائق بعد كل صلاة، و**دعاء ما بعد الأذان** على شاشة الأذان، كلها محفوظة على التلفاز دون إنترنت. وتذكير مساءً بصيام الغد المسنون (الاثنين والخميس والأيام البيض وعاشوراء)، ومنتصف الليل والثلث الأخير في مواقيت اليوم. وتخفّ إضاءة شاشة الاستراحة ليلًا من بعد العشاء بساعتين إلى الفجر.
7. **الإقامة** تحت كل صلاة: حسب المعيار الرسمي للدولة، أو دقائق بعد الأذان، أو وقت ثابت، ولرمضان ضبطه المستقل.
   **وصلاة الجمعة** (مواقيت الصلاة): حسب الدولة (عند أذان الظهر، والإمارات 12:45 بتعميم الهيئة العامة للشؤون الإسلامية)،
   أو عند الظهر، أو موعد ثابت. **والإمساك في رمضان**: بلا افتراضيًا (السحور ينتهي عند الفجر)، أو 5 إلى 20 دقيقة قبله.
   وفي شاشة الاستراحة سطر ذهبي تحت العد التنازلي: من مغرب الخميس إلى مغرب الجمعة «سورة الكهف · الصلاة على النبي ﷺ»،
   وفي رمضان رقم اليوم ونهاية السحور (أو الإمساك)، و«الإفطار بعد» قبل المغرب و«السحور ينتهي بعد» في الليل.
   **وعدّاد الإقامة فوق التطبيقات** (اختياري، الإقامة): بطاقة صغيرة في زاوية الشاشة من بعد الأذان حتى الإقامة.
   **ومستوى أذان الفجر** (التنبيهات والأذان ← أذان الفجر): كامل أو 75% أو 50% أو 25% من صوت التلفاز، والبقية كاملة.
   **وحالة التطبيق** (حول التطبيق): هل الخدمة وشاشة الاستراحة تعمل، وآخر أذان شُغّل ومتى، والصلاحية والطقس والنسخة.
8. **الطقس** بجانب الساعة: الحرارة الآن وأيقونة للحالة (صافٍ، غائم، مطر...)، من Open-Meteo للمكان المختار، ويختفي دون إنترنت. يُوقف أو يُختار °م / °ف من الإعدادات.
9. **أي موقع في العالم**: في أول تشغيل يقترح التطبيق موقع الشاشة من خدمة الموقع فيها، أو ابحث بالعربية أو الإنجليزية في 34 ألف مدينة (من GeoNames)، فتُضبط طريقة الحساب حسب دولتها، أو أدخل الإحداثيات لمكان أصغر.
10. **التلاوات المحفوظة**: حمّل سور قارئك أو المصحف كاملًا من mp3quran.net لتُسمع من الإذاعة دون إنترنت، مع ترك 2 جيجابايت فارغة للتلفاز دائمًا. تُحفظ في مجلد `Music/Awan TV` على التلفاز، فتبقى إن أزلت التطبيق إلا إذا اخترت حذفها.
11. **التحكم في التشغيل** (الإعدادات ← تشغيل Awan TV): الكل، أو الرئيسية فقط، أو شاشة الاستراحة فقط، أو إيقاف. الأذكار تتبع شاشة الاستراحة، والأذان والتذكير لهما إعداداتهما.
12. **ألوان الواجهة**: لازوردي أو زمردي أو عنّابي أو فحمي أو رقّ فاتح، أو تلقائي (لازوردي نهارًا وفحمي بعد المغرب). وشاشة الاستراحة سوداء ما لم تُختر لها الصور.
13. **حماية الشاشة من الاحتراق**: الواجهة وشاشة الاستراحة تنزاحان بضع نقاط كل 3 دقائق.
14. من أفنون: حساب المواقيت دون إنترنت لـ 136 مدينة، والأذان فوق أي تطبيق، والتذكير قبل الصلاة (ومنطوقًا)، والقبلة، والجمعة ورمضان، وإذاعة القرآن الكريم.

## البناء (Android Studio على ويندوز)

1. **تسجيلات الأذان** (مرة واحدة، قبل البناء):
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\AwanTV-fetch-adhans.ps1
   ```
   و**التذكير المنطوق** (اختياري، يحتاج Python و`pip install edge-tts`):
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\AwanTV-fetch-announce.ps1
   ```
   دونها يظهر التذكير دون صوت. وهو لبنائك الخاص فقط: لا يحمله الإصدار المنشور، لأن الصوت ليس لنا لنوزّعه.
2. افتح مجلد المشروع في **Android Studio**، وانتظر انتهاء مزامنة Gradle. إن سأل عن نسخة Java لـ Gradle (Gradle JVM)
   فاختر **21**: نسخة Gradle في المشروع لا تعمل على Java الأحدث المضمّنة في Android Studio الحديث.
3. **Build ← Build App Bundle(s) / APK(s) ← Build APK(s)**: نسخة تجريبية موقّعة بمفتاح التصحيح في جهازك، تخرج في
   `app\build\outputs\apk\debug\app-debug.apk`.
   أو نسخة إصدار بمفتاحك: **Build ← Generate Signed App Bundle or APK ← APK** ← مفتاحك ← **release**، فتخرج في
   `app\release\AwanTV.apk`، وأداة التثبيت تختارها إن وُجدت. احفظ ملف المفتاح وكلمة مروره نسخة احتياطية خارج
   مجلد المشروع: ضياعه يمنع كل تحديث للشاشات التي ثُبّت عليها.

## التثبيت بأدوات المشروع (من الكود)

لمن بنى التطبيق بنفسه، من مجلد المشروع:

1. في التلفاز: **خيارات المطوّر** ← فعّل **Wireless debugging**، ثم اقترن واتصل بالتلفاز عبر `adb pair` و`adb connect`.
   الشاشات الأقدم (Android TV 11 وما قبله، مثل TCL BeyondTV) ليس فيها Wireless debugging: فعّل **USB debugging**، ثم
   `adb connect <TV address>:5555` (مكان `<TV address>` عنوان الشاشة في الشبكة) واقبل على الشاشة «Allow USB debugging?» مع «Always allow».
2. ثم:
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\AwanTV-install.ps1
   ```
   يثبّت التطبيق، ويمنح صلاحياته، ويجعله شاشة التوقف (ويحفظ شاشة التوقف السابقة)، ويشغّل خدمة Awan TV ويتأكد أنها
   تعمل فعلًا («Running: yes»). وإن اتصل أكثر من جهاز: `-Device <TV>`، ومكان `<TV>` جزء ثابت من اسمه، مثل `9Z8Y7X6W5V`.
   ولتثبيت ملف APK نزّلته من صفحة الإصدارات: `-Apk <file>`، ومكان `<file>` مسار الملف.
3. اضغط **Home** في الريموت. في أول تشغيل يقترح التطبيق موقع الشاشة، فتؤكده أو تختار موقعًا آخر.

**الإعدادات في ملف واحد من الحاسب** (`tools\AwanTV-settings.ps1`): كل إعداد في ملف `.jsonc` وفوقه شرح وقيمه وقيمته الافتراضية.
تعدّله في Notepad أو VS Code ثم ترسله إلى الشاشة نفسها أو إلى شاشة أخرى. الشاشة تفحص الملف كاملًا قبل أي تغيير:
أي خطأ يوقف الاستيراد وتظهر الأخطاء مرقّمة. وقبل الاستيراد تُحفظ إعدادات الشاشة نسخة احتياطية. لا يُنقل ما يخص كل جهاز
(شاشة استراحته السابقة، والتحميلات، والطقس).
```powershell
powershell -ExecutionPolicy Bypass -File tools\AwanTV-settings.ps1 -Export -Device <TV>
powershell -ExecutionPolicy Bypass -File tools\AwanTV-settings.ps1 -Import -Device <TV>
powershell -ExecutionPolicy Bypass -File tools\AwanTV-settings.ps1 -Import -File <file> -Device <TV>
```
الأول يحفظ إعدادات الشاشة في `state\settings`، والثاني يرسل إليها أحدث ملف محفوظ لها، والثالث يرسل ملفًا تحدده. مكان `<TV>` جزء
ثابت من اسم الشاشة، ومكان `<file>` مسار الملف.
1. أسماء الملفات في `state\settings`:
   - الإعدادات المصدَّرة: `awantv-settings-<TV serial>-<yyyyMMdd>-<HHmmss>.jsonc`
   - النسخة الاحتياطية قبل كل استيراد: `awantv-backup-<TV serial>-<yyyyMMdd>-<HHmmss>.jsonc`
   - في الاسم: الرقم التسلسلي للشاشة (`<TV serial>`)، ثم التاريخ (`<yyyyMMdd>`)، ثم الوقت (`<HHmmss>`).
2. **التراجع عن استيراد:** استورد النسخة الاحتياطية، والأداة تطبع هذا الأمر في نهاية كل استيراد:
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\AwanTV-settings.ps1 -Import -File state\settings\awantv-backup-<...>.jsonc -Device <TV>
   ```
3. حذف إعداد من الملف يُبقيه على الشاشة كما هو. والموقع يُنقل كما هو، وتغييره من التلفاز.
4. **إعادة ضبط الإعدادات** من الشاشة نفسها: الإعدادات ← حول التطبيق ← إعادة ضبط الإعدادات. تأكيدان: قائمة بما يُمسح وما يبقى،
   ثم ضغط مطوّل على OK ثلاث ثوانٍ. بعدها يبدأ التطبيق كأول تشغيل من اختيار الموقع. تبقى التلاوات المحفوظة وشاشة استراحة
   التلفاز السابقة. وقبل المسح تُحفظ الإعدادات على الشاشة بنسخة لكل إعادة ضبط (آخر خمس، ولا نسخة إن لم يتغيّر غير الموقع،
   فإعادة الضبط مرتين لا تجعل نسخة افتراضية أحدث من الصالحة). فإن كانت إعادة الضبط خطأً تستعيد أحدثها من الحاسب، وتُنسخ
   البقية إلى `state\settings` لتستورد أيًّا منها بـ `-Import -File`:
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\AwanTV-settings.ps1 -Restore -Device <TV>
   ```

**محرر الإعدادات على Windows** (`tools\AwanTV-editor.cmd`، انقر عليه مرتين): نافذة واحدة بنفس أقسام الإعدادات في التلفاز وترتيبها.
1. المصدر: «جلب من الشاشة» (من شاشة متصلة بـ adb) أو «فتح ملف».
2. التعديل بقوائم وأزرار ومفاتيح، لا كتابة يدوية. صفوف الإقامة تتبدّل حسب نوعها كما في التلفاز، وكل ما غيّرته يظهر بجانبه خط ذهبي.
3. الإرسال: «إرسال إلى» يختار شاشة أو أكثر (بحث، تحديد الكل، تسمية كل شاشة باسم يُحفظ في الحاسب)، و«حفظ في ملف»، و«استعادة نسخة».
4. قبل الإرسال تُحفظ إعدادات كل شاشة نسخة احتياطية، والشاشة تفحص كل ما يصلها. والشاشة الأقدم تأخذ ما تعرفه فقط.
5. يحتاج adb، ويأخذ وصف الإعدادات من الشاشة نفسها. ملفاته في `state\settings` و`state\editor`.
6. **الشاشات:** كل شاشة اتصلت ولو مرة تبقى في القائمة؛ غير المتصلة في مجموعة أسفل المتصلة مع «آخر اتصال». اسمها: تسميتك
   في المحرر (✎)، وإلا اسم الجهاز في إعدادات التلفاز، وإلا الطراز، والأسماء المتشابهة تُميَّز بآخر رقمها. **«تعريف»** يُظهر
   اسمها على الشاشة نفسها 10 ثوانٍ. الأسماء تبقى في الحاسب (`state\editor\tvs.jsonc`، يُحرَّر في Notepad
   أيضًا)، ولا يُكتب في الشاشات شيء منها.
7. **لغة المحرر** لمستخدمه لا للشاشة: لغة Windows أول مرة، ثم مفتاح «ع / EN» أعلى النافذة (يُحفظ اختيارك)، وأسماء الإعدادات
   وشروحها تتبعه.

**التراجع من الحاسب:**
```powershell
powershell -ExecutionPolicy Bypass -File tools\AwanTV-restore.ps1
powershell -ExecutionPolicy Bypass -File tools\AwanTV-restore.ps1 -Uninstall
```
الأول يوقف الخدمة ويعيد شاشة التوقف السابقة، والثاني يفعل ذلك ويحذف التطبيق أيضًا. والتلاوات المحفوظة تبقى، إلا مع `-RemoveRecitations`.

## ملاحظات مهمة

1. **مفتاح التوقيع:** ملفات APK في صفحة الإصدارات موقّعة بمفتاح إصدار واحد، وبصمة شهادته (SHA-256):
   `b445fc840602ca363c3ad83b60db22da1d04737633a47622d2405e97a3b50ed3`
   للتحقق من ملف نزّلته: `apksigner verify --print-certs <file>` (من build-tools في Android SDK، ومكان `<file>` مسار الملف)، ويجب أن يطابق
   سطر «SHA-256 digest» هذه البصمة. التلفاز لا يقبل التحديث إلا من نسخة موقّعة **بالمفتاح نفسه**: فالنسخة التي
   تبنيها بنفسك (بمفتاح التصحيح أو بمفتاحك) لا تُثبَّت فوق نسخة الإصدار ولا العكس إلا بعد إزالة التطبيق. صدّر
   الإعدادات أولًا (`AwanTV-settings -Export`)، ثم `AwanTV-restore -Uninstall`، ثم ثبّت، ثم استوردها (`-Import -File`).
2. **تسجيلات الأذان** (على طريقة أفنون): مجموعتها [abodehq/Athan-MP3](https://github.com/abodehq/Athan-MP3) لا تذكر ترخيصًا،
   فهي ليست ضمن الكود. أداة `AwanTV-fetch-adhans` تجلبها قبل البناء، ونسخة الإصدار (APK) تحملها مع نسبتها إلى مصدرها.
   من يملك حقوق تسجيل ويريد حذفه فليفتح Issue، ويُحذف.
3. **التشخيص:** `adb logcat -s AwanGuard` يبيّن متى ظهرت الشاشة الأصلية ومتى فُتح Awan TV فوقها.
4. **شاشات TCL القديمة (BeyondTV، Realtek، Android TV 11):** واجهتها Android TV القديمة (`tvlauncher`)، ونظامها 32-bit،
   وذاكرتها نحو 2 GB فهي بطيئة بطبعها. TCL تمنع خدمات التطبيقات المثبتة يدويًا من البدء إلا بإذنيها `APP_AUTO_START`
   و`APP_ASSOC_START` (أداة التثبيت تمنحهما). وبعد التثبيت بقيت الخدمة «مفعّلة» غير «مربوطة»، فكان زر Home يذهب إلى
   الواجهة الأصلية ولا يعود؛ صارت أداة التثبيت تتحقق من ذلك وتعيد الربط، والتطبيق نفسه يعيده عند فتح الرئيسية أو
   الاستراحة وبعد كل تحديث. وبعد إعادة التشغيل تبدأ الخدمة وحدها. وأطفئ USB debugging بعد الانتهاء (المنفذ 5555 غير مشفّر).
5. **أجهزة Google TV الحالية 32-bit** حتى بمعالج 64-bit (Google TV Streamer وشاشة TCL «Smart TV Pro» أيضًا:
   `adb shell getprop ro.product.cpu.abilist` يعطي `armeabi-v7a`). فالتطبيقات التي تُنزَّل يدويًا تؤخذ بنسختها arm
   (32-bit)، لا arm64. Awan TV مكتوب بـ Java فيعمل على الاثنين.
6. **تنظيف الشاشة من الإعلانات والتتبّع:** `tools\clean\README.md` (نسخة احتياطية، ثم تعطيل لا حذف، ثم تراجع).
7. **أجهزة البث الموصولة بشاشة (Google TV Streamer) ومكبّر الصوت:** أندرويد نفسه يرسل مع كل ضغطة Home أمر HDMI-CEC
   «التشغيل بلمسة واحدة» (تشغيل الشاشة، وإعلان المصدر، وطلب نظام الصوت من المكبّر)، ولا إعداد يمنعه. ومكبّر صوت على
   منفذ ARC في الشاشة قد يصمت معه في كل مرة. فإن حدث هذا عندك ففعّل «زر Home يفتح Awan TV مباشرة» في
   الإعدادات > إعدادات الجهاز (يظهر على أجهزة البث فقط، ومطفأ افتراضيًا): يأخذ Awan TV الزر بنفسه ويفتح الرئيسية، فلا
   يصل الأمر، لكن ضغطة Home لا تحوّل الشاشة إلى مدخل الجهاز حينها، ولا يتكرر رفع الصوت بالضغط المطوّل (أندرويد لا يكرر زر
   الصوت بعد أن يمر بخدمة إمكانية الوصول، ولا يستطيع تطبيق أن يرسل أمر الصوت عبر HDMI بنفسه): يُرفع بضغطات متتالية، أو
   اضبط زر الصوت في ريموت الجهاز على الأشعة (IR) للمكبّر. الضغط المطوّل على Home يفتح واجهة الجهاز الأصلية.
   الشاشات نفسها (TCL وBeyondTV) لا يتغير فيها شيء. أما المكبّر الذي يعمل بلا صوت عند التشغيل فسببه أن الجهاز يوقظ
   المكبّر والشاشة معًا، فيطلب المكبّر وصلة الصوت (ARC) والشاشة لم تجهز بعد. لتجنّبه: شغّل الشاشة أولًا بريموتها، ثم أيقظ
   الجهاز فيشغّل المكبّر بعد أن تجهز الشاشة (الشاشة ثم المكبّر، عكس توصية TCL العامة). وإن صمت: غيّر مدخل الشاشة إلى
   مدخل آخر ثم أعده (أو أطفئ المكبّر وشغّله)، ولا يستطيع تطبيق فعل ذلك عنك.
8. **الخصوصية:** لا يتصل التطبيق إلا بالإذاعة التي تشغّلها، وبـ Open-Meteo للطقس (إحداثيات المكان المختار فقط، كل 30 دقيقة أثناء ظهور الشاشة)، وبـ mp3quran.net حين تستخدم التلاوات المحفوظة أو تفتح الإعدادات ← الإذاعة، وبـ GitHub مرة في اليوم ليعرف هل صدر إصدار جديد (لا يُرسل شيء عن الشاشة). إيقاف الطقس (الإعدادات ← العرض واللغة) أو الإصدارات الجديدة (الإعدادات ← حول التطبيق) يوقف اتصاله. وموقع الشاشة يُقرأ في أول تشغيل فقط ليقترح موقعك، ولا يُرسل إلى أي مكان. وصلاحية تسجيل الصوت (من أفنون) لأشرطة مستوى صوت الإذاعة فقط: تقرأ صوت التطبيق نفسه وهو يشغّل الإذاعة، لا صوت الغرفة، ولا يُحفظ منها شيء ولا يُرسل.

## الشكر والترخيص

تطوير عمر أحمد الحويرث

https://awanpro.app

مبني على تطبيقي [أفنون](https://github.com/egyjs/afnon-android-tv-launcher) و[Masjid TV](https://github.com/obaid/masjid-tv-tcl) (ترخيص MIT)

الترخيص في `LICENSE`، وما استُعمل من غيرنا (الخطوط والمكتبات والتسجيلات والصور) في `THIRD_PARTY.md`

</div>

---

# Awan TV (English)

**Prayer times, adhkar and more — a home screen and screensaver for Android TV and Google TV smart TVs, boxes and streamers.**

Got a smart TV running Android TV or Google TV, or a device with it such as Chromecast, Google TV Streamer or a TV box
like the Xiaomi Mi Box, whose home screen just sits there, saying and offering nothing?

What if it reminded you of every prayer and its iqamah, showed today's Hijri and Gregorian dates in a monthly calendar
of both, brought you the morning and evening adhkar at their times, played the adhan over whatever you are watching,
and told you how long until the iqamah?

That is what **Awan TV** brings you, and more.

[العربية](#awan-tv)

| | |
|:---:|:---:|
| ![Home, Lapis theme](docs/images/home-photo-en.webp) | ![Home, Charcoal theme](docs/images/home-charcoal-en.webp) |
| Home, Lapis theme | Home, Charcoal theme |
| ![Screensaver with a hadith](docs/images/saver-photo-en.webp) | ![Screensaver during the morning adhkar](docs/images/saver-adhkar-en.webp) |
| Screensaver: a hadith from al-Bukhari and Muslim | Screensaver: the morning adhkar |
| ![Iqamah countdown over another app](docs/images/iqamah-overlay-en.webp) | ![Settings](docs/images/settings-en.webp) |
| Iqamah countdown over any app (optional) | Settings, all by remote |
| ![First start: choose your location](docs/images/first-location-en.webp) | ![Saved recitations](docs/images/recitations-en.webp) |
| First start: choose your location, suggested by the TV | Saved recitations, to listen offline |

<p align="center"><img src="docs/images/editor-en.webp" alt="The settings editor on Windows" width="80%"></p>
<p align="center">The settings editor on Windows: get them from a TV, send them to one or more</p>

> Illustrations drawn from the app's own design; the apps, times and weather on your TV differ. The background (pattern
> or photos) and the theme (six of them) are in Settings > Display & language; the iqamah countdown in Settings > Iqamah.

## In short

1. **Home over the device's own home screen**, with nothing disabled.
2. **A screensaver** with all six times, iqamah, weather and both dates, and the morning and evening adhkar and hadiths
   from al-Bukhari and Muslim, each with its source.
3. **The adhan over any app**, reminders before the prayer, and the iqamah countdown.
4. **Any location in the world**; the times are worked out on the TV, offline.
5. **Quran radio**, and saved recitations to listen to offline.
6. **Arabic and English**, and six colour themes.

## Install

Three ways; choose one. Every file is on the [releases page](../../releases/latest).

**On the TV first (ways 1 and 2):**
1. Turn on Developer options: Settings > System > About, and press OK 7 times on "Android TV OS build" until the TV
   says you are now a developer. On older TVs: Settings > Device Preferences > About > Build.
2. In Developer options turn on "Wireless debugging", or "USB debugging" on older TVs without it.

### 1. From a phone (the easiest)
1. Install [AwanTVInstaller.apk](../../releases/latest/download/AwanTVInstaller.apk) on your Android phone, on the same
   Wi-Fi as the TV.
2. Open it, choose your TV, then "Install Awan TV". If the TV asks to allow it or shows a code, accept or type the code
   in the installer.

### 2. From a Windows 10 or 11 computer
1. Download [AwanTV-Windows.zip](../../releases/latest/download/AwanTV-Windows.zip) and unzip it into a folder.
2. Double-click `AwanTV-install.cmd`. It asks for the TV's address and connects; with no adb it offers to download it
   from Google. If Windows shows a security warning, choose Run: it shows one for every file downloaded from the
   internet. The details are in `README.txt` in the folder.

### 3. By hand, with no phone or computer
1. Install [AwanTV.apk](../../releases/latest/download/AwanTV.apk) on the TV any way you like (unknown sources allowed is
   enough).
2. Turn the service on: the TV's Settings > System > Accessibility > Awan TV. (Some Android 13 and later devices block
   this for an app from outside the store; then use way 1 or 2.)
3. The screensaver: Awan TV opens its own after idle minutes on Home. Making it the TV's screensaver in every app needs
   way 1 or 2 on Google TV.

**After installing:** press Home on the remote. On the first start the app suggests the TV's location; confirm it or
choose another. You may switch debugging off in Developer options, and on again to update.

**Updating:** the same way with the new release; the settings and saved recitations stay. The app tells you about a
new release in Settings > About (and is turned off there), or ask any time: Settings > About > Check for updates now.

**Removing:** Settings > Awan TV > **Remove Awan TV** puts back the earlier screensaver, asks whether any saved
recitations stay or go, then opens the uninstall. Or from a computer: `AwanTV-restore.cmd`. If the app is removed from
the TV's own settings, the TV goes back to its default screensaver by itself.

## What it does

1. **Home over the TV's own home screen**: whenever the TV's launcher shows its home screen, Awan TV opens over it.
   Nothing is disabled, so casting from phones and every TV feature keep working.
2. **Screensaver with all six times**: clock, weather, both dates, next prayer, and every prayer with its iqamah;
   also "Screensaver now" on Home.
3. **Morning and evening adhkar** on the screensaver, from Hisn al-Muslim with each one's count and source.
4. **The Hijri month** on Home by the Umm al-Qura calendar, the Gregorian day under each day, and a countdown to
   Ramadan, the two Eids and the Day of Arafah from 30 days before.
5. **A hadith from al-Bukhari and Muslim** on the screensaver outside the adhkar times, every ten minutes; **the
   adhkar after the prayer** for ten minutes after each prayer; **the supplication after the adhan** on the call
   screen; all kept on the TV (no network). An evening reminder of tomorrow's sunnah fast (Mondays, Thursdays, the
   White Days, Ashura), and the middle and last third of the night on Today & Qibla. The screensaver dims at
   night, from two hours after Isha until Fajr.
6. **Iqamah** under each prayer: the country's official wait, minutes after the adhan, or a fixed time; Ramadan apart.
   **Friday prayer** (Prayer times): by country (at the Dhuhr adhan; the UAE 12:45 by the Islamic Affairs authority's
   circular), at Dhuhr, or a fixed time. **Imsak in Ramadan**: none by default (suhoor ends at Fajr), or 5 to 20
   minutes before it. On the screensaver a gold line under the countdown: from Thursday's Maghrib to Friday's "Surat
   Al-Kahf · Salawat on the Prophet ﷺ"; in Ramadan the day and when suhoor (or the imsak) ends, "Iftar in" before
   Maghrib and "Suhoor ends in" at night. **The iqamah countdown over apps** (optional, Iqamah): a small card in a
   corner from the adhan until the iqamah. **Fajr's adhan level** (Reminders > Fajr adhan): full, 75%, 50% or 25% of
   the TV's sound, the others full. **App status** (About): whether the service and the screensaver work, the last
   adhan played and when, the permission, the weather and the version.
7. **Weather** by the clock: the temperature and an icon for the sky, from Open-Meteo for the chosen place; hidden
   when offline; off, °C or °F in Settings.
8. **Any location in the world**: on the first start the app suggests the TV's own location from its location
   service; or search 34,000 cities (GeoNames) in Arabic or English, whose country's method comes with them; or enter
   coordinates for a smaller place.
9. **Saved recitations**: download a reciter's surahs, or the whole Quran, from mp3quran.net and play them from the
   radio without internet; 2 GB are always left free for the TV. They are kept in `Music/Awan TV` on the TV, so they
   stay if you remove the app, unless you choose to delete them.
10. **What runs** (Settings > Awan TV): all, Home only, the screensaver only, or off. The adhkar follow the
   screensaver; the adhan and reminders keep their own settings.
11. **Colour themes**: Lapis, Emerald, Burgundy, Charcoal or a light Parchment, or Automatic (Lapis by day,
   Charcoal after Maghrib).
12. **Background photos** (Settings > Display): 26 photos of the Haramain, al-Aqsa, mosques, the night
   sky and nature, in the app. Home shows them under the theme's colour; they change every 10 or 30 minutes, every
   hour, with each prayer or daily (Automatic: Home daily, the screensaver every 10 minutes), only the dark ones from
   Isha until Fajr. Home starts with them (the pattern is the other choice); on the screensaver they are optional,
   otherwise it stays black.
13. **Burn-in care**: Home and the screensaver shift a few pixels every three minutes.
14. From Afnon: offline prayer times for 136 cities, the adhan over any app, reminders (also spoken), Qibla, Friday
   and Ramadan modes, Quran radio.

## Build (Android Studio on Windows)

1. Adhan recordings, once: `powershell -ExecutionPolicy Bypass -File tools\AwanTV-fetch-adhans.ps1`. Spoken reminders
   (optional, for your own build only; needs Python and `pip install edge-tts`):
   `powershell -ExecutionPolicy Bypass -File tools\AwanTV-fetch-announce.ps1`. Without them the reminders show without a voice.
2. Open the folder in Android Studio and let Gradle sync. If it asks for a Gradle JVM, choose **21**: the project's
   Gradle does not run on the newer Java bundled with recent Android Studio.
3. Build > Build App Bundle(s) / APK(s) > Build APK(s) → `app\build\outputs\apk\debug\app-debug.apk`, signed with
   your debug key. Or a release with your own key: Build > Generate Signed App Bundle or APK > APK > your key >
   release → `app\release\AwanTV.apk`, which the install tool prefers when it is there. Keep the key file and
   its password backed up outside the project: without them no TV it is installed on takes an update.

## Install with the project's tools (from the source)

For a build of your own, from the project folder: pair and connect adb to the TV (Developer options > Wireless debugging), then
`powershell -ExecutionPolicy Bypass -File tools\AwanTV-install.ps1` (`-Device <serial>` when more than one is connected,
`-Apk <file>` for an APK downloaded from the releases page).
It checks that the service actually runs ("Running: yes"). Older TVs (Android TV 11 and before, such as TCL BeyondTV)
have no Wireless debugging: turn on USB debugging, `adb connect <TV address>:5555` and accept the prompt on the TV.
Undo with `tools\AwanTV-restore.ps1` (`-Uninstall` also removes the app; saved recitations stay unless
`-RemoveRecitations`).
Every setting in one commented file: `tools\AwanTV-settings.ps1 -Export -Device <TV>` saves a `.jsonc` in
`state\settings`, each setting with what it is, its values and its default; edit it and send it back (or to another
TV) with `-Import [-File <file>] -Device <TV>`. The TV checks the whole file first (one mistake and nothing changes,
the mistakes listed) and the tool keeps a backup of the TV's settings before it. Not in the file: what belongs to one
TV (its old screensaver, downloads, the weather read).
```powershell
powershell -ExecutionPolicy Bypass -File tools\AwanTV-settings.ps1 -Export -Device <TV>               # to state\settings
powershell -ExecutionPolicy Bypass -File tools\AwanTV-settings.ps1 -Import -Device <TV>               # the newest saved file
powershell -ExecutionPolicy Bypass -File tools\AwanTV-settings.ps1 -Import -File <file> -Device <TV>  # a given file, or a backup to undo
```
Files: `awantv-settings-<TV serial>-<yyyyMMdd>-<HHmmss>.jsonc`; before each import the TV's own settings as
`awantv-backup-<TV serial>-<yyyyMMdd>-<HHmmss>.jsonc` (the tool prints the undo command). A setting left out of the
file stays as it is; the location is copied as it is (change it on the TV).
The settings editor for Windows (`tools\AwanTV-editor.cmd`, double-click): one window in the TV's Settings order. Get the
settings from a connected TV or open a file; change them with lists, switches and steppers (Iqamah's rows follow its mode
as on the TV; changes marked in gold); send them to one or more TVs (search, all, a name for each TV kept on the
computer), save them to a file, or restore a copy. Each TV's settings are backed up before it receives new ones; an
older TV gets only what it knows. Needs adb; the description of the settings comes from the TV.
Every TV met stays listed (those not connected below the others, with when they were last seen); a TV's name is yours
(✎), else its own device name, else its model, alike names told apart by the end of the serial; Identify shows a TV's
name on its own screen for 10 seconds. Names stay on the computer (`state\editor\tvs.jsonc`, editable
in Notepad); nothing is written to the TVs.
The editor's language is its user's, not the TV's: Windows' display language at first, then the ع / EN switch at the
top (kept); the settings' names and help follow it.
Settings > About > Reset settings puts every setting back to a new install's (two confirmations: what goes and what
stays, then OK held for three seconds) and starts from the location; saved recitations and the TV's earlier screensaver
stay. The settings are kept on the TV first, one copy per reset (the last five; none when nothing but the location is
set, so two resets in a row keep the good copy newest): `tools\AwanTV-settings.ps1 -Restore -Device <TV>` brings back
the newest and saves every copy in `state\settings` for `-Import -File`.

## Notes

1. The APKs on the releases page are signed with one release key, whose certificate SHA-256 is
   `b445fc840602ca363c3ad83b60db22da1d04737633a47622d2405e97a3b50ed3`.
   To check a downloaded file: `apksigner verify --print-certs <file>` (Android SDK build-tools); its "SHA-256 digest"
   line must match. TVs only accept updates signed with the same key, so a build of your own (debug key or your key)
   and a release do not install over each other without removing the app first: export the settings
   (`AwanTV-settings -Export`), `AwanTV-restore -Uninstall`, install, then import them (`-Import -File`).
2. Adhan recordings, as Afnon does: their collection, [abodehq/Athan-MP3](https://github.com/abodehq/Athan-MP3),
   states no licence, so they are not in the source. `AwanTV-fetch-adhans` downloads them before building, and the
   release APK carries them, credited to that collection. If you hold the rights to a recording and want it removed,
   open an issue and it will be.
3. `adb logcat -s AwanGuard` shows when the TV's home screen came up and Awan TV opened over it.
4. Older TCL (BeyondTV, Realtek, Android TV 11): the old Android TV launcher (`tvlauncher`), a 32-bit system and about
   2 GB of memory, so slow by nature. TCL keeps a sideloaded app's services from starting without its own app ops
   `APP_AUTO_START` and `APP_ASSOC_START` (the install tool grants them). After the install the service stayed enabled
   but unbound, so Home went to the stock launcher and stayed there: the install tool now checks and binds it again,
   and the app itself does on Home, on the screensaver and after an update (`ServiceCheck`). After a reboot it starts
   on its own. Switch USB debugging off afterwards (port 5555 is unencrypted).
5. Today's Google TV devices run a 32-bit system even on a 64-bit chip (the Google TV Streamer and the TCL "Smart TV
   Pro" too: `getprop ro.product.cpu.abilist` gives `armeabi-v7a`), so sideloaded apps take their arm (32-bit) build.
6. Cleaning a TV of ads and tracking: `tools/clean/README.md` (backup, disable rather than delete, undo).
7. HDMI source boxes (Google TV Streamer) and a soundbar: Android itself sends HDMI-CEC One Touch Play on every Home
   press (Text View On, Active Source and a System Audio Mode Request to the soundbar), and no setting stops it. A soundbar
   on the TV's ARC may go silent on it every time. If yours does, turn on "Home button opens
   Awan TV directly" in Settings > Device settings (source boxes only, off by default): Awan TV takes the button and
   opens Home itself, so the request is never sent, though Home no longer switches the TV to the box's input then,
   and holding Volume moves it one step only (Android does not repeat a volume key that went through an
   accessibility service, and no app may send the HDMI-CEC volume itself): press it again, or set the remote's volume
   buttons to IR for the soundbar. Holding Home opens the box's own home screen. TVs themselves (TCL, BeyondTV) are
   unchanged. A soundbar that is on but silent at switch-on: the box wakes the soundbar and the TV together, and the
   soundbar asks for the audio return channel (ARC) before the TV is ready. To avoid it, switch the TV on first with
   its own remote, then wake the box, which switches the soundbar on once the TV is ready (TV first, then the soundbar:
   the opposite of TCL's general advice). If it is silent: switch the TV to another input and back (or power the
   soundbar off and on); no app can do that for you.
8. Privacy: the app connects only to the radio station you play and, for the weather, to Open-Meteo (the chosen
   place's coordinates, every 30 minutes while Home or the screensaver is on screen), to mp3quran.net when you
   use Saved recitations or open Settings > Radio, and to GitHub once a day to see whether a new release is out (nothing about the TV is
   sent). Turning the weather (Settings > Display & language) or new versions (Settings > About) off stops its connection. The TV's location is read
   on the first start only, to suggest yours, and is sent nowhere. The record-audio permission (from Afnon) is for the
   radio's level bars only: it reads the app's own sound while the radio plays, not the room, and nothing is kept or sent.

## Credits and licence

Developed by OMAR A ALHWAIRTH

https://awanpro.app

Built on [Afnon](https://github.com/egyjs/afnon-android-tv-launcher) and [Masjid TV](https://github.com/obaid/masjid-tv-tcl) (MIT licence)

The licence is in `LICENSE`; what others made (fonts, libraries, recordings, photos) is in `THIRD_PARTY.md`
