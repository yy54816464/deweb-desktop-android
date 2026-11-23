(function(){
  'use strict';
  window.__dewebCloudService = 'v3';
if (!window.dewebShell || !window.dewebShell.isElectron || !window.dewebShell.onWalletRequest) return;

  var ui = null;
  var accounts = [];
var walletBusy = false;
  var chain = 'eip155:196';
  var chainHex = '0xc4';
  var queue = Promise.resolve();
  var APPROVED = 'deweb.electron.wc.origins';
  var APPROVED_EXP = 'deweb.electron.wc.origins.exp';
  var AUTH_TTL = 30 * 24 * 60 * 60 * 1000;
  var ORIGIN_CHAIN = 'deweb.electron.wc.originchain';
  var connectedOrigins = {};
  var panelEl = null;
  var siteSession = {};
  var windowConnected = {};
  var generation = 0;
  var ALPHA_FREE_UNTIL = Date.parse('2026-12-31T15:59:59Z');
  function alphaFree(){ return Date.now() < ALPHA_FREE_UNTIL; }
  /* A stale OKX/WalletConnect session survives app restarts and makes a new
     site look connected without showing a QR. Clear only wallet cache keys,
     never the user's site profile or cloud login. */
  function clearWalletCache(){
    try {
      Object.keys(localStorage).forEach(function(k){
        if (/^OKXStorageKey|^okx_wallet_connect|^wc@|^walletconnect/i.test(k)) localStorage.removeItem(k);
      });
    } catch(e) {}
    try {
      if (indexedDB && indexedDB.databases){
        indexedDB.databases().then(function(dbs){
          (dbs || []).forEach(function(d){ if (d && /okx|walletconnect|wc/i.test(d.name || '')) indexedDB.deleteDatabase(d.name); });
        }).catch(function(){});
      }
    } catch(e) {}
  }
  var __walletReset = false;
  try { __walletReset = new URLSearchParams(location.search).get('reset') === '1'; } catch (e) {}
  if (__walletReset) {
    clearWalletCache();
    try { localStorage.removeItem(APPROVED); localStorage.removeItem(APPROVED_EXP); localStorage.removeItem(ORIGIN_CHAIN); } catch (e) {}
  }
  if (!window.__dewebKeepWalletCache) clearWalletCache();
  var RPCS = { '56': 'https://bsc-rpc.publicnode.com', '196': 'https://rpc.xlayer.tech', '1': 'https://ethereum-rpc.publicnode.com' };
  var selectedChain = 'bsc';
  async function publicRpc(method, params){
    var id = parseInt((chain.split(':')[1] || '196'), 10);
    var url = RPCS[String(id)] || RPCS['196'];
    try {
      var r = await fetch(url, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ jsonrpc: '2.0', id: Date.now(), method: method, params: params || [] })
      });
      var j = await r.json();
      if (j && j.error) throw new Error(j.error.message || 'rpc error');
      if (j && j.result !== undefined) return j.result;
    } catch(e) {}
    try {
      if (window.dewebShell && window.dewebShell.rpc) return await window.dewebShell.rpc(method, params, String(id));
    } catch(e) {}
    return null;
  }
  function loadOkx(){
    if (window.OKXTonConnectUISdk && window.OKXTonConnectUISdk.OKXUniversalConnectUI) return Promise.resolve();
    return new Promise(function(resolve, reject){
      var s = document.createElement('script');
      s.src = './okx-ui.js';
      s.onload = function(){ resolve(); };
      s.onerror = function(){ reject(new Error('OKX Connect bundle failed to load')); };
      document.head.appendChild(s);
    });
  }
  function confirmBox(title, text, opts){
    opts = opts || {};
    return new Promise(function(resolve){
      var mask = document.createElement('div');
      mask.className = 'mask on';
      mask.innerHTML = '<div class="modal"><div class="hd"><span>WALLET CONFIRM</span><span class="wc-x" style="cursor:pointer;color:var(--dim)">×</span></div>' +
        '<div class="bd"><p id="wcTitle"></p><div class="tip-addr" id="wcBody" style="white-space:pre-wrap;max-height:260px;overflow:auto"></div></div>' +
        '<div class="ft"><button class="btn" id="wcNo"></button><button class="btn on" id="wcYes"></button></div></div>';
      document.body.appendChild(mask);
      mask.querySelector('#wcTitle').textContent = title;
      mask.querySelector('#wcBody').textContent = text;
      mask.querySelector('#wcNo').textContent = opts.cancelText || '取消';
      mask.querySelector('#wcYes').textContent = opts.confirmText || '确认';
      function close(v){ mask.remove(); resolve(v); }
      mask.querySelector('.wc-x').onclick = function(){ close(false); };
      mask.querySelector('#wcNo').onclick = function(){ close(false); };
      mask.querySelector('#wcYes').onclick = function(){ close(true); };
    });
  }
  function hexToBig(hex){
    try { return BigInt(hex || '0x0'); } catch(e) { return BigInt(0); }
  }
  function isUnlimited(word){
    try {
      var v = BigInt('0x' + word);
      return v > (BigInt(2) ** BigInt(255));
    } catch(e) { return /^f{64}$/i.test(word); }
  }
  function hexToText(hex){
    try {
      var h = String(hex || '').replace(/^0x/i, '');
      if (h.length % 2) h += '0';
      var bytes = new Uint8Array(h.length / 2);
      for (var i = 0; i < bytes.length; i++) bytes[i] = parseInt(h.substr(i * 2, 2), 16);
      var text = new TextDecoder('utf-8').decode(bytes);
      return text.replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, '');
    } catch(e) { return ''; }
  }
  function wordAt(data, index){
    var body = data.slice(10);
    return body.slice(index * 64, (index + 1) * 64);
  }
  function addrWord(word){
    return word ? '0x' + word.slice(-40) : '';
  }
  async function lookupSelector(sel){
    try {
      var ctl = new AbortController();
      var timer = setTimeout(function(){ ctl.abort(); }, 2500);
      var res = await fetch('https://api.openchain.xyz/signature-database/v1/lookup?function=' + encodeURIComponent(sel) + '&filter=true', { signal: ctl.signal, cache: 'no-store' });
      clearTimeout(timer);
      if (!res.ok) return [];
      var json = await res.json();
      var list = json && json.result && json.result.function && json.result.function[sel];
      if (!Array.isArray(list)) return [];
      var names = [];
      for (var i = 0; i < list.length && names.length < 3; i++) {
        if (list[i] && list[i].name) names.push(String(list[i].name));
      }
      return names;
    } catch(e) { return []; }
  }
  async function describeTx(tx){
    var lines = ['⚠️ 这是链上交易，不是登录。'];
    var to = tx.to || '(创建合约)';
    lines.push('交互地址：' + to);
    if (tx.value && tx.value !== '0x0') lines.push('附带 value：' + hexToBig(tx.value).toString() + ' wei');
    var data = String(tx.data || '0x').toLowerCase();
    if (data.length < 10) {
      lines.push('data：空');
      lines.push('用途：普通转账，请核对收款地址和金额。');
      return lines.join('\n');
    }
    var sel = data.slice(0, 10);
    var name = '未知合约调用';
    if (sel === '0xa9059cbb') {
      name = 'transfer(address,uint256)';
      lines.push('代币接收地址：' + addrWord(wordAt(data, 0)));
      lines.push('转账数量（最小单位）：' + hexToBig('0x' + wordAt(data, 1)).toString());
    } else if (sel === '0x095ea7b3') {
      name = 'approve(address,uint256)';
      lines.push('被授权地址：' + addrWord(wordAt(data, 0)));
      lines.push(isUnlimited(wordAt(data, 1)) ? '⚠️ 无限额度授权：对方可转走该代币全部余额' : '授权额度（最小单位）：' + hexToBig('0x' + wordAt(data, 1)).toString());
    } else if (sel === '0x39509351') {
      name = 'increaseAllowance(address,uint256)';
      lines.push('被授权地址：' + addrWord(wordAt(data, 0)));
      lines.push(isUnlimited(wordAt(data, 1)) ? '⚠️ 无限额度授权' : '增加额度（最小单位）：' + hexToBig('0x' + wordAt(data, 1)).toString());
    } else if (sel === '0x23b872dd') {
      name = 'transferFrom(address,address,uint256)';
      lines.push('从：' + addrWord(wordAt(data, 0)));
      lines.push('到：' + addrWord(wordAt(data, 1)));
      lines.push('数量（最小单位）：' + hexToBig('0x' + wordAt(data, 2)).toString());
    } else if (sel === '0xa22cb465') {
      name = 'setApprovalForAll(address,bool)';
      lines.push('操作者：' + addrWord(wordAt(data, 0)));
      lines.push(wordAt(data, 1).replace(/0/g, '') ? '⚠️ 授权操作者管理你的全部 NFT' : '取消全部 NFT 授权');
    } else if (sel === '0x42842e0e') {
      name = 'safeTransferFrom(address,address,uint256)';
      lines.push('从：' + addrWord(wordAt(data, 0)));
      lines.push('到：' + addrWord(wordAt(data, 1)));
      lines.push('Token ID：' + hexToBig('0x' + wordAt(data, 2)).toString());
    }
    lines.push('函数：' + name + ' (' + sel + ')');
    if (sel === '0x095ea7b3' || sel === '0x39509351' || sel === '0xa22cb465') {
      lines.push('⚠️ 请先确认你是否真的需要这类授权。');
    } else if (name === '未知合约调用') {
      var candidates = await lookupSelector(sel);
      if (candidates.length) {
        lines.push('公开签名库候选：' + candidates.join(' / '));
        lines.push('⚠️ 名称来自公开签名库，不代表参数或合约安全，仍需自行核对。');
      } else {
        lines.push('⚠️ 无法解码该合约调用，请确认来源可靠。');
      }
    }
    lines.push('\n完整 data：\n' + data.slice(0, 1200) + (data.length > 1200 ? '…' : ''));
    return lines.join('\n');
  }
  function describeSign(method, params){
    var lines = ['⚠️ 这是签名请求，不是登录。'];
    var p = (params || []);
    if (method === 'personal_sign') {
      var msg = p[0];
      if (typeof msg === 'string' && /^0x/i.test(msg)) {
        var text = hexToText(msg);
        lines.push('消息文本：' + (text ? text.slice(0, 1200) : '(不可读的二进制数据)'));
        lines.push('消息 Hex：' + msg.slice(0, 300));
        if (!text) lines.push('⚠️ 不可读消息，可能包含授权内容。');
      } else {
        lines.push('消息：' + String(msg).slice(0, 1200));
      }
    } else {
      var typed = p[0];
      if (typeof typed === 'string') { try { typed = JSON.parse(typed); } catch(e) {} }
      if (typed && typeof typed === 'object') {
        lines.push('类型：' + (typed.primaryType || '未知'));
        try { lines.push('Domain：' + JSON.stringify(typed.domain || {}).slice(0, 800)); } catch(e) {}
        var pt = String(typed.primaryType || '');
        try { lines.push('Message：' + JSON.stringify(typed.message || {}).slice(0, 1600)); } catch(e) {}
        if (/permit/i.test(pt) || /permit/i.test(JSON.stringify(typed.domain || {}))) {
          lines.push('⚠️ 这是 Permit/授权类签名，可能允许对方动用你的代币。');
        }
      } else {
        lines.push('数据：' + JSON.stringify(p).slice(0, 1200));
      }
    }
    return lines.join('\n');
  }
  function approved(origin){
    try {
      var a = JSON.parse(localStorage.getItem(APPROVED) || '[]');
      var exp = JSON.parse(localStorage.getItem(APPROVED_EXP) || '{}');
      if (a.indexOf(origin) < 0) return false;
      if (!(exp[origin] > Date.now())) {
        a = a.filter(function(x){ return x !== origin; });
        delete exp[origin];
        localStorage.setItem(APPROVED, JSON.stringify(a));
        localStorage.setItem(APPROVED_EXP, JSON.stringify(exp));
        return false;
      }
      return !!chainFor(origin);
    } catch(e){ return false; }
  }
  function chainFor(origin){
    try { var m = JSON.parse(localStorage.getItem(ORIGIN_CHAIN) || '{}'); return m[origin] || ''; } catch(e){ return ''; }
  }
  function setChainFor(origin, value){
    try { var m = JSON.parse(localStorage.getItem(ORIGIN_CHAIN) || '{}'); m[origin] = value; localStorage.setItem(ORIGIN_CHAIN, JSON.stringify(m)); } catch(e){}
  }
  function isConnected(origin){
    return !!connectedOrigins[origin];
  }
  function markConnected(origin){
    connectedOrigins[origin] = true;
  }
  function clearConnected(){
    connectedOrigins = {};
    windowConnected = {};
  }
  function chooseChain(origin){
    return new Promise(function(resolve){
      var mask = document.createElement('div');
      mask.className = 'mask on';
      mask.innerHTML = '<div class="modal"><div class="hd"><span>选择网络</span><span class="wc-x" style="cursor:pointer;color:var(--dim)">×</span></div>' +
        '<div class="bd"><p id="chainOrigin" class="tip-addr" style="word-break:break-all"></p>' +
        '<p style="margin-top:10px">这个站点用哪条链？连接后站点会读到对应 chainId。</p>' +
        '<div class="btnrow"><button class="btn on" data-chain="196">X Layer</button><button class="btn" data-chain="56">BNB Chain</button><button class="btn" data-chain="1">Ethereum</button></div></div>' +
        '<div class="ft"><button class="btn" id="chainNo">取消</button></div></div>';
      document.body.appendChild(mask);
      mask.querySelector('#chainOrigin').textContent = origin;
      function close(v){ mask.remove(); resolve(v); }
      mask.querySelector('.wc-x').onclick = function(){ close(''); };
      mask.querySelector('#chainNo').onclick = function(){ close(''); };
      mask.querySelectorAll('[data-chain]').forEach(function(b){
        b.onclick = function(){ close('eip155:' + b.getAttribute('data-chain')); };
      });
    });
  }
  function approve(origin){
    try {
      var a = JSON.parse(localStorage.getItem(APPROVED) || '[]');
      if (a.indexOf(origin) < 0) a.push(origin);
      var exp = JSON.parse(localStorage.getItem(APPROVED_EXP) || '{}');
      exp[origin] = Date.now() + AUTH_TTL;
      localStorage.setItem(APPROVED, JSON.stringify(a));
      localStorage.setItem(APPROVED_EXP, JSON.stringify(exp));
    } catch(e) {}
  }
  function parseAccounts(session){
    try { return (session.namespaces.eip155.accounts || []).map(function(a){ return String(a).split(':').pop(); }); } catch(e){ return []; }
  }
  function notifyAccounts(list){
    try {
      if (window.dewebShell && window.dewebShell.walletAccountsChanged) window.dewebShell.walletAccountsChanged(Array.isArray(list) ? list : []);
    } catch(e) {}
  }
  function notifyChain(hex){
    try {
      if (window.dewebShell && window.dewebShell.walletChainChanged) window.dewebShell.walletChainChanged(String(hex || ''));
    } catch(e) {}
  }
  var keepConnectedOnDrop = false;
  function dropOkxSession(keepConnected){
    keepConnectedOnDrop = !!keepConnected;
    if (keepConnected) setTimeout(function(){ keepConnectedOnDrop = false; }, 120000);
    try {
      if (ui && ui.disconnect){
        var p = ui.disconnect();
        if (p && p.catch) p.catch(function(){});
      }
    } catch(e) {}
    ui = null; accounts = [];
    notifyAccounts([]);
  }
  function openQrModal(opts){
    dbg('openModal start');
    // Do not infer failure from the modal disappearing: on Android the OKX app
    // return can hide the modal after the session was already approved.
    if (!ui) throw new Error('wallet UI not initialized');
    var pm = ui.openModal(opts);
    dbg('openModal returned');
    return pm;
  }
  async function activateChain(targetChain){
    if (!ui || !targetChain) return true;
    var dec = parseInt(String(targetChain).split(':')[1], 10);
    if (!dec) return true;
    var wantHex = '0x' + dec.toString(16);
    try {
      var got = await ui.request({ method: 'eth_chainId', params: [] }, targetChain);
      if (got && parseInt(got, 16) === dec){
        chain = targetChain; chainHex = wantHex; return true;
      }
    } catch (e) {}
    await ui.request({
      method: 'wallet_switchEthereumChain',
      params: [{ chainId: wantHex }]
    }, targetChain);
    chain = targetChain; chainHex = wantHex;
    return true;
  }
  function dbg(m){ try{ window.__dewebCloudPhase = String(m); }catch(e){} try{ console.log('DEWEBDBG ' + m); }catch(e){} try{ if(window.dewebShell && window.dewebShell.debug) window.dewebShell.debug(m); }catch(e){} }
  async function ensureUi(){
    dbg('ensureUi start');
    await loadOkx();
    dbg('okx bundle loaded');
    if (!window.OKXTonConnectUISdk || !window.OKXTonConnectUISdk.OKXUniversalConnectUI) throw new Error('OKX Connect unavailable');
    if (ui && typeof ui.connected === 'function'){
      try { if (ui.connected() === false) dropOkxSession(); } catch(e) {}
    }
    if (!ui){
      dbg('okx init');
      ui = await window.OKXTonConnectUISdk.OKXUniversalConnectUI.init({
        dappMetaData: {
          icon: 'https://1-2-399.tapekit.org/favicon.ico',
          name: 'DeWEB Desktop'
        },
        actionsConfiguration: {
          modals: 'all',
          returnStrategy: 'back',
          tmaReturnUrl: 'back'
        },
        language: 'zh_CN',
        uiPreferences: { theme: 'DARK' },
        restoreConnection: !!window.__dewebAndroidWallet && !window.__dewebStrictConnect,
        openAppLinkType: window.__dewebAndroidWallet ? 'deeplink' : 'universallink'
      });
      dbg('okx ui ready');
      try {
        ui.on('session_delete', function(){
          if (keepConnectedOnDrop){
            keepConnectedOnDrop = false;
            return;
          }
          generation++;
          ui = null; accounts = []; siteSession = {}; windowConnected = {}; clearConnected();
          notifyAccounts([]);
          refreshPanel();
        });
        ui.on('accountChanged', function(session){
          try {
            var next = session ? parseAccounts(session) : [];
            if (next.length){ accounts = next; notifyAccounts(accounts); refreshPanel(); }
            else if (!session){ accounts = []; notifyAccounts([]); refreshPanel(); }
          } catch(e) {}
        });
        ui.on('session_update', function(session){
          try {
            var next = session ? parseAccounts(session) : [];
            if (next.length && next.join(',') !== accounts.join(',')){ accounts = next; notifyAccounts(accounts); refreshPanel(); }
          } catch(e) {}
        });
      } catch(e) {}
    }
    /* Keep the same OKX session across BSC / X Layer sites.
       Do not validate eth_accounts on the requested chain here: switching
       chains used to time out, drop the session and force a second QR scan. */
    if (!accounts.length){
      var defaultChain = (chain.split(':')[1] || '196');
      var targetChain = chain || ('eip155:' + defaultChain);
      /* OKX sessions are namespace-scoped.  A BSC-only session rejects X Layer
         (and vice versa) with "Namespace not include the chain eip155:56".
         Authorize both DeWEB chains up front, but use the site's chain as the
         default so the first connection opens on the correct network. */
      var bothChains = ['eip155:56', 'eip155:196'];
      if (bothChains.indexOf(targetChain) < 0) bothChains.push(targetChain);
      var session;
      try {
        session = await openQrModal({
          namespaces: { eip155: { chains: bothChains, defaultChain: defaultChain } },
          optionalNamespaces: { eip155: { chains: ['eip155:1'] } }
        });
      } catch (e) {
        // A late modal error can arrive after session_update already populated
        // accounts. In that case the connection actually succeeded.
        if (!accounts.length){
          ui = null;
          throw e;
        }
        notifyAccounts(accounts);
        return ui;
      }
      accounts = parseAccounts(session);
      if (!accounts.length){
        try { var got = await ui.request({ method:'eth_accounts', params: [] }, chain); if (Array.isArray(got)) accounts = got; } catch(e){}
      }
    }
    if (!accounts.length) throw new Error('no wallet account');
    notifyAccounts(accounts);
    return ui;
  }
  function refreshPanel(){
    if (!panelEl) return;
    var st = panelEl.querySelector('#okxStatus');
    var btn = panelEl.querySelector('#okxConnect');
    var dis = panelEl.querySelector('#okxDisconnect');
    if (st) st.textContent = accounts.length ? (accounts[0] + ' · ' + chainHex) : '未连接';
    if (btn) btn.disabled = !!accounts.length;
    if (dis) dis.disabled = !accounts.length;
    var hint = panelEl.querySelector('#okxHint');
    if (hint) hint.textContent = accounts.length ? ('已复用当前 OKX 会话（' + accounts[0].slice(0,6) + '…），无需重复扫码。需要换账号请先点“断开”，再点“扫码连接”。') : '只连接 OKX App；站点仍需按 origin 授权，交易逐笔确认。';
    try { var box = panelEl.querySelector('#memberBox'); if (box && box.style.display === 'block') renderMembership(); } catch(e) {}
  }
  function openPanel(){
    if (panelEl){ panelEl.classList.add('on'); refreshPanel(); return; }
    panelEl = document.createElement('div');
    panelEl.className = 'mask on';
    panelEl.innerHTML = '<div class="modal"><div class="hd"><span>连接钱包</span><span id="okxClose" style="cursor:pointer;color:var(--dim)">×</span></div>' +
      '<div class="bd"><p>用 OKX App 扫二维码连接。不需要 Project ID。</p>' +
      '<div class="tip-addr" id="okxStatus">未连接</div>' +
      '<div class="btnrow"><button class="btn on" id="okxConnect">扫码连接</button><button class="btn" id="okxRescan">重新扫码</button><button class="btn" id="okxDisconnect">断开</button></div>' +
      '<div id="memberBox" style="display:none;margin-top:12px;border-top:1px solid rgba(255,255,255,.1);padding-top:12px">' +
      '<div class="tip-addr" id="memberInfo">Alpha 测试期 · Pro 免费开放至 2026-12-31</div>' +
      '<div class="tip-note" id="memberStatus">Alpha 测试期 Pro 自动生效，有效期至 2026-12-31。</div></div>' +
      '<div class="tip-note" id="okxHint">只连接 OKX App；站点仍需按 origin 授权，交易逐笔确认。</div></div>' +
      '<div class="ft"><button class="btn" id="okxPanelClose">关闭</button></div></div>';
    document.body.appendChild(panelEl);
    function close(){ panelEl.classList.remove('on'); }
    panelEl.querySelector('#okxClose').onclick = close;
    panelEl.querySelector('#okxPanelClose').onclick = close;
    panelEl.addEventListener('click', function(e){ if (e.target === panelEl) close(); });
    panelEl.querySelector('#okxConnect').onclick = async function(){
      try { await ensureUi(); refreshPanel(); } catch(e){ var st=panelEl.querySelector('#okxStatus'); if(st) st.textContent='连接失败：' + String(e.message||e); }
    };
    panelEl.querySelector('#okxRescan').onclick = async function(){
      try { dropOkxSession(); await ensureUi(); refreshPanel(); } catch(e) { var st=panelEl.querySelector('#okxStatus'); if(st) st.textContent='扫码失败：' + String(e.message||e); }
    };
    panelEl.querySelector('#okxDisconnect').onclick = async function(){
      generation++;
      try { if (ui && ui.disconnect) await ui.disconnect(); } catch(e) {}
      dropOkxSession(); clearConnected(); windowConnected = {}; siteSession = {}; chain = 'eip155:196'; chainHex = '0xc4'; refreshPanel();
    };
    refreshPanel();
  }

  function pad32(hex){
    var h = String(hex).replace(/^0x/, '');
    while (h.length < 64) h = '0' + h;
    return h;
  }
  function renderMembership(){
    var box = panelEl && panelEl.querySelector('#memberBox');
    if (!box) return;
    var info = panelEl.querySelector('#memberInfo');
    var status = panelEl.querySelector('#memberStatus');
    if (alphaFree()) {
      if (info) info.textContent = 'Alpha 测试期 · Pro 免费开放至 2026-12-31';
      if (status) status.textContent = 'Alpha 测试期 Pro 自动生效。';
    } else {
      if (info) info.textContent = 'Alpha 免费期已结束';
      if (status) status.textContent = 'Pro 后续提供方式以正式公告为准。';
    }
  }
  function openMembership(){
    openPanel();
    var box = panelEl && panelEl.querySelector('#memberBox');
    if (box) box.style.display = 'block';
    renderMembership();
    try { panelEl.scrollIntoView({ block: 'center' }); } catch(e) {}
  }
  window.__dewebOpenMembership = openMembership;

  async function handle(req){
    walletBusy = true;
    function ok(result){ window.dewebShell.sendWalletResponse({ id: req.id, result: result }); }
    function fail(e){ window.dewebShell.sendWalletResponse({ id: req.id, error: { code: (e && e.code) || -32603, message: String((e && e.message) || e) } }); }
    try {
      var origin = req.origin || 'unknown';
      var method = req.method;
      var params = req.params || [];
      var hint = String(req.chainHint || '');
      var gen = generation;
      var strict = !!req.strict;
      var windowId = String(req.windowId || origin);
      var alreadyConnected = isConnected(origin);
      var forceConnect = !!req.forceConnect;
      window.__dewebStrictConnect = strict && !accounts.length && !ui;
      if (hint) setChainFor(origin, 'eip155:' + hint);
      var oc = chainFor(origin);
      if (oc){ chain = oc; chainHex = '0x' + parseInt(oc.split(':')[1], 10).toString(16); }
      if (method === 'eth_chainId') return ok(chainHex);
      if (method === 'net_version') return ok(String(parseInt(chainHex, 16)));
      if (method === 'eth_accounts') {
        // Strict mode: an origin that has not completed an OKX connection in
        // this wallet session must not receive accounts, even if it was
        // approved in a previous session.
        if (strict && (!alreadyConnected || (forceConnect && !windowConnected[windowId]))) return ok([]);
        return ok(((hint || siteSession[origin]) && approved(origin)) ? accounts : []);
      }
      if (method === 'eth_requestAccounts' || method === 'wallet_requestPermissions'){
        if (accounts.length && ui && typeof ui.connected === 'function'){
        try { if (ui.connected() === false) dropOkxSession(); } catch(e) {}
      }
      if ((method === 'eth_requestAccounts' || method === 'wallet_requestPermissions') && accounts.length && !siteSession[origin]){
        try { openPanel(); } catch(e) {}
      }
      if (method === 'wallet_requestPermissions' && !hint){
          /* AppKit always starts a fresh connect with wallet_requestPermissions.
             Never reuse a stale OKX UI session: that produced accounts but
             wallet_switchEthereumChain then failed with "Connect wallet". */
          try { if (window.dewebShell.focusWallet) window.dewebShell.focusWallet(); } catch(e){}
          dropOkxSession();
          delete siteSession[origin];
        }
        /* 普通 web 的 OKX/AppKit 连接器只有在“没有现成会话”时才走扫码。
           复用主站已连接会话会让它停在扩展连接器上，所以 web 首次连接强制新扫码。 */
        if (!hint && accounts.length && !siteSession[origin]){
          try { if (window.dewebShell.focusWallet) window.dewebShell.focusWallet(); } catch(e) {}
          dropOkxSession();
          delete siteSession[origin];
        }
        var needApprove = !req.siteApproved && !approved(origin);
        if (needApprove){
          var allowSite = await confirmBox(
            '站点授权',
            '站点：' + origin + '\n\n允许该站点读取你的钱包地址？\n\n交易和签名仍会逐笔确认。'
          );
          if (!allowSite) throw Object.assign(new Error('User rejected'), { code: 4001 });
          if (gen !== generation) throw Object.assign(new Error('Wallet disconnected'), { code: 4001 });
        }
        // Reuse the current OKX session. Per-site authorization is still required.
        if (needApprove){
          if (!hint){
            try { if (window.dewebShell.focusWallet) window.dewebShell.focusWallet(); } catch(e){}
            await new Promise(function(r){ setTimeout(r, 120); });
            var picked = await chooseChain(origin);
            if (!picked) throw Object.assign(new Error('User rejected'), { code: 4001 });
            setChainFor(origin, picked);
          }
        }
        oc = chainFor(origin);
        if (oc){ chain = oc; chainHex = '0x' + parseInt(oc.split(':')[1], 10).toString(16); }
        try {
          await ensureUi();
          if (gen !== generation) throw Object.assign(new Error('Wallet disconnected'), { code: 4001 });
        } catch (e) {
          dropOkxSession();
          delete siteSession[origin];
          throw e;
        }
        if (gen !== generation) throw Object.assign(new Error('Wallet disconnected'), { code: 4001 });
        if (needApprove || req.siteApproved) approve(origin);
        siteSession[origin] = true;
        markConnected(origin);
        windowConnected[windowId] = true;
        if (method === 'wallet_requestPermissions') return ok({ eth_accounts: { caveats: [] } });
        return ok(accounts);
      }
      if (method === 'wallet_switchEthereumChain'){
        if (!approved(origin)) throw Object.assign(new Error('Site not authorized'), { code: 4100 });
        await ensureUi();
        var target = params && params[0] && params[0].chainId;
        if (!target) throw new Error('missing chainId');
        var dec = parseInt(target, 16);
        var targetChain = 'eip155:' + dec;
        var r = await ui.request({ method: method, params: params }, targetChain);
        chain = targetChain; chainHex = '0x' + dec.toString(16); setChainFor(origin, targetChain);
        return ok(r);
      }
      if (method === 'eth_sendTransaction'){
        if (!approved(origin)) throw Object.assign(new Error('Site not authorized'), { code: 4100 });
        await ensureUi();
        var tx = (params && params[0]) || {};
        if (!req.nativeConfirmed) {
          var yes = await confirmBox('发送交易', '站点：' + origin + '\n\n' + await describeTx(tx), { cancelText: '取消', confirmText: '发送交易' });
          if (!yes) throw Object.assign(new Error('User rejected'), { code: 4001 });
        }
        return ok(await ui.request({ method: method, params: params }, chain));
      }
      if (method === 'personal_sign' || method === 'eth_signTypedData_v4'){
        if (!approved(origin)) throw Object.assign(new Error('Site not authorized'), { code: 4100 });
        await ensureUi();
        if (!req.nativeConfirmed) {
          var okSign = await confirmBox('签名请求', '站点：' + origin + '\n\n' + describeSign(method, params), { cancelText: '取消', confirmText: '签名' });
          if (!okSign) throw Object.assign(new Error('User rejected'), { code: 4001 });
        }
        return ok(await ui.request({ method: method, params: params }, chain));
      }
      var reads = ['eth_call','eth_estimateGas','eth_getBalance','eth_blockNumber','eth_getTransactionReceipt','eth_getCode','eth_getTransactionCount','eth_gasPrice','eth_feeHistory','eth_getLogs','eth_getBlockByNumber','eth_getTransactionByHash','eth_getBlockByHash','eth_getStorageAt','eth_maxPriorityFeePerGas'];
      if (reads.indexOf(method) >= 0){
        return ok(await publicRpc(method, params));
      }
      throw new Error('unsupported wallet method: ' + method);
    } catch(e) { fail(e); }
    finally { walletBusy = false; }
  }

  window.__dewebWalletDisconnectAll = async function () {
    generation++;
    try { if (ui && ui.disconnect) await ui.disconnect(); } catch (e) {}
    try { dropOkxSession(); } catch (e) {}
    accounts = [];
    siteSession = {};
    clearConnected();
    ui = null;
    chain = 'eip155:196';
    chainHex = '0xc4';
    try { localStorage.removeItem(APPROVED); localStorage.removeItem(APPROVED_EXP); localStorage.removeItem(ORIGIN_CHAIN); } catch (e) {}
    notifyAccounts([]);
    refreshPanel();
    return true;
  };

  window.dewebShell.onWalletRequest(function(req){
    queue = queue.then(function(){ return handle(req); });
  });
  setTimeout(function(){
    var b = document.getElementById('wcBtn');
    if (b){ b.style.display = 'inline-flex'; b.textContent = '连接钱包'; b.title = '连接钱包 / OKX Connect'; b.onclick = openPanel; }
  }, 0);
})();
