# DarbakMaps — RELEASE ACCEPTANCE

الإصدار المرشح: **0.9.0 / vc21**  
الحزمة: `com.abosultan.darbakmaps.debug`  
مصدر APK: `1da936e63bd42a6214a0afb76c337f66c00d31be`  
Final Candidate: workflow `34884270566` — **SUCCESS**  
الحالة: **مرشح اختبار ميداني**، لا يسمى Production قبل تحقق شهادة النسخة المثبتة واختبارات T3.

## التسجيل التلقائي وآخر 1000 كم
- ✅ اختبار وحدة: رحلة صناعية >1200 كم، وبعد التقليم يبقى أحدث ~1000 كم ضمن هامش أخذ العينات الموثق.
- ✅ اختبار وحدة: فجوة segment لا تدخل حساب المسافة.
- ✅ اختبار وحدة: استعادة backup عند فقد ملف primary أثناء نافذة trim.
- ✅ compile/integration: snapshot GPX لا يمسح rolling journal.
- ✅ compile/integration: pause/resume وخدمة التسجيل الجديدة.
- ⬜ ميداني: إغلاق Activity، screen off، reboot، GPS loss/recovery، الوقوف الطويل.
- ⬜ fault: kill أثناء write/trim، storage full والتعافي.
- ⬜ أداء: recording + search + navigation طويلًا مع قياس RAM على T3.

## البحث
- ✅ compile/lint: تطبيع عربي، partial match، aliases، POI + Way/area، وفئات البر.
- ✅ compile/integration: حول موقعي وحول معلم بنطاقات 5/10/25/50/100 كم.
- ✅ compile: حالات partial/truncated بدل «لا توجد» قبل اكتمال الفهرسة.
- ✅ compile: persistent index مربوط بهوية ملف الخريطة.
- ⬜ بيانات فعلية: اختيار عينات مؤكدة من ملف السعودية لكل فئة وإثبات الدقة/إزالة التكرار.
- ⬜ أداء: زمن البحث/RAM على T3 قبل/بعد اكتمال الفهرس.

## المحفوظات والأيقونات
- ✅ compile: حفظ icon-first والاسم اختياري وauto-label مؤرخ.
- ✅ compile: Canvas icons ثابتة، ومنها طير السمان، دون الاعتماد على Emoji.
- ✅ compile/integration: filters، nearest، direct distance، no-GPS state، undo delete.
- ✅ unit: اتجاه 359↔0 صحيح وcircular smoothing.
- ✅ unit: اتجاهات جغرافية عربية fallback عند غياب heading موثوق.
- ⬜ ميداني: حفظ 3 سمان بلا أسماء + نوع آخر، reboot، touch no-reorder، توجيه للإحداثيات الفعلية.

## التوجيه والعودة
- ✅ compile: التوجيه المباشر يوضح أنه خط هدف لا طريق صالح مضمون.
- ✅ unit: backtrack لا يعبر gaps.
- ✅ unit: لا يختار هدفًا افتراضيًا إذا لا يوجد connected segment.
- ⬜ ميداني: GPS stale/loss أثناء التوجيه والعودة.

## حماية البيانات والخريطة
- ✅ compile/lint: LegacyMigration يفحص إلى staging قبل live state، يتحقق من الهوية/الحجم/المساحة، ويستخدم rollback وlock مشترك.
- ✅ compile/lint: runtime recording prefs القديمة لا تستورد.
- ✅ compile/lint: map replacement لا يحذف backup إلا بعد تحقق البديل كاملًا.
- ⬜ fault: corrupted archive/wrong identity/space failure/forced replacement failure على Android.
- ⬜ fault: انقطاع نسخ الخريطة ثم reboot.

## Final Candidate 0.9.0
workflow `34884270566` نفذ من نفس source commit:
- ✅ `testDebugUnitTest`
- ✅ `lintRelease`
- ✅ `assembleRelease`
- ✅ candidate metadata + SHA
- ✅ artifact upload

Artifact CI: `DarbakMaps-0.9.0-vc21-unsigned`  
SHA-256 للـAPK غير الموقع:  
`adfec3b578aecf0060bd10df254d465ba3cd132f4ceac5fbde75798331b1f6c4`

## التوقيع والتحديث
- تم العثور على سلسلة توقيع Darbak stable خارج المستودع، وإنشاء **Field Candidate موقّع** منها والتحقق من سلامة توقيع JAR.
- يوجد APK انتقال موقّع سابق في ملفات المشروع بنفس سلسلة Darbak stable، بينما يوجد APK أقدم في Drive بشهادة مختلفة.
- **لم تُقرأ شهادة التطبيق المثبت فعليًا على شاشة السيارة في هذه الجولة**؛ لذلك لا يمكن وصف Field Candidate بأنه update-in-place مضمون.
- لا تطلب إزالة النسخة الحالية. أول خطوة ميدانية هي قراءة شهادة APK المثبت/نسخة APK منه، ثم مقارنة الشهادة.
- update manifest الخاص بـProduction يبقى hold حتى نجاح هذه الخطوة واختبار بقاء البيانات.

Field Candidate الموقّع:
- `DarbakMaps-0.9.0-vc21-field-signed.apk`
- SHA-256: `650ca55ebf19b9381e277cdee68fde229d02160ac841144717d95132b926b376`

## بيئة الاختبار وما لم يُنفذ
- CI: GitHub Actions / Ubuntu / Temurin Java 17 / Gradle.
- التوقيع: JDK jarsigner/keytool في بيئة خاصة؛ لم يوضع المفتاح أو كلمات المرور في المستودع.
- الجهاز المستهدف: Allwinner T3 / Android 7.1 / 1024×600 / ~1GB.
- لم يتم تشغيل Android emulator أو T3 فعلي في هذه الجولة، لذلك يبقى الوصف الصحيح: **مرشح اختبار ميداني**.
