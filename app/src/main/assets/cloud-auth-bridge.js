(function () {
  try {
    if (window.top !== window) return;
    if (window.__dewebCloudAuthShim) return;
    window.__dewebCloudAuthShim = true;
    function send(method, id, message) {
      try {
        var payload = { id: String(id || ''), method: String(method || '') };
        if (method === 'sign') payload.message = String(message || '');
        window.dewebCloudAuthNative.postMessage(JSON.stringify(payload));
      } catch (e) {}
    }
    window.dewebCloudAuth = {
      connect: function (id) { send('connect', id); },
      sign: function (id, message) { send('sign', id, message); },
      disconnect: function (id) { send('disconnect', id); }
    };
  } catch (e) {}
})();
