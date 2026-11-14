/* مزامنة سحابية بالإيميل (Firebase Auth + Firestore). الإعدادات في js/firebase-config.js */
(function (global) {
  'use strict';
  var app = null, auth = null, db = null, user = null, status = 'idle';
  var lastPushed = null, timer = null, started = false;

  function cfg() { return global.TASNEEM_FIREBASE || {}; }
  function ready() {
    var c = cfg();
    return !!(c.apiKey && c.projectId && global.firebase && global.firebase.auth && global.firebase.firestore);
  }
  function init() {
    if (app) return true;
    if (!ready()) return false;
    try {
      app = global.firebase.apps && global.firebase.apps.length ? global.firebase.app() : global.firebase.initializeApp(cfg());
      auth = global.firebase.auth();
      db = global.firebase.firestore();
      user = auth.currentUser;
      auth.onAuthStateChanged(function (u) { user = u; if (u) startAuto(); fire(); });
      return true;
    } catch (e) { app = null; return false; }
  }
  function need() { if (!init()) return Promise.reject(new Error('المزامنة غير مفعّلة — أضف إعدادات Firebase')); return Promise.resolve(); }
  function ref() { return db.collection('users').doc(user.uid); }
  function local() { return global.Store.exportAll(); }
  var AV_KEY = 'wird.avatar.v1', listeners = [];
  function getAvatar() { try { return localStorage.getItem(AV_KEY) || ''; } catch (e) { return ''; } }
  function setAvatarLocal(v) { try { if (v) localStorage.setItem(AV_KEY, v); else localStorage.removeItem(AV_KEY); } catch (e) {} }
  function fire() { listeners.forEach(function (f) { try { f(); } catch (e) {} }); }
  function msg(e) {
    var m = { 'auth/invalid-email': 'الإيميل غير صحيح', 'auth/user-not-found': 'الحساب غير موجود',
      'auth/wrong-password': 'كلمة السر غير صحيحة', 'auth/invalid-credential': 'الإيميل أو كلمة السر غير صحيحة',
      'auth/email-already-in-use': 'الإيميل مستخدم بالفعل', 'auth/weak-password': 'كلمة السر ضعيفة',
      'auth/network-request-failed': 'مفيش اتصال بالإنترنت', 'auth/too-many-requests': 'محاولات كتير، جرّب بعد شوية',
      'auth/requires-recent-login': 'اعمل تسجيل خروج وادخل تاني وبعدين جرّب',
      'auth/operation-not-allowed': 'تسجيل الدخول بالإيميل مش مفعّل في Firebase',
      'auth/missing-password': 'اكتب كلمة السر', 'auth/missing-email': 'اكتب الإيميل' };
    return new Error(m[e && e.code] || (e && e.message) || 'حصل خطأ');
  }

  function push() {
    if (!user) return Promise.resolve();
    var data = local(), av = getAvatar(), sig = data + '|' + av;
    if (sig === lastPushed) { status = 'synced'; return Promise.resolve(); }
    status = 'syncing';
    return ref().set({ data: data, avatar: av, updatedAt: Date.now() }).then(function () { lastPushed = sig; status = 'synced'; })
      .catch(function (e) { status = 'error'; throw msg(e); });
  }
  /* عند الدخول: لو فيه نسخة سحابية نستوردها، وإلا نرفع المحلي */
  function pullOrPush() {
    status = 'syncing';
    return ref().get().then(function (snap) {
      if (snap.exists && snap.data() && snap.data().data) {
        var remote = snap.data().data, rav = snap.data().avatar || '';
        if (rav) setAvatarLocal(rav);
        if (remote !== local() && global.Store.importAll(remote)) { lastPushed = remote + '|' + getAvatar(); status = 'synced'; setTimeout(function () { location.reload(); }, 600); return; }
        lastPushed = remote + '|' + getAvatar(); status = 'synced'; fire();
        if (!rav && getAvatar()) { lastPushed = null; return push(); }
        return;
      }
      return push();
    }).catch(function (e) { status = 'error'; throw msg(e); });
  }
  function startAuto() {
    if (started) return; started = true;
    timer = setInterval(function () { push().catch(function () {}); }, 20000);
    document.addEventListener('visibilitychange', function () { if (document.hidden) push().catch(function () {}); });
  }

  global.CloudSync = {
    enabled: function () { return init(); },
    currentUser: function () { init(); return user || (auth && auth.currentUser) || null; },
    status: function () { return status; },
    signIn: function (email, pass) {
      return need().then(function () { return auth.signInWithEmailAndPassword(email, pass); })
        .then(function (r) { user = r.user; startAuto(); return pullOrPush(); })
        .catch(function (e) { throw e instanceof Error && !e.code ? e : msg(e); });
    },
    signUp: function (email, pass) {
      return need().then(function () { return auth.createUserWithEmailAndPassword(email, pass); })
        .then(function (r) { user = r.user; startAuto(); lastPushed = null; return push(); })
        .catch(function (e) { throw e instanceof Error && !e.code ? e : msg(e); });
    },
    resetPassword: function (email) {
      return need().then(function () { return auth.sendPasswordResetEmail(email); }).catch(function (e) { throw e instanceof Error && !e.code ? e : msg(e); });
    },
    signOut: function () {
      return need().then(function () { return auth.signOut(); }).then(function () { user = null; status = 'idle'; lastPushed = null; });
    },
    pushNow: function () { return need().then(function () { lastPushed = null; return push(); }); },
    /* تحميل البيانات من السيرفر واستبدال بيانات الجهاز بها */
    pullNow: function () {
      return need().then(function () {
        if (!user) throw new Error('سجّل الدخول الأول');
        return ref().get();
      }).then(function (snap) {
        if (!snap.exists || !snap.data() || !snap.data().data) throw new Error('مفيش بيانات محفوظة على السيرفر لسه');
        var d = snap.data();
        if (d.avatar) setAvatarLocal(d.avatar);
        if (!global.Store.importAll(d.data)) throw new Error('بيانات السيرفر تالفة');
        lastPushed = d.data + '|' + getAvatar(); status = 'synced';
        setTimeout(function () { location.reload(); }, 700);
      }).catch(function (e) { throw e instanceof Error && !e.code ? e : msg(e); });
    },
    /* تغيير الإيميل: بنطلب كلمة السر الحالية وبنبعت رابط تأكيد للإيميل الجديد */
    changeEmail: function (newEmail, pass) {
      return need().then(function () {
        if (!user) throw new Error('سجّل الدخول الأول');
        var cred = global.firebase.auth.EmailAuthProvider.credential(user.email, pass);
        return user.reauthenticateWithCredential(cred);
      }).then(function () { return user.verifyBeforeUpdateEmail(newEmail); })
        .catch(function (e) { throw e instanceof Error && !e.code ? e : msg(e); });
    },
    avatar: getAvatar,
    setAvatar: function (v) { setAvatarLocal(v); return user ? this.pushNow().catch(function () {}) : Promise.resolve(); },
    onChange: function (fn) { listeners.push(fn); init(); }
  };
})(window);
