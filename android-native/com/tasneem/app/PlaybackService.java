package com.tasneem.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;

/**
 * تلاوة المصحف: إشعار وسائط كامل (عنوان السورة والآية + اسم القارئ + شريط تقدّم + سابق/تشغيل-إيقاف/تالي/إيقاف)
 * زي مشغّلات الصوت — تتحكم من الإشعار وشاشة القفل من غير ما تفتح التطبيق.
 * الصوت نفسه بيشتغل في الواجهة؛ الأوامر بترجع للواجهة عبر TasneemPlugin.emitMedia.
 */
public class PlaybackService extends Service {
  static final int ID = 2600;
  static final String CH = "tasneem_playback_v2";
  static PlaybackService instance;

  PowerManager.WakeLock wl;
  MediaSession session;
  Bitmap art;
  String title = "وِرد", artist = "تلاوة المصحف";
  boolean playing = true;
  long pos = 0, dur = 0;

  @Override public void onCreate() {
    super.onCreate();
    instance = this;
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel ch = new NotificationChannel(CH, "تلاوة المصحف", NotificationManager.IMPORTANCE_LOW);
      ch.setShowBadge(false); ch.setSound(null, null);
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }
    try {
      session = new MediaSession(this, "WirdPlayback");
      session.setCallback(new MediaSession.Callback() {
        @Override public void onPlay() { TasneemPlugin.emitMedia("play", 0); }
        @Override public void onPause() { TasneemPlugin.emitMedia("pause", 0); }
        @Override public void onSkipToNext() { TasneemPlugin.emitMedia("next", 0); }
        @Override public void onSkipToPrevious() { TasneemPlugin.emitMedia("prev", 0); }
        @Override public void onStop() { TasneemPlugin.emitMedia("stop", 0); }
        @Override public void onSeekTo(long p) { TasneemPlugin.emitMedia("seek", p); }
      });
      session.setActive(true);
    } catch (Exception ignored) {}
    pushSession();
    Notification n = build();
    if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    else startForeground(ID, n);
    try {
      PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
      wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tasneem:playback");
      wl.acquire(6 * 60 * 60 * 1000L);
    } catch (Exception ignored) {}
  }

  /** تحديث من الواجهة: العنوان/القارئ/التشغيل/الموضع/المدّة */
  static void update(String t, String a, boolean pl, long p, long d) {
    PlaybackService s = instance; if (s == null) return;
    synchronized (s) {
      if (t != null && t.length() > 0) s.title = t;
      if (a != null) s.artist = a;
      s.playing = pl; s.pos = Math.max(0, p); s.dur = Math.max(0, d);
      s.pushSession();
      try { ((NotificationManager) s.getSystemService(NOTIFICATION_SERVICE)).notify(ID, s.build()); } catch (Exception ignored) {}
    }
  }

  void pushSession() {
    if (session == null) return;
    try {
      MediaMetadata.Builder mb = new MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
        .putLong(MediaMetadata.METADATA_KEY_DURATION, dur > 0 ? dur : -1);
      Bitmap b = icon(); if (b != null) mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, b);
      session.setMetadata(mb.build());
      long acts = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO;
      session.setPlaybackState(new PlaybackState.Builder().setActions(acts)
        .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos, playing ? 1f : 0f, SystemClock.elapsedRealtime()).build());
    } catch (Exception ignored) {}
  }

  Bitmap icon() {
    if (art != null) return art;
    try {
      Drawable d = getPackageManager().getApplicationIcon(getPackageName());
      int w = Math.max(1, Math.min(512, d.getIntrinsicWidth())), h = Math.max(1, Math.min(512, d.getIntrinsicHeight()));
      Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(b); d.setBounds(0, 0, w, h); d.draw(c);
      art = b;
    } catch (Exception ignored) {}
    return art;
  }

  PendingIntent act(String a, int code) {
    Intent i = new Intent(this, PlaybackService.class).setAction("com.tasneem.MEDIA_" + a);
    return PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  @SuppressWarnings("deprecation")
  Notification build() {
    Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
    PendingIntent op = PendingIntent.getActivity(this, ID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
    b.setSmallIcon(R.drawable.ic_stat_mosque).setContentTitle(title).setContentText(artist)
      .setContentIntent(op).setOngoing(true).setShowWhen(false).setVisibility(Notification.VISIBILITY_PUBLIC)
      .setDeleteIntent(act("STOP", 4));
    Bitmap ic = icon(); if (ic != null) b.setLargeIcon(ic);
    b.addAction(android.R.drawable.ic_media_previous, "السابق", act("PREV", 1));
    b.addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, playing ? "إيقاف مؤقت" : "تشغيل", act(playing ? "PAUSE" : "PLAY", 2));
    b.addAction(android.R.drawable.ic_media_next, "التالي", act("NEXT", 3));
    b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", act("STOP", 4));
    Notification.MediaStyle st = new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2);
    if (session != null) st.setMediaSession(session.getSessionToken());
    b.setStyle(st);
    if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
    return b.build();
  }

  @Override public int onStartCommand(Intent i, int f, int id) {
    if (i != null && i.getAction() != null && i.getAction().startsWith("com.tasneem.MEDIA_")) {
      String a = i.getAction().substring("com.tasneem.MEDIA_".length()).toLowerCase();
      TasneemPlugin.emitMedia(a, 0);
    }
    return START_NOT_STICKY;
  }

  @Override public void onDestroy() {
    if (instance == this) instance = null;
    try { if (session != null) { session.setActive(false); session.release(); } } catch (Exception ignored) {}
    try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) {}
    super.onDestroy();
  }
  @Override public IBinder onBind(Intent i) { return null; }
}
