# رفع المشروع على GitHub / Uploading to GitHub

## الطريقة الأسهل (بدون أوامر) / Easiest way
1. أنشئ مستودعاً جديداً على github.com/new (مثلاً باسم `ConvertPro`)
2. اضغط **"uploading an existing file"**
3. اسحب كل محتويات هذا المجلد إلى الصفحة واضغط **Commit**

## بالأوامر / Via terminal
```bash
cd ConvertPro
git init -b main
git add -A
git commit -m "ConvertPro v1.0"
git remote add origin https://github.com/YOUR_USER/ConvertPro.git
git push -u origin main
```

## لبناء APK لاحقاً / To build the APK later
```bash
# يتطلب JDK 17 + Android SDK 34
./gradlew assembleRelease
# الناتج: app/build/outputs/apk/release/app-release.apk
```
> ملاحظة: للتوقيع تحتاج إنشاء keystore خاص بك وإضافة ملف `keystore.properties`
> (انظر `app/build.gradle`) — بدونها يُبنى APK بدون توقيع release.
