(function () {
  "use strict";

  var DIRECTIONS = ["north", "east", "south", "west"];
  var DEFAULT_COLORS = [78, 69, 58, 76];
  var FRAME_MS = 100;

  var $ = function (id) { return document.getElementById(id); };

  var state = {
    outfit: null,      // grupo do outfit (nome + looktype por sexo)
    sex: "male",
    addons: 0,
    colors: DEFAULT_COLORS.slice(), // índice da paleta (número) ou hex ("ff0000")
    part: 0,
    mount: 0,
    mountOn: false,
    direction: 2,
    walking: true
  };

  var palette = [];
  var outfits = [];  // [{name, male, female, addons, mountable, colorable}]
  var mounts = [];   // [{looktype, name}]
  var tab = "outfits";

  function looktype() {
    var o = state.outfit;
    return o ? (o[state.sex] || o.male || o.female) : 128;
  }

  function params(format) {
    var o = state.outfit || {};
    return {
      looktype: looktype(),
      addons: state.addons,
      head: state.colors[0],
      body: state.colors[1],
      legs: state.colors[2],
      feet: state.colors[3],
      mount: state.mountOn && o.mountable ? state.mount : 0,
      direction: DIRECTIONS[state.direction],
      idle: !state.walking,
      frameMs: FRAME_MS,
      format: format
    };
  }

  function hex(color) {
    return typeof color === "number" ? palette[color] || "ffffff" : color;
  }

  // ---------- carregamento ----------

  function getJson(path) {
    return fetch(path).then(function (r) {
      if (!r.ok) throw new Error(path + ": HTTP " + r.status);
      return r.json();
    });
  }

  /** Junta as versões masculina e feminina de cada outfit pelo nome. */
  function groupOutfits(rows) {
    var byName = {};
    rows.forEach(function (row) {
      var key = row.name.toLowerCase();
      var group = byName[key] || (byName[key] = {
        name: row.name, addons: row.addons, mountable: row.mountable, colorable: row.colorable
      });
      group[row.sex] = row.looktype;
      group.addons = Math.max(group.addons, row.addons);
    });
    return Object.keys(byName).map(function (k) { return byName[k]; })
      .sort(function (a, b) { return a.name.localeCompare(b.name); });
  }

  // ---------- estado na URL (#looktype=...) para compartilhar ----------

  function readHash() {
    var query = new URLSearchParams(location.hash.slice(1));
    var lt = query.get("looktype");
    if (lt) {
      var id = +lt;
      state.outfit = outfits.find(function (o) { return o.male === id || o.female === id; }) || state.outfit;
      if (state.outfit && state.outfit.female === id) state.sex = "female";
    }
    if (query.has("addons")) state.addons = clamp(+query.get("addons"), 0, 3);
    ["head", "body", "legs", "feet"].forEach(function (key, i) {
      var v = query.get(key);
      if (v) state.colors[i] = /^\d{1,3}$/.test(v) ? clamp(+v, 0, 132) : v.replace("#", "");
    });
    if (query.has("mount")) {
      state.mount = +query.get("mount");
      state.mountOn = state.mount > 0;
    }
    var dir = DIRECTIONS.indexOf(query.get("direction"));
    if (dir >= 0) state.direction = dir;
    if (query.get("idle") === "1") state.walking = false;
  }

  function writeHash() {
    var p = params("gif");
    var query = new URLSearchParams();
    query.set("looktype", p.looktype);
    if (p.addons) query.set("addons", p.addons);
    ["head", "body", "legs", "feet"].forEach(function (key) { query.set(key, p[key]); });
    if (p.mount) query.set("mount", p.mount);
    query.set("direction", p.direction);
    if (p.idle) query.set("idle", "1");
    history.replaceState(null, "", "#" + query.toString());
  }

  function clamp(v, min, max) {
    return isNaN(v) ? min : Math.max(min, Math.min(max, v));
  }

  // ---------- desenho da interface ----------

  function render() {
    var o = state.outfit;
    if (!o) return;
    if (!o[state.sex]) state.sex = o.male ? "male" : "female";
    if (o.addons < 2) state.addons &= o.addons === 1 ? 1 : 0;

    $("outfit-name").textContent = o.name;
    setPressed($("sex-male"), state.sex === "male");
    setPressed($("sex-female"), state.sex === "female");
    $("sex-male").disabled = !o.male;
    $("sex-female").disabled = !o.female;

    $("addon1").disabled = o.addons < 1;
    $("addon2").disabled = o.addons < 2;
    $("addon1").checked = (state.addons & 1) !== 0;
    $("addon2").checked = (state.addons & 2) !== 0;
    $("walking").checked = state.walking;

    var mount = mounts.find(function (m) { return m.looktype === state.mount; });
    $("mount-on").disabled = !o.mountable || !mount;
    $("mount-on").checked = state.mountOn && o.mountable && !!mount;
    $("mount-name").textContent = mount ? mount.name : "Sem montaria";
    var mountPreview = $("mount-preview");
    if (mount) {
      mountPreview.setAttribute("looktype", mount.looktype);
      mountPreview.style.visibility = "";
    } else {
      mountPreview.style.visibility = "hidden";
    }

    setAttributes($("preview"), params("gif"));

    document.querySelectorAll(".part").forEach(function (button) {
      var i = +button.dataset.part;
      button.classList.toggle("active", i === state.part);
      button.setAttribute("aria-selected", String(i === state.part));
      button.querySelector("i").style.background = "#" + hex(state.colors[i]);
      button.disabled = !o.colorable;
    });
    document.querySelectorAll(".swatch").forEach(function (cell) {
      cell.classList.toggle("selected", +cell.dataset.index === state.colors[state.part]);
    });
    var current = state.colors[state.part];
    $("hex").value = typeof current === "string" ? "#" + current : "";

    $("download-gif").href = TibiaWalk.url(params("gif"));
    $("download-gif").download = fileName("gif");
    $("download-png").href = TibiaWalk.url(params("png"));
    $("download-png").download = fileName("png");
    $("embed-code").value = embedCode();
    writeHash();
  }

  function setPressed(button, pressed) {
    button.setAttribute("aria-pressed", String(pressed));
  }

  function setAttributes(el, p) {
    ["looktype", "addons", "head", "body", "legs", "feet", "direction"].forEach(function (key) {
      el.setAttribute(key, p[key]);
    });
    if (p.mount) el.setAttribute("mount", p.mount); else el.removeAttribute("mount");
    if (p.idle) el.setAttribute("idle", ""); else el.removeAttribute("idle");
    el.setAttribute("frame-ms", p.frameMs);
  }

  function fileName(ext) {
    var p = params(ext);
    var base = (state.outfit ? state.outfit.name : "outfit").toLowerCase().replace(/[^a-z0-9]+/g, "_");
    return base + "_" + p.looktype + "_a" + p.addons + (p.mount ? "_m" + p.mount : "") + "_" + p.direction + "." + ext;
  }

  function embedCode() {
    var p = params("gif");
    var attrs = ['looktype="' + p.looktype + '"'];
    if (p.addons) attrs.push('addons="' + p.addons + '"');
    ["head", "body", "legs", "feet"].forEach(function (key) { attrs.push(key + '="' + p[key] + '"'); });
    if (p.mount) attrs.push('mount="' + p.mount + '"');
    if (p.direction !== "south") attrs.push('direction="' + p.direction + '"');
    if (p.idle) attrs.push("idle");
    attrs.push('scale="2"');
    return '<script src="' + TibiaWalk.server + '/tibiawalk.js"></' + 'script>\n' +
      "<tibia-outfit " + attrs.join(" ") + "></tibia-outfit>";
  }

  function buildPalette() {
    var grid = $("palette");
    palette.forEach(function (color, i) {
      var cell = document.createElement("button");
      cell.className = "swatch";
      cell.style.background = "#" + color;
      cell.dataset.index = i;
      cell.title = i + " (#" + color + ")";
      cell.setAttribute("aria-label", "Cor " + i);
      cell.addEventListener("click", function () {
        state.colors[state.part] = i;
        render();
      });
      grid.appendChild(cell);
    });
  }

  function renderTiles() {
    var q = $("search").value.trim().toLowerCase();
    var tiles = $("tiles");
    tiles.innerHTML = "";
    var items = tab === "outfits"
      ? outfits.filter(function (o) { return !q || o.name.toLowerCase().indexOf(q) >= 0; })
      : [{ looktype: 0, name: "Sem montaria" }].concat(mounts.filter(function (m) {
        return !q || m.name.toLowerCase().indexOf(q) >= 0;
      }));
    if (!items.length) {
      tiles.innerHTML = '<div class="empty">Nada encontrado</div>';
      return;
    }
    var fragment = document.createDocumentFragment();
    items.forEach(function (item) {
      var tile = document.createElement("button");
      tile.className = "btn tile";
      var id = tab === "outfits" ? (item[state.sex] || item.male || item.female) : item.looktype;
      var selected = tab === "outfits" ? item === state.outfit : (state.mountOn ? state.mount : 0) === id;
      tile.classList.toggle("active", selected);
      tile.title = item.name + (id ? " (" + id + ")" : "");
      var img = document.createElement("img");
      img.loading = "lazy";
      img.alt = "";
      if (id) {
        img.src = TibiaWalk.url({ looktype: id, idle: true, format: "png" });
      }
      var label = document.createElement("span");
      label.textContent = item.name;
      tile.appendChild(img);
      tile.appendChild(label);
      tile.addEventListener("click", function () {
        if (tab === "outfits") {
          state.outfit = item;
        } else if (id) {
          state.mount = id;
          state.mountOn = true;
        } else {
          state.mountOn = false;
        }
        render();
        renderTiles();
      });
      fragment.appendChild(tile);
    });
    tiles.appendChild(fragment);
  }

  // ---------- ações ----------

  function cycle(list, current, step) {
    var i = list.indexOf(current);
    return list[(i + step + list.length) % list.length];
  }

  function cycleOutfit(step) {
    state.outfit = cycle(outfits, state.outfit, step);
    render();
    renderTiles();
  }

  function cycleMount(step) {
    var current = mounts.find(function (m) { return m.looktype === state.mount; }) || mounts[0];
    state.mount = cycle(mounts, current, step).looktype;
    state.mountOn = true;
    render();
    renderTiles();
  }

  function randomize() {
    var pick = function (list) { return list[Math.floor(Math.random() * list.length)]; };
    state.outfit = pick(outfits);
    state.sex = state.outfit.male && state.outfit.female ? pick(["male", "female"]) : (state.outfit.male ? "male" : "female");
    state.addons = Math.floor(Math.random() * 4);
    state.colors = [0, 1, 2, 3].map(function () { return Math.floor(Math.random() * palette.length); });
    state.mount = pick(mounts).looktype;
    state.mountOn = Math.random() < 0.5;
    state.direction = Math.floor(Math.random() * 4);
    render();
    renderTiles();
  }

  function copy(text, message) {
    navigator.clipboard.writeText(text).then(function () {
      toast(message);
    }, function () {
      toast("Não consegui copiar; selecione e copie manualmente");
    });
  }

  var toastTimer;
  function toast(message) {
    $("toast").textContent = message;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { $("toast").textContent = ""; }, 2500);
  }

  function bind() {
    $("outfit-prev").addEventListener("click", function () { cycleOutfit(-1); });
    $("outfit-next").addEventListener("click", function () { cycleOutfit(1); });
    $("mount-prev").addEventListener("click", function () { cycleMount(-1); });
    $("mount-next").addEventListener("click", function () { cycleMount(1); });
    $("sex-male").addEventListener("click", function () { state.sex = "male"; render(); renderTiles(); });
    $("sex-female").addEventListener("click", function () { state.sex = "female"; render(); renderTiles(); });
    $("addon1").addEventListener("change", function (e) { state.addons = (state.addons & 2) | (e.target.checked ? 1 : 0); render(); });
    $("addon2").addEventListener("change", function (e) { state.addons = (state.addons & 1) | (e.target.checked ? 2 : 0); render(); });
    $("walking").addEventListener("change", function (e) { state.walking = e.target.checked; render(); });
    $("mount-on").addEventListener("change", function (e) { state.mountOn = e.target.checked; render(); renderTiles(); });
    $("rotate-left").addEventListener("click", function () { state.direction = (state.direction + 3) % 4; render(); });
    $("rotate-right").addEventListener("click", function () { state.direction = (state.direction + 1) % 4; render(); });
    $("randomize").addEventListener("click", randomize);
    document.querySelectorAll(".part").forEach(function (button) {
      button.addEventListener("click", function () { state.part = +button.dataset.part; render(); });
    });
    $("hex").addEventListener("change", function (e) {
      var v = e.target.value.trim().replace("#", "");
      if (/^[0-9a-fA-F]{6}$/.test(v)) {
        state.colors[state.part] = v.toLowerCase();
        render();
      } else if (v) {
        toast("Use 6 dígitos hex, ex.: #ff0000");
      }
    });
    document.querySelectorAll(".tab").forEach(function (button) {
      button.addEventListener("click", function () {
        tab = button.dataset.tab;
        document.querySelectorAll(".tab").forEach(function (b) {
          b.setAttribute("aria-selected", String(b === button));
        });
        $("search").value = "";
        renderTiles();
      });
    });
    $("search").addEventListener("input", renderTiles);
    $("copy-embed").addEventListener("click", function () { copy($("embed-code").value, "Código copiado"); });
    $("copy-link").addEventListener("click", function () {
      copy(new URL(TibiaWalk.url(params("gif")), location.href).href, "Link copiado");
    });
    $("preview").addEventListener("tibiawalk-error", function (e) { toast(e.detail); });
  }

  Promise.all([
    getJson("api/palette"),
    getJson("api/looktypes?kind=player"),
    getJson("api/mounts")
  ]).then(function (results) {
    palette = results[0];
    outfits = groupOutfits(results[1]);
    mounts = results[2].map(function (m) { return { looktype: m.looktype, name: m.name }; });
    state.outfit = outfits.find(function (o) { return o.male === 128; }) || outfits[0];
    state.mount = mounts.length ? mounts[0].looktype : 0;
    readHash();
    buildPalette();
    bind();
    render();
    renderTiles();
  }).catch(function (e) {
    $("outfit-name").textContent = "Erro ao falar com o servidor: " + e.message;
  });
})();
