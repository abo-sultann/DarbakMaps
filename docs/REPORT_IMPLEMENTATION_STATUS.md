# DarbakMaps — REPORT IMPLEMENTATION STATUS

مرجع التنفيذ: `docs/IMPLEMENTATION_ORDER.md`  
بدء هذه الجولة: 2026-09-14  
الحالات المستخدمة: **لم يبدأ / جارٍ / متعثر / أُغلق باختبار**.  
لا تعني عبارة “نُفذ في الكود” أن بند القبول أُغلق ما لم يذكر اختبارًا ناجحًا.

| ID | البند | الحالة | الالتزام/الدليل | الاختبار والنتيجة | قيود متبقية |
|---|---|---|---|---|---|
| DATA-01 | LegacyMigration يفحص الأرشيف كاملًا قبل لمس البيانات ويطبق rollback | جارٍ | `cf1014a` | Java compile/اختبارات التكامل الشاملة لم تُسجل بعد لهذا HEAD | يلزم wrong identity/corrupt/space/failure acceptance |
| DATA-02 | تنسيق migration مع كاتب المسار وعدم استيراد runtime state | جارٍ | `6a1c4cf`, `15a82d5`, `cf1014a` | لم يُنفذ اختبار تزامن ميداني | يحتاج kill/concurrent write test |
| MAP-01 | عدم حذف backup لخريطة بديلة لمجرد أنها غير فارغة | جارٍ | `5540eb8` | لم يُنفذ اختبار انقطاع نسخ فعلي | يحتاج interruption/reboot acceptance |
| TRACK-01 | سجل تلقائي دائم بدل session تُمسح عند finalize | أُغلق باختبار جزئي | `da661be`, `15a82d5`, MainActivity CI | workflow `34883690007`: `testDebugUnitTest` + `compileDebugJavaWithJavac` SUCCESS | ما زال T3 lifecycle ميداني مطلوبًا |
| TRACK-02 | أحدث 1000 كم بالتقليم التدريجي حسب المسافة المتصلة | أُغلق باختبار وحدة | `9db27d8`, `1a6b0ae` | `TrackJournalRetentionTest`: دخل suite الناجح في workflow `34883690007` | محاكاة power/storage الفعلية باقية |
| TRACK-03 | الفجوات لا تدخل المسافة ولا تُوصل | أُغلق باختبار وحدة | `9db27d8`, `1a6b0ae` | test `segmentGapDoesNotCountAsDistance` ضمن suite ناجح | اختبار GPS فعلي باقٍ |
| TRACK-04 | trim crash-safe: fsync ثم pending/backup/recovery | أُغلق باختبار وحدة جزئي | `9db27d8`, `1a6b0ae` | test استعادة backup ضمن suite ناجح | kill أثناء swap ونقص مساحة على Android باقيان |
| TRACK-05 | عدم إسقاط write مقبول عند stop/finalize + Looper رئيسي | أُغلق باختبار compile | `9b30ab6` | workflow `34883690007` compile/tests SUCCESS | يحتاج rapid stop/resume device test |
| TRACK-06 | process restart أثناء FINALIZING | جارٍ | `9b30ab6` | منطق موجود، لم يُقتل process فعليًا أثناء الحالة | acceptance ميداني باقٍ |
| TRACK-07 | boot يستأنف الخدمة دون فتح الواجهة | جارٍ | `7a292c9` | compile ناجح سابقًا | يحتاج reboot حقيقي على T3؛ Force Stop مستثنى |
| TRACK-08 | حفظ نسخة GPX لا يوقف السجل | أُغلق باختبار compile | `da661be`, MainActivity CI | workflow `34883690007` SUCCESS | يحتاج ملف فعلي طويل للتحقق اليدوي |
| TRACK-09 | pause/resume واضح ومستمر | أُغلق باختبار compile | MainActivity generated patch بعد `de6dc58` | workflow `34883690007` SUCCESS | تجربة T3 مطلوبة |
| TRACK-10 | سقف فعلي لنقاط وطبقات الرسم | أُغلق باختبار compile/unit | rendering workflow `34883845877` | tests + compile SUCCESS | قياس RAM طويل على T3 باقٍ |
| SEARCH-01 | تطبيع عربي/جزئي/aliases | جارٍ | `3f1b7cb` | لم يُضف suite بحث مستقل بعد | عينات من map السعودية مطلوبة |
| SEARCH-02 | فهرس محلي دائم مرتبط بهوية الخريطة وقابل للاستكمال | جارٍ | `3f1b7cb` | compile النهائي للمجموعة قيد التحقق | يحتاج restart/map-change acceptance |
| SEARCH-03 | POI + Way/area + فئات البر | جارٍ | `3f1b7cb` | لم تُثبت عينات لكل فئة بعد | نقص البيانات يجب توثيقه من map الفعلية |
| SEARCH-04 | حول موقعي/حول معلم 5/10/25/50/100 كم وترتيب أقرب | جارٍ | UI integration workflow `34884015788` | workflow قيد التنفيذ عند آخر تحديث | — |
| SEARCH-05 | لا “لا توجد نتائج” قبل اكتمال الفهرس + truncated state | جارٍ | `3f1b7cb` + UI integration | قيد CI | اختبار cap مطلوب |
| SAVED-01 | حفظ بالأيقونة دون اسم ولوحة مفاتيح تلقائية | جارٍ | `d883c79`, `b859d40` | compile المجموعة قيد CI | اختبار حفظ/reboot على جهاز باقٍ |
| SAVED-02 | رسومات ثابتة للسمان/مخيم/ماء/شجرة/صيد/عام | أُغلق باختبار compile | rendering patch | workflow `34883845877` SUCCESS | مراجعة بصرية 1024×600 باقية |
| SAVED-03 | UUID/legacy IDs وعدم دمج مواقع متشابهة | جارٍ | `d883c79` | لم يضف test repository مستقل بعد | — |
| SAVED-04 | فلترة أيقونات، nearest، مسافة واتجاه وحالة GPS | جارٍ | `9dcfc5c`, `58835cf` | `DirectionMathTest` ضمن CI النهائي الجاري | UI device acceptance باقٍ |
| SAVED-05 | 359↔0 + smoothing | أُغلق باختبار وحدة سابقًا/يعاد التحقق | `58835cf`, `f2f5203` | DirectionMath tests مضافة؛ آخر suite شامل قيد التحقق | — |
| SAVED-06 | لا reorder تحت الإصبع + undo delete | جارٍ | `9dcfc5c` | compile/UI integration قيد CI | touch acceptance يدوي باقٍ |
| NAV-01 | توجيه مباشر مع تحذير أنه ليس طريقًا | جارٍ | UI integration workflow | قيد CI | اختبار T3 باقٍ |
| NAV-02 | backtrack لا يعبر gap | أُغلق باختبار وحدة سابق | `90687ed` وما قبله | tests موجودة | — |
| NAV-03 | لا هدف افتراضي إذا لا يوجد segment متصل | جارٍ | `e5507a5`, `7f34fca` | test مضاف؛ ينتظر suite أحدث | — |
| GPS-01 | stale GPS يمسح السرعة/التوجيه | أُغلق باختبار compile سابق | 0.8.1 remediation | CI 0.8.1 السابق SUCCESS | T3 field test باقٍ |
| RELEASE-01 | package يبقى `com.abosultan.darbakmaps.debug` | أُغلق بالمراجعة | build.gradle | المصدر يثبت suffix release الحالي | لا يثبت توقيع الجهاز |
| RELEASE-02 | شهادة التوقيع مطابقة للنسخة المثبتة | متعثر | لا يوجد دليل من APK المثبت في هذه الجولة | لم يُنفذ | **حاجز Production** |
| RELEASE-03 | update فوق النسخة السابقة وبقاء البيانات | متعثر | يعتمد RELEASE-02 | لم يُنفذ | لا يجوز وصف unsigned بأنه تحديث |
| RELEASE-04 | manifest/APK/version/SHA موحدة | جارٍ | ستغلق عند بناء المرشح الجديد | لم يُبن المرشح الجديد بعد | لا تنشر manifest قبل signed APK |
| PERF-01 | بحث+توجيه+تسجيل دون تجمد/نمو RAM | لم يبدأ ميدانيًا | — | لم يُنفذ | يحتاج T3/مدة طويلة |
| FIELD-01 | reboot/screen off/GPS loss/storage/power interruption | لم يبدأ ميدانيًا | — | لم يُنفذ | يحتاج الجهاز/محاكي مناسب |

## تصنيف المراجعة الحالية
- **مشاكل باقية مثبتة ثم عولجت في الكود:** غياب rolling 1000km، LegacyMigration يكتب قبل تحقق الهوية، backup الخريطة يحذف مع target غير فارغ فقط، backtrack له default target، حد طبقات الرسم غير موجود، saved quail يعتمد glyph.
- **إصلاحات مثبتة باختبار حتى الآن:** دورة MainActivity للتسجيل التلقائي (compile/unit CI)، retention الصناعي >1200km، فجوات المسار، تعافي backup للتقليم، سقف طبقات الرسم والأيقونات الثابتة.
- **تراجعات جديدة مثبتة بالمقارنة:** لا يوجد تراجع مصنف نهائيًا حتى الآن؛ أي فشل CI لاحق يجب تسجيله هنا قبل إصلاحه.

## ملاحظة إصدار
الإصدار الموجود في المصدر قبل هذه الجولة هو 0.8.1/20. لن يُرفع رقم جديد إلا بعد اكتمال مجموعة التغييرات الحالية كي لا نهدر أرقام إصدارات على مراحل داخلية.
