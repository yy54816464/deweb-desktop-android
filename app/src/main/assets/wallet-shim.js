/* Preloaded into the trusted wallet UI WebView before wallet-service.js. */
(function () {
  'use strict';
  window.__dewebKeepWalletCache = true;
  window.__dewebAndroidWallet = true;
  if (window.__dewebWalletShimInstalled) return;
  window.__dewebWalletShimInstalled = true;

  var nativeOpen = window.open;
  window.open = function (url, target, features) {
    try {
      var u = String(url || '');
      if (/^(okx|okxwallet|okxweb3):/i.test(u) ||
          /^https?:\/\/(web3link\.okx\.com|link\.okx\.com|web3\.okx\.com\/ul\/connect)/i.test(u)) {
        send({ type: 'open_url', url: u });
        return null;
      }
    } catch (e) {}
    return nativeOpen.apply(window, arguments);
  };

  function send(obj) {
    var raw = JSON.stringify(obj);
    try {
      if (window.dewebWalletNative && window.dewebWalletNative.postMessage) {
        window.dewebWalletNative.postMessage(raw);
        return;
      }
    } catch (e) {}
    try {
      if (window.dewebWalletBridge) window.dewebWalletBridge.postMessage(raw);
    } catch (e) {}
  }

  window.dewebShell = {
    isElectron: true,
    onWalletRequest: function (handler) {
      window.__dewebWalletHandler = handler;
    },
    sendWalletResponse: function (res) {
      send(res || {});
    },
    walletAccountsChanged: function (accounts) {
      send({ type: 'accounts', accounts: accounts || [] });
    },
    walletChainChanged: function (chainId) {
      send({ type: 'chain', chainId: chainId });
    },
    focusWallet: function () {
      send({ type: 'focus' });
    },
    debug: function (message) {
      send({ type: 'debug', message: String(message || '') });
    },
    membershipChanged: function () {
      send({ type: 'membership_changed' });
    },
    rpc: function () {
      return Promise.resolve(null);
    }
  };

  try { send({ type: 'debug', message: 'wallet shim v2' }); } catch(e) {}

  window.__dewebWalletRequest = function (req) {
    try {
      if (window.__dewebWalletHandler) window.__dewebWalletHandler(req);
      else send({ type: 'error', message: 'wallet handler not ready' });
    } catch (e) {
      send({ type: 'error', message: String(e && e.message || e) });
    }
  };
})();
