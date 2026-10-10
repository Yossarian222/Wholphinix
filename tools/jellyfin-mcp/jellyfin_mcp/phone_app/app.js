// Telka: chat with Claude about the TV + a small remote. Talks only to /<secret>/app/* on this server.
(function () {
  "use strict";

  var BASE = window.APP_BASE;
  var $ = function (id) { return document.getElementById(id); };
  var log = $("log"), input = $("input"), sendBtn = $("sendBtn"), micBtn = $("micBtn");
  var busy = false;

  // ---- per-device storage (best effort: private mode may refuse it) ----
  var store = {
    get: function (k, d) { try { var v = localStorage.getItem("telka." + k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
    set: function (k, v) { try { localStorage.setItem("telka." + k, JSON.stringify(v)); } catch (e) { /* ignore */ } },
  };
  function newId() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID().replace(/-/g, "");
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 12);
  }
  var conversation = store.get("conversation", null) || newId();
  store.set("conversation", conversation);
  var history = store.get("history", []);

  // ---- server ----
  function api(path, body) {
    var opts = body === undefined ? {} : {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    };
    return fetch(BASE + path, opts).then(function (r) {
      if (!r.ok) throw new Error("HTTP " + r.status);
      return r.json();
    });
  }

  // ---- chat log ----
  function bubble(kind, text) {
    var empty = $("empty");
    if (empty) empty.remove();
    var el = document.createElement("div");
    el.className = "msg " + kind;
    el.textContent = text;
    log.appendChild(el);
    log.scrollTop = log.scrollHeight;
    return el;
  }
  function remember(kind, text) {
    history.push([kind, text]);
    history = history.slice(-60);
    store.set("history", history);
  }
  history.forEach(function (m) { bubble(m[0], m[1]); });

  function typing() {
    var el = bubble("bot typing", "");
    el.innerHTML = "<span></span><span></span><span></span>";
    return el;
  }

  function send(text) {
    text = (text || "").trim();
    if (!text || busy) return;
    busy = true;
    sendBtn.disabled = true;
    input.value = "";
    autosize();
    bubble("me", text);
    remember("me", text);
    var dots = typing();
    api("chat", { text: text, conversation: conversation })
      .then(function (data) {
        dots.remove();
        var reply = data.reply || "👍";
        bubble("bot", reply);
        remember("bot", reply);
        speak(reply);
        refreshSoon();
      })
      .catch(function () {
        dots.remove();
        bubble("bot err", "Neviem sa spojiť so serverom 📡 Skús to znova.");
      })
      .finally(function () {
        busy = false;
        sendBtn.disabled = false;
      });
  }

  $("composer").addEventListener("submit", function (e) {
    e.preventDefault();
    send(input.value);
  });
  input.addEventListener("keydown", function (e) {
    // Enter sends, Shift+Enter is a new line (phone keyboards show "send" via enterkeyhint)
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      send(input.value);
    }
  });
  function autosize() {
    input.style.height = "auto";
    input.style.height = Math.min(input.scrollHeight + 2, 120) + "px";
  }
  input.addEventListener("input", autosize);

  $("chips").addEventListener("click", function (e) {
    if (e.target.tagName === "BUTTON") send(e.target.textContent);
  });

  // ---- menu ----
  var menu = $("menu");
  $("menuBtn").addEventListener("click", function (e) {
    e.stopPropagation();
    menu.hidden = !menu.hidden;
  });
  document.addEventListener("click", function () { menu.hidden = true; });
  $("newChat").addEventListener("click", function () {
    api("reset", { conversation: conversation }).catch(function () {});
    conversation = newId();
    store.set("conversation", conversation);
    history = [];
    store.set("history", history);
    log.innerHTML = "";
    bubble("note", "Nový rozhovor 🧹");
  });
  $("refresh").addEventListener("click", function () { refresh(); });

  // ---- toast ----
  var toastTimer;
  function toast(text) {
    var t = $("toast");
    t.textContent = text;
    t.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.classList.remove("show"); }, 2500);
  }

  // ---- read replies aloud ----
  var speakOn = store.get("speak", false);
  var speakBtn = $("speakBtn");
  function speakUi() { speakBtn.classList.toggle("active", speakOn); }
  if (!("speechSynthesis" in window)) speakBtn.hidden = true;
  speakUi();
  speakBtn.addEventListener("click", function () {
    speakOn = !speakOn;
    store.set("speak", speakOn);
    speakUi();
    if (!speakOn) speechSynthesis.cancel();
    toast(speakOn ? "Odpovede budem čítať nahlas 🔊" : "Čítanie nahlas vypnuté");
  });
  function slovakVoice() {
    var voices = speechSynthesis.getVoices();
    return voices.find(function (v) { return /^sk/i.test(v.lang); }) ||
      voices.find(function (v) { return /^cs/i.test(v.lang); }) || null;
  }
  function speak(text) {
    if (!speakOn || !("speechSynthesis" in window)) return;
    var clean = text.replace(/\p{Extended_Pictographic}|️/gu, "").replace(/^\s*[-•]\s*/gm, "");
    var u = new SpeechSynthesisUtterance(clean);
    u.lang = "sk-SK";
    var v = slovakVoice();
    if (v) u.voice = v;
    speechSynthesis.cancel();
    speechSynthesis.speak(u);
  }

  // ---- dictation (Chrome on Android: Google speech recognition) ----
  var Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (Recognition) {
    micBtn.hidden = false;
    var rec = null;
    micBtn.addEventListener("click", function () {
      if (rec) { rec.stop(); return; }
      if ("speechSynthesis" in window) speechSynthesis.cancel();
      rec = new Recognition();
      rec.lang = "sk-SK";
      rec.interimResults = true;
      rec.maxAlternatives = 1;
      var finalText = "";
      rec.onresult = function (e) {
        var interim = "";
        for (var i = e.resultIndex; i < e.results.length; i++) {
          if (e.results[i].isFinal) finalText += e.results[i][0].transcript;
          else interim += e.results[i][0].transcript;
        }
        input.value = (finalText + interim).trim();
        autosize();
      };
      rec.onerror = function (e) {
        if (e.error === "not-allowed" || e.error === "service-not-allowed") toast("Povoľ mikrofón v nastaveniach prehliadača 🎤");
        else if (e.error === "no-speech") toast("Nič som nepočul 🙉");
        else if (e.error !== "aborted") toast("Diktovanie zlyhalo (" + e.error + ")");
      };
      rec.onend = function () {
        micBtn.classList.remove("listening");
        rec = null;
        if (finalText.trim()) send(finalText);
      };
      micBtn.classList.add("listening");
      if (navigator.vibrate) navigator.vibrate(15);
      rec.start();
    });
  }

  // ---- now playing + remote ----
  var now = $("now"), dot = $("dot");
  function fmt(min) {
    var m = Math.round(min || 0);
    return m >= 60 ? Math.floor(m / 60) + " h " + (m % 60) + " min" : m + " min";
  }
  function render(s) {
    if (s.connected === false) {
      dot.className = "dot off";
      now.classList.add("idle");
      $("nowTitle").textContent = "Wholphinix na TV nie je pripojený";
      $("nowSub").textContent = "Zapni telku a otvor appku 📺";
      return;
    }
    if (s.connected !== true) {
      dot.className = "dot";
      now.classList.add("idle");
      $("nowTitle").textContent = "Stav TV neviem zistiť";
      $("nowSub").textContent = s.message || "";
      return;
    }
    dot.className = "dot on";
    var p = s.playing;
    if (!p) {
      now.classList.add("idle");
      $("nowTitle").textContent = "Na TV nič nebeží";
      $("nowSub").textContent = "Napíš, čo pustiť 🍿";
      return;
    }
    now.classList.remove("idle");
    var title = p.series
      ? p.series + " · S" + (p.season || "?") + "E" + (p.episode || "?") + " " + (p.name || "")
      : (p.name || "?") + (p.year ? " (" + p.year + ")" : "");
    $("nowTitle").textContent = title;
    var sub = (s.paused ? "⏸ Pauza · " : "▶ ") + fmt(s.position_min);
    if (p.runtime_min) sub += " / " + fmt(p.runtime_min);
    $("nowSub").textContent = sub;
    $("nowBar").style.width = p.runtime_min ? Math.min(100, (100 * (s.position_min || 0)) / p.runtime_min) + "%" : "0";
  }
  var refreshTimer;
  function refresh() {
    clearTimeout(refreshTimer);
    return api("status")
      .then(render)
      .catch(function () { render({ connected: null, message: "Server neodpovedá" }); })
      .finally(function () {
        if (document.visibilityState === "visible") refreshTimer = setTimeout(refresh, 10000);
      });
  }
  // Jellyfin learns the new state from the TV's next progress report, so wait a moment
  function refreshSoon() {
    clearTimeout(refreshTimer);
    refreshTimer = setTimeout(refresh, 1500);
  }
  document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "visible") refresh();
  });

  now.querySelector(".remote").addEventListener("click", function (e) {
    var b = e.target.closest("button");
    if (!b) return;
    if (navigator.vibrate) navigator.vibrate(10);
    var body = { action: b.dataset.act };
    if (b.dataset.sec) body.seconds = Number(b.dataset.sec);
    api("control", body)
      .then(function (r) {
        if (r.ok === false) toast(r.error || "Nepodarilo sa 😕");
        refreshSoon();
      })
      .catch(function () { toast("Server neodpovedá 📡"); });
  });

  refresh();
  log.scrollTop = log.scrollHeight;
})();
