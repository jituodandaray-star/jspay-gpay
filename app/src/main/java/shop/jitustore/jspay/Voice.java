package shop.jitustore.jspay;

import android.speech.tts.TextToSpeech;
import java.util.Locale;

/** Awaaz (voice) ke liye text banata hai aur bhasha chunta hai. */
public class Voice {
    /** kind: paid, wrong, noorder, failed, plain */
    static String say(String kind, String lang, String amount, String exp) { return say(kind, lang, amount, exp, "gp"); }

    static String gwName(String gw, boolean hi) {
        if ("bp".equals(gw)) return hi ? "भारत पे बिज़नेस" : "Bharat Pe Business";
        return hi ? "गूगल पे बिज़नेस" : "Google Pay Business";
    }

    static String say(String kind, String lang, String amount, String exp, String gw) {
        String n = gwName(gw, lang.equals("hi"));
        String a = money(amount, lang.equals("hi"));
        String e = money(exp, lang.equals("hi"));
        if (lang.equals("hi")) {
            switch (kind) {
                case "paid": return n + " में " + a + " प्राप्त हुए।";
                case "wrong": return "सावधान! " + n + " में गलत अमाउंट। " + a + " आए, ऑर्डर " + e + " का था।";
                case "noorder": return "सावधान! " + n + " में बिना ऑर्डर के " + a + " आए।";
                case "expired": return "सावधान! आपका प्रीमियम खत्म हो गया है। पेमेंट वेरीफाई नहीं हुई। रिन्यू करें।";
                case "failed": return "पेमेंट सर्वर तक नहीं पहुंची। इंटरनेट चेक करें।";
                default: return n + " में " + a + " प्राप्त हुए।";
            }
        }
        switch (kind) {
            case "paid": return "Received " + a + " in " + n + ".";
            case "wrong": return "Warning. Wrong amount in " + n + ". " + a + " received, order was " + e + ".";
            case "noorder": return "Warning. " + a + " received in " + n + " without any order.";
            case "expired": return "Warning. Your premium has expired. Payment was not verified. Please renew.";
            case "failed": return "Payment could not reach the server. Check internet.";
            default: return "Received " + a + " in " + n + ".";
        }
    }

    static String money(String s, boolean hi) {
        try {
            double v = Double.parseDouble(s.replace(",", ""));
            long r = (long) v;
            long p = Math.round((v - r) * 100);
            String word = hi ? " रुपये" : (r == 1 && p == 0 ? " rupee" : " rupees");
            if (p > 0) return r + word + (hi ? " " + p + " पैसे" : " " + p + " paise");
            return r + word;
        } catch (Exception ex) {
            return hi ? "पैसे" : "money";
        }
    }

    /** Chuni hui awaaz (voice_name) laga deta hai. Return: "hi" ya "en". */
    static String pick(TextToSpeech tts, String pref, String name) {
        String l = pick(tts, pref);
        if (name != null && !name.isEmpty()) {
            try {
                java.util.Set<android.speech.tts.Voice> vs = tts.getVoices();
                if (vs != null) {
                    for (android.speech.tts.Voice v : vs) {
                        if (name.equals(v.getName())) {
                            tts.setVoice(v);
                            return "hi".equals(v.getLocale().getLanguage()) ? "hi" : "en";
                        }
                    }
                }
            } catch (Exception ignored) { }
        }
        return l;
    }

    /** pref: hi / en / auto. Return: "hi" ya "en" (jo asal mein chal sakti hai). */
    static String pick(TextToSpeech tts, String pref) {
        if (!pref.equals("en")) {
            try {
                int r = tts.setLanguage(new Locale("hi", "IN"));
                if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) return "hi";
            } catch (Exception ignored) { }
        }
        try { tts.setLanguage(Locale.US); } catch (Exception ignored) { }
        return "en";
    }
}
