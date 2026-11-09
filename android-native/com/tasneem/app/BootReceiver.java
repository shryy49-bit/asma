package com.tasneem.app;
import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
public class BootReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    String a = i.getAction();
    if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !"android.intent.action.MY_PACKAGE_REPLACED".equals(a)) return;
    TasneemPlugin.scheduleAyahOfDay(c);
    TasneemPlugin.syncUnlockService(c);
    TasneemPlugin.scheduleDhikr(c, true);
    TasneemPlugin.scheduleSalawat(c, true);
    // إعادة جدولة الأذان المخزّن (الأجهزة بتمسح المنبهات بعد إعادة التشغيل)
    try {
      JSONArray t = new JSONArray(TasneemPlugin.prefs(c).getString("adhan_times", "[]"));
      TasneemPlugin.scheduleAdhanAlarms(c, t);
    } catch (Exception ignored) {}
  }
}
