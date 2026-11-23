/* Main DeWEB desktop background: only the native openSite hook. */
(function () {
  'use strict';
  if (window.top !== window) return;
  if (!window.dewebBridge) return;
  window.dewebNative = {
    isNative: true,
    openSite: function (url, chain) {
      try {
        window.dewebBridge.postMessage(JSON.stringify({
          id: 'bg-open-' + Date.now(),
          method: 'deweb_open_site',
          params: [String(url || ''), String(chain || 'web')]
        }));
      } catch (e) {}
    }
  };
})();
