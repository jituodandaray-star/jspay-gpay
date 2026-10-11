package shop.jitustore.jspay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Phone ke upar pop-up (heads-up) aur notification center ke liye. */
final class Notifier {
    static final String CH_PAY = "pay_v1";
    static final String CH_WARN = "warn_v1";
    static final String CH_SUM = "sum_v1";
    static final String GROUP = "jspay_pay";

    private Notifier() { }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel a = new NotificationChannel(CH_PAY, "Payments (pop-up)", NotificationManager.IMPORTANCE_HIGH);
            a.setDescription("Payment aate hi upar pop-up");
            a.enableVibration(true);
            a.setVibrationPattern(new long[]{0, 180, 90, 180});
            a.setSound(null, null);
            a.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(a);
            NotificationChannel w = new NotificationChannel(CH_WARN, "Chetavni (pop-up)", NotificationManager.IMPORTANCE_HIGH);
            w.setDescription("Wrong amount, internet off, premium expired");
            w.enableVibration(true);
            w.setSound(null, null);
            w.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(w);
            NotificationChannel d = new NotificationChannel(CH_SUM, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT);
            d.setDescription("Daily summary");
            nm.createNotificationChannel(d);
        } catch (Exception ignored) { }
    }

    static boolean canPost(Context c) {
        try {
            if (MainActivity.front != null) return false;   // app khula hai: beech wala popup dikhega
            SharedPreferences sp = c.getSharedPreferences("c", Context.MODE_PRIVATE);
            if (!sp.getBoolean("pop_on", true)) return false;
            if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static Bitmap smallIcon() {
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(58f);
        p.setTextAlign(Paint.Align.CENTER);
        cv.drawText("JS", 48f, 66f, p);
        return b;
    }

    static Bitmap largeIcon() {
        Bitmap b = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0f, 0f, 128f, 128f, Color.parseColor("#6366F1"), Color.parseColor("#A855F7"), Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(0f, 0f, 128f, 128f), 36f, 36f, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(62f);
        p.setTextAlign(Paint.Align.CENTER);
        cv.drawText("JS", 64f, 86f, p);
        return b;
    }

    @SuppressWarnings("deprecation")
    static Notification.Builder base(Context c, String ch) {
        channels(c);
        Notification.Builder x;
        if (Build.VERSION.SDK_INT >= 26) x = new Notification.Builder(c, ch);
        else x = new Notification.Builder(c).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_VIBRATE);
        x.setSmallIcon(Icon.createWithBitmap(smallIcon()));
        x.setLargeIcon(largeIcon());
        x.setColor(Color.parseColor("#6366F1"));
        x.setAutoCancel(true);
        x.setVisibility(Notification.VISIBILITY_PUBLIC);
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        x.setContentIntent(PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return x;
    }

    static void post(Context c, int id, Notification n) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(id, n);
        } catch (Exception ignored) { }
    }

    static String money(String s) {
        try {
            double v = Double.parseDouble(s.replace(",", ""));
            return String.format(Locale.US, "%.2f", v);
        } catch (Exception e) {
            return s;
        }
    }

    static String gwName(String gw) {
        return "bp".equals(gw) ? "BharatPe" : "GPay";
    }

    /** "[BP] ... from NAME and settled ..." se naam nikalta hai. */
    static String who(String sum) {
        try {
            Matcher m = Pattern.compile("(?i)from\\s+(.+?)(?:\\s+and\\s+settled|\\s+settled|\\s+at\\s)").matcher(sum + " ");
            return m.find() ? m.group(1).trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    static void pay(Context c, String amount, String gw, String who) {
        if (!canPost(c)) return;
        String title = "₹" + money(amount) + " mile · " + gwName(gw);
        String text = (who == null || who.isEmpty()) ? "Order PAID" : who + " · Order PAID";
        Notification.Builder x = base(c, CH_PAY);
        x.setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text));
        x.setGroup(GROUP);
        x.setWhen(System.currentTimeMillis());
        post(c, 100 + (int) (System.currentTimeMillis() % 100000L), x.build());
        Notification.Builder g = base(c, CH_PAY);
        g.setContentTitle("JS PAY").setContentText("New payments received").setGroup(GROUP).setGroupSummary(true);
        if (Build.VERSION.SDK_INT >= 26) g.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN);
        post(c, 1, g.build());
    }

    static void warn(Context c, int id, String title, String text) {
        if (!canPost(c)) return;
        Notification.Builder x = base(c, CH_WARN);
        x.setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text));
        x.setColor(Color.parseColor("#F59E0B"));
        post(c, id, x.build());
    }

    static void summary(Context c, String title, String text) {
        try {
            Notification.Builder x = base(c, CH_SUM);
            x.setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text));
            post(c, 2, x.build());
        } catch (Exception ignored) { }
    }
}
