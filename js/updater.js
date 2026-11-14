/* نور القرآن — تحديث التطبيق من GitHub Releases (زي سناب تيوب: يظهر "تحديث" فتدوس ويتثبّت فوق القديم) */
(function () {
  'use strict';
  var CAP = window.Capacitor && window.Capacitor.Plugins ? window.Capacitor.Plugins : null;
  function native() { return !!(CAP && CAP.Tasneem && CAP.Tasneem.installApk); }
  var busy = false, KEY = 'wird_upd_last';

  function el(html) { var d = document.createElement('div'); d.innerHTML = html; return d.firstChild; }
  function close() { var o = document.getElementById('upd-box'); if (o) o.remove(); }
  function toast(t) { var x = document.getElementById('toast'); if (!x) return; x.textContent = t; x.classList.add('show'); setTimeout(function () { x.classList.remove('show'); }, 3200); }

  function latest() {
    var repo = window.WIRD_REPO;
    if (!repo) return Promise.reject(new Error('no_repo'));
    return fetch('https://api.github.com/repos/' + repo + '/releases/latest', { headers: { Accept: 'application/vnd.github+json' } })
      .then(function (r) { if (!r.ok) throw new Error('http_' + r.status); return r.json(); })
      .then(function (j) {
        var m = /(\d+)\s*$/.exec(j.tag_name || ''), build = m ? +m[1] : 0;
        var asset = (j.assets || []).filter(function (a) { return /\.apk$/i.test(a.name); })[0];
        return { build: build, url: asset && asset.browser_download_url, size: asset && asset.size, name: j.name || j.tag_name };
      });
  }

  function show(info) {
    close();
    var mb = info.size ? ' (' + (info.size / 1048576).toFixed(0) + ' ميجا)' : '';
    var box = el('<div id="upd-box"><div class="upd-card"><div class="upd-ic">⬇️</div>' +
      '<h3>تحديث جديد متاح</h3><p>في نسخة أحدث من نور القرآن' + mb + '. دوس «تحديث» وهيتنزل ويتثبّت فوق النسخة الحالية من غير ما تمسح حاجة.</p>' +
      '<div class="upd-bar" style="display:none"><i></i></div><div class="upd-pct"></div>' +
      '<div class="upd-acts"><button class="upd-go">تحديث</button><button class="upd-later">لاحقًا</button></div></div></div>');
    document.body.appendChild(box);
    box.querySelector('.upd-later').onclick = close;
    box.querySelector('.upd-go').onclick = function () { install(info, box); };
  }

  function install(info, box) {
    if (busy) return; busy = true;
    var bar = box.querySelector('.upd-bar'), fill = bar.firstChild, pct = box.querySelector('.upd-pct'), go = box.querySelector('.upd-go');
    bar.style.display = 'block'; go.disabled = true; go.textContent = 'جارٍ التنزيل…';
    var h = null;
    try { h = CAP.Tasneem.addListener('updateProgress', function (e) { if (e.percent >= 0) { fill.style.width = e.percent + '%'; pct.textContent = e.percent + '٪'; } }); } catch (x) {}
    CAP.Tasneem.installApk({ url: info.url }).then(function (r) {
      busy = false;
      if (r && r.reason) {
        go.disabled = false; go.textContent = 'تحديث'; bar.style.display = 'none';
        if (r.reason === 'signature') {
          pct.innerHTML = 'النسخة اللي عندك متوقّعة بمفتاح مختلف عن التحديث، فأندرويد بيرفض يثبّته فوقها (ده بيحصل مرة واحدة بس). الحل: «نزّل من المتصفح» ← امسح التطبيق الحالي ← ثبّت الملف. لو مسجّل بإيميلك بياناتك هترجع.' +
            '<div style="display:flex;gap:8px;margin-top:10px"></div>';
          var row = pct.querySelector('div'); row.style.cssText = 'display:flex;gap:8px;margin-top:10px;justify-content:center;flex-wrap:wrap';
          var b1 = document.createElement('button'); b1.className = 'upd-go'; b1.textContent = 'نزّل من المتصفح';
          b1.onclick = function () { CAP.Tasneem.openExternal({ url: info.url }).catch(function () {}); };
          var b2 = document.createElement('button'); b2.className = 'upd-later'; b2.textContent = 'امسح التطبيق الحالي';
          b2.onclick = function () { CAP.Tasneem.uninstallSelf().catch(function () {}); };
          row.appendChild(b1); row.appendChild(b2);
        } else if (r.reason === 'notnewer') pct.textContent = 'الملف اللي اتنزّل مش أحدث من النسخة المثبّتة عندك، فأندرويد بيرفضه.';
        else pct.textContent = 'الملف اللي اتنزّل مش بتاع التطبيق ده (اسم الحزمة مختلف).';
        return;
      }
      if (r && r.needPermission) {
        go.disabled = false; go.textContent = 'تحديث'; bar.style.display = 'none';
        pct.textContent = 'اسمح لنور القرآن بتثبيت التطبيقات من الشاشة اللي فتحت، ارجع وادوس «تحديث» تاني.';
      } else { pct.textContent = 'اكتمل التنزيل — أكمل التثبيت من نافذة أندرويد.'; go.textContent = 'تثبيت'; go.disabled = false; go.onclick = function () { install(info, box); }; }
    }).catch(function (e) { busy = false; go.disabled = false; go.textContent = 'إعادة المحاولة'; pct.textContent = (e && e.message === 'bad_apk') ? 'الملف اتنزّل ناقص أو تالف — جرّب تاني.' : 'تعذّر التنزيل — تأكد من الإنترنت وجرّب تاني. (' + ((e && e.message) || 'خطأ') + ')'; });
  }

  function check(manual) {
    if (!window.WIRD_REPO) { if (manual) toast('رابط التحديث غير مضبوط في هذه النسخة'); return; }
    if (!native()) { if (manual) toast('التحديث التلقائي بيشتغل في تطبيق الأندرويد'); return; }
    latest().then(function (info) {
      try { localStorage.setItem(KEY, String(Date.now())); } catch (e) {}
      if (info.build > (window.WIRD_BUILD || 0) && info.url) show(info);
      else if (manual) toast('أنت على آخر إصدار ✓');
    }).catch(function () { if (manual) toast('تعذّر فحص التحديثات — تأكد من الإنترنت'); });
  }

  function auto() {
    var last = 0; try { last = +localStorage.getItem(KEY) || 0; } catch (e) {}
    if (Date.now() - last > 20 * 60 * 1000) check(false);
  }
  window.WirdUpdater = { check: check };
  window.addEventListener('load', function () { setTimeout(auto, 4000); });
  document.addEventListener('visibilitychange', function () { if (!document.hidden) setTimeout(auto, 1500); });
})();
