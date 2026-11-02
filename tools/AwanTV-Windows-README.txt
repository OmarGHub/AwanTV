Awan TV for Windows 10 and 11
Awan TV لـ Windows 10 و11
https://github.com/OmarGHub/AwanTV
https://awanpro.app

==================== العربية ====================

على التلفاز أولًا:
1. افتح: الإعدادات › النظام › حول (أو لمحة)، واضغط OK سبع مرات على «إصدار نظام تشغيل Android TV»
   حتى تظهر رسالة أنك أصبحت مطوّرًا.
2. افتح: الإعدادات › النظام › خيارات المطوّرين، وفعّل «تصحيح أخطاء USB»،
   وإن وجدت «تصحيح الأخطاء اللاسلكي» ففعّله أيضًا.
3. اجعل التلفاز والحاسوب على شبكة الواي فاي نفسها، واعرف عنوان التلفاز من: الإعدادات › الشبكة › شبكتك.

ثم على الحاسوب:
4. انقر نقرتين على AwanTV-install.cmd
   - إن ظهر تحذير Windows فاختر «تشغيل»: يظهر مع كل ملف نُزّل من الإنترنت.
   - إن لم يجد adb يعرض تنزيله من Google إلى المجلد platform-tools هنا.
   - يطلب عنوان التلفاز ويتصل به. ومع «تصحيح الأخطاء اللاسلكي» يطلب أيضًا رمز الإقران من التلفاز.

للإزالة وإعادة شاشة الاستراحة السابقة: انقر نقرتين على AwanTV-restore.cmd

لا تحذف المجلد state: فيه شاشة الاستراحة التي كانت على تلفازك، لإعادتها عند الإزالة.
وعند التحديث فُكّ الإصدار الجديد فوق هذا المجلد، فيبقى state و platform-tools كما هما.

==================== English ====================

On the TV first:
1. Open Settings › System › About and press OK 7 times on "Android TV OS build",
   until the TV says you are now a developer.
2. Open Settings › System › Developer options and turn on "USB debugging",
   and "Wireless debugging" too if the TV has it.
3. Keep the TV and the computer on the same Wi-Fi, and find the TV's address in Settings › Network › your Wi-Fi.

Then on the computer:
4. Double-click AwanTV-install.cmd
   - If Windows shows a warning, choose Run: it shows one for every file downloaded from the internet.
   - If adb is not found, it offers to download it from Google into the platform-tools folder here.
   - It asks for the TV's address and connects; with Wireless debugging it also asks for the TV's pairing code.

To remove Awan TV and put back the screensaver you had: double-click AwanTV-restore.cmd

Keep the state folder: it holds the screensaver your TV had, to put back on removal.
To update, unzip the new version over this folder; state and platform-tools stay as they are.
