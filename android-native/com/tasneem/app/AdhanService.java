package com.tasneem.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import java.io.File;
import androidx.core.app.NotificationCompat;

public class AdhanService extends Service {
  public static final String ACTION_PLAY = "PLAY", ACTION_STOP = "STOP";
  static final int ID = 2300;
  MediaPlayer player;
  final Handler callWatch = new Handler(Looper.getMainLooper());
  final Runnable callCheck = new Runnable() {
    @Override public void run() {
      if (player == null) return;
      if (CallState.active(AdhanService.this)) { stopNow(); return; } // جت مكالمة والأذان شغّال
      callWatch.postDelayed(this, 1000);
    }
  };

  @Override public int onStartCommand(Intent in, int flags, int startId) {
    String action = in == null ? null : in.getAction();
    if (ACTION_STOP.equals(action)) { stopNow(); return START_NOT_STICKY; }
    play(in == null ? null : in.getStringExtra("name"));
    return START_NOT_STICKY;
  }

  void play(String name) {
    releasePlayer();
    // لو في مكالمة أو حد بيرن: ما نقولش الأذان
    if (CallState.active(this)) {
      try { startForeground(ID, new NotificationCompat.Builder(this, TasneemPlugin.ADHAN_CHANNEL).setSmallIcon(R.drawable.ic_stat_mosque).setContentTitle("الأذان").setSilent(true).build()); } catch (Exception ignored) {}
      stopNow(); return;
    } // لا نستدعي stopSelf هنا: كان بيقتل الخدمة قبل ما الأذان يشتغل
    Intent stop = new Intent(this, NotificationActionReceiver.class).setAction("com.tasneem.STOP_ADHAN");
    PendingIntent sp = PendingIntent.getBroadcast(this, ID, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification n = new NotificationCompat.Builder(this, TasneemPlugin.ADHAN_CHANNEL)
      .setSmallIcon(R.drawable.ic_stat_mosque)
      .setContentTitle("حان الآن موعد أذان " + (name == null ? "الصلاة" : name))
      .setContentText("الأذان يعمل الآن — اضغط ⏹ لإيقافه")
      .setOngoing(true).setCategory(NotificationCompat.CATEGORY_ALARM)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .addAction(new NotificationCompat.Action(0, "⏹ إيقاف الأذان", sp)).build();
    if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    else startForeground(ID, n);
    int res = getResources().getIdentifier("adhan", "raw", getPackageName());
    File downloaded = new File(getFilesDir(), "adhan.ogg");
    if (res == 0 && !downloaded.exists()) { stopNow(); return; }
    try {
      AudioAttributes attrs = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
      if (res != 0) {
        // الأذان المضمّن داخل التطبيق (نفس أذان النسخة القديمة) له الأولوية دائمًا
        player = MediaPlayer.create(this, res, attrs, ((AudioManager) getSystemService(AUDIO_SERVICE)).generateAudioSessionId());
        if (player == null) { stopNow(); return; }
        player.setOnCompletionListener(x -> stopNow());
        player.setOnErrorListener((x, w, e) -> { stopNow(); return true; });
        player.start();
        callWatch.postDelayed(callCheck, 1500);
      } else {
        player = new MediaPlayer();
        player.setDataSource(downloaded.getAbsolutePath());
        player.setAudioAttributes(attrs);
        player.setOnCompletionListener(x -> stopNow());
        player.setOnErrorListener((x, w, e) -> { stopNow(); return true; });
        player.setOnPreparedListener(mp -> { mp.start(); callWatch.postDelayed(callCheck, 1500); });
        player.prepareAsync();
      }
    } catch (Exception e) { stopNow(); }
  }

  void releasePlayer() {
    callWatch.removeCallbacks(callCheck);
    try { if (player != null) { player.stop(); } } catch (Exception ignored) {}
    try { if (player != null) player.release(); } catch (Exception ignored) {}
    player = null;
  }

  void stopNow() {
    releasePlayer();
    try { stopForeground(true); } catch (Exception ignored) {}
    stopSelf();
  }

  @Override public void onDestroy() { releasePlayer(); super.onDestroy(); }
  @Override public IBinder onBind(Intent i) { return null; }
}
