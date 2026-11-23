/* Keeps the wallpaper file input usable in older cached web builds. */
(function () {
  'use strict';
  function mobileShell() {
    try {
      if (window.top !== window) return;
      if (!window.matchMedia || !window.matchMedia('(max-width: 720px)').matches) return;
      localStorage.setItem('deweb.desktop.mode.v1', 'mobile');
      var style = document.getElementById('deweb-mobile-shell-fix');
      if (!style) {
        style = document.createElement('style');
        style.id = 'deweb-mobile-shell-fix';
        style.textContent = '#modeBtn,#mDesk,#mobileReturn{display:none!important}';
        (document.head || document.documentElement).appendChild(style);
      }
    } catch (e) {}
  }
  function apply() {
    try {
          var id = 'deweb-android-page-fix';
      if (!document.getElementById(id)) {
        var style = document.createElement('style');
        style.id = id;
        style.textContent = '.wallupload{position:relative!important;overflow:hidden!important}' +
          '.wallupload input{display:block!important;position:absolute!important;left:0!important;top:0!important;' +
          'width:100%!important;height:100%!important;opacity:0!important;cursor:pointer!important;font-size:0!important;z-index:2!important}';
        (document.head || document.documentElement).appendChild(style);
      }
      var label = document.querySelector('.wallupload');
      var input = document.getElementById('wallFile');
      if (label && input && !label.__dewebFileFix) {
        label.__dewebFileFix = true;
        label.addEventListener('click', function (e) {
          if (e.target !== input) {
            e.preventDefault();
            input.click();
          }
        }, true);
      }
    } catch (e) {}
  }
  mobileShell();
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', apply);
  else apply();
  setTimeout(apply, 600);
})();
