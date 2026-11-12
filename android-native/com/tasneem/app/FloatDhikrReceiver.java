package com.tasneem.app;
import android.content.*;

/** منبّه الذكر العائم — بيعرض الذكر ويعيد جدولة نفسه. */
public class FloatDhikrReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    int idx = i.getIntExtra("idx", -1);
    try { if (idx >= 0) FloatDhikr.fire(c, idx); } catch (Exception ignored) {}
    if (idx >= 0) FloatDhikr.scheduleOne(c, idx, true);
  }
}
