package com.via.whitelistbypass;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;











public final class PinyinUtil {

    private PinyinUtil() {}

    private static final HanyuPinyinOutputFormat FMT = new HanyuPinyinOutputFormat();

    static {
        FMT.setCaseType(HanyuPinyinCaseType.UPPERCASE);
        FMT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FMT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }





    public static char initial(char c) throws Exception {
        if (c < 0x4E00 || c > 0x9FFF) return 0;
        String[] arr = PinyinHelper.toHanyuPinyinStringArray(c, FMT);
        if (arr == null || arr.length == 0) return 0;
        return arr[0].charAt(0);
    }







    public static String initials(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                sb.append((char) (c & ~0x20));
            } else if (c >= '0' && c <= '9') {
                sb.append(c);
            } else if (c >= 0x4E00 && c <= 0x9FFF) {
                try {
                    char in = initial(c);
                    if (in != 0) sb.append(in);
                } catch (Exception ignored) {

                }
            }

        }
        return sb.toString();
    }
















    public static boolean matches(String name, String kw) {
        if (name == null || kw == null || kw.isEmpty()) return false;
        String lowerKw = kw.toLowerCase(java.util.Locale.ROOT);
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);

        if (lowerName.contains(lowerKw)) return true;

        for (int i = 0; i < kw.length(); i++) {
            char c = kw.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return false;
        }

        StringBuilder seq = new StringBuilder();
        for (int i = 0; i < kw.length(); i++) {
            char c = kw.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) seq.append(Character.toLowerCase(c));
            else if (c >= '0' && c <= '9') seq.append(c);
        }
        if (seq.length() < 2) return false;
        String seqLc = seq.toString();
        String seqUp = seqLc.toUpperCase(java.util.Locale.ROOT);

        if (isSubseq(seqLc, lowerName)) return true;
        String init = initials(name);
        if (init.isEmpty()) return false;

        if (init.contains(seqUp)) return true;

        return isSubseq(seqUp, init);
    }


    private static boolean isSubseq(String needle, String haystack) {
        if (needle.isEmpty()) return true;
        int i = 0, j = 0;
        int nl = needle.length(), hl = haystack.length();
        while (i < nl && j < hl) {
            if (needle.charAt(i) == haystack.charAt(j)) i++;
            j++;
        }
        return i == nl;
    }
}
