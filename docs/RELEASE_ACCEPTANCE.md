# DarbakMaps — RELEASE ACCEPTANCE

الإصدار المرشح: **0.9.0 / vc21**  
الحزمة: `com.abosultan.darbakmaps.debug`  
الحالة: **مرشح للاختبار الميداني** حتى يكتمل T3 والتوقيع.  
هذه الوثيقة تسجل الاختبارات المنفذة فقط؛ عدم وجود دليل يعني أن البند غير مغلق.

## 1) التسجيل التلقائي وآخر 1000 كم
| قبول | النتيجة | الدليل |
|---|---|---|
| رحلة صناعية >1200 كم تبقي أحدث ~1000 كم | ناجح — وحدة | `TrackJournalRetentionTest.trimsSyntheticRoutePast1200KmToNewest1000Km` ضمن suite الناجح workflow `34883690007` |
| gap لا يدخل حساب المسافة | ناجح — وحدة | `segmentGapDoesNotCountAsDistance` |
| recovery عند فقد primary ووجود backup | ناجح — وحدة | `recoversBackupWhenPrimaryMissing` |
| حفظ snapshot دون مسح السجل | compile/unit integration ناجح | workflow `34883690007` |
| إغلاق Activity مع استمرار Service | غير مختبر ميدانيًا | يحتاج T3 |
| screen off | غير مختبر ميدانيًا | يحتاج T3 |
| reboot resume بدون فتح UI | غير مختبر ميدانيًا | يحتاج T3 |
| GPS loss/recovery والوقوف الطويل | غير مختبر ميدانيًا | يحتاج GPS/T3 |
| kill أثناء write/trim | recovery logic موجود؛ kill فعلي غير منفذ | يحتاج fault injection/device |
| storage full والتعافي | غير منفذ | يحتاج fault injection/device |
| البحث+التوجيه+التسجيل طويلًا دون RAM growth | غير منفذ | يحتاج profiler/T3 |
| عدم حذف saved places/manual GPX عند rolling trim | مثبت بنيويًا بالكود؛ لم ينفذ integration filesystem test بعد | rolling trim يلمس `active-track.csv` فقط |

## 2) البحث والمعالم
| قبول | النتيجة | الدليل |
|---|---|---|
| عربي/جزئي/aliases | منفذ في الكود، قبول بيانات فعلية غير مكتمل | `OfflineMapSearchEngine` |
| POI + Way/area | منفذ في الكود، لم تثبت عينات الخريطة بعد | `readMapData`, representativePosition |
| persistent incremental index | منفذ؛ restart acceptance غير منفذ | `.darbak-search-<identity>.idx` + state |
| around current/landmark 5/10/25/50/100km | compile/unit integration ناجح | workflow `34884015788` |
| categories | compile ناجح | الكل/أودية/جبال/معالم/قرى/مياه/خدمات |
| لا “لا توجد” قبل اكتمال index | compile ناجح، UI device check باقي | partial/truncated state |
| dedupe | منفذ، test map sample باقي | normalized name/source + coordinates |
| cap/truncation | منفذ، synthetic cap acceptance باقي | `MAX_INDEX_ITEMS=250000`, `isTruncated()` |
| زمن/RAM على T3 وخريطة السعودية | غير منفذ | ميداني |

## 3) المحفوظات والأيقونات والاتجاه
| قبول | النتيجة | الدليل |
|---|---|---|
| الاسم غير مطلوب وauto-label | compile ناجح | `PlaceRepository` + `PointEditor` |
| أيقونة سمان ثابتة لا تعتمد Emoji | compile ناجح | Canvas artwork؛ workflow `34883845877` |
| الفلاتر حسب الأيقونة | compile ناجح | `SavedPlacesDialog` |
| nearest + direct distance | compile ناجح | `SavedPlacesDialog` |
| relative arrow مع 359↔0 | ناجح — وحدة | `DirectionMathTest.relativeDirectionHandles359ToZeroWrap` |
| circular smoothing | ناجح — وحدة | `DirectionMathTest.smoothingUsesShortestCircularPath` |
| no heading => cardinal | ناجح — وحدة للـmath | `cardinalNamesAreStable`; UI ميداني باقٍ |
| no GPS => انتظار GPS | compile ناجح | `SavedPlacesDialog` |
| no reorder تحت touch | compile ناجح؛ touch acceptance باقٍ | touch flag + reorder on UP/CANCEL |
| edit/delete/undo | compile ناجح | `PlaceRepository.restore` + panel |
| reboot persistence | غير مختبر ميدانيًا | يحتاج T3 |

## 4) التوجيه والرجوع
| قبول | النتيجة | الدليل |
|---|---|---|
| direct navigation لا يدعي وجود طريق | compile ناجح | UI message “الخط لا يعني وجود طريق صالح” |
| فقد GPS يوقف الإرشاد القديم | كان مغلقًا في remediation 0.8.1 | يحتاج T3 إعادة تأكيد |
| backtrack لا يعبر gap | ناجح — وحدة سابقًا | `TrackNavigatorTest` |
| لا default target بلا connected segment | test مضاف | يجب تأكيد دخوله في Final Candidate 0.9.0 |
| navigation لا يوقف recorder | بنيويًا منفصل؛ ميداني باقي | separate service/state |

## 5) حماية البيانات/الخريطة
| قبول | النتيجة | الدليل |
|---|---|---|
| LegacyMigration يفحص قبل live state | منفذ؛ Android fault tests باقية | staged temp + identity + space |
| migration rollback | منفذ؛ forced-failure acceptance باقٍ | original prefs + imported track cleanup |
| recorder/migration serialization | منفذ | `DataStoreLock` |
| map backup retained until verified replacement | منفذ | full length/Mapsforge/SHA check |
| interrupted map copy/reboot | لم ينفذ fault test | يحتاج test |

## 6) Build / release / signing
- Final Candidate 0.9.0 يجب أن ينجح من commit واحد في: unit tests + release lint + assembleRelease.
- workflow صار يشتق اسم artifact من `versionName/versionCode` بدل الاسم الثابت 0.8.0.
- **التوقيع Production غير مغلق**: لم نفحص شهادة APK المثبت فعليًا على الشاشة في هذه الجولة.
- أي APK unsigned هو artifact للاختبار/الفحص فقط، وليس update مثبتًا يحافظ على البيانات.
- `update-manifest.json` لا يفتح لقناة Production قبل signed APK متحقق منه.

## 7) بيئة الاختبار
- CI: GitHub Actions / Ubuntu / Temurin Java 17 / Gradle project tests.
- الجهاز المستهدف الفعلي: Allwinner T3 Android 7.1 1024×600 ~1GB.
- **لم يتم في هذه الجولة تشغيل اختبارات Android emulator أو شاشة T3 الفعلية**؛ لذلك لا تسمى 0.9.0 نهائية حتى إكمال البنود الميدانية.
