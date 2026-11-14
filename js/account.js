/* =========================================================
   نور القرآن — صفحة «حسابي» + إشعار أول تثبيت + صورة البروفايل
   تعتمد على CloudSync (Firebase Auth + Firestore) الموجود في cloud-sync.js
   ========================================================= */
(function (global) {
  'use strict';

  var AV_KEY = 'wird.avatar.v1';        // صورة البروفايل (محلية + بتتزامن مع السيرفر)
  var SEEN_KEY = 'wird.onboard.v1';     // إشعار أول تثبيت اتعرض قبل كده
  var HAD_KEY = 'wird.hadAccount.v1';   // الجهاز سجّل/دخل بحساب قبل كده

  var mode = 'signup';                  // signup | signin | forgot
  var changing = false;                 // نموذج تغيير الإيميل ظاهر
  var busy = false;
  var msgText = '', msgOk = false;
  var root = null;

  /* ---------- أدوات ---------- */
  function $(s, r) { return (r || document).querySelector(s); }
  function $$(s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function lsGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function lsSet(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
  function lsDel(k) { try { localStorage.removeItem(k); } catch (e) {} }
  function toast(m) {
    var t = $('#toast'); if (!t) return;
    t.textContent = m; t.classList.add('show');
    clearTimeout(toast._t); toast._t = setTimeout(function () { t.classList.remove('show'); }, 2400);
  }
  function cs() { return global.CloudSync || null; }
  function user() {
    var c = cs();
    try { return c && c.enabled() ? c.currentUser() : null; } catch (e) { return null; }
  }
  function avatar() { return lsGet(AV_KEY) || ''; }

  var PERSON_SVG = '<svg viewBox="0 0 48 48" width="100%" height="100%" aria-hidden="true">' +
    '<circle cx="24" cy="18" r="9" fill="#F4A94E"/><path d="M6 46c0-10 8-16 18-16s18 6 18 16z" fill="#F4A94E"/></svg>';

  /* ---------- زر البروفايل في الشريط العلوي ---------- */
  function avatarInner() {
    var a = avatar();
    return a ? '<img src="' + esc(a) + '" alt="">' : PERSON_SVG;
  }
  function headerButton() {
    return '<button class="iconbtn avbtn' + (user() ? '' : ' nologin') + '" data-action="open-account" aria-label="حسابي">' +
      avatarInner() + '</button>';
  }
  function refreshHeader() {
    var logged = !!user();
    $$('.avbtn').forEach(function (b) {
      b.innerHTML = avatarInner();
      b.classList.toggle('nologin', !logged);
    });
  }

  /* ---------- رسم الصفحة ---------- */
  var HERO_SVG =
    '<svg viewBox="0 0 360 170" class="acct-hero-svg" aria-hidden="true">' +
    '<path d="M180 6c-34 26-64 52-64 96v64h128v-64C244 58 214 32 180 6z" fill="#fff" opacity=".55"/>' +
    '<path d="M70 70c-16 14-30 30-30 56v40h60v-40C100 100 86 84 70 70z" fill="#fff" opacity=".4"/>' +
    '<path d="M290 70c-16 14-30 30-30 56v40h60v-40c0-26-14-42-30-56z" fill="#fff" opacity=".4"/>' +
    '<circle cx="180" cy="62" r="17" fill="none" stroke="#C9A227" stroke-width="3"/>' +
    '<circle cx="187" cy="58" r="14" fill="#E9F3EE"/>' +
    '<text x="180" y="132" text-anchor="middle" font-family="Amiri,serif" font-size="32" font-weight="700" fill="#12332A">نور القرآن</text>' +
    '<rect x="0" y="164" width="360" height="6" fill="#0B211B" opacity=".08"/></svg>';

  function avatarBlock() {
    var a = avatar();
    return '<div class="acct-av">' +
      '<div class="acct-avimg">' + (a ? '<img src="' + esc(a) + '" alt="">' : PERSON_SVG) + '</div>' +
      '<button class="acct-cam" data-acct="pick-photo" aria-label="اختيار صورة">📷</button>' +
      '<input type="file" id="acct-file" accept="image/*" hidden></div>' +
      '<div class="acct-avhint">' +
        (a ? '<button class="acct-link" data-acct="pick-photo">تغيير الصورة</button> · <button class="acct-link" data-acct="rm-photo">حذف</button>'
           : '<button class="acct-link" data-acct="pick-photo">إضافة صورة (اختياري)</button>') +
      '</div>';
  }

  function field(icon, value, attrs) {
    return '<div class="acct-field"><span class="acct-ic">' + icon + '</span>' +
      '<input class="acct-in" ' + (attrs || '') + ' value="' + esc(value || '') + '"></div>';
  }

  function msgHtml() {
    return '<div class="acct-msg ' + (msgOk ? 'ok' : 'err') + '" id="acct-msg">' + esc(msgText) + '</div>';
  }

  function loggedInView(u) {
    var st = cs() && cs().status ? cs().status() : 'idle';
    var stLabel = { syncing: 'بيتزامن الآن…', synced: 'متزامن ✓', error: 'حصل خطأ في المزامنة' }[st] || 'متصل ✓';
    var h = '<div class="acct-status">' + stLabel + '</div>' +
      field('👤', u.uid.slice(0, 12), 'readonly dir="ltr" aria-label="المعرّف"') +
      field('✉', u.email, 'readonly dir="ltr" aria-label="الإيميل"');
    if (changing) {
      h += '<div class="acct-box"><div class="acct-box-t">تغيير البريد الإلكتروني</div>' +
        field('✉', '', 'id="acct-newmail" type="email" dir="ltr" placeholder="الإيميل الجديد" autocapitalize="off"') +
        field('🔒', '', 'id="acct-curpass" type="password" dir="ltr" placeholder="كلمة السر الحالية"') +
        '<div class="acct-note">هنبعتلك رابط تأكيد على الإيميل الجديد، وبعد ما تضغط عليه الإيميل بيتغيّر.</div>' +
        msgHtml() +
        '<button class="acct-btn" data-acct="do-change-email">إرسال رابط التأكيد</button>' +
        '<button class="acct-btn ghost" data-acct="cancel-change">إلغاء</button></div>';
    } else {
      h += msgHtml() +
        '<button class="acct-btn" data-acct="push"><span class="bi">⬆</span>رفع البيانات الخاصة بك إلى السيرفر</button>' +
        '<button class="acct-btn" data-acct="pull"><span class="bi">⬇</span>تحميل البيانات من السيرفر</button>' +
        '<button class="acct-btn" data-acct="change-email"><span class="bi">✉</span>تغيير البريد الإلكتروني</button>' +
        '<button class="acct-btn orange" data-acct="logout"><span class="bi">✕</span>تسجيل خروج</button>';
    }
    return h;
  }

  function guestView() {
    var c = cs();
    var enabled = false;
    try { enabled = !!(c && c.enabled()); } catch (e) {}
    var h = '';
    if (!enabled) {
      return '<div class="acct-note big">⚠ المزامنة محتاجة اتصال بالإنترنت. اتصل بالنت وافتح الصفحة دي تاني.</div>';
    }
    if (mode === 'forgot') {
      return '<div class="acct-title2">نسيت كلمة السر</div>' +
        '<div class="acct-note">اكتب إيميلك وهنبعتلك رسالة لتغيير كلمة السر.</div>' +
        field('✉', '', 'id="acct-email" type="email" dir="ltr" placeholder="الإيميل" autocapitalize="off"') +
        msgHtml() +
        '<button class="acct-btn" data-acct="submit">إرسال الرسالة</button>' +
        '<button class="acct-btn ghost" data-acct="mode-signin">رجوع</button>';
    }
    var signup = mode === 'signup';
    h += '<div class="acct-note big">' + (signup
      ? 'بياناتك (الورد والختمة والأذكار والإحصائيات) هتتحفظ على حسابك، ولو التطبيق اتمسح ترجعلك كلها.'
      : 'ادخل بحسابك عشان ترجّع بياناتك المحفوظة.') + '</div>';
    if (signup) {
      h += '<div class="acct-steps"><span><b>١</b> الإيميل</span><span><b>٢</b> كلمة السر</span><span><b>٣</b> تم ✓</span></div>';
    }
    h += field('✉', '', 'id="acct-email" type="email" dir="ltr" placeholder="الإيميل" autocapitalize="off" autocomplete="email"') +
      field('🔒', '', 'id="acct-pass" type="password" dir="ltr" placeholder="كلمة السر (٦ حروف على الأقل)" autocomplete="' + (signup ? 'new-password' : 'current-password') + '"');
    if (signup) h += field('🔒', '', 'id="acct-pass2" type="password" dir="ltr" placeholder="أعد كتابة كلمة السر" autocomplete="new-password"');
    h += msgHtml() +
      '<button class="acct-btn" data-acct="submit">' + (signup ? 'إنشاء حساب جديد' : 'دخول') + '</button>' +
      '<button class="acct-btn ghost" data-acct="' + (signup ? 'mode-signin' : 'mode-signup') + '">' +
        (signup ? 'عندي حساب بالفعل — تسجيل دخول' : 'مفيش عندي حساب — إنشاء حساب جديد') + '</button>' +
      (signup ? '' : '<button class="acct-link center" data-acct="mode-forgot">نسيت كلمة السر؟</button>');
    return h;
  }

  function render() {
    if (!root) return;
    var u = user();
    var keepScroll = root.scrollTop;
    root.innerHTML =
      '<div class="acct-top"><button class="acct-close" data-acct="close" aria-label="رجوع">▶</button></div>' +
      '<div class="acct-hero">' + HERO_SVG + '</div>' +
      '<div class="acct-card">' +
        avatarBlock() +
        '<div class="acct-title">حسابي</div>' +
        (u ? loggedInView(u) : guestView()) +
      '</div>';
    root.scrollTop = keepScroll;
  }

  /* ---------- فتح / غلق ---------- */
  function ensureRoot() {
    if (root) return;
    root = document.createElement('div');
    root.id = 'account';
    root.addEventListener('click', onClick);
    root.addEventListener('change', onChange);
    root.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && e.target && e.target.classList.contains('acct-in') && !e.target.readOnly) {
        var b = $('[data-acct="submit"], [data-acct="do-change-email"]', root);
        if (b) b.click();
      }
    });
    document.body.appendChild(root);
  }
  function open(startMode) {
    ensureRoot();
    msgText = ''; changing = false; busy = false;
    if (startMode) mode = startMode;
    else mode = lsGet(HAD_KEY) ? 'signin' : 'signup';
    render();
    root.classList.add('open');
    document.body.classList.add('acct-open');
    root.scrollTop = 0;
  }
  function close() {
    if (!root) return;
    root.classList.remove('open');
    document.body.classList.remove('acct-open');
    refreshHeader();
  }
  function isOpen() { return !!(root && root.classList.contains('open')); }

  function setMsg(t, ok) {
    msgText = t || ''; msgOk = !!ok;
    var m = $('#acct-msg', root);
    if (m) { m.textContent = msgText; m.className = 'acct-msg ' + (ok ? 'ok' : 'err'); }
  }
  function setBusy(b, label) {
    busy = b;
    $$('.acct-btn', root).forEach(function (x) { x.disabled = b; });
    if (b && label) setMsg(label, true);
  }

  /* ---------- الأحداث ---------- */
  function onClick(e) {
    var t = e.target.closest('[data-acct]');
    if (!t || busy) return;
    var a = t.getAttribute('data-acct');
    switch (a) {
      case 'close': close(); break;
      case 'pick-photo': { var f = $('#acct-file', root); if (f) f.click(); break; }
      case 'rm-photo': setPhoto(''); break;
      case 'mode-signup': mode = 'signup'; msgText = ''; render(); break;
      case 'mode-signin': mode = 'signin'; msgText = ''; render(); break;
      case 'mode-forgot': mode = 'forgot'; msgText = ''; render(); break;
      case 'submit': submit(); break;
      case 'push': doPush(); break;
      case 'pull': doPull(); break;
      case 'change-email': changing = true; msgText = ''; render(); break;
      case 'cancel-change': changing = false; msgText = ''; render(); break;
      case 'do-change-email': doChangeEmail(); break;
      case 'logout': doLogout(); break;
    }
  }
  function onChange(e) {
    if (e.target && e.target.id === 'acct-file' && e.target.files && e.target.files[0]) {
      var file = e.target.files[0];
      e.target.value = '';
      readPhoto(file).then(setPhoto).catch(function () { toast('تعذّر قراءة الصورة'); });
    }
  }

  /* ---------- الصورة: قص مربع 256px وضغط JPEG ---------- */
  function readPhoto(file) {
    return new Promise(function (res, rej) {
      var fr = new FileReader();
      fr.onerror = rej;
      fr.onload = function () {
        var img = new Image();
        img.onerror = rej;
        img.onload = function () {
          var S = 256, c = document.createElement('canvas');
          c.width = c.height = S;
          var m = Math.min(img.width, img.height);
          c.getContext('2d').drawImage(img, (img.width - m) / 2, (img.height - m) / 2, m, m, 0, 0, S, S);
          res(c.toDataURL('image/jpeg', 0.82));
        };
        img.src = fr.result;
      };
      fr.readAsDataURL(file);
    });
  }
  function setPhoto(dataUrl) {
    if (dataUrl) lsSet(AV_KEY, dataUrl); else lsDel(AV_KEY);
    var c = cs();
    if (c && c.setAvatar && user()) c.setAvatar(dataUrl || '');
    refreshHeader(); render();
    toast(dataUrl ? 'تم حفظ الصورة ✓' : 'تم حذف الصورة');
  }

  /* ---------- العمليات ---------- */
  function submit() {
    var c = cs();
    if (!c) return;
    var email = ($('#acct-email', root) || {}).value;
    email = (email || '').trim();
    if (!email) { setMsg('اكتب الإيميل'); return; }
    if (mode === 'forgot') {
      setBusy(true, 'جارٍ الإرسال…');
      c.resetPassword(email).then(function () {
        setBusy(false); setMsg('اتبعتلك رسالة على إيميلك لتغيير كلمة السر ✓', true);
      }).catch(function (err) { setBusy(false); setMsg(err.message || 'حصل خطأ'); });
      return;
    }
    var pass = ($('#acct-pass', root) || {}).value || '';
    if (!pass) { setMsg('اكتب كلمة السر'); return; }
    if (mode === 'signup') {
      if (pass.length < 6) { setMsg('كلمة السر لازم تكون ٦ حروف على الأقل'); return; }
      var p2 = ($('#acct-pass2', root) || {}).value || '';
      if (p2 !== pass) { setMsg('كلمتا السر مش متطابقتين'); return; }
      setBusy(true, 'جارٍ إنشاء الحساب…');
      c.signUp(email, pass).then(function () {
        lsSet(HAD_KEY, '1'); lsSet(SEEN_KEY, '1');
        setBusy(false); msgText = ''; toast('تم إنشاء الحساب وحفظ بياناتك ✓'); refreshHeader(); render();
      }).catch(function (err) { setBusy(false); setMsg(err.message || 'حصل خطأ'); });
    } else {
      setBusy(true, 'جارٍ الدخول…');
      c.signIn(email, pass).then(function () {
        lsSet(HAD_KEY, '1'); lsSet(SEEN_KEY, '1');
        setBusy(false); msgText = ''; toast('تم تسجيل الدخول ✓'); refreshHeader(); render();
      }).catch(function (err) { setBusy(false); setMsg(err.message || 'حصل خطأ'); });
    }
  }
  function doPush() {
    setBusy(true, 'جارٍ رفع بياناتك…');
    cs().pushNow().then(function () {
      setBusy(false); setMsg('تم رفع بياناتك إلى السيرفر ✓', true); toast('تم الرفع ✓');
    }).catch(function (err) { setBusy(false); setMsg(err.message || 'حصل خطأ'); });
  }
  function doPull() {
    if (!global.confirm('بيانات الجهاز الحالية هتتستبدل ببيانات السيرفر. تكمّل؟')) return;
    setBusy(true, 'جارٍ تحميل البيانات…');
    cs().pullNow().then(function () {
      setMsg('تم التحميل ✓ — بيتم تحديث التطبيق…', true);
    }).catch(function (err) { setBusy(false); setMsg(err.message || 'حصل خطأ'); });
  }
  function doChangeEmail() {
    var ne = (($('#acct-newmail', root) || {}).value || '').trim();
    var pw = ($('#acct-curpass', root) || {}).value || '';
    if (!ne) { setMsg('اكتب الإيميل الجديد'); return; }
    if (!pw) { setMsg('اكتب كلمة السر الحالية'); return; }
    setBusy(true, 'جارٍ الإرسال…');
    cs().changeEmail(ne, pw).then(function () {
      setBusy(false); setMsg('اتبعتنا رابط تأكيد على ' + ne + ' — اضغط عليه وهيتغيّر الإيميل ✓', true);
    }).catch(function (err) { setBusy(false); setMsg(err.message || 'حصل خطأ'); });
  }
  function doLogout() {
    if (!global.confirm('تسجيل الخروج؟ بياناتك محفوظة على السيرفر وترجعلك لما تدخل تاني.')) return;
    var c = cs();
    var p = c.pushNow ? c.pushNow().catch(function () {}) : Promise.resolve();
    p.then(function () { return c.signOut(); }).then(function () {
      mode = 'signin'; msgText = ''; refreshHeader(); render(); toast('تم تسجيل الخروج');
    }).catch(function (err) { setMsg(err.message || 'حصل خطأ'); });
  }

  /* ---------- إشعار أول تثبيت (مرة واحدة بس) ---------- */
  var obRoot = null;
  function showOnboard() {
    lsSet(SEEN_KEY, '1');
    if (obRoot) return;
    obRoot = document.createElement('div');
    obRoot.id = 'acct-onboard';
    obRoot.innerHTML =
      '<div class="ob-card" role="dialog" aria-modal="true">' +
        '<div class="ob-ic">☁</div>' +
        '<div class="ob-t">احفظ ورْدك وختمتك</div>' +
        '<div class="ob-p">سجّل بإيميلك دلوقتي، وبياناتك هتتحفظ على حسابك. لو التطبيق اتمسح أو غيّرت الموبايل، ترجعلك كل حاجة.</div>' +
        '<ol class="ob-steps">' +
          '<li><b>١</b><span>اضغط «إنشاء حساب»</span></li>' +
          '<li><b>٢</b><span>اكتب إيميلك واختار كلمة سر</span></li>' +
          '<li><b>٣</b><span>خلاص! بياناتك بتتحفظ أوتوماتيك</span></li>' +
        '</ol>' +
        '<button class="acct-btn" data-ob="signup">إنشاء حساب الآن</button>' +
        '<button class="acct-btn ghost" data-ob="signin">عندي حساب — تسجيل دخول</button>' +
        '<button class="acct-link center" data-ob="later">لاحقًا</button>' +
        '<div class="ob-tip">تلاقي حسابك في أي وقت من أيقونة 👤 فوق على الشمال.</div>' +
      '</div>';
    obRoot.addEventListener('click', function (e) {
      var t = e.target.closest('[data-ob]');
      if (!t) return;
      var a = t.getAttribute('data-ob');
      closeOnboard();
      if (a === 'signup') open('signup');
      else if (a === 'signin') open('signin');
    });
    document.body.appendChild(obRoot);
  }
  function closeOnboard() {
    if (obRoot && obRoot.parentNode) obRoot.parentNode.removeChild(obRoot);
    obRoot = null;
  }
  function maybeOnboard() {
    if (lsGet(SEEN_KEY)) return;
    var tries = 0;
    (function wait() {
      var gate = $('#ayah-gate');
      if (gate && gate.classList.contains('open')) {       // استنى لحد ما الآية تتقفل
        if (tries++ < 60) setTimeout(wait, 1500);
        return;
      }
      if (user()) { lsSet(SEEN_KEY, '1'); return; }          // داخل بالفعل
      showOnboard();
    })();
  }

  function init() {
    var c = cs();
    try { if (c && c.onChange) c.onChange(function () { refreshHeader(); if (isOpen()) render(); }); } catch (e) {}
    refreshHeader();
    setTimeout(maybeOnboard, 3000);
  }

  global.WirdAccount = {
    open: open, close: close, isOpen: isOpen, init: init,
    headerButton: headerButton, refreshHeader: refreshHeader,
    onboardOpen: function () { return !!obRoot; }, closeOnboard: closeOnboard
  };
})(window);
