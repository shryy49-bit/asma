package com.tasneem.app;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;
import org.json.*;

/**
 * أذكار عائمة على جنب الشاشة (فوق أي تطبيق) — كل ذكر له فاصل خاص يحدده المستخدم.
 * منفصلة تمامًا عن الإشعارات العادية (الذكر الدوري/الأذان/آية اليوم… كلها زي ما هي).
 * لو صلاحية «العرض فوق التطبيقات» مش مفعّلة: بنعرض إشعار عالي الأهمية في قناة مستقلة كبديل.
 */
public class FloatDhikr {
  static final String[] TXT = {
    "الله أكبر", "الحمد لله", "لا إله إلا الله", "سبحان الله",
    "سبحان الله وبحمده سبحان الله العظيم", "أستغفر الله", "لا حول ولا قوة إلا بالله"
  };
  static final int REQ = 2900, NOTIF_ID = 2950;
  static final String CHANNEL = "tasneem_float_v1";
  static final Handler H = new Handler(Looper.getMainLooper());
  static View cur; static Runnable curDismiss;

  static boolean canOverlay(Context c) { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c); }

  static int minutesFor(Context c, int idx) {
    try {
      JSONObject o = new JSONObject(TasneemPlugin.prefs(c).getString("floatCfg", "{}"));
      return o.optInt(String.valueOf(idx), 0);
    } catch (Exception e) { return 0; }
  }

  static void scheduleAll(Context c, boolean advance) { for (int i = 0; i < TXT.length; i++) scheduleOne(c, i, advance); }

  static PendingIntent pi(Context c, int idx) {
    Intent in = new Intent(c, FloatDhikrReceiver.class).setAction("com.tasneem.FLOAT").setData(Uri.parse("tasneem://float/" + idx));
    in.putExtra("idx", idx);
    return PendingIntent.getBroadcast(c, REQ + idx, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  static void scheduleOne(Context c, int idx, boolean advance) {
    try {
      SharedPreferences p = TasneemPlugin.prefs(c);
      AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
      PendingIntent pi = pi(c, idx);
      int min = minutesFor(c, idx);
      if (!p.getBoolean("floatEnabled", false) || min <= 0) {
        am.cancel(pi); p.edit().remove("fd_next_" + idx).remove("fd_every_" + idx).apply(); return;
      }
      long now = System.currentTimeMillis(), next = p.getLong("fd_next_" + idx, 0);
      if (advance || next <= now || p.getInt("fd_every_" + idx, 0) != min) next = now + min * 60000L + idx * 15000L;
      p.edit().putLong("fd_next_" + idx, next).putInt("fd_every_" + idx, min).apply();
      TasneemPlugin.setAlarm(c, am, next, pi);
    } catch (Exception ignored) {}
  }

  /** بيتنادى من المنبّه: لا يظهر أثناء مكالمة أو والشاشة مقفولة. */
  static void fire(Context c, int idx) {
    SharedPreferences p = TasneemPlugin.prefs(c);
    if (!p.getBoolean("floatEnabled", false) || minutesFor(c, idx) <= 0) return;
    if (CallState.active(c)) return;
    PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
    if (pm != null && !pm.isInteractive()) return;
    KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
    if (km != null && km.isKeyguardLocked()) return;
    show(c, idx);
  }

  static void show(final Context c0, final int idx) {
    final Context c = c0.getApplicationContext();
    if (idx < 0 || idx >= TXT.length) return;
    if (!canOverlay(c)) { notifyFallback(c, idx); return; }
    H.post(new Runnable() { public void run() { try { showOverlay(c, idx); } catch (Exception e) { notifyFallback(c, idx); } } });
  }

  static int dp(Context c, float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()); }

  /** كبسولة بتصميم «الله أكبر»: إطار ذهبي، جامع أخضر يمين، زخرفة خضرا شمال، وموجات صوت ذهبية. */
  static View pill(Context c, String text) {
    LinearLayout outer = new LinearLayout(c);
    outer.setOrientation(LinearLayout.HORIZONTAL); outer.setGravity(Gravity.CENTER_VERTICAL);
    outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    ImageView arcs = new ImageView(c);
    arcs.setImageResource(R.drawable.ic_sound_arcs);
    outer.addView(arcs, new LinearLayout.LayoutParams(dp(c, 22), dp(c, 34)));

    LinearLayout row = new LinearLayout(c);
    row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
    row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFF8E1, 0xFFFBEBBE});
    bg.setCornerRadius(dp(c, 40)); bg.setStroke(dp(c, 3), 0xFFE2B53C);
    row.setBackground(bg); row.setElevation(dp(c, 12));
    row.setPadding(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));

    ImageView ic = new ImageView(c);
    ic.setImageResource(R.drawable.ic_mosque_pill); ic.setColorFilter(0xFF0E5A2A);
    LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(c, 58), dp(c, 46));
    il.rightMargin = dp(c, 12); row.addView(ic, il);

    TextView t = new TextView(c);
    t.setText(text); t.setTypeface(Typeface.DEFAULT_BOLD); t.setGravity(Gravity.CENTER);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, text.length() > 14 ? 19 : 28);
    t.setTextColor(0xFF0E5A2A); t.setMaxLines(2);
    t.setMaxWidth(dp(c, 210)); t.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
    row.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    View cap = new View(c);
    GradientDrawable cg = new GradientDrawable(); cg.setColor(0xFF0E5A2A);
    float r = dp(c, 36);
    cg.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
    cg.setStroke(dp(c, 2), 0xFFE2B53C);
    cap.setBackground(cg);
    row.addView(cap, new LinearLayout.LayoutParams(dp(c, 20), ViewGroup.LayoutParams.MATCH_PARENT));

    outer.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return outer;
  }

  static void showOverlay(final Context c, int idx) {
    final WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    removeNow(wm);
    View stack = pill(c, TXT[idx]);   // كل ذكر بيظهر لوحده
    int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
    WindowManager.LayoutParams wp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, type,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT);
    wp.gravity = Gravity.TOP | Gravity.RIGHT;
    wp.y = c.getResources().getDisplayMetrics().heightPixels / 4;
    stack.setTranslationX(dp(c, 480));
    stack.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dismiss(c); } });
    wm.addView(stack, wp);
    cur = stack;
    stack.animate().translationX(0).setDuration(380).start();
    curDismiss = new Runnable() { public void run() { dismiss(c); } };
    H.postDelayed(curDismiss, 7000);
  }

  static void dismiss(final Context c) {
    final View v = cur; if (v == null) return;
    if (curDismiss != null) H.removeCallbacks(curDismiss);
    v.animate().translationX(dp(c, 480)).setDuration(300).withEndAction(new Runnable() {
      public void run() { if (cur == v) removeNow((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)); }
    }).start();
  }

  static void removeNow(WindowManager wm) {
    if (curDismiss != null) H.removeCallbacks(curDismiss);
    try { if (cur != null) wm.removeView(cur); } catch (Exception ignored) {}
    cur = null;
  }

  static void notifyFallback(Context c, int idx) {
    try {
      NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
      if (Build.VERSION.SDK_INT >= 26) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "أذكار جانبية", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null); ch.enableVibration(false); nm.createNotificationChannel(ch);
      }
      Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
      PendingIntent op = open == null ? null : PendingIntent.getActivity(c, 2951, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      Notification n = new NotificationCompat.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_stat_mosque)
        .setContentTitle(TXT[idx]).setContentText("فعّل «العرض فوق التطبيقات» ليظهر الذكر على جنب الشاشة")
        .setAutoCancel(true).setTimeoutAfter(8000).setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(op).build();
      nm.notify(NOTIF_ID, n);
    } catch (Exception ignored) {}
  }
}
