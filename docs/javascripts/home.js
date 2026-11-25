/* Home page: the switch of the theme and the facts of the repository (stars and latest version).
   The choice of the theme is kept where the documentation keeps its own, in the same format, so it holds on every page.
   The stars come from the GitHub API, asked by the browser of the reader once for each visit and kept for the session
   (in the same place as the documentation's header, which shows the same numbers). */
(function () {
  "use strict";
  var root = document.documentElement;
  var scope = new URL(root.getAttribute("data-scope") || ".", location).pathname;

  function get(key, storage) {
    try { return JSON.parse((storage || localStorage).getItem(scope + ".__" + key)); } catch (e) { return null; }
  }
  function set(key, value, storage) {
    try { (storage || localStorage).setItem(scope + ".__" + key, JSON.stringify(value)); } catch (e) { /* storage blocked: the choice lasts for the visit */ }
  }

  /* ---------- Theme ---------- */
  var palette = [];
  try { palette = JSON.parse(root.getAttribute("data-palette") || "[]"); } catch (e) { palette = []; }
  var button = document.querySelector(".theme");
  var light = matchMedia("(prefers-color-scheme: light)");

  function isAuto(entry) { return entry.media === "(prefers-color-scheme)"; }
  function scheme(entry) {            // "default" (light) or "slate" (dark)
    if (isAuto(entry)) return light.matches ? "default" : "slate";
    return entry.scheme === "slate" ? "slate" : "default";
  }
  function show(index) {
    var entry = palette[index];
    root.setAttribute("data-scheme", scheme(entry) === "slate" ? "dark" : "light");
    var name = (entry.toggle && entry.toggle.name) || "Switch the theme";
    button.setAttribute("data-mode", index);
    button.setAttribute("aria-label", name);
    button.setAttribute("title", name);
  }
  function save(index) {
    var entry = palette[index];
    var color = { media: entry.media, primary: entry.primary || "custom", accent: entry.accent || "custom" };
    color.scheme = scheme(entry);
    set("palette", { index: index, color: color });
  }

  if (button && palette.length > 1) {
    var stored = get("palette");
    var index = stored && typeof stored.index === "number" && palette[stored.index] ? stored.index : 0;
    show(index);
    button.hidden = false;
    button.addEventListener("click", function () {
      index = (index + 1) % palette.length;
      show(index);
      save(index);
    });
    var follow = function () { if (isAuto(palette[index])) show(index); };
    if (light.addEventListener) light.addEventListener("change", follow); else if (light.addListener) light.addListener(follow);
  }

  /* ---------- Stars and version ---------- */
  var repo = root.getAttribute("data-repo");
  function count(n) { return n >= 10000 ? Math.round(n / 1000) + "k" : n >= 1000 ? (n / 1000).toFixed(1).replace(".0", "") + "k" : String(n); }
  function render(facts) {
    document.querySelectorAll("[data-stars]").forEach(function (el) { el.textContent = count(facts.stars); });
    document.querySelectorAll(".stars").forEach(function (el) { el.hidden = false; });
    if (facts.version) document.querySelectorAll("[data-version]").forEach(function (el) { el.textContent = facts.version; });
  }
  function json(response) { if (!response.ok) throw new Error(response.status); return response.json(); }

  if (repo && window.fetch) {
    var cached = get("source", sessionStorage);
    if (cached && typeof cached.stars === "number") {
      render(cached);
    } else {
      var api = "https://api.github.com/repos/" + repo;
      Promise.all([
        fetch(api).then(json),
        fetch(api + "/releases/latest").then(json).catch(function () { return null; })
      ]).then(function (r) {
        var facts = { version: r[1] && r[1].tag_name, stars: r[0].stargazers_count, forks: r[0].forks_count };
        set("source", facts, sessionStorage);
        render(facts);
      }).catch(function () { /* offline or over the rate limit: the page is just as good without the numbers */ });
    }
  }
})();
