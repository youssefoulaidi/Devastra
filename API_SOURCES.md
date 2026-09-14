# مصادر الصوت والصورة والنص في تطبيق QuranPro 📖🎧

وثيقة بحثية توثّق المصادر المجانية التي يعتمد عليها التطبيق — كلها **مجانية ودون مفتاح API**.

> تاريخ البحث: 2026-09-13 — تم التأكد من عمل الواجهات من التوثيق الرسمي.

## 1) المصدر الرئيسي: mp3quran.net — واجهة API v3 ⭐

الموقع الرسمي للتوثيق: https://www.mp3quran.net/ar/api (تجربة حية للواجهات من نفس الصفحة).

- **الوصف**: أكبر مكتبة صوتية للقرآن الكريم — أكثر من **150 قارئًا** بعشرات الروايات والمصاحف.
- **التكلفة**: مجاني بالكامل، لا يحتاج مفتاحًا ولا تسجيلًا.
- **اللغة الافتراضية**: العربية (`language=ar`)، ويدعم `eng` وغيرها.

### النهايات (Endpoints) المستخدمة

| الوظيفة | الرابط |
|---|---|
| السور (الأسماء + مكي/مدني + الصفحات) | `https://mp3quran.net/api/v3/suwar?language=ar` |
| الروايات | `https://mp3quran.net/api/v3/riwayat?language=ar` |
| **القرّاء والمصاحف** (مع فلاتر الرواية والسورة) | `https://mp3quran.net/api/v3/reciters?language=ar` |
| قارئ محدد | `.../reciters?language=ar&reciter=96` |
| فلترة برواية/سورة | `.../reciters?language=ar&rewaya=1&sura=18` |
| أحدث التلاوات | `https://mp3quran.net/api/v3/recent_reads?language=ar` |
| **الفيديو (تلاوات مرئية mp4 + صور مصغرة)** | `https://mp3quran.net/api/v3/videos?language=ar` |
| تصنيفات الفيديو | `https://mp3quran.net/api/v3/video_types?language=ar` |
| **البث المباشر (مكة: القرآن/السنة — HLS)** | `https://mp3quran.net/api/v3/live-tv?language=ar` |
| **الإذاعات** | `https://mp3quran.net/api/v3/radios?language=ar` |
| التفاسير الصوتية | `https://mp3quran.net/api/v3/tafasir?language=ar` |
| سور تفسير محدد | `https://mp3quran.net/api/v3/tafsir?tafsir=1&language=ar` |
| الوقفات التدبرية | `https://mp3quran.net/api/v3/tadabor?sura=3&language=ar` |
| **توقيت الآيات** (إبراز آية بآية) | `https://mp3quran.net/api/v3/ayat_timing?surah=2&read=5` |
| القراءات التي لها توقيت | `https://mp3quran.net/api/v3/ayat_timing/reads` |

### مثال ردّ القرّاء (مختصر)

```json
{
  "reciters": [{
    "id": 1,
    "name": "إبراهيم الأخضر",
    "moshaf": [{
      "id": 1,
      "name": "حفص عن عاصم - مرتل",
      "server": "https://server6.mp3quran.net/akdr/",
      "surah_total": 114,
      "surah_list": "1,2,3,...,114"
    }]
  }]
}
```

### نمط رابط الصوت المباشر (مهم)

```
{server} + رقم السورة بثلاث خانات + .mp3
مثال: https://server6.mp3quran.net/akdr/018.mp3  (سورة الكهف)
```

## 2) نص المصحف: AlQuran Cloud ☁️

- **الوصف**: واجهة REST مفتوحة لنص القرآن (الرسم العثماني) والترجمات.
- **التكلفة**: مجاني، لا يحتاج مفتاحًا.
- **التوثيق**: https://alquran.cloud/api

| الوظيفة | الرابط |
|---|---|
| بيانات السور الـ114 | `https://api.alquran.cloud/v1/surah` |
| نص سورة بالرسم العثماني | `https://api.alquran.cloud/v1/surah/{n}/quran-uthmani` |

## 3) الصوت البديل (وضع عدم الاتصال): Islamic Network CDN 🔊

- **الوصف**: شبكة توصيل ملفات صوتية للسور كاملة — تُستخدم كبديل تلقائي داخل التطبيق
  (13 قارئًا مشهورًا) عند تعذّر الوصول لخادم mp3quran.
- **التوثيق**: https://alquran.cloud/cdn
- **النمط**: `https://cdn.islamic.network/quran/audio/128/{edition}/{surah}.mp3`
- **مثال**: `https://cdn.islamic.network/quran/audio/128/ar.alafasy/36.mp3` (سورة يس — العفاسي)
- **القرّاء البدلاء**: العفاسي، الحصري، المنشاوي (مرتل/مجوّد)، عبد الباسط (مرتل/مجوّد)،
  السديس، الشريم، العجمي، محمد أيوب، الحذيفي، محمد جبريل.

## 3.5) مواقيت الصلاة والأذان 🕌

| الوظيفة | الرابط |
|---|---|
| **مواقيت الصلاة اليومية** (بحسب الإحداثيات + طريقة الحساب) | `https://api.aladhan.com/v1/timings/{dd-MM-yyyy}?latitude=..&longitude=..&method=..` |

- **Aladhan (islamic.network)** — مجاني ودون مفتاح، يعيد التوقيتات (Fajr/Sunrise/Dhuhr/Asr/Maghrib/Isha)
  مع التاريخ الهجري والمنطقة الزمنية للموقع.
- **أصوات المؤذنين**: مجموعة أذان mp3 مستضافة مجانًا على GitHub (`raw.githubusercontent.com/abodehq/Athan-MP3`)
  مع مرآة احتياطية على archive.org لأذان العفاسي.

## 4) مصادر مرجعية إضافية (للتوسعة مستقبلًا)

| المصدر | الاستخدام | الرابط |
|---|---|---|
| EveryAyah.com | ملفات آية بآية لـ 44+ قارئًا + ملفات توقيت | https://everyayah.com |
| Quranicaudio.com | تلاوات بجودات متعددة | https://quranicaudio.com |
| UmmahAPI | واجهة موحدة (نص + صوت + تفسير) | https://ummahapi.com/quran-api |
| zekr.online/dev | واجهة عربية للتلاوات | https://zekr.online/ar/dev |
| fawazahmed0/quran-api | نصوص وترجمات عبر CDN (GitHub) | https://github.com/fawazahmed0/quran-api |

## الحقوق والشكر

- التلاوات: ملك أصحابها ومنصاتها (mp3quran.net وشبكة Islamic Network) — تُبثّ من خوادمها مباشرة دون إعادة رفع.
- النص العثماني: AlQuran Cloud (بيانات مفتوحة).
- الخطوط: Amiri و Amiri Quran (رخصة SIL Open Font License).
- جزى الله القائمين على هذه المنصات المجانية خير الجزاء.
