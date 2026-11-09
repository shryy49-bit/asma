package com.tasneem.app;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import androidx.core.app.NotificationCompat;
import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.*;
import java.io.*;
import java.util.*;
import java.net.*;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

@CapacitorPlugin(name = "Tasneem")
public class TasneemPlugin extends Plugin {
  static java.lang.ref.WeakReference<TasneemPlugin> INST;
  @Override public void load() { INST = new java.lang.ref.WeakReference<>(this); }

  /** أوامر إشعار الوسائط (play/pause/next/prev/stop/seek) → الواجهة */
  static void emitMedia(String action, long posMs) {
    try {
      TasneemPlugin p = INST == null ? null : INST.get(); if (p == null) return;
      JSObject o = new JSObject(); o.put("action", action); o.put("position", posMs);
      p.notifyListeners("mediaAction", o);
    } catch (Exception ignored) {}
  }

  @PluginMethod public void updatePlayback(PluginCall call) {
    try {
      PlaybackService.update(call.getString("title", ""), call.getString("artist", ""), call.getBoolean("playing", true),
        (long) (double) call.getDouble("position", 0.0), (long) (double) call.getDouble("duration", 0.0));
    } catch (Exception ignored) {}
    call.resolve();
  }
  static final String PREF = "tasneem_native";
  static final int AYAH_ID = 2200;
  static final String AYAH_CHANNEL = "tasneem_ayah";
  static final String SALAWAT_CHANNEL = "tasneem_salawat_v4"; // v4: قناة صامتة — الصوت من SalawatSound (MediaPlayer ثم TTS)
  static final String SALAWAT_SILENT_CHANNEL = SALAWAT_CHANNEL;
  static final String ADHAN_CHANNEL = "tasneem_adhan";
  static final String DHIKR_CHANNEL = "tasneem_dhikr_v1";
  static final String PREADHAN_CHANNEL = "tasneem_preadhan";
  static final int DHIKR_ID = 2600, PRE_ID = 2700;
  static final String[] DHIKR = {
    "سُبْحَانَ اللهِ", "الْحَمْدُ لِلَّهِ", "اللهُ أَكْبَرُ", "لَا إِلَٰهَ إِلَّا اللهُ", "أَسْتَغْفِرُ اللهَ",
    "سُبْحَانَ اللهِ وَبِحَمْدِهِ، سُبْحَانَ اللهِ الْعَظِيمِ", "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللهِ",
    "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَىٰ نَبِيِّنَا مُحَمَّدٍ", "أَسْتَغْفِرُ اللهَ الْعَظِيمَ وَأَتُوبُ إِلَيْهِ",
    "لَا إِلَٰهَ إِلَّا اللهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ"
  };
  static final String ADHAN_URL = "https://upload.wikimedia.org/wikipedia/commons/e/e7/Adhan.ogg";

  static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

  @PluginMethod public void start(PluginCall call) {
    ensureChannels();
    downloadAdhanIfNeeded(getContext());
    scheduleAyahOfDay(this.getContext());
    syncUnlockService(this.getContext());
    scheduleDhikr(this.getContext(), false);
    scheduleSalawat(this.getContext(), false);
    call.resolve();
  }


  // الأذان مضمّن داخل APK. لا نعتمد على الإنترنت لتشغيله.
  static void downloadAdhanIfNeeded(final Context c) {
    // متروك كدالة توافقية مع الإصدارات السابقة؛ لا يوجد تنزيل شبكي.
  }

  @PluginMethod public void setConfig(PluginCall call) {
    JSObject o = call.getData();
    SharedPreferences.Editor e = prefs(getContext()).edit();
    if (o.has("unlockEnabled")) e.putBoolean("unlockEnabled", o.optBoolean("unlockEnabled"));
    if (o.has("soundEnabled")) e.putBoolean("soundEnabled", o.optBoolean("soundEnabled"));
    if (o.has("periodicEnabled")) e.putBoolean("periodicEnabled", o.optBoolean("periodicEnabled"));
    if (o.has("periodicMin")) e.putInt("periodicMin", o.optInt("periodicMin"));
    if (o.has("adhanEnabled")) e.putBoolean("adhanEnabled", o.optBoolean("adhanEnabled"));
    if (o.has("cooldownMin")) e.putInt("cooldownMin", o.optInt("cooldownMin"));
    if (o.has("preAdhanMin")) e.putInt("preAdhanMin", o.optInt("preAdhanMin"));
    if (o.has("salawatPeriodicEnabled")) e.putBoolean("salawatPeriodicEnabled", o.optBoolean("salawatPeriodicEnabled"));
    if (o.has("salawatPeriodicMin")) e.putInt("salawatPeriodicMin", o.optInt("salawatPeriodicMin"));
    e.apply();
    syncUnlockService(getContext());
    scheduleDhikr(getContext(), false);
    scheduleSalawat(getContext(), false);
    call.resolve();
  }

  static void syncUnlockService(Context c) {
    try {
      Intent in = new Intent(c, UnlockService.class);
      if (prefs(c).getBoolean("unlockEnabled", true)) {
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(in); else c.startService(in);
      } else c.stopService(in);
    } catch (Exception ignored) {}
  }

  // زر «تجربة الصوت» في الإعدادات: يشغّل نفس مسار الصوت اللي بيشتغل عند فتح الهاتف
  @PluginMethod public void testSalawat(PluginCall call) {
    syncUnlockService(getContext()); // لو الخدمة كانت واقعة نرجّعها
    SalawatSound.play(getContext());
    call.resolve(diagnostics(getContext()));
  }

  @PluginMethod public void getDiagnostics(PluginCall call) { call.resolve(diagnostics(getContext())); }

  // تشخيص يظهر للمستخدم بعد «تجربة الصوت»: هل الخدمة شغالة؟ هل النظام بعت حدث فتح الشاشة؟ مستوى الصوت؟ البطارية؟
  static JSObject diagnostics(Context c) {
    JSObject r = new JSObject();
    try {
      SharedPreferences p = prefs(c);
      long now = System.currentTimeMillis();
      boolean running = false;
      try {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(200))
          if (UnlockService.class.getName().equals(s.service.getClassName())) { running = true; break; }
      } catch (Exception ignored) {}
      r.put("serviceRunning", running);
      r.put("serviceState", p.getString("dbg_service", "-"));
      r.put("lastEvent", p.getString("dbg_event", "-"));
      long ev = p.getLong("dbg_event_ts", 0);
      r.put("lastEventAgoSec", ev == 0 ? -1 : (now - ev) / 1000);
      r.put("lastSound", p.getString("dbg_sound", "-"));
      android.media.AudioManager a = (android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
      r.put("mediaVol", a.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
      r.put("notifVol", a.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION));
      PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
      r.put("batteryUnrestricted", Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(c.getPackageName()));
      r.put("notificationsAllowed", androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled());
      r.put("unlockEnabled", p.getBoolean("unlockEnabled", true));
      r.put("soundEnabled", p.getBoolean("soundEnabled", true));
      r.put("cooldownMin", p.getInt("cooldownMin", 0));
    } catch (Exception e) { r.put("error", String.valueOf(e)); }
    return r;
  }

  // يطلب استثناء التطبيق من توفير البطارية عشان أندرويد مايقتلش الخدمة
  @PluginMethod public void requestIgnoreBattery(PluginCall call) {
    JSObject r = new JSObject();
    try {
      Context c = getContext();
      PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
      boolean ok = Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(c.getPackageName());
      r.put("ignoring", ok);
      if (!ok) {
        Intent in = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
          .setData(android.net.Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(in);
      }
    } catch (Exception e) { r.put("error", String.valueOf(e)); }
    call.resolve(r);
  }

  // صلاحية «الهاتف» (READ_PHONE_STATE): عشان نعرف إن في مكالمة/رنين حتى لو الفون صامت
  @PluginMethod public void requestPhonePermission(PluginCall call) {
    JSObject r = new JSObject();
    try {
      Context c = getContext();
      boolean ok = Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
      r.put("granted", ok);
      if (!ok && getActivity() != null) getActivity().requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE}, 9101);
    } catch (Exception e) { r.put("error", String.valueOf(e)); }
    call.resolve(r);
  }

  // «الوصول إلى الإشعارات»: بيخلّي التطبيق يشوف مكالمات واتساب/تليجرام/ماسنجر وهي بترن
  @PluginMethod public void callDetectionStatus(PluginCall call) {
    JSObject r = new JSObject();
    try { r.put("enabled", androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(getContext()).contains(getContext().getPackageName())); }
    catch (Exception e) { r.put("enabled", false); }
    call.resolve(r);
  }
  @PluginMethod public void openNotificationAccess(PluginCall call) {
    try { getContext().startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    catch (Exception ignored) {}
    call.resolve();
  }

  @PluginMethod public void refreshWidget(PluginCall call) { scheduleAyahOfDay(getContext()); call.resolve(); }

  @PluginMethod public void scheduleAdhan(PluginCall call) {
    cancelAdhanAlarms(getContext());
    JSONArray times = call.getData().optJSONArray("times"); if (times == null) times = new JSONArray();
    SharedPreferences.Editor e = prefs(getContext()).edit().putString("adhan_times", times.toString());
    if (call.getData().has("preMin")) e.putInt("preAdhanMin", call.getData().optInt("preMin"));
    e.apply();
    scheduleAdhanAlarms(getContext(), times);
    call.resolve();
  }

  /** يجدول الأذان + تنبيه «قبل الأذان» (٥/١٠/١٥ دقيقة) — مستخدمة من الواجهة ومن إعادة التشغيل. */
  static void scheduleAdhanAlarms(Context c, JSONArray times) {
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    int pre = prefs(c).getInt("preAdhanMin", 10);
    for (int i = 0; i < times.length(); i++) {
      try {
        JSONObject t = times.getJSONObject(i);
        long at = t.getLong("at"); String name = t.optString("name", "الصلاة");
        if (at <= System.currentTimeMillis()) continue;
        Intent in = new Intent(c, AdhanAlarmReceiver.class).setAction("com.tasneem.ADHAN");
        in.putExtra("name", name); in.putExtra("request", 3000 + i);
        PendingIntent pi = PendingIntent.getBroadcast(c, 3000 + i, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(c, am, at, pi);
        long preAt = at - pre * 60000L;
        if (pre > 0 && preAt > System.currentTimeMillis()) {
          Intent pin = new Intent(c, PreAdhanReceiver.class).setAction("com.tasneem.PREADHAN");
          pin.putExtra("name", name); pin.putExtra("pre", pre);
          PendingIntent ppi = PendingIntent.getBroadcast(c, 3500 + i, pin, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
          setAlarm(c, am, preAt, ppi);
        }
      } catch (Exception ignored) {}
    }
  }

  static void setAlarm(Context c, AlarmManager am, long at, PendingIntent pi) {
    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
  }

  // ---------- ذكر كل فترة (سبحان الله / الحمد لله / الله أكبر / لا إله إلا الله / أستغفر الله) ----------
  static void scheduleDhikr(Context c, boolean advance) {
    try {
      SharedPreferences p = prefs(c);
      AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
      Intent in = new Intent(c, DhikrAlarmReceiver.class).setAction("com.tasneem.DHIKR");
      PendingIntent pi = PendingIntent.getBroadcast(c, DHIKR_ID, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      if (!p.getBoolean("periodicEnabled", true)) { am.cancel(pi); p.edit().remove("dhikr_next").remove("dhikr_every").apply(); return; }
      int min = Math.max(5, p.getInt("periodicMin", 60));
      long now = System.currentTimeMillis(), next = p.getLong("dhikr_next", 0);
      // الفاصل اتغيّر أو الموعد فات أو ده تنبيه وصل → موعد جديد؛ غير كده نثبّت نفس الموعد (فتح التطبيق مايأخّرش التنبيه)
      if (advance || next <= now || p.getInt("dhikr_every", 0) != min) next = now + min * 60000L;
      p.edit().putLong("dhikr_next", next).putInt("dhikr_every", min).apply();
      setAlarm(c, am, next, pi);
    } catch (Exception ignored) {}
  }

  // ---------- صلاة على النبي كل فترة (غير فتح الهاتف) ----------
  static void scheduleSalawat(Context c, boolean advance) {
    try {
      SharedPreferences p = prefs(c);
      AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
      Intent in = new Intent(c, SalawatAlarmReceiver.class).setAction("com.tasneem.SALAWAT");
      PendingIntent pi = PendingIntent.getBroadcast(c, 2810, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      if (!p.getBoolean("salawatPeriodicEnabled", false)) { am.cancel(pi); p.edit().remove("salawat_next").remove("salawat_every").apply(); return; }
      int min = Math.max(1, p.getInt("salawatPeriodicMin", 15));
      long now = System.currentTimeMillis(), next = p.getLong("salawat_next", 0);
      if (advance || next <= now || p.getInt("salawat_every", 0) != min) next = now + min * 60000L;
      p.edit().putLong("salawat_next", next).putInt("salawat_every", min).apply();
      setAlarm(c, am, next, pi);
    } catch (Exception ignored) {}
  }

  static void showDhikr(Context c) {
    ensureChannels(c);
    String z = DHIKR[new Random().nextInt(DHIKR.length)];
    Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
    PendingIntent op = open == null ? null : PendingIntent.getActivity(c, 2601, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    NotificationCompat.Builder b = new NotificationCompat.Builder(c, DHIKR_CHANNEL).setSmallIcon(R.drawable.ic_stat_mosque)
      .setContentTitle("ذكر").setContentText(z).setStyle(new NotificationCompat.BigTextStyle().bigText(z))
      .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).setContentIntent(op);
    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(DHIKR_ID, b.build());
  }

  static void showPreAdhan(Context c, String name, int pre) {
    ensureChannels(c);
    if (name == null) name = "الصلاة";
    String m = pre <= 10 ? pre + " دقائق" : pre + " دقيقة";
    Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
    PendingIntent op = open == null ? null : PendingIntent.getActivity(c, 2701, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    NotificationCompat.Builder b = new NotificationCompat.Builder(c, PREADHAN_CHANNEL).setSmallIcon(R.drawable.ic_stat_mosque)
      .setContentTitle("باقي " + m + " على أذان " + name).setContentText("استعدّ للصلاة")
      .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER).setContentIntent(op);
    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(PRE_ID, b.build());
  }

  // ---------- بوصلة القبلة (حساس الجهاز مباشرة + تعويض الميل + الانحراف المغناطيسي) ----------
  private SensorManager compassMgr;
  private SensorEventListener compassL;
  private final float[] rotM = new float[9], remapM = new float[9], oriV = new float[3];
  private float[] gravV, geoV;
  private double sinS, cosS; private boolean smoothInit; private long lastEmit; private float declination; private int compAcc = 3;

  @PluginMethod public void startCompass(PluginCall call) {
    stopCompassInternal();
    try {
      Context c = getContext();
      double lat = call.getDouble("lat", 0.0), lng = call.getDouble("lng", 0.0);
      declination = new GeomagneticField((float) lat, (float) lng, 0f, System.currentTimeMillis()).getDeclination();
      compassMgr = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
      final Sensor rv = compassMgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
      smoothInit = false; gravV = null; geoV = null;
      compassL = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
          int t = e.sensor.getType();
          if (t == Sensor.TYPE_ROTATION_VECTOR) { SensorManager.getRotationMatrixFromVector(rotM, e.values); emitHeading(); }
          else if (t == Sensor.TYPE_ACCELEROMETER) { gravV = lowPass(e.values, gravV); tryLegacy(); }
          else if (t == Sensor.TYPE_MAGNETIC_FIELD) { geoV = lowPass(e.values, geoV); tryLegacy(); }
        }
        @Override public void onAccuracyChanged(Sensor s, int a) { compAcc = a; }
      };
      boolean ok;
      if (rv != null) ok = compassMgr.registerListener(compassL, rv, SensorManager.SENSOR_DELAY_GAME);
      else {
        Sensor ac = compassMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), mg = compassMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        ok = ac != null && mg != null
          && compassMgr.registerListener(compassL, ac, SensorManager.SENSOR_DELAY_GAME)
          && compassMgr.registerListener(compassL, mg, SensorManager.SENSOR_DELAY_GAME);
      }
      JSObject r = new JSObject(); r.put("ok", ok); r.put("rotationVector", rv != null); r.put("declination", declination);
      call.resolve(r);
    } catch (Exception e) { JSObject r = new JSObject(); r.put("ok", false); r.put("error", String.valueOf(e)); call.resolve(r); }
  }

  private float[] lowPass(float[] in, float[] out) {
    if (out == null) return in.clone();
    for (int i = 0; i < 3; i++) out[i] += 0.2f * (in[i] - out[i]);
    return out;
  }
  private void tryLegacy() {
    if (gravV != null && geoV != null && SensorManager.getRotationMatrix(rotM, null, gravV, geoV)) emitHeading();
  }
  private void emitHeading() {
    float[] m = rotM;
    if (Math.abs(m[8]) > 0.5f) SensorManager.getOrientation(m, oriV);              // الفون أفقي: اتجاه أعلى الفون
    else { SensorManager.remapCoordinateSystem(m, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapM); SensorManager.getOrientation(remapM, oriV); } // الفون واقف: اتجاه الكاميرا الخلفية
    double h = Math.toRadians((Math.toDegrees(oriV[0]) + declination + 360.0) % 360.0);
    if (!smoothInit) { sinS = Math.sin(h); cosS = Math.cos(h); smoothInit = true; }
    else { sinS += 0.12 * (Math.sin(h) - sinS); cosS += 0.12 * (Math.cos(h) - cosS); }   // تنعيم دائري: مفيش قفزة عند 0°/360°
    long now = System.currentTimeMillis();
    if (now - lastEmit < 80) return; lastEmit = now;
    JSObject o = new JSObject();
    o.put("heading", (Math.toDegrees(Math.atan2(sinS, cosS)) + 360.0) % 360.0);
    o.put("accuracy", compAcc);
    notifyListeners("compass", o);
  }
  private void stopCompassInternal() {
    try { if (compassMgr != null && compassL != null) compassMgr.unregisterListener(compassL); } catch (Exception ignored) {}
    compassL = null;
  }
  @PluginMethod public void stopCompass(PluginCall call) { stopCompassInternal(); call.resolve(); }
  @Override protected void handleOnPause() { stopCompassInternal(); super.handleOnPause(); }
  @Override protected void handleOnDestroy() { stopCompassInternal(); super.handleOnDestroy(); }

  @PluginMethod public void startPlayback(PluginCall call) {
    try { Intent i=new Intent(getContext(), PlaybackService.class); if(Build.VERSION.SDK_INT>=26) getContext().startForegroundService(i); else getContext().startService(i); call.resolve(); }
    catch(Exception e){ call.resolve(); }
  }
  @PluginMethod public void stopPlayback(PluginCall call) {
    try { getContext().stopService(new Intent(getContext(), PlaybackService.class)); } catch(Exception ignored){}
    call.resolve();
  }

  @PluginMethod public void stopAdhan(PluginCall call) {
    try { getContext().stopService(new Intent(getContext(), AdhanService.class)); } catch(Exception ignored) {}
    ((NotificationManager)getContext().getSystemService(Context.NOTIFICATION_SERVICE)).cancel(AdhanService.ID);
    call.resolve();
  }

  @PluginMethod public void pullCounts(PluginCall call) {
    SharedPreferences p=prefs(getContext());
    JSObject r=new JSObject();
    r.put("salawat",p.getInt("pending_salawat",0)); r.put("gateRead",p.getInt("pending_gate",0)); r.put("gateChars",p.getInt("pending_chars",0));
    p.edit().putInt("pending_salawat",0).putInt("pending_gate",0).putInt("pending_chars",0).apply();
    call.resolve(r);
  }

  @PluginMethod public void requestPinWidget(PluginCall call) { JSObject r=new JSObject(); r.put("supported",false); call.resolve(r); }
  @PluginMethod public void shareApk(PluginCall call) {
    try { Intent i=new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT,"تطبيق وِرد — المصحف والأذكار"); getContext().startActivity(Intent.createChooser(i,"مشاركة وِرد")); call.resolve(); }
    catch(Exception e){ call.reject("share_failed",e); }
  }

  static void ensureChannels(Context c) {
    if (Build.VERSION.SDK_INT<26) return;
    NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
    NotificationChannel ay=new NotificationChannel(AYAH_CHANNEL,"آية اليوم",NotificationManager.IMPORTANCE_DEFAULT);
    NotificationChannel sal=new NotificationChannel(SALAWAT_CHANNEL,"الصلاة على النبي",NotificationManager.IMPORTANCE_DEFAULT);
    sal.setDescription("تنبيه الصلاة على النبي ﷺ عند فتح الهاتف"); sal.setSound(null,null); sal.enableVibration(false);
    NotificationChannel dh=new NotificationChannel(DHIKR_CHANNEL,"ذكر كل فترة",NotificationManager.IMPORTANCE_DEFAULT);
    NotificationChannel pa=new NotificationChannel(PREADHAN_CHANNEL,"تنبيه قبل الأذان",NotificationManager.IMPORTANCE_HIGH);
    NotificationChannel ad=new NotificationChannel(ADHAN_CHANNEL,"الأذان",NotificationManager.IMPORTANCE_HIGH);
    ad.setDescription("تنبيه الأذان مع صوت الأذان وزر الإيقاف"); ad.enableVibration(true); ad.setSound(null,null);
    // القنوات القديمة كانت بصوت النظام الافتراضي (وأندرويد مابيغيّرش صوت قناة موجودة) — نمسحها مرة واحدة
    SharedPreferences pr=prefs(c);
    if(!pr.getBoolean("salawat_channels_v4",false)){
      try{ nm.deleteNotificationChannel("tasneem_salawat"); nm.deleteNotificationChannel("tasneem_salawat_silent"); nm.deleteNotificationChannel("tasneem_salawat_v2"); nm.deleteNotificationChannel("tasneem_salawat_v3"); nm.deleteNotificationChannel("tasneem_salawat_v3_silent"); }catch(Exception ignored){}
      pr.edit().putBoolean("salawat_channels_v4",true).apply();
    }
    nm.createNotificationChannel(ay); nm.createNotificationChannel(sal); nm.createNotificationChannel(ad); nm.createNotificationChannel(dh); nm.createNotificationChannel(pa);
  }
  void ensureChannels(){ensureChannels(getContext());}

  static void scheduleAyahOfDay(Context c) {
    ensureChannels(c);
    Calendar cal=Calendar.getInstance(); cal.set(Calendar.HOUR_OF_DAY,8); cal.set(Calendar.MINUTE,0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
    if(cal.getTimeInMillis()<=System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR,1);
    Intent in=new Intent(c,AyahAlarmReceiver.class).setAction("com.tasneem.AYAH");
    PendingIntent pi=PendingIntent.getBroadcast(c,AYAH_ID,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
    am.cancel(pi);
    // آية جديدة كل 4 ساعات تقريبًا (بدل مرة في اليوم) — أول موعد على أقرب مضاعف من الساعة 8 صباحًا
    scheduleDhikr(c, false);
    scheduleSalawat(c, false);
    long every = 4L*60*60*1000, first = cal.getTimeInMillis();
    while (first - every > System.currentTimeMillis()) first -= every;
    if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) am.setInexactRepeating(AlarmManager.RTC_WAKEUP,first,every,pi);
    else am.setRepeating(AlarmManager.RTC_WAKEUP,first,every,pi);
  }

  static void cancelAdhanAlarms(Context c){
    AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
    String raw=prefs(c).getString("adhan_times","[]");
    try { JSONArray a=new JSONArray(raw); for(int i=0;i<a.length();i++){ Intent in=new Intent(c,AdhanAlarmReceiver.class).setAction("com.tasneem.ADHAN"); PendingIntent pi=PendingIntent.getBroadcast(c,3000+i,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); am.cancel(pi); Intent pin=new Intent(c,PreAdhanReceiver.class).setAction("com.tasneem.PREADHAN"); am.cancel(PendingIntent.getBroadcast(c,3500+i,pin,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)); } } catch(Exception ignored){}
  }

  static JSONObject nextAyah(Context c){
    try {
      String raw=readAsset(c,"public/data/quran.json");
      JSONArray a=new JSONArray(raw);
      int idx=prefs(c).getInt("ayah_index",0)%a.length();
      JSONObject q=a.getJSONObject(idx);
      prefs(c).edit().putInt("ayah_index",(idx+1)%a.length()).apply();
      return q;
    } catch(Exception e){ return null; }
  }
  static String readAsset(Context c,String name)throws Exception{ InputStream in=c.getAssets().open(name); ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1)out.write(b,0,n); in.close(); return out.toString("UTF-8"); }
  static void showAyah(Context c){
    ensureChannels(c); JSONObject q=nextAyah(c); if(q==null)return;
    String text=q.optString("text_uthmani",q.optString("text_clean","")); String surah=q.optString("surah_name","سورة"); int ayah=q.optInt("ayah",0);
    Intent read=new Intent(c,NotificationActionReceiver.class).setAction("com.tasneem.READ_AYAH").putExtra("id",AYAH_ID);
    PendingIntent rp=PendingIntent.getBroadcast(c,AYAH_ID,read,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    NotificationCompat.Builder b=new NotificationCompat.Builder(c,AYAH_CHANNEL).setSmallIcon(R.drawable.ic_stat_mosque).setContentTitle("آية اليوم").setContentText(surah+" — آية "+ayah).setStyle(new NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).addAction(new NotificationCompat.Action(0,"✓ قرأت الآية",rp));
    ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(AYAH_ID,b.build());
  }
}
