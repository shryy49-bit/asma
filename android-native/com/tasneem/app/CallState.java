package com.tasneem.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Build;
import android.telephony.TelephonyManager;

/**
 * هل في مكالمة شغّالة أو حد بيرن دلوقتي؟ (مكالمة عادية أو واتساب/VoIP) — حتى لو الفون صامت أو على الاهتزاز.
 *  1) حالة الاتصال من النظام (TelephonyManager) — بتشوف الرنين حتى في الصامت (محتاجة صلاحية «الهاتف» على أندرويد 12+)
 *  2) وضع الصوت (AudioManager): MODE_RINGTONE / MODE_IN_CALL / MODE_IN_COMMUNICATION (مكالمات واتساب)
 */
final class CallState {
  private CallState() {}

  static boolean active(Context c) {
    try {
      boolean can = Build.VERSION.SDK_INT < 31
        || c.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
      if (can) {
        TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) return true;
      }
    } catch (Exception ignored) {}
    // إشعارات المكالمات من أي تطبيق (واتساب/تليجرام/ماسنجر…) — لو «الوصول إلى الإشعارات» متفعّل
    try { if (CallNotificationListener.connected && CallNotificationListener.hasActiveCall()) return true; } catch (Exception ignored) {}
    try {
      AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
      if (am == null) return false;
      // رنين أو صوت مكالمة شغّال من أي تطبيق
      if (Build.VERSION.SDK_INT >= 26) {
        try {
          for (AudioPlaybackConfiguration pc : am.getActivePlaybackConfigurations()) {
            AudioAttributes a = pc.getAudioAttributes();
            if (a == null) continue;
            int u = a.getUsage();
            if (u == AudioAttributes.USAGE_NOTIFICATION_RINGTONE || u == AudioAttributes.USAGE_VOICE_COMMUNICATION
                || u == AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING) return true;
          }
        } catch (Exception ignored) {}
      }
      // ميكروفون مفتوح لمكالمة (VoIP أو اتصال عادي)
      if (Build.VERSION.SDK_INT >= 24) {
        try {
          for (AudioRecordingConfiguration rc : am.getActiveRecordingConfigurations()) {
            int src = rc.getClientAudioSource();
            if (src == MediaRecorder.AudioSource.VOICE_COMMUNICATION || src == MediaRecorder.AudioSource.VOICE_CALL) return true;
          }
        } catch (Exception ignored) {}
      }
      int m = am.getMode();
      return m == AudioManager.MODE_RINGTONE
          || m == AudioManager.MODE_IN_CALL
          || m == AudioManager.MODE_IN_COMMUNICATION;
    } catch (Exception e) {
      return false;
    }
  }
}
