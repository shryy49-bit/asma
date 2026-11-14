package com.tasneem.app;
import android.content.*;

/** تنبيه قبل الأذان بـ ٥ / ١٠ / ١٥ دقيقة (حسب اختيار المستخدم). */
public class PreAdhanReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    try { TasneemPlugin.showPreAdhan(c, i.getStringExtra("name"), i.getIntExtra("pre", 10)); } catch (Exception ignored) {}
  }
}
