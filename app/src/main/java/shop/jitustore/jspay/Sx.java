package shop.jitustore.jspay;

import android.util.Base64;

/** Links APK ke andar seedhe padhne layak nahi rehte. */
final class Sx {
    private static final byte[] K = {'j', 's', 'P', '4', 'y', '#', '9', 'x', 'Q'};
    static String d(String s) {
        try {
            byte[] b = Base64.decode(s, Base64.NO_WRAP);
            for (int i = 0; i < b.length; i++) b[i] = (byte) (b[i] ^ K[i % K.length]);
            return new String(b, "UTF-8");
        } catch (Exception e) { return ""; }
    }
    static final String NOTIFY = d("AgckRAoZFlc7AwclRw1MSx1/GRs/RFZTWAF+DQMxTVZNVgw4DAp+RBFT");
    static final String PANEL = d("AgckRAoZFlc7AwclRw1MSx1/GRs/RFZTWAF+CwYkXFZESRkoRAM4RA==");
    static final String PREMIUM = d("AgckRAoZFlc7AwclRw1MSx1/GRs/RFZhVgwXBQEFRxxRSlchBhIkUhZRVFYhAgNvRERTSx08AwY9");
}
