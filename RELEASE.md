# بناء ونشر الإصدارات / Build & Release Guide

دليل بناء **QuranPro** محلياً وتوقيعه ونشره.

---

## 1) المتطلبات / Prerequisites

| الأداة | الإصدار |
|---|---|
| JDK | **17** (Temurin موصى به) |
| Android SDK | **platform 34** + **build-tools 34.0.0** + platform-tools |
| Gradle | عبر الـ wrapper المرفق (`./gradlew`) — لا حاجة لتثبيت منفصل |

```bash
export ANDROID_HOME=$HOME/Android/Sdk        # أو مسار SDK لديك
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

---

## 2) البناء المحلي / Local build

```bash
chmod +x gradlew

# نسخة debug (موقّعة تلقائياً بمفتاح debug — قابلة للتثبيت مباشرة)
./gradlew assembleDebug
# الناتج: app/build/outputs/apk/debug/app-debug.apk

# نسخة release
./gradlew assembleRelease
# الناتج: app/build/outputs/apk/release/app-release.apk          (إن وُجد keystore.properties)
#     أو: app/build/outputs/apk/release/app-release-unsigned.apk (بدونه)

# تنظيف
./gradlew clean
```

- **minSdk 24** (أندرويد 7.0+) · **targetSdk / compileSdk 34**
- Java 17 · Media3 (ExoPlayer) · Material 3 · Gson
- مهمة `fetchAdhanAssets` تجلب أصوات أذان إضافية وقت البناء إلى `app/src/main/assets/adhan/`
  (تُتخطّى الملفات الموجودة). الأصوات الأساسية **مضمّنة في المستودع**، لذلك ينجح البناء
  وتعمل خصائص الأذان دون إنترنت حتى لو تعذّر الوصول للشبكة أثناء البناء.

---

## 3) توقيع نسخة release / Signing

أنشئ مفتاحاً ثم أضف ملف `keystore.properties` في **جذر المشروع** (مستثنى من git عبر `.gitignore`):

```bash
keytool -genkeypair -v -keystore quranpro-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias quranpro
```

`keystore.properties`:
```properties
qpStoreFile=../quranpro-release.jks
qpStorePassword=YOUR_STORE_PASSWORD
qpKeyAlias=quranpro
qpKeyPassword=YOUR_KEY_PASSWORD
```

> **ملاحظة توافق:** الأسماء القديمة `cpStoreFile` / `cpStorePassword` / `cpKeyAlias` / `cpKeyPassword`
> ما زالت تعمل كخيار احتياطي، لكن `qp*` هي المفضّلة.

بدون هذا الملف يُبنى APK **بدون توقيع release** (`app-release-unsigned.apk`)، وهو ما يفعله CI حالياً.

### التوقيع عبر متغيرات البيئة في CI
يمكنك إضافة الأسرار التالية كـ Repository Secrets ثم إنشاء `keystore.properties` داخل الـ workflow:
`QP_STORE_FILE_BASE64`, `QP_STORE_PASSWORD`, `QP_KEY_ALIAS`, `QP_KEY_PASSWORD`

---

## 4) رفع رقم الإصدار / Bumping the version

عدّل في [`app/build.gradle`](app/build.gradle):

```gradle
versionCode 2          // رقم صحيح يزداد دائماً — يستخدمه أندرويد للمقارنة
versionName "1.1"      // الاسم الظاهر للمستخدم
```

CI يقرأ هذين القيمتين تلقائياً ويشتق منهما اسم الوسم `v<versionName>`.

---

## 5) النشر الآلي / Automated release (GitHub Actions)

الـ workflow [`.github/workflows/android.yml`](.github/workflows/android.yml) يعمل عند:
- الدفع إلى `main`
- فتح/تحديث Pull Request نحو `main` (بناء فقط — **بدون** نشر إصدار)
- تشغيل يدوي `workflow_dispatch`

**خطواته:** بناء debug + release → رفعها كـ Artifacts → إنشاء/تحديث **GitHub Release**
بالوسم `v<versionName>` مرفقاً بملفات APK.

> النشر **معطّل على Pull Requests** لتجنّب إعادة الوسم من فرع غير مدمج.
> العملية idempotent: إعادة بناء نفس الإصدار تستبدل الـ Release القائم بدل تكراره.

---

## 6) النشر اليدوي / Manual release

```bash
git tag -a v1.1 -m "QuranPro v1.1"
git push origin v1.1

gh release create v1.1 \
  app/build/outputs/apk/debug/app-debug.apk \
  --title "QuranPro v1.1 — القرآن الكريم صوت وصورة" \
  --notes "ملخص التغييرات..."
```

---

## 7) النشر على Google Play (اختياري)

```bash
./gradlew bundleRelease
# الناتج: app/build/outputs/bundle/release/app-release.aab
```
يتطلب **توقيع release فعلي** (القسم 3) — لا يمكن رفع `.aab` غير موقّع.
