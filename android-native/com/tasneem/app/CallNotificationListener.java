package com.tasneem.app;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;

/**
 * بيراقب إشعارات المكالمات من أي تطبيق (واتساب، تليجرام، ماسنجر، الفون…): أي إشعار من نوع «مكالمة»
 * بيرنّ (full-screen) أو مكالمة شغّالة (ongoing). بيتفعّل من: الإعدادات ← «الوصول إلى الإشعارات».
 * مكالمة فايتة (مش ongoing وملهاش شاشة كاملة) مابتتحسبش.
 */
public class CallNotificationListener extends NotificationListenerService {
  private static final Map<String, Long> CALLS = Collections.synchronizedMap(new HashMap<String, Long>());
  static volatile boolean connected = false;

  static boolean hasActiveCall() {
    long now = System.currentTimeMillis();
    synchronized (CALLS) {
      Iterator<Map.Entry<String, Long>> it = CALLS.entrySet().iterator();
      boolean any = false;
      while (it.hasNext()) {
        Map.Entry<String, Long> e = it.next();
        if (now - e.getValue() > 3L * 60 * 60 * 1000) { it.remove(); continue; } // أمان: إشعار عالق أكتر من ٣ ساعات
        any = true;
      }
      return any;
    }
  }

  private void consider(StatusBarNotification s) {
    try {
      if (s == null || getPackageName().equals(s.getPackageName())) return;
      Notification n = s.getNotification();
      boolean ongoing = n != null && ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0
          || (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0 || n.fullScreenIntent != null);
      if (n != null && Notification.CATEGORY_CALL.equals(n.category) && ongoing) CALLS.put(s.getKey(), s.getPostTime());
      else CALLS.remove(s.getKey());
    } catch (Exception ignored) {}
  }

  @Override public void onListenerConnected() {
    connected = true;
    CALLS.clear();
    try { for (StatusBarNotification s : getActiveNotifications()) consider(s); } catch (Exception ignored) {}
  }
  @Override public void onListenerDisconnected() { connected = false; CALLS.clear(); }
  @Override public void onNotificationPosted(StatusBarNotification s) { consider(s); }
  @Override public void onNotificationRemoved(StatusBarNotification s) { if (s != null) CALLS.remove(s.getKey()); }
}
