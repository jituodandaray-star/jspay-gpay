package shop.jitustore.jspay;

import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

/** Awaaz (voice) ke liye text banata hai aur bhasha chunta hai.
 *  Bhasha codes: hi (Hindi), en (English), bn (Bangla), or (Odia),
 *  od (Odia, Devanagari mein likhi — jab phone mein Odia awaaz na ho to Hindi awaaz se padhi jaati hai). */
public class Voice {
    /** kind: paid, wrong, noorder, failed, plain */
    static String say(String kind, String lang, String amount, String exp) { return say(kind, lang, amount, exp, "gp"); }

    static String gwName(String gw, String lang) {
        boolean bp = "bp".equals(gw);
        switch (lang) {
            case "hi": return bp ? "भारत पे बिज़नेस" : "गूगल पे बिज़नेस";
            case "bn": return bp ? "ভারত পে বিজনেস" : "গুগল পে বিজনেস";
            case "or": return bp ? "ଭାରତ ପେ ବିଜନେସ" : "ଗୁଗଲ ପେ ବିଜନେସ";
            case "od": return bp ? "भारत पे बिज़नेस" : "गूगल पे बिज़नेस";
            default: return bp ? "Bharat Pe Business" : "Google Pay Business";
        }
    }

    static String say(String kind, String lang, String amount, String exp, String gw) {
        String n = gwName(gw, lang);
        String a = money(amount, lang);
        String e = money(exp, lang);
        switch (lang) {
            case "hi":
                switch (kind) {
                    case "paid": return n + " में " + a + " प्राप्त हुए।";
                    case "wrong": return "सावधान! " + n + " में गलत अमाउंट। " + a + " आए, ऑर्डर " + e + " का था।";
                    case "noorder": return "सावधान! " + n + " में बिना ऑर्डर के " + a + " आए।";
                    case "expired": return "सावधान! आपका प्रीमियम खत्म हो गया है। पेमेंट वेरीफाई नहीं हुई। रिन्यू करें।";
                    case "failed": return "पेमेंट सर्वर तक नहीं पहुंची। इंटरनेट चेक करें।";
                    default: return n + " में " + a + " प्राप्त हुए।";
                }
            case "bn":
                switch (kind) {
                    case "paid": return n + "-এ " + a + " পাওয়া গেছে।";
                    case "wrong": return "সাবধান! " + n + "-এ ভুল অ্যামাউন্ট। " + a + " এসেছে, অর্ডার ছিল " + e + "।";
                    case "noorder": return "সাবধান! " + n + "-এ অর্ডার ছাড়া " + a + " এসেছে।";
                    case "expired": return "সাবধান! আপনার প্রিমিয়াম শেষ হয়ে গেছে। পেমেন্ট যাচাই হয়নি। রিনিউ করুন।";
                    case "failed": return "পেমেন্ট সার্ভারে পৌঁছায়নি। ইন্টারনেট চেক করুন।";
                    default: return n + "-এ " + a + " পাওয়া গেছে।";
                }
            case "or":
                switch (kind) {
                    case "paid": return n + "ରେ " + a + " ମିଳିଲା।";
                    case "wrong": return "ସାବଧାନ! " + n + "ରେ ଭୁଲ ଅମାଉଣ୍ଟ। " + a + " ଆସିଛି, ଅର୍ଡର " + e + " ର ଥିଲା।";
                    case "noorder": return "ସାବଧାନ! " + n + "ରେ ଅର୍ଡର ବିନା " + a + " ଆସିଛି।";
                    case "expired": return "ସାବଧାନ! ଆପଣଙ୍କ ପ୍ରିମିୟମ ଶେଷ ହୋଇଯାଇଛି। ପେମେଣ୍ଟ ଯାଞ୍ଚ ହୋଇନାହିଁ। ରିନ୍ୟୁ କରନ୍ତୁ।";
                    case "failed": return "ପେମେଣ୍ଟ ସର୍ଭରକୁ ପହଞ୍ଚିନାହିଁ। ଇଣ୍ଟରନେଟ ଯାଞ୍ଚ କରନ୍ତୁ।";
                    default: return n + "ରେ " + a + " ମିଳିଲା।";
                }
            case "od":
                switch (kind) {
                    case "paid": return n + " रे " + a + " मिळिला।";
                    case "wrong": return "सावधान! " + n + " रे भुल अमाउण्ट। " + a + " आसिछि, अर्डर " + e + " र थिला।";
                    case "noorder": return "सावधान! " + n + " रे अर्डर बिना " + a + " आसिछि।";
                    case "expired": return "सावधान! आपणङ्क प्रिमियम शेष होइजाइछि। पेमेण्ट याञ्च होइनाहिँ। रिन्यू करन्तु।";
                    case "failed": return "पेमेण्ट सर्भरकु पहञ्चिनाहिँ। इण्टरनेट याञ्च करन्तु।";
                    default: return n + " रे " + a + " मिळिला।";
                }
            default:
                switch (kind) {
                    case "paid": return "Received " + a + " in " + n + ".";
                    case "wrong": return "Warning. Wrong amount in " + n + ". " + a + " received, order was " + e + ".";
                    case "noorder": return "Warning. " + a + " received in " + n + " without any order.";
                    case "expired": return "Warning. Your premium has expired. Payment was not verified. Please renew.";
                    case "failed": return "Payment could not reach the server. Check internet.";
                    default: return "Received " + a + " in " + n + ".";
                }
        }
    }

    static String money(String s, String lang) {
        try {
            double v = Double.parseDouble(s.replace(",", ""));
            long r = (long) v;
            long p = Math.round((v - r) * 100);
            String word, pw;
            switch (lang) {
                case "hi": word = " रुपये"; pw = " पैसे"; break;
                case "bn": word = " টাকা"; pw = " পয়সা"; break;
                case "or": word = " ଟଙ୍କା"; pw = " ପଇସା"; break;
                case "od": word = " टंका"; pw = " पइसा"; break;
                default: word = (r == 1 && p == 0) ? " rupee" : " rupees"; pw = " paise";
            }
            if (p > 0) return r + word + " " + p + pw;
            return r + word;
        } catch (Exception ex) {
            switch (lang) {
                case "hi": case "od": return "पैसे";
                case "bn": return "টাকা";
                case "or": return "ଟଙ୍କା";
                default: return "money";
            }
        }
    }

    private static boolean has(TextToSpeech tts, Locale l) {
        try {
            int r = tts.setLanguage(l);
            return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
        } catch (Exception e) {
            return false;
        }
    }

    /** pref: hi / en / bn / or / auto. Return: jo bhasha asal mein chal sakti hai (hi, en, bn, or, od). */
    static String pick(TextToSpeech tts, String pref) {
        if (pref.equals("bn") && has(tts, new Locale("bn", "IN"))) return "bn";
        if (pref.equals("or")) {
            if (has(tts, new Locale("or", "IN"))) return "or";
            if (has(tts, new Locale("hi", "IN"))) return "od";   // Odia awaaz nahi: Hindi awaaz se Odia padhenge
        }
        if (!pref.equals("en") && !pref.equals("bn") && !pref.equals("or")) {
            if (has(tts, new Locale("hi", "IN"))) return "hi";
        }
        if (pref.equals("hi") || pref.equals("auto")) {
            // Hindi na mile to English
        }
        try { tts.setLanguage(Locale.US); } catch (Exception ignored) { }
        return "en";
    }

    /** Har bhasha ki alag chuni hui awaaz (voice_hi / voice_en / voice_bn / voice_or) laga deta hai. */
    static String pick(TextToSpeech tts, SharedPreferences sp) {
        String l = pick(tts, sp.getString("voice_lang", "auto"));
        String key = l.equals("od") ? "voice_hi" : "voice_" + l;
        String name = sp.getString(key, "");
        if (!name.isEmpty()) {
            try {
                java.util.Set<android.speech.tts.Voice> vs = tts.getVoices();
                if (vs != null) {
                    for (android.speech.tts.Voice v : vs) {
                        if (name.equals(v.getName())) {
                            tts.setVoice(v);
                            break;
                        }
                    }
                }
            } catch (Exception ignored) { }
        }
        return l;
    }
}
