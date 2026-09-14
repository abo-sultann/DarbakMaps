# DarbakMaps — RELEASE ACCEPTANCE

المرشح الجاري: **0.9.1 / vc22 بعد بوابة الإصدار**  
الحزمة: `com.abosultan.darbakmaps.debug`  
الحالة: **Field Candidate فقط** حتى اختبارات T3 والتوقيع المثبت.

## 1. الرسم والتسجيل
- ✅ **اختبار سلوك:** `TrackJournalPreviewTest` يكتب >6000 نقطة مع bend قوي وgap ويتحقق أن preview لا يختفي، يبقى محدودًا، ويحافظ على gap/bend/latest. Workflow `34887402112`.
- ✅ **اختبار سلوك:** `TrackRenderGateTest` يمنع async load أقدم من استبدال generation أحدث.
- ✅ المصدر الوحيد للرسم أصبح snapshot من journal بعد commit؛ MainActivity لا يرسم raw GPS.
- ✅ **اختبار سلوك:** `TrackDisplayPolicyTest` مع 1000 segment / cap 160 يحتفظ بالبداية والنهاية وكل المقاطع الحديثة وعينة موزعة من التاريخ الأقدم. Workflow `34888533307`.
- ⬜ T3: فقد/عودة GPS + trim >1000km + background/resume مع مطابقة بصرية للسجل.

## 2. المحفوظات
- ✅ **اختبار سلوك:** `StableIdOrderTest` يثبت الحفاظ على ترتيب IDs أثناء تحديث بيانات الصفوف. Workflow `34887515362`.
- ✅ null/stale GPS يتجاوز throttle فورًا، يمسح location/smoothing ويعرض انتظار GPS.
- ✅ click/long-click يقرأ stable place ID من repository، لا index متغير.
- ✅ map Marker tap يوجه لموقع منفرد؛ تداخل عدة علامات يفتح chooser ولا يختار عشوائيًا.
- ✅ **اختبار سلوك:** `SavedPlaceSpatialSelectorTest` ينشئ 300 موقع ويثبت أن الموقع الأخير يظهر عند تحريك viewport إليه. Workflow `34888533307`.
- ⬜ T3: حفظ 3 سمان بلا أسماء والنقر على كل أيقونة فعلية + scroll/touch متكرر.

## 3. البحث
- ✅ لا يوجد hard cap يوقف index وسط tile؛ الفهرس sharded على القرص (`pending → fsync → rename`).
- ✅ اكتمال index منفصل عن read failure وعن query-more؛ progress يُشتق من shards الصالحة، فلا تكفي state قديمة لإعلان complete.
- ✅ لا `readIds` شامل؛ query يحتفظ بصفحة محدودة فقط.
- ✅ **اختبار سلوك:** `سـمان` يساوي `سمان` بعد حذف التطويل لا تحويله لمسافة. Workflow `34888101861`.
- ✅ **اختبار سلوك:** Way يعبر مركز البحث يلتقط بقرب الهندسة حتى لو endpoints بعيدة.
- ✅ **اختبار سلوك:** Keyset pagination يصل إلى 95 نتيجة عبر `40 + 40 + 15` بلا تكرار وبذاكرة O(page). Workflow `34888316301`.
- ✅ نتائج الصفحة تظهر أيضًا على الخريطة بحد عرض، والبحث حول نقطة map متاح من long press.

### خريطة السعودية الفعلية
Workflow `34888782778` استخدم asset المعتمد نفسه:
- SHA-256: `409d7ecaf2d6c610921cfadd42855fbdd3ce63ae08a00ff94965b18bb25fdd1c`
- الحجم: `189923374` bytes.
- نتائج مجمعة لدوائر 100km حول الرياض/القصيم/حائل: خدمات=5، قرى=45، شعاب وأودية=120، مياه وآبار=120، معالم=120، جبال=0 في هذه العينات.
- أمثلة مثبتة: الرياض/الدرعية/بريدة/حائل، شعيب النقيب، وادي الأديرع، ومياه/آبار غير مسماة.
- أبطأ query على GitHub CI: `1897 ms`.
- أعلى heap delta على CI: `121542688` bytes (~116 MiB).
- ⚠️ هذه ليست أرقام T3؛ فئة الجبال غير مثبتة في المناطق الثلاث ولا تُفسر تلقائيًا كعطل واجهة.

## 4. LegacyMigration
- ✅ stage/identity/space validation قبل live mutation.
- ✅ transaction dir دائم، نسخ أصلية للمفضلات، manifests للمسارات، `APPLYING/COMMITTED`, startup recovery.
- ✅ legacy `active-track.csv` يتحول إلى GPX منفصل ولا يستبدل rolling journal الحالي.
- ✅ tests/compile في workflow `34887602753`.
- ⬜ **لم يُنفذ process kill حقيقي** بين تطبيق ملفي preferences؛ لا يسمى البند ميدانيًا مغلقًا حتى ذلك.

## 5. بوابة البناء المطلوبة
قبل التسليم يجب أن ينجح من commit واحد:
- `:app:testDebugUnitTest`
- `:app:lintRelease`
- `:app:assembleRelease`
- artifact metadata/version/SHA.
سيضاف رقم run وSHA هنا بعد نجاح 0.9.1.

## 6. التوقيع والتحديث
- Production channel يبقى **hold**.
- APK غير موقع ليس تحديثًا جاهزًا.
- Field Candidate الموقّع يجب التحقق منه بـ`apksigner verify --print-certs` إن توفرت build-tools.
- لم تُقرأ شهادة التطبيق المثبت فعليًا على شاشة السيارة؛ لذلك لا يوجد دليل update-in-place بعد.
- لا تحذف النسخة القديمة. الاختبار المطلوب: مقارنة الشهادة ثم تثبيت فوق النسخة الموجودة والتأكد من بقاء المواقع والمسارات والإعدادات.

## 7. ما بقي ميدانيًا
- T3: reboot/screen-off/GPS loss/long drive/RAM.
- fault injection: storage full، kill أثناء migration/trim/map replacement، انقطاع طاقة.
- التوقيع المثبت وupdate-in-place.

لذلك الوصف الصحيح حتى إتمام هذه البنود: **مرشح اختبار ميداني، وليس إصدار Production نهائيًا**.
