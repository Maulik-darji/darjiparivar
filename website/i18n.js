// English / Gujarati switch. Text lives in data-en / data-gu attributes on each element.
(function () {
  var KEY = "dp-lang";
  function saved() {
    try { return localStorage.getItem(KEY); } catch (e) { return null; }
  }
  function apply(lang) {
    document.documentElement.lang = lang;
    document.querySelectorAll("[data-en]").forEach(function (el) {
      var text = el.getAttribute("data-" + lang) || el.getAttribute("data-en");
      if (el.hasAttribute("data-html")) el.innerHTML = text; else el.textContent = text;
    });
    document.querySelectorAll("[data-en-alt]").forEach(function (el) {
      el.alt = el.getAttribute("data-" + lang + "-alt") || el.getAttribute("data-en-alt");
    });
    document.querySelectorAll("img[data-src-gu]").forEach(function (img) {
      if (!img.dataset.srcEn) img.dataset.srcEn = img.getAttribute("src");
      img.src = lang === "gu" ? img.dataset.srcGu : img.dataset.srcEn;
    });
    document.querySelectorAll(".lang button").forEach(function (b) {
      b.setAttribute("aria-pressed", String(b.dataset.lang === lang));
    });
    try { localStorage.setItem(KEY, lang); } catch (e) { /* private mode */ }
  }
  var initial = saved() || ((navigator.language || "").toLowerCase().indexOf("gu") === 0 ? "gu" : "en");
  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll(".lang button").forEach(function (b) {
      b.addEventListener("click", function () { apply(b.dataset.lang); });
    });
    apply(initial);
  });
})();
