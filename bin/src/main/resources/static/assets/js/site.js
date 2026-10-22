/*
 * Site behaviour: disclosure menus (navigation, language, accessibility panel), mobile menu and the
 * DBIM accessibility controls. Progressive enhancement only — every page is usable without JavaScript.
 */
(function () {
    "use strict";

    var STORAGE_KEY = "kvk-a11y";
    var TEXT_MIN = -2;
    var TEXT_MAX = 4;
    var TOGGLES = ["dark", "invert", "saturation", "links", "hide-images", "cursor"];
    var root = document.documentElement;

    /* ----------------------------------------------------------- disclosures */

    var openButton = null;

    function targetOf(button) {
        return document.getElementById(button.getAttribute("aria-controls"));
    }

    function setOpen(button, open) {
        var target = targetOf(button);
        if (!target) {
            return;
        }
        button.setAttribute("aria-expanded", String(open));
        target.hidden = !open;
        openButton = open ? button : (openButton === button ? null : openButton);
    }

    function closeOpen(returnFocus) {
        if (openButton) {
            var button = openButton;
            setOpen(button, false);
            if (returnFocus) {
                button.focus();
            }
        }
    }

    document.querySelectorAll("[data-disclosure]").forEach(function (button) {
        button.addEventListener("click", function () {
            var opening = button.getAttribute("aria-expanded") !== "true";
            if (openButton && openButton !== button) {
                setOpen(openButton, false);
            }
            setOpen(button, opening);
            if (opening && button.hasAttribute("data-focus-target")) {
                var first = targetOf(button).querySelector("button, a, input");
                if (first) {
                    first.focus();
                }
            }
        });
    });

    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape") {
            closeOpen(true);
        }
    });

    document.addEventListener("click", function (event) {
        if (!openButton) {
            return;
        }
        var target = targetOf(openButton);
        if (!openButton.contains(event.target) && !(target && target.contains(event.target))) {
            setOpen(openButton, false);
        }
    });

    document.querySelectorAll("[data-close]").forEach(function (button) {
        button.addEventListener("click", function () {
            closeOpen(true);
        });
    });

    /* ------------------------------------------------------------ mobile menu */

    var navToggle = document.querySelector(".nav-toggle");
    var menu = document.getElementById("global-menu");
    if (navToggle && menu) {
        menu.setAttribute("data-collapsed", "true");
        navToggle.addEventListener("click", function () {
            var expanded = navToggle.getAttribute("aria-expanded") === "true";
            navToggle.setAttribute("aria-expanded", String(!expanded));
            menu.setAttribute("data-collapsed", String(expanded));
        });
    }

    /* -------------------------------------------------------- form errors */

    /* Move focus to the error summary after a failed submission, so screen readers announce it first. */
    var errorSummary = document.querySelector("[data-error-summary]");
    if (errorSummary) {
        errorSummary.focus();
    }

    /* ------------------------------------------------- accessibility controls */

    function loadPrefs() {
        try {
            return JSON.parse(window.localStorage.getItem(STORAGE_KEY)) || {};
        } catch (e) {
            return {};
        }
    }

    function savePrefs(prefs) {
        try {
            window.localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
        } catch (e) {
            /* storage unavailable (private mode): settings still apply for this page view */
        }
    }

    var prefs = loadPrefs();
    var status = document.getElementById("a11y-status");

    function textStep() {
        var step = parseInt(prefs.text, 10);
        return isNaN(step) ? 0 : Math.max(TEXT_MIN, Math.min(TEXT_MAX, step));
    }

    function announce(message) {
        if (status) {
            status.textContent = message;
        }
    }

    function render() {
        TOGGLES.forEach(function (name) {
            var on = prefs[name] === true;
            root.classList.toggle("a11y-" + name, on);
            document.querySelectorAll('[data-a11y-toggle="' + name + '"]').forEach(function (button) {
                button.setAttribute("aria-pressed", String(on));
            });
        });
        for (var i = TEXT_MIN; i <= TEXT_MAX; i++) {
            root.classList.remove("a11y-text-" + i);
        }
        var step = textStep();
        if (step !== 0) {
            root.classList.add("a11y-text-" + step);
        }
        document.querySelectorAll('[data-a11y-text="up"]').forEach(function (b) {
            b.setAttribute("aria-disabled", String(step >= TEXT_MAX));
        });
        document.querySelectorAll('[data-a11y-text="down"]').forEach(function (b) {
            b.setAttribute("aria-disabled", String(step <= TEXT_MIN));
        });
    }

    document.querySelectorAll("[data-a11y-toggle]").forEach(function (button) {
        button.addEventListener("click", function () {
            var name = button.getAttribute("data-a11y-toggle");
            prefs[name] = prefs[name] !== true;
            savePrefs(prefs);
            render();
            announce(button.textContent.trim() + (prefs[name] ? " on" : " off"));
        });
    });

    document.querySelectorAll("[data-a11y-text]").forEach(function (button) {
        button.addEventListener("click", function () {
            var delta = button.getAttribute("data-a11y-text") === "up" ? 1 : -1;
            prefs.text = Math.max(TEXT_MIN, Math.min(TEXT_MAX, textStep() + delta));
            savePrefs(prefs);
            render();
            /* matches the .a11y-text-N scales in dbim.css: +12.5% per step up, -6.25% per step down */
            announce("Text size " + (100 + prefs.text * (prefs.text < 0 ? 6.25 : 12.5)) + "%");
        });
    });

    document.querySelectorAll("[data-a11y-reset]").forEach(function (button) {
        button.addEventListener("click", function () {
            prefs = {};
            savePrefs(prefs);
            render();
            announce("Accessibility settings reset");
        });
    });

    render();
    /* ----------------------------------------------------------- cookie consent */
    /*
     * DBIM 7.6.1: the banner is shown until the visitor accepts, rejects or customises. The choice is kept only in
     * this browser (localStorage "kvk-consent"). Any future optional script (for example analytics) must run only when
     * document.documentElement has the class "consent-analytics".
     */
    var CONSENT_KEY = "kvk-consent";
    var banner = document.getElementById("cookie-banner");

    function loadConsent() {
        try {
            return JSON.parse(window.localStorage.getItem(CONSENT_KEY));
        } catch (e) {
            return null;
        }
    }

    function applyConsent(consent) {
        root.classList.toggle("consent-analytics", !!(consent && consent.analytics === true));
    }

    if (banner) {
        var settings = document.getElementById("cookie-settings");
        var customize = banner.querySelector("[data-cookie-customize]");
        var saveButton = banner.querySelector("[data-cookie-save]");
        var analyticsBox = document.getElementById("cookie-analytics");

        var showSettings = function (show) {
            settings.hidden = !show;
            saveButton.hidden = !show;
            customize.setAttribute("aria-expanded", String(show));
        };

        var record = function (analytics) {
            var consent = { analytics: analytics, version: 1, date: new Date().toISOString().slice(0, 10) };
            try {
                window.localStorage.setItem(CONSENT_KEY, JSON.stringify(consent));
            } catch (e) {
                /* storage unavailable: the choice applies to this page view only */
            }
            applyConsent(consent);
            banner.hidden = true;
            showSettings(false);
        };

        var existing = loadConsent();
        applyConsent(existing);
        if (!existing) {
            banner.hidden = false;
        }

        banner.querySelector("[data-cookie-accept]").addEventListener("click", function () {
            record(true);
        });
        banner.querySelector("[data-cookie-reject]").addEventListener("click", function () {
            record(false);
        });
        saveButton.addEventListener("click", function () {
            record(analyticsBox.checked);
        });
        customize.addEventListener("click", function () {
            showSettings(settings.hidden);
        });

        document.querySelectorAll("[data-cookie-open]").forEach(function (button) {
            button.addEventListener("click", function () {
                var current = loadConsent();
                analyticsBox.checked = !!(current && current.analytics === true);
                banner.hidden = false;
                showSettings(true);
                analyticsBox.focus();
            });
        });
    }
})();
