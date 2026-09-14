# DarbakMaps — REPORT IMPLEMENTATION STATUS

مرجع التنفيذ: `docs/IMPLEMENTATION_ORDER.md`  
الإصدار: **0.9.0 / vc21**  
مصدر Final Candidate: `1da936e63bd42a6214a0afb76c337f66c00d31be`  
Final Candidate run: `34884270566` — **SUCCESS**  
الحالة العامة: **مرشح اختبار ميداني، وليس Production نهائيًا**.

الحالات: **لم يبدأ / جارٍ / متعثر / أُغلق باختبار**. نجاح build لا يغلق اختبار جهاز لم يُنفذ.

| ID | البند | الحالة | الدليل/الاختبار | المتبقي |
|---|---|---|---|---|
| DATA-01 | LegacyMigration: stage كامل قبل live state + rollback | جارٍ | الكود داخل Final Candidate وRelease lint ناجح | corrupt/wrong-id/space/forced-failure على Android |
| DATA-02 | migration/recorder serialization وعدم استيراد runtime state | جارٍ | `DataStoreLock` + sanitization؛ Final Candidate SUCCESS | concurrent kill/device test |
| MAP-01 | backup الخريطة لا يحذف إلا بعد تحقق البديل | جارٍ | code + release lint SUCCESS | interrupted copy/reboot fault test |
| TRACK-01 | التسجيل التلقائي بعد الصلاحية واستمراره مستقلًا عن Activity | أُغلق باختبار برمجي | workflow `34883690007` + Final Candidate | lifecycle T3 ميداني |
| TRACK-02 | الاحتفاظ بأحدث ~1000 كم بالتقليم التدريجي | أُغلق باختبار وحدة | `TrackJournalRetentionTest` >1200 كم ضمن Final Candidate suite | power/storage fault tests |
| TRACK-03 | الفجوات لا تحسب ولا توصل | أُغلق باختبار وحدة | `segmentGapDoesNotCountAsDistance` | GPS ميداني |
| TRACK-04 | trim بـ fsync/pending/backup/recovery | أُغلق باختبار وحدة جزئي | backup recovery test + Final Candidate | kill أثناء swap/storage-full |
| TRACK-05 | writes المقبولة لا تسقط عند stop + Looper صحيح | أُغلق باختبار compile/unit | workflow `34883690007` | rapid lifecycle T3 |
| TRACK-06 | استعادة FINALIZING بعد process recreation | جارٍ | code + Final Candidate compile/lint | process-kill فعلي |
| TRACK-07 | boot يعيد recorder دون فتح UI | جارٍ | BootReceiver compile/lint ناجح | reboot حقيقي؛ Force Stop مستثنى |
| TRACK-08 | snapshot GPX لا يوقف/يمسح rolling journal | أُغلق باختبار برمجي | workflow `34883690007` | ملف طويل فعلي |
| TRACK-09 | pause/resume واضح ومستمر | أُغلق باختبار compile | workflow `34883690007` | UX على T3 |
| TRACK-10 | حدود فعلية للنقاط والطبقات | أُغلق باختبار compile/unit | workflow `34883845877` | RAM طويل على T3 |
| SEARCH-01 | عربي/جزئي/aliases | جارٍ | Final Candidate tests/lint/build SUCCESS | عينات حقيقية من map |
| SEARCH-02 | persistent incremental index مربوط بهوية map | جارٍ | Final Candidate SUCCESS | restart/map-change acceptance |
| SEARCH-03 | POI + Way/area + فئات البر | جارٍ | compile/lint SUCCESS | عينات مثبتة لكل فئة |
| SEARCH-04 | حول موقعي/حول معلم 5/10/25/50/100 كم | أُغلق باختبار compile/unit integration | workflow `34884015788` + Final Candidate | T3 UI acceptance |
| SEARCH-05 | partial/truncated state بدل “لا توجد” المضللة | أُغلق باختبار compile | workflow `34884015788` | cap synthetic/data test |
| SAVED-01 | حفظ بالأيقونة دون اسم/keyboard إلزامي | أُغلق باختبار compile | PointEditor/Repository داخل Final Candidate | save/reboot field test |
| SAVED-02 | fixed Canvas icons ومنها السمان | أُغلق باختبار compile | workflow `34883845877` | مراجعة بصرية 1024×600 |
| SAVED-03 | stable IDs وعدم دمج المواقع المتشابهة | جارٍ | code + Final Candidate | repository test مستقل |
| SAVED-04 | filters/nearest/distance/direction/GPS state | أُغلق باختبار compile+math | workflow `34884015788`, `DirectionMathTest` | UI field test |
| SAVED-05 | 359↔0 + circular smoothing | أُغلق باختبار وحدة | `DirectionMathTest` داخل Final Candidate suite | — |
| SAVED-06 | no reorder أثناء touch + undo delete | أُغلق باختبار compile | workflow `34884015788` | touch acceptance |
| NAV-01 | direct off-road navigation مع تحذير أنه ليس طريقًا | أُغلق باختبار compile | workflow `34884015788` | T3 field test |
| NAV-02 | backtrack لا يعبر gap | أُغلق باختبار وحدة | `TrackNavigatorTest` | — |
| NAV-03 | لا default target بلا connected segment | أُغلق باختبار وحدة | test الجديد دخل Final Candidate suite SUCCESS | — |
| GPS-01 | stale GPS يمسح القراءة/الإرشاد | أُغلق باختبار compile سابق | remediation + Final Candidate | T3 recheck |
| RELEASE-01 | package = `com.abosultan.darbakmaps.debug` | أُغلق بالمراجعة/build metadata | artifact metadata من Final Candidate | — |
| RELEASE-02 | شهادة APK مساوية للمثبت فعليًا على السيارة | **متعثر** | لا توجد قراءة من APK المثبت على T3 | حاجز Production |
| RELEASE-03 | update-in-place مع بقاء البيانات | **متعثر** | يعتمد RELEASE-02 | لا تحذف النسخة القديمة |
| RELEASE-04 | اسم artifact/version/commit/SHA متسقة | أُغلق باختبار build | `DarbakMaps-0.9.0-vc21-unsigned`; run `34884270566` | manifest Production يبقى hold |
| PERF-01 | recording+search+navigation طويل بلا RAM growth | لم يبدأ ميدانيًا | — | T3/profiler |
| FIELD-01 | screen-off/reboot/GPS/storage/power | لم يبدأ ميدانيًا | — | T3/fault injection |

## أدلة البناء 0.9.0
Final Candidate `34884270566` نفذ من commit `1da936e...`:
- `testDebugUnitTest`: SUCCESS
- `lintRelease`: SUCCESS
- `assembleRelease`: SUCCESS
- candidate metadata/SHA: SUCCESS
- artifact upload: SUCCESS

Unsigned APK SHA-256:  
`adfec3b578aecf0060bd10df254d465ba3cd132f4ceac5fbde75798331b1f6c4`

تم أيضًا إنشاء Field Candidate موقّع بسلسلة Darbak stable المتاحة خارج المستودع. توقيعه تحقق بنجاح، لكن **توافقه مع التطبيق المثبت على السيارة غير مثبت** حتى تُقرأ شهادة النسخة المثبتة. لذلك لا يوصف بأنه update مضمون.

## التصنيف النهائي لهذه الجولة
- **مشاكل باقية ثبتت ثم أصلحت:** غياب rolling 1000km، migration يكتب قبل تحقق كامل، حذف map backup مبكرًا، default backtrack target، عدم وجود سقف layer، أيقونة السمان المعتمدة على glyph، اسم artifact المثبت على 0.8.0.
- **إصلاحات مثبتة باختبار:** retention >1200km، gap distance، trim backup recovery، direction wrap/smoothing، backtrack no-target، recording UI compile/unit، fixed icons/layer bounds، nearby/saved UI integration، Final Candidate كامل.
- **تراجعات جديدة مثبتة بالمقارنة:** لم يبق تراجع build/lint/test مثبت في commit المرشح.
- **غير مغلق ميدانيًا:** التوقيع المثبت، update-in-place، أعطال الطاقة/التخزين، قياس RAM وزمن البحث، وعينات الخريطة السعودية الفعلية.
