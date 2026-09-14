# DarbakMaps — CONTINUATION

آخر تحديث: 2026-09-14  
المرجع الأعلى: `docs/IMPLEMENTATION_ORDER.md`  
الفرع: `darbakmaps-final-0.8.0`  
الإصدار الجاري: **0.9.0 / versionCode 21**  
الحزمة: `com.abosultan.darbakmaps.debug`

> هذه نسخة تطوير مرشحة للاختبار الميداني، وليست Production حتى يثبت توافق التوقيع واختبار T3.

## ما اكتمل في الكود
- سجل تلقائي مستمر يحتفظ بأحدث ~1000 كم حسب المسافة المتصلة، مع trim تدريجي و`fsync` وpending/backup/recovery.
- حفظ نسخة GPX دون إيقاف أو حذف السجل التلقائي.
- خدمة GPS بخيط/Looper صحيح، عدم إسقاط writes المقبولة قبل الإيقاف، فجوة جديدة بعد restart، ومرشح للقفزات وانحراف الوقوف.
- BootReceiver يعيد الخدمة فقط ولا يفتح Activity.
- LegacyMigration بفحص كامل مؤقت قبل live state، تحقق هوية/حجم/مساحة، lock مشترك، sanitization للـruntime state، وrollback.
- RecommendedMapDownloader لا يحذف backup إلا بعد تحقق كامل من البديل.
- الرسم محدود النقاط والطبقات مع حفظ gaps، وأيقونات المحفوظات مرسومة Canvas ثابتة.
- البحث بفهرس محلي متدرج على القرص مربوط بهوية map؛ يدعم POI وWay/area وaliases والتطبيع العربي والفئات والبحث القريب.
- حفظ المواقع icon-first والاسم اختياري مع auto-label مؤرخ وundo delete.
- لوحة المحفوظات: filters، nearest، distance، direction relative/smoothing، cardinal fallback، وعدم reorder أثناء touch.
- البحث حول موقعي/حول معلم: 5/10/25/50/100 كم، مع عرض/حفظ/توجيه/بحث حول النتيجة.
- Backtrack لا يختار هدفًا افتراضيًا إذا لا يوجد segment متصل.

## اختبارات مؤكدة
- workflow `34883690007`: `testDebugUnitTest` + `compileDebugJavaWithJavac` = SUCCESS لتكامل التسجيل التلقائي.
- workflow `34883845877`: tests + compile = SUCCESS للأيقونات الثابتة وحدود طبقات الرسم.
- workflow `34884015788`: tests + compile = SUCCESS لتكامل المحفوظات والبحث القريب.
- `TrackJournalRetentionTest`: رحلة صناعية >1200 كم، بقاء ~1000 كم، gap لا يحسب، واستعادة backup.
- `DirectionMathTest`: 359↔0، smoothing الدائري، والاتجاهات الجغرافية.
- `TrackNavigatorTest`: أضيف no-connected-segment؛ يجب إثبات دخوله في Final Candidate بعد `7f34fca`.

## الخطوة التالية
1. افحص Final Candidate الناتج عن `0.9.0/21` ويجب أن ينفذ من نفس commit:
```bash
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:lintRelease
./gradlew --no-daemon :app:assembleRelease
```
2. أصلح أي regression قبل تسليم APK.
3. حدّث `REPORT_IMPLEMENTATION_STATUS.md` و`RELEASE_ACCEPTANCE.md` برقم run/commit نفسه.
4. لا تنشر `update-manifest.json` إلى APK unsigned.
5. Production يتطلب فحص شهادة APK المثبت على الشاشة ثم signed build بنفس الشهادة واختبار update-in-place وبقاء البيانات.

## عوائق/اختبارات غير مثبتة
- شهادة النسخة المثبتة على T3: غير متحققة في هذه الجولة؛ حاجز Production.
- update-in-place وبقاء البيانات: لم يُنفذ.
- reboot/screen-off/force-stop/GPS loss/storage-full/process-kill أثناء trim على T3: لم يُنفذ ميدانيًا.
- قياس RAM/زمن البحث بالخريطة السعودية الفعلية على T3: لم يُنفذ.
- عينات مثبتة من ملف الخريطة لكل فئة بحث: لم توثق بعد.
- clustering للعلامات الكثيفة: لم ينفذ بعد.
- بوصلة موثوقة عند الوقوف: غير مستخدمة حاليًا؛ يعرض اتجاهًا جغرافيًا بدل سهم نسبي مضلل.

## عدم التراجع
- لا تحذف `active-track.csv` عند حفظ snapshot.
- لا تعيد BootReceiver لفتح MainActivity.
- لا تغير package suffix لتجاوز التوقيع.
- لا تحذف backup الخريطة لمجرد وجود target غير فارغ.
- لا تستورد runtime prefs القديمة في LegacyMigration.
- لا تستبدل Canvas icons بإيموجي.
