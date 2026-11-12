package com.tasneem.app;
import android.app.*;
import android.content.*;
import androidx.core.app.NotificationCompat;

/** الصلاة على النبي ﷺ كل فترة يحددها المستخدم (صوت + إشعار) — منبّه دقيق بيعيد جدولة نفسه. */
public class SalawatAlarmReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    try {
      SharedPreferences p = TasneemPlugin.prefs(c);
      if (p.getBoolean("salawatPeriodicEnabled", false) && !CallState.active(c)) {
        p.edit().putInt("pending_salawat", p.getInt("pending_salawat", 0) + 1).apply();
        if (p.getBoolean("soundEnabled", true)) SalawatSound.play(c);
        TasneemPlugin.ensureChannels(c);
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        PendingIntent op = open == null ? null : PendingIntent.getActivity(c, 2801, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(c, TasneemPlugin.SALAWAT_CHANNEL).setSmallIcon(R.drawable.ic_stat_mosque)
          .setContentTitle("الصلاة على النبي ﷺ").setContentText("اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَىٰ نَبِيِّنَا مُحَمَّدٍ")
          .setAutoCancel(true).setSilent(true).setContentIntent(op).build();
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(2800, n);
      }
    } catch (Exception ignored) {}
    TasneemPlugin.scheduleSalawat(c, true);
  }
}
