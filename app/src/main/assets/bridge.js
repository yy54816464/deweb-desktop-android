/* Injected into all allowed frames by WebViewCompat.addDocumentStartJavaScript. */
(function () {
  'use strict';
  // Wallet provider is top-frame only. Third-party iframes must never receive it.
  if (window.top !== window) return;
  if (window.__dewebBridgeInstalled) return;
  window.__dewebBridgeInstalled = true;
  if (!window.dewebBridge) return;

  var pending = new Map();
  var seq = 0;
  var frameSalt = '';
  try {
    var rnd = new Uint8Array(8);
    crypto.getRandomValues(rnd);
    frameSalt = Array.prototype.map.call(rnd, function (b) { return ('0' + b.toString(16)).slice(-2); }).join('');
  } catch (e) {
    frameSalt = Math.random().toString(16).slice(2) + Date.now().toString(16);
  }
  var listeners = {};
  var accounts = [];
  var selected = null;
  var initialChain = (window.__dewebChainHint === '196' || window.__dewebChainHint === '0xc4') ? '0xc4' : '0x38';
  var initialNetwork = String(parseInt(initialChain, 16));

  function emit(event, data) {
    var arr = listeners[event] || [];
    arr.slice().forEach(function (fn) { try { fn(data); } catch (e) {} });
  }

  function request(method, params) {
    return new Promise(function (resolve, reject) {
      var id = frameSalt + '-r' + (++seq);
      var timer = setTimeout(function () {
        pending.delete(id);
        reject(Object.assign(new Error('bridge timeout'), { code: -32603 }));
      }, 15000);
      pending.set(id, { resolve: resolve, reject: reject, timer: timer, method: method });
      try {
        window.dewebBridge.postMessage(JSON.stringify({ id: id, method: method, params: params || [] }));
      } catch (e) {
        clearTimeout(timer);
        pending.delete(id);
        reject(e);
      }
    });
  }

  window.dewebBridge.onmessage = function (event) {
    var msg;
    try { msg = JSON.parse(event.data); } catch (e) { return; }
    var p = pending.get(msg.id);
    if (!p) return;
    pending.delete(msg.id);
    clearTimeout(p.timer);
    if (msg.error) {
      p.reject(Object.assign(new Error(msg.error.message || 'bridge error'), { code: msg.error.code || -32603 }));
      return;
    }
    var result = msg.result;
    if (p.method === 'eth_chainId') {
      provider.chainId = result;
      try { window.dispatchEvent(new CustomEvent('chainChanged', { detail: result })); } catch (e) {}
    }
    if (p.method === 'eth_requestAccounts' || p.method === 'eth_accounts') {
      accounts = Array.isArray(result) ? result : [];
      selected = accounts[0] || null;
      provider.selectedAddress = selected;
      provider.chainId = initialChain;
      if (p.method === 'eth_requestAccounts') emit('accountsChanged', accounts);
    }
    p.resolve(result);
  };

  if (window.top === window) {
    window.dewebNative = {
      isNative: true,
      openSite: function (url, chain) {
        try {
          window.dewebBridge.postMessage(JSON.stringify({
            id: frameSalt + '-native-open-' + Date.now(),
            method: 'deweb_open_site',
            params: [String(url || ''), String(chain || 'web')]
          }));
        } catch (e) {}
      }
    };
  }

  var provider = {
    isDeWeb: true,
    isMetaMask: true,
    isOkxWallet: true,
    isOKExWallet: true,
    chainId: initialChain,
    networkVersion: initialNetwork,
    selectedAddress: null,
    isConnected: function () { return true; },
    request: function (args) {
      var a = args || {};
      return request(a.method, a.params);
    },
    enable: function () { return request('eth_requestAccounts', []); },
    connect: function () { return request('eth_requestAccounts', []); },
    on: function (event, fn) {
      (listeners[event] || (listeners[event] = [])).push(fn);
    },
    removeListener: function (event, fn) {
      var arr = listeners[event] || [];
      for (var i = arr.length - 1; i >= 0; i--) if (arr[i] === fn) arr.splice(i, 1);
    }
  };

  // Native wallet revocation must also clear the provider state that dApps
  // read, otherwise their UI keeps showing the old address.
  window.addEventListener('deweb-wallet-disconnected', function () {
    try {
      accounts = [];
      selected = null;
      provider.selectedAddress = null;
      emit('accountsChanged', []);
      emit('disconnect', { code: 4900, message: 'wallet disconnected' });
    } catch (e) {}
  });

  try {
    var eip6963Info = {
      uuid: 'deweb-android-wallet-0001',
      name: 'DeWEB Wallet',
      icon: 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 32 32%22%3E%3Ccircle cx=%2216%22 cy=%2216%22 r=%2214%22 fill=%22%233DFFC0%22/%3E%3C/svg%3E',
      rdns: 'com.deweb.wallet'
    };
    var announceEip6963 = function () {
      window.dispatchEvent(new CustomEvent('eip6963:announceProvider', {
        detail: { info: eip6963Info, provider: provider }
      }));
    };
    window.addEventListener('eip6963:requestProvider', announceEip6963);
    announceEip6963();
  } catch (e) {}

  /* Some dApps register their EIP-6963 listener after the document-start
     announce. Re-announce on the standard lifecycle points so late listeners
     can still discover the same provider. */
  (function () {
    var lastAnnounce = 0;
    function announceAgain() {
      var now = Date.now();
      if (now - lastAnnounce < 200) return;
      lastAnnounce = now;
      try { announceEip6963(); } catch (e) {}
      try { window.dispatchEvent(new Event('ethereum#initialized')); } catch (e) {}
    }
    try { setTimeout(announceAgain, 50); } catch (e) {}
    try { document.addEventListener('DOMContentLoaded', announceAgain, { once: true }); } catch (e) {}
    try { window.addEventListener('load', announceAgain, { once: true }); } catch (e) {}
    try { window.addEventListener('pageshow', announceAgain); } catch (e) {}
    try { document.addEventListener('visibilitychange', function () { if (!document.hidden) announceAgain(); }); } catch (e) {}
  })();

  /* A wallet request must follow a real tap. This prevents sites that call
     eth_requestAccounts on page load from flashing the wallet window. */
  (function () {
    var last = 0;
    function gesture() {
      var now = Date.now();
      if (now - last < 400) return;
      last = now;
      try {
        window.dewebBridge.postMessage(JSON.stringify({ method: 'deweb_user_gesture', params: [] }));
      } catch (e) {}
    }
    ['pointerdown', 'touchstart', 'keydown'].forEach(function (name) {
      document.addEventListener(name, gesture, { capture: true, passive: true });
    });
  })();

  /* TapeOut's picker lists injected providers next to WalletConnect. When the
     user opens it, choose the injected provider automatically instead of
     showing a QR. No extra browser-wallet UI is added. */
  var tapeoutHost = /(^|\.)tapeout\.(net|link|site)$/i.test(location.hostname);
  if (window.top === window && tapeoutHost) {
    (function () {
      var clicked = false;
      function chooseInjected() {
        try {
          var t = window.to && window.to.wallet;
          if (!t) return;
          var options = document.querySelectorAll('[data-t="wallet-option"]');
          if (!options.length) {
            if (clicked) setTimeout(function () { clicked = false; }, 800);
            return;
          }
          if (clicked) return;
          for (var i = 0; i < options.length; i++) {
            var text = options[i].textContent || '';
            if (/WalletConnect/i.test(text)) continue;
            clicked = true;
            options[i].click();
            return;
          }
        } catch (e) {}
      }
      var timer = setInterval(chooseInjected, 350);
      setTimeout(function () { clearInterval(timer); }, 45000);
    })();
  }

  /* TapeOut and similar dApps expose window.to.wallet.connect(kind).
     When the site's default is WalletConnect, use the injected native wallet
     instead. An explicit 'wc' choice is preserved. This keeps the site UI
     unchanged and avoids asking the user to pick a "browser wallet". */
  if (window.top === window && tapeoutHost) {
    (function () {
      var tries = 0;
      var timer = setInterval(function () {
        try {
          var t = window.to && window.to.wallet;
          if (t && typeof t.connect === 'function' && !t.__dewebDefaultInjected) {
            t.__dewebDefaultInjected = true;
            var original = t.connect;
            t.connect = function (kind) {
              if (kind == null || kind === '') kind = 'injected';
              return original.call(this, kind);
            };
            clearInterval(timer);
          }
        } catch (e) {}
        if (++tries > 150) clearInterval(timer);
      }, 200);
    })();
  }

  try {
    Object.defineProperty(window, 'ethereum', {
      value: provider,
      configurable: true,
      enumerable: true,
      writable: false
    });
    try { window.okxwallet = provider; } catch (e) {}
    try { window.okexwallet = provider; } catch (e) {}
    window.dispatchEvent(new Event('ethereum#initialized'));
  } catch (e) {}
})();
