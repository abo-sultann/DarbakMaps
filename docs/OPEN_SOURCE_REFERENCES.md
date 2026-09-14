# Darbak Maps — Open Source Reference Implementations

اعتماد المالك: 2026-09-14.

هذه الوثيقة جزء من مرجع التنفيذ الرسمي لمشروع Darbak Maps. المشاريع المذكورة هنا **مراجع تقنية** وليست بدائل للتطبيق، ولا يجوز تحويل Darbak Maps إلى نسخة من أي مشروع آخر.

## القاعدة الإلزامية قبل أي ميزة كبيرة

قبل تنفيذ ميزة كبيرة أو إعادة تصميم جزء تقني موجود:

1. تحقق أولًا من وجود حل مشابه في أحد المراجع أدناه.
2. ادرس المعمارية، الخوارزمية، إدارة البيانات، معالجة الحالات الحدية، واختبارات المشروع المرجعي.
3. استخرج فقط الفكرة أو الجزء المفيد لـ Darbak Maps.
4. أعد التنفيذ بما يناسب Android 7.1/API 25 وشاشة Allwinner T3.
5. لا تنسخ تطبيقًا كاملًا أو تعتمد معماريته كاملة دون حاجة.
6. لا تدخل مكتبة ثقيلة لمجرد أن المرجع يستخدمها.
7. افحص الترخيص والـattribution لكل ملف/خوارزمية/أصل قبل أي نقل فعلي للكود أو الأصول.
8. إذا كان الحل المرجعي حديثًا لكنه غير مناسب لـ API 25 أو ~1GB RAM، تُؤخذ الفكرة فقط ويُعاد تنفيذها بخفة.
9. عند تعدد الحلول، اختر أبسط وأخف حل يحقق المطلوب بثبات طويل أثناء القيادة.
10. وثّق في تقرير التنفيذ المرجع الذي تمت مراجعته وما الذي استفدنا منه، وما الذي رفضناه ولماذا.

## المراجع الرسمية

### 1. Organic Maps — أولوية أولى
Repository: https://github.com/organicmaps/organicmaps

مرجع أساسي لـ:
- Offline Search.
- POI والفهرسة الجغرافية.
- Bookmarks / Saved Places.
- Tracks import/export وإدارة المسارات.
- تجربة استخدام الخرائط وحالات Offline.
- إدارة البيانات الجغرافية والبحث السريع.
- التعامل مع OSM والخرائط المتجهية.

قاعدة الاستخدام:
- ندرس نموذج البيانات وتجربة المستخدم والخوارزميات.
- لا ننقل البنية الثقيلة أو محرك التطبيق كاملًا إلى T3.
- أي كود/بيانات/واجهة مقتبسة فعليًا تخضع أولًا لمراجعة الترخيص والـattribution.

### 2. Osmin — أولوية أولى
Repository: https://github.com/janbar/osmin

مرجع أساسي لـ:
- Offline Navigation.
- On-road / Off-road.
- GPX.
- POI.
- GPS tracking.
- تنظيم ملفات المستخدم والخرائط الأوفلاين.
- سلوك الملاحة عند غياب الاتصال.

قاعدة الاستخدام:
- نركز على منطق الملاحة الأوفلاين والتعامل مع GPX/GPS.
- لا نفترض أن كل اختيار معماري فيه مناسب لـ Android 7.1؛ توافق API 25 يُراجع لكل فكرة.

### 3. AAT Activity Tracker — أولوية أولى
Repository: https://github.com/bailuk/AAT

مرجع أساسي لـ:
- Mapsforge.
- Offline POI وقواعد Mapsforge المتوافقة.
- Track Recording.
- GPX.
- Hill Shading / DEM.
- الارتفاعات ومصادر التضاريس.
- التعامل الخفيف مع الخرائط والحساسات.

قاعدة الاستخدام:
- هذا المرجع مهم خصوصًا لأن Darbak Maps يستخدم Mapsforge ولأن الجهاز محدود الموارد.
- قبل إضافة Hill Shading أو DEM، يجب قياس RAM/CPU ومساحة التخزين على T3 وعدم تحميل طبقات كاملة بلا حدود.

### 4. GlanceMap — مرجع إضافي
Repository: https://github.com/GlanceMap/GlanceMap

يستخدم عند الحاجة لـ:
- Offline map workflows.
- GPX management.
- POI organization.
- Hill shading / slope overlays.
- تصميم location/compass modules.
- عزل منطق الموقع والبوصلة عن الواجهة.

ملاحظة:
- المشروع يستهدف Wear OS/Android حديثًا في أجزاء منه؛ يؤخذ منه التصميم المعماري والأفكار فقط ما لم يثبت توافق الجزء المطلوب مع API 25.

### 5. MBCompass — مرجع إضافي
Repository: https://github.com/CompassMB/MBCompass

يستخدم عند الحاجة لـ:
- Compass.
- Sensor fusion.
- True/Magnetic north.
- GPS + اتجاه الحركة.
- Track recording.
- GPX export.
- Mapsforge offline maps.
- تقليل استهلاك البطارية والمعالج.

ملاحظة ترخيص مهمة:
- المشروع GPL-3.0؛ لا يُنقل كوده مباشرة إلى Darbak Maps دون مراجعة أثر الترخيص. يمكن دراسة الخوارزمية والمعمارية ثم إعادة التنفيذ بصورة مستقلة متوافقة مع ترخيص مشروعنا.

### 6. MGMapViewer — مرجع إضافي
Repository: https://github.com/mg4gh/MGMapViewer

يستخدم عند الحاجة لـ:
- Mapsforge.
- Offline maps.
- Track recording.
- تخطيط/عرض/مشاركة المسارات.
- إدارة طبقات المسار على الخريطة.

### 7. TrekMe — مرجع إضافي مهم للأداء
Repository: https://github.com/p-lr/TrekMe

يستخدم عند الحاجة لـ:
- Offline maps.
- GPX recording/import/following.
- Markers / landmarks.
- Orientation and speed indicators.
- إدارة تخزين الخرائط داخليًا/SD.
- تخفيف CPU واستهلاك الطاقة.

قاعدة الاستخدام:
- يعتبر مرجعًا مهمًا لأي قرار يتعلق بالأداء الطويل على جهاز ضعيف.

### 8. Cruiser GPS Navigation — مرجع إضافي
Repository: https://github.com/devemux86/cruiser

يستخدم عند الحاجة لـ:
- Offline route planning.
- On-road / Off-road route concepts.
- GPX/KML/GeoJSON route workflows.
- ملفات التوجيه وأنماط الطريق.
- no-go areas والارتفاعات كأفكار تصميمية.

قاعدة الاستخدام:
- Darbak Maps لا يحتاج تقليد محرك الطرق كاملًا؛ نأخذ فقط ما يخدم التوجيه المباشر/البر أو متطلبات مستقبلية معتمدة.

### 9. Trailmap — مرجع إضافي
Repository: https://github.com/maxbennedich/trailmap

يستخدم عند الحاجة لـ:
- Offline tile rendering.
- Route/waypoint drawing.
- POI.
- raw GPS logging.
- تقليل استهلاك البطارية.
- caching.
- هياكل بيانات تقلل تكلفة رسم مسارات طويلة عبر مستويات التكبير.

قاعدة الاستخدام:
- مفيد خصوصًا لفكرة الحد من تكلفة رسم سجل طويل، لكن أي خوارزمية أو كود فعلي يخضع لفحص الترخيص أولًا.

## جدول اختيار المرجع حسب الميزة

| الميزة | المرجع الأول | مراجع مساندة |
|---|---|---|
| Offline Search | Organic Maps | AAT, Osmin |
| POI / Nearby | Organic Maps | AAT, GlanceMap |
| Saved Places / Bookmarks | Organic Maps | TrekMe, GlanceMap |
| Track Recording | AAT | Osmin, TrekMe, MBCompass |
| GPX | AAT | Osmin, TrekMe, GlanceMap |
| Backtrack / Track Following | TrekMe | Trailmap, Osmin |
| GPS quality/filtering | Osmin | AAT, TrekMe |
| Compass / bearing | MBCompass | GlanceMap, TrekMe |
| Mapsforge | AAT | MGMapViewer, MBCompass, GlanceMap |
| Hill Shading / DEM | AAT | GlanceMap |
| Long-track rendering/performance | Trailmap | TrekMe, MGMapViewer |
| Offline route concepts | Osmin | Cruiser |
| Low CPU / long-running behavior | TrekMe | AAT, Trailmap |

## قيود Darbak Maps التي تتغلب على أي اختيار في المرجع

أي حل لا يحقق القيود التالية يُرفض أو يُعاد تبسيطه:

- Android 7.1 / API 25.
- 1024×600 landscape وواجهة عربية RTL.
- Allwinner T3 / Cortex-A7 / Mali-400.
- RAM تقارب 1GB.
- Offline قدر الإمكان.
- لا Firebase ولا خدمات Google حديثة غير لازمة.
- استهلاك CPU/RAM منخفض.
- استقرار طويل أثناء القيادة.
- عدم تعطيل التسجيل بسبب البحث أو التوجيه والعكس.
- عدم المساس ببيانات المستخدم أو ملف الخريطة دون مسار آمن.
- لا إضافة اعتماد ثقيل قبل قياس كلفته وفائدته.

## سياسة الترخيص

- كون المشروع Open Source لا يعني أن نسخ الكود بلا شروط مسموح.
- قبل نقل أي كود أو asset فعلي: افحص LICENSE والملفات التابعة له في commit/نسخة المرجع المستخدمة.
- إذا كان الترخيص يفرض copyleft أو attribution غير متوافق مع خطة Darbak Maps، ندرس الفكرة وننفذها استقلاليًا بدل النسخ.
- الأصول الرسومية والثيمات وبيانات الخرائط لها تراخيص مستقلة محتملة ويجب فحصها منفصلة عن كود التطبيق.
- تقرير أي ميزة كبيرة يجب أن يسجل: المرجع، الملف/الوحدة التي دُرست، الترخيص، ما تم اقتباسه كمفهوم، وما تم رفضه.

## معيار القرار

المرجع لا يفرض علينا تقنية بعينها. القرار النهائي دائمًا هو: **أخف حل مجرّب يحقق الوظيفة المطلوبة ويحافظ على استقرار Darbak Maps على T3**.
