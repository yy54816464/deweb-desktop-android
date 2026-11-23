/* Pro entry bridge: reuses the already-working dewebBridge WebMessage channel. */
(function () {
  'use strict';
  if (window.top !== window) return;
  if (window.__dewebProBridgeInstalled) return;
  window.__dewebProBridgeInstalled = true;
  window.dewebCloudPro = {
    open: function () {
      try {
        if (window.dewebBridge && window.dewebBridge.postMessage) {
          window.dewebBridge.postMessage(JSON.stringify({ id: 'pro-' + Date.now(), method: 'deweb_open_pro', bridgeVersion: 1 }));
        }
      } catch (e) {}
    }
  };
})();
