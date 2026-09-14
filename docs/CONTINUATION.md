# DarbakMaps — CONTINUATION

آخر تحديث: 2026-09-14  
الفرع: `darbakmaps-review-0.9.1`  
القاعدة الأصلية لهذه الجولة: `7c30c5fdb394997c228a1efcb39cf11aff318841`  
الحزمة: `com.abosultan.darbakmaps.debug`  
الحالة: **مرشح اختبار ميداني قيد بوابة الإصدار؛ ليس Production**.

## ما اكتمل في هذه الجولة
- الرسم يتبع journal committed فقط؛ لا raw-GPS drawing من MainActivity.
- journal generation + `TrackRenderGate` يمنع preview قديمًا من استبدال أحدث.
- لا مسح كامل عند 4000 نقطة؛ preview محدود ويحافظ على bends/gaps.
- سياسة طبقات واضحة لكثرة المقاطع: كل الحديث + عينة موزعة من التاريخ القديم.
- العلامات المحفوظة spatial حسب viewport بدل أول 250، مع tap للوجهة الدقيقة وcluster chooser.
- SavedPlacesDialog يستخدم stable IDs، ويحافظ على ترتيب الصفوف أثناء touch/scroll، ويصفر حالة GPS فور فقد الصلاحية.
- Offline search انتقل إلى atomic disk shards بلا hard item cap، مع فصل index/query states وKeyset paging بذاكرة O(page).
- التطويل العربي يحذف من الكلمة؛ Way nearby يعتمد أقرب نقطة من الهندسة.
- نتائج البحث تظهر كطبقة محدودة على الخريطة، والضغط المطول على نقطة يتيح البحث حولها.
- LegacyMigration يملك transaction evidence دائمًا وrollback/recovery عند startup؛ legacy active track يبقى GPX منفصلًا.

## أدلة ناجحة
- Track source-of-truth: workflow `34887402112` SUCCESS.
- Saved places/touch/GPS: `34887515362` SUCCESS.
- Migration recovery code/tests/compile: `34887602753` SUCCESS.
- Search shards/geometry/tatweel: `34888101861` SUCCESS. (Run سابق `34888034380` توقف في patch بسبب test directory مفقود ولم يصل Gradle؛ أصلح.)
- Keyset paging: `34888316301` SUCCESS، 95 نتيجة عبر 40+40+15 بلا تكرار.
- Whole-history display + >250 saved: `34888533307` SUCCESS.
- Approved Saudi map acceptance: `34888782778` SUCCESS.

## نقطة الاستكمال الدقيقة
1. ارفع `versionName/versionCode` إلى 0.9.1/22 فقط بعد بقاء جميع الاختبارات أعلاه ناجحة.
2. شغّل من **commit واحد**:
```bash
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:lintRelease
./gradlew --no-daemon :app:assembleRelease
```
3. سجّل SHA وartifact metadata في `RELEASE_ACCEPTANCE.md`.
4. وقّع Field Candidate خارج المستودع فقط؛ تحقق باستخدام `apksigner verify --print-certs` إن توفر Android build-tools.
5. لا تفتح Production/update-manifest حتى مقارنة شهادة APK المثبت على T3 واختبار update-in-place مع بقاء البيانات.

## اختبارات لا يجوز الادعاء بنجاحها بعد
- process kill فعلي وسط LegacyMigration.
- screen-off/reboot/Force Stop/storage-full/power interruption على T3.
- نقر 3 علامات سمان فعلية على الخريطة والتأكد من كل وجهة.
- RAM طويل مع recording+search+navigation على T3.
- شهادة التطبيق المثبت والتحديث فوقه مع بقاء البيانات.

## عدم التراجع
- لا تعيد `mapController.addTrackPoint(raw GPS)` إلى MainActivity.
- لا تعيد clear كامل للرسم عند 4000.
- لا تعد لأول 250 محفوظًا.
- لا تستخدم offset paging المتزايد الذاكرة بدل Keyset.
- لا تغير package suffix لتجاوز التوقيع ولا تحذف النسخة القديمة.
