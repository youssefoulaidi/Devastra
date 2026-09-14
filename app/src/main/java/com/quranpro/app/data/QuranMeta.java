package com.quranpro.app.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bundled, offline-first metadata of the 114 surahs (names, ayah counts, type). */
public final class QuranMeta {
    private QuranMeta() {}

    private static final String[] AR = {
            "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف",
            "الأنفال", "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر",
            "النحل", "الإسراء", "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون",
            "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم",
            "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص",
            "الزمر", "غافر", "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية",
            "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات", "الطور",
            "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر",
            "الممتحنة", "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم",
            "الملك", "القلم", "الحاقة", "المعارج", "نوح", "الجن", "المزمل",
            "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
            "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق", "الأعلى",
            "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى", "الشرح",
            "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات", "القارعة",
            "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر",
            "الكافرون", "النصر", "المسد", "الإخلاص", "الفلق", "الناس"
    };

    private static final String[] EN = {
            "Al-Faatiha", "Al-Baqara", "Aal-i-Imraan", "An-Nisaa", "Al-Maaida",
            "Al-An'aam", "Al-A'raaf", "Al-Anfaal", "At-Tawba", "Yunus", "Hud",
            "Yusuf", "Ar-Ra'd", "Ibrahim", "Al-Hijr", "An-Nahl", "Al-Israa",
            "Al-Kahf", "Maryam", "Taa-Haa", "Al-Anbiyaa", "Al-Hajj", "Al-Muminoon",
            "An-Noor", "Al-Furqaan", "Ash-Shu'araa", "An-Naml", "Al-Qasas",
            "Al-Ankaboot", "Ar-Room", "Luqman", "As-Sajda", "Al-Ahzaab", "Saba",
            "Faatir", "Yaseen", "As-Saaffaat", "Saad", "Az-Zumar", "Ghafir",
            "Fussilat", "Ash-Shura", "Az-Zukhruf", "Ad-Dukhaan", "Al-Jaathiya",
            "Al-Ahqaf", "Muhammad", "Al-Fath", "Al-Hujuraat", "Qaaf", "Adh-Dhaariyat",
            "At-Tur", "An-Najm", "Al-Qamar", "Ar-Rahmaan", "Al-Waaqia", "Al-Hadid",
            "Al-Mujaadila", "Al-Hashr", "Al-Mumtahana", "As-Saff", "Al-Jumu'a",
            "Al-Munaafiqoon", "At-Taghaabun", "At-Talaaq", "At-Tahrim", "Al-Mulk",
            "Al-Qalam", "Al-Haaqqa", "Al-Ma'aarij", "Nooh", "Al-Jinn", "Al-Muzzammil",
            "Al-Muddaththir", "Al-Qiyaama", "Al-Insaan", "Al-Mursalaat", "An-Naba",
            "An-Naazi'aat", "Abasa", "At-Takwir", "Al-Infitaar", "Al-Mutaffifin",
            "Al-Inshiqaaq", "Al-Burooj", "At-Taariq", "Al-A'laa", "Al-Ghaashiya",
            "Al-Fajr", "Al-Balad", "Ash-Shams", "Al-Lail", "Ad-Dhuhaa", "Ash-Sharh",
            "At-Tin", "Al-Alaq", "Al-Qadr", "Al-Bayyina", "Az-Zalzala", "Al-Aadiyaat",
            "Al-Qaari'a", "At-Takaathur", "Al-Asr", "Al-Humaza", "Al-Fil", "Quraish",
            "Al-Maa'un", "Al-Kawthar", "Al-Kaafiroon", "An-Nasr", "Al-Masad",
            "Al-Ikhlaas", "Al-Falaq", "An-Naas"
    };

    private static final int[] AYAHS = {
            7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99,
            128, 111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
            34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35,
            38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11,
            11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40,
            46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8,
            19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
    };

    private static final Set<Integer> MEDINAN = new HashSet<>();
    static {
        int[] m = {2, 3, 4, 5, 8, 9, 13, 22, 24, 33, 47, 48, 49, 55, 57, 58,
                59, 60, 61, 62, 63, 64, 65, 66, 76, 98, 99, 110};
        for (int x : m) MEDINAN.add(x);
    }

    private static List<Models.Surah> cache;

    public static List<Models.Surah> all() {
        if (cache != null) return cache;
        cache = new ArrayList<>(114);
        for (int i = 0; i < 114; i++) {
            int id = i + 1;
            cache.add(new Models.Surah(id, AR[i], EN[i], AYAHS[i], !MEDINAN.contains(id)));
        }
        return cache;
    }

    public static Models.Surah byId(int id) {
        if (id < 1 || id > 114) return null;
        return all().get(id - 1);
    }
}
