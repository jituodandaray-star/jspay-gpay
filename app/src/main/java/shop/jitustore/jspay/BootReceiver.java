package shop.jitustore.jspay;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;

/** Phone on hote hi (ya app update ke baad) khud chalu ho jaye. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        try {
            SharedPreferences sp = c.getSharedPreferences("c", Context.MODE_PRIVATE);
            if (!sp.getBoolean("autostart", true)) return;
            try {
                NotificationListenerService.requestRebind(new ComponentName(c, NotifService.class));
            } catch (Exception ignored) { }
            Notifier.channels(c);
            Daily.schedule(c);
            DashService.ensure(c);
        } catch (Exception ignored) { }
    }
}
