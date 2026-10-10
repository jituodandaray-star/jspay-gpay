package shop.jitustore.jspay;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Calendar;
import java.util.Locale;

/** Roz raat 10 baje: aaj ka hisaab notification mein. */
public class Daily extends BroadcastReceiver {
    static final int REQ = 7010;

    static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Calendar t = Calendar.getInstance();
            t.set(Calendar.HOUR_OF_DAY, 22);
            t.set(Calendar.MINUTE, 0);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            if (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.DAY_OF_YEAR, 1);
            PendingIntent pi = PendingIntent.getBroadcast(c, REQ, new Intent(c, Daily.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
            else am.set(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
        } catch (Exception ignored) { }
    }

    @Override
    public void onReceive(Context c, Intent in) {
        try {
            SharedPreferences sp = c.getSharedPreferences("c", Context.MODE_PRIVATE);
            if (sp.getBoolean("daily_on", true)) {
                Calendar d = Calendar.getInstance();
                d.set(Calendar.HOUR_OF_DAY, 0);
                d.set(Calendar.MINUTE, 0);
                d.set(Calendar.SECOND, 0);
                d.set(Calendar.MILLISECOND, 0);
                long start = d.getTimeInMillis();
                double gp = 0, bp = 0;
                int n = 0;
                String h = sp.getString("hist", "");
                if (!h.isEmpty()) {
                    for (String line : h.split("\n")) {
                        String[] f = line.split("\\|", -1);
                        if (f.length < 5) continue;
                        long ts = 0;
                        try { ts = Long.parseLong(f[0]); } catch (Exception ignored) { }
                        if (ts < start || !f[1].equals("200")) continue;
                        if (!f[4].equals("paid")) continue;
                        double a = 0;
                        try { a = Double.parseDouble(f[2].replace(",", "")); } catch (Exception ignored) { }
                        if (f.length > 3 && f[3].startsWith("[BP]")) bp += a; else gp += a;
                        n++;
                    }
                }
                String txt = n == 0 ? "Aaj koi payment nahi aayi."
                        : "GPay ₹" + fmt(gp) + " · BharatPe ₹" + fmt(bp) + " · " + n + " payments";
                Notifier.summary(c, "Aaj ₹" + fmt(gp + bp) + " aaye", txt);
            }
        } catch (Exception ignored) { }
        schedule(c);
    }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.2f", v);
    }
}
