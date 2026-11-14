package com.tasneem.app;
import android.content.*;

/** إشعار الذكر الدوري (سبحان الله، الحمد لله، …) — منبّه دقيق بيعيد جدولة نفسه. */
public class DhikrAlarmReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    try {
      if (!TasneemPlugin.prefs(c).getBoolean("periodicEnabled", true)) return;
      if (!CallState.active(c)) TasneemPlugin.showDhikr(c);
    } catch (Exception ignored) {}
    TasneemPlugin.scheduleDhikr(c, true);
  }
}
