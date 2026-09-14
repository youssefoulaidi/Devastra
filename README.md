# QuranPro — القرآن الكريم صوتًا وصورةً وقراءةً 📖🎧

تطبيق أندرويد **مجاني ومفتوح المصدر** للقرآن الكريم: استماع، مشاهدة، قراءة، بث مباشر،
إذاعات، وتفسير صوتي — بأصوات أكثر من **150 قارئًا**.

A **free & open-source** Android app for the Holy Quran: audio, video, reading,
live TV, radios and audio tafsir — with **150+ reciters**.

[![Build QuranPro APK](https://github.com/youssefoulaidi/quranpro/actions/workflows/android.yml/badge.svg)](https://github.com/youssefoulaidi/quranpro/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/youssefoulaidi/quranpro?label=Release)](https://github.com/youssefoulaidi/quranpro/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-emerald.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Android-7.0%2B-green)

---

## ⬇️ التثبيت / Install

حمّل أحدث نسخة من **[صفحة الإصدارات](https://github.com/youssefoulaidi/quranpro/releases)**:

| الملف | الوصف |
|---|---|
| `QuranPro-v*-debug.apk` | **موصى به** — موقّع وجاهز للتثبيت مباشرة |
| `QuranPro-v*-release-unsigned.apk` | نسخة release غير موقّعة (تحتاج توقيعك الخاص) |

> يتطلب أندرويد **7.0 (API 24)** فأعلى.

---

## ✨ المميزات / Features

| القسم | الوصف |
|---|---|
| 🎙️ السور والقرّاء | 114 سورة + بحث + فلترة بالرواية + اختيار المصحف لكل قارئ |
| ▶️ مشغّل متكامل | تشغيل في الخلفية + إشعار + تكرار السورة + السرعة + مؤقت النوم |
| 📖 القراءة | نص المصحف بالرسم العثماني (خط أميري قرآن) + **متابعة الآيات أثناء التلاوة** |
| 🎬 فيديو | تلاوات مرئية بمشغّل داخل التطبيق وقائمة تشغيل |
| 📡 البث المباشر | قناتا القرآن والسنة من مكة (HLS) + عشرات إذاعات القرآن |
| 📚 التفسير الصوتي | اختر التفسير ثم السورة واستمع |
| 💾 مكتبتي | تحميل السور للاستماع **دون إنترنت** + المفضلة |
| 🕌 مواقيت وأذان | مواقيت الصلاة لمدينتك + إعداد المؤذن (أصوات، تنبيه مسبق، إيقاف تلقائي) |
| 🧭 القبلة | بوصلة اتجاه القبلة مع المسافة إلى مكة والمعايرة الحيّة |
|  صفحات التواصل | اتصل بنا (نموذج بريد) • تابعنا • ادعمنا (PayPal) • من نحن (YSAH-DEV) • سياسة الخصوصية |
| 🎨 التصميم | مظهر أخضر زمردي وذهبي + وضع ليلي + عربي/إنجليزي (RTL) |

---

## 🔊 مصادر الصوت والصورة / Sources

كلها **مجانية ودون مفتاح** — انظر التوثيق الكامل في [`API_SOURCES.md`](API_SOURCES.md):

- **mp3quran.net API v3** — القرّاء، المصاحف، الفيديو، البث، الإذاعات، التفسير، توقيت الآيات
- **api.alquran.cloud** — نص المصحف بالرسم العثماني
- **cdn.islamic.network** — ملفات صوتية بديلة (وضع عدم الاتصال)

---

## 🧱 بنية المشروع / Project structure

```
app/src/main/java/com/quranpro/app/
├── App.java                 # نقطة التطبيق
├── audio/                   # Media3: PlayerService, PlayerManager, Track
├── data/                    # Api, Models, QuranMeta, Store (SharedPreferences)
├── pray/                   # مواقيت الصلاة والأذان: جدولة التنبيهات، صوت المؤذن، الإشعارات
├── ui/                     # Activities + Fragments (السور، القرّاء، الفيديو، البث، القبلة، المواقيت…)
└── util/                   # DownloadHelper, ImageLoader, Ui
app/src/main/assets/fonts/   # Amiri + Amiri Quran (الرسم العثماني)
```

- **الحزمة:** `com.quranpro.app` · **اللغة:** Java 17
- **الاعتماديات:** AndroidX AppCompat/Material3/RecyclerView/DrawerLayout · Media3 (ExoPlayer + HLS + Session) · Gson

---

## 🛠️ البناء / Build

```bash
# يتطلب JDK 17 + Android SDK 34
./gradlew assembleDebug
# APK في: app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# (موقّع فقط عند وجود keystore.properties — انظر RELEASE.md)
```

- minSdk 24 (أندرويد 7.0+) — targetSdk 34
- Java + Media3 (ExoPlayer) + Material3
- يُبنى تلقائيًا عبر GitHub Actions عند الدفع إلى `main`،
  ويُنتج **GitHub Release** بملفات APK — انظر [`RELEASE.md`](RELEASE.md)

---

## 📜 الترخيص / License

MIT — انظر [LICENSE](LICENSE). التلاوات تُبث من خوادم مصادرها (mp3quran.net وIslamic Network).
