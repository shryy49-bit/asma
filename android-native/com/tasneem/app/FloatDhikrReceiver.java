package com.tasneem.app;
import android.content.*;

/** منبّه الذكر العائم — بيعرض الذكر اللي دوره وبيعيد جدولة نفسه بنفس الفاصل. */
public class FloatDhikrReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    try { FloatDhikr.fire(c); } catch (Exception ignored) {}
    FloatDhikr.schedule(c, true);
  }
}
