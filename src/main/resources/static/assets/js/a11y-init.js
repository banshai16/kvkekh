/*
 * Applies saved accessibility preferences before first paint. Loaded synchronously in <head>;
 * kept tiny and dependency-free. Preferences are stored only in this browser (localStorage).
 */
(function () {
    "use strict";
    var prefs;
    try {
        prefs = JSON.parse(window.localStorage.getItem("kvk-a11y")) || {};
    } catch (e) {
        prefs = {};
    }
    var root = document.documentElement;
    ["dark", "invert", "saturation", "links", "hide-images", "cursor"].forEach(function (name) {
        if (prefs[name] === true) {
            root.classList.add("a11y-" + name);
        }
    });
    var step = parseInt(prefs.text, 10);
    if (step >= -2 && step <= 4 && step !== 0) {
        root.classList.add("a11y-text-" + step);
    }
})();
