/* DeWEB partial-verifiability badge: local kernel + official gateway + chain hash. */
(function () {
  'use strict';
  if (window.top !== window) return;
  var local = null;
  var official = null;
  var el = null;
  function ensure() {
    if (el) return el;
    if (!document.body) return null;
    el = document.createElement('div');
    el.id = 'deweb-verify-badge';
    el.style.cssText = 'position:fixed;left:10px;bottom:12px;z-index:2147483000;max-width:calc(100vw - 20px);padding:7px 10px;border-radius:10px;font:10px/1.5 ui-monospace,monospace;color:#dff8ec;background:rgba(7,16,11,.88);border:1px solid rgba(86,230,176,.45);box-shadow:0 6px 20px rgba(0,0,0,.25);pointer-events:auto;white-space:pre-wrap;word-break:break-all';
    document.body.appendChild(el);
    el.onclick = function () {
      el.style.display = el.style.display === 'none' ? 'block' : 'none';
    };
    return el;
  }
  function render() {
    var e = ensure();
    if (!e) return;
    var lines = [];
    var lv = local && (local.verified != null ? local.verified : (local.ok ? '1' : '0'));
    var lmode = local && local.mode ? local.mode : 'client';
    var lsha = local && (local.sha || local.officialSha256) ? (local.sha || local.officialSha256) : '';
    if (local && lv === '1' && lmode === 'client') {
      e.style.borderColor = 'rgba(86,230,176,.8)';
      lines.push(local && local.fromCache ? '官方页：已验证（缓存）' : '官方页：已验证');
    } else if (local && lv === '1') {
      e.style.borderColor = 'rgba(217,166,46,.8)';
      lines.push('网关校验：已验证');
    } else {
      e.style.borderColor = 'rgba(224,255,242,.25)';
      lines.push('官方页：未接管 / 官方网关');
    }
    if (lsha) lines.push('内容 ' + String(lsha).slice(0, 16) + '…');
    if (local && local.block) lines.push('链上 block ' + local.block);
    if (official) {
      if (official.ok) lines.push('官方对拍：hash 一致 · block ' + official.block);
      else if (official.error) lines.push('官方对拍：不可用');
      else lines.push('官方对拍：不一致');
    }
    lines.push('点此隐藏');
    e.textContent = lines.join('\n');
  }
  if (document.body) render(); else document.addEventListener('DOMContentLoaded', render, { once: true });
  window.__dewebSetLocalVerify = function (data) {
    local = data || null;
    render();
  };
  window.__dewebSetOfficialVerify = function (data) {
    official = data || null;
    render();
  };
  window.__dewebVerifyState = function () { return { local: local, official: official }; };
})();
