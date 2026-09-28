(function () {
  "use strict";

  var DIRECTIONS = ["north", "east", "south", "west"];
  var DEFAULT_COLORS = [78, 69, 58, 76];
  var DEFAULT_FRAME_MS = 100;

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
    walking: true,
    frameMs: DEFAULT_FRAME_MS
  };

  var palette = [];
  var outfits = [];  // [{name, male, female, addons, mountable, colorable}]
  var mounts = [];   // [{looktype, name}]
  // Abas carregadas na primeira vez que abrem: personagens (npcs, monsters) e looktypes (unknown, all).
  var lists = { npcs: null, monsters: null, unknown: null, all: null };
  var counts = {};
  var tab = "outfits";
  var bossOnly = false; // aba Monstros: mostrar só bosses

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
      frameMs: state.frameMs,
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
      if (row.sex) {
        group[row.sex] = row.looktype;
      } else {
        group.male = group.male || row.looktype; // sem sexo definido: vale para os dois
        group.female = group.female || row.looktype;
      }
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
    if (query.has("frameMs")) state.frameMs = clamp(+query.get("frameMs"), 40, 400);
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
    if (p.frameMs !== DEFAULT_FRAME_MS) query.set("frameMs", p.frameMs);
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
    $("sex-male").disabled = !o.male || !!o.character || !!o.single;
    $("sex-female").disabled = !o.female || !!o.character || !!o.single;

    // Addon de NPC/monstro é fixo: as caixas mostram o que ele usa, mas não mudam.
    var character = !!o.character;
    $("addon1").disabled = o.addons < 1 || character;
    $("addon2").disabled = o.addons < 2 || character;
    var target = character ? playerOutfitOf(o.character) : null;
    $("goto-outfit").hidden = !target;
    if (target) $("goto-outfit").textContent = "Ir para o outfit: " + target.name;
    $("addon1").checked = (state.addons & 1) !== 0;
    $("addon2").checked = (state.addons & 2) !== 0;
    $("walking").checked = state.walking;
    $("speed").value = state.frameMs;
    $("speed").disabled = !state.walking;
    $("speed-value").textContent = state.frameMs + " ms";

    // Aba Montarias: só a montaria, em destaque. Fora dela, quem não tem versão montada
    // (NPCs, monstros, objetos) nem mostra a caixa da montaria.
    var mountsTab = tab === "mounts";
    $("outfit-box").hidden = mountsTab;
    $("mount-box").hidden = !mountsTab && !o.mountable;
    document.querySelector(".previews").classList.toggle("single", mountsTab || !o.mountable);
    $("mount-preview").setAttribute("scale", mountsTab ? "3" : "2");
    // Na aba Montarias, o botão leva a montaria escolhida para o outfit (e volta para ele).
    $("mount-on-label").hidden = mountsTab;
    $("use-mount").hidden = !mountsTab;

    var mount = mounts.find(function (m) { return m.looktype === state.mount; });
    $("mount-on").disabled = !o.mountable || !mount;
    $("mount-on").checked = state.mountOn && o.mountable && !!mount;
    $("mount-name").textContent = mount ? mount.name : "Sem montaria";
    $("use-mount").disabled = !o.mountable || !mount;
    $("use-mount").textContent = !o.mountable ? "O outfit atual não monta" : "Usar no outfit: " + o.name;
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
    else if (p.frameMs !== DEFAULT_FRAME_MS) attrs.push('frame-ms="' + p.frameMs + '"');
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

  /** Personagem vira um "outfit" avulso, já vestido com as cores, addons e montaria dele. */
  function applyCharacter(c) {
    state.outfit = {
      name: c.name, male: c.looktype, female: c.looktype, addons: c.addonCount,
      mountable: c.mountable, colorable: c.colorable, character: c
    };
    state.addons = c.addons;
    state.colors = [c.head, c.body, c.legs, c.feet];
    state.mountOn = c.mount > 0 && c.mountable;
    if (c.mount > 0) state.mount = c.mount;
  }

  /** O outfit de player com o mesmo looktype do NPC/monstro, se houver. */
  function playerOutfitOf(c) {
    return outfits.find(function (g) { return g.male === c.looktype || g.female === c.looktype; }) || null;
  }

  /** Vai para o outfit de player que o NPC/monstro veste, mantendo cores e addons. */
  function goToPlayerOutfit() {
    var c = state.outfit && state.outfit.character;
    var target = c ? playerOutfitOf(c) : null;
    if (!target) return;
    state.outfit = target;
    state.sex = target.female === c.looktype && target.male !== c.looktype ? "female" : "male";
    tab = "outfits";
    document.querySelectorAll(".tab").forEach(function (b) {
      b.setAttribute("aria-selected", String(b.dataset.tab === "outfits"));
    });
    $("search").value = "";
    render();
    renderTiles();
  }

  /** "Mostrando 12 de 435 bosses" ou "957 itens". */
  function showCount(shown, total, what) {
    $("tiles-count").textContent = total == null ? ""
      : shown === total ? total + " " + (what || "itens") : "Mostrando " + shown + " de " + total + (what ? " " + what : "");
  }

  /** Tira o brilho de "carregando" da miniatura quando a imagem chega (ou falha). */
  function watchThumb(img) {
    var done = function () { img.classList.add("ready"); };
    img.addEventListener("load", done);
    img.addEventListener("error", done);
  }

  /** Mostra o "Gerando sprite…" no quadro enquanto o <tibia-outfit> carrega. */
  function watchPreview(el, box) {
    el.addEventListener("tibiawalk-loading", function () {
      box.querySelector(".loading-text").textContent = "Gerando sprite…";
      box.classList.add("busy");
    });
    el.addEventListener("tibiawalk-load", function () { box.classList.remove("busy"); });
    el.addEventListener("tibiawalk-error", function () { box.classList.remove("busy"); });
  }

  /** Aba Montarias: monta o outfit na montaria escolhida e volta para a aba Outfits. */
  function useMount() {
    state.mountOn = true;
    tab = "outfits";
    document.querySelectorAll(".tab").forEach(function (b) {
      b.setAttribute("aria-selected", String(b.dataset.tab === "outfits"));
    });
    $("search").value = "";
    render();
    renderTiles();
  }

  function renderCharacterTiles(list) {
    var q = $("search").value.trim().toLowerCase();
    var tiles = $("tiles");
    tiles.innerHTML = "";
    if (!list) {
      tiles.innerHTML = '<div class="empty">Carregando…</div>';
      showCount(0, null);
      return;
    }
    var scope = list.filter(function (c) { return !bossOnly || tab !== "monsters" || c.kind === "boss"; });
    var items = scope.filter(function (c) { return !q || c.name.toLowerCase().indexOf(q) >= 0; });
    showCount(items.length, scope.length, bossOnly && tab === "monsters" ? "bosses" : null);
    if (!items.length) {
      tiles.innerHTML = '<div class="empty">Nada encontrado</div>';
      return;
    }
    var fragment = document.createDocumentFragment();
    items.forEach(function (c) {
      var tile = document.createElement("button");
      tile.className = "btn tile";
      tile.classList.toggle("active", !!(state.outfit && state.outfit.character === c));
      tile.title = c.name + (c.kind === "boss" ? " (boss)" : "") + " — looktype " + c.looktype;
      var img = document.createElement("img");
      img.loading = "lazy";
      img.alt = "";
      watchThumb(img);
      img.src = TibiaWalk.url({
        looktype: c.looktype, addons: c.addons, head: c.head, body: c.body, legs: c.legs, feet: c.feet,
        idle: true, format: "png"
      });
      var label = document.createElement("span");
      label.textContent = (c.kind === "boss" ? "★ " : "") + c.name;
      tile.appendChild(img);
      tile.appendChild(label);
      tile.addEventListener("click", function () {
        applyCharacter(c);
        render();
        renderTiles();
      });
      fragment.appendChild(tile);
    });
    tiles.appendChild(fragment);
  }

  /** Looktype avulso (abas Sem nome / Todos): um outfit editável, sem par masculino/feminino. */
  function applyLooktype(row) {
    state.outfit = {
      name: row.name || "#" + row.looktype, male: row.looktype, female: row.looktype, addons: row.addons,
      mountable: row.mountable, colorable: row.colorable, single: true, looktype: row.looktype
    };
  }

  function renderLooktypeTiles(list) {
    var q = $("search").value.trim().toLowerCase();
    var tiles = $("tiles");
    tiles.innerHTML = "";
    if (!list) {
      tiles.innerHTML = '<div class="empty">Carregando…</div>';
      showCount(0, null);
      return;
    }
    var items = list.filter(function (row) {
      return !q || String(row.looktype) === q || (row.name && row.name.toLowerCase().indexOf(q) >= 0);
    });
    showCount(items.length, list.length);
    if (!items.length) {
      tiles.innerHTML = '<div class="empty">Nada encontrado</div>';
      return;
    }
    var fragment = document.createDocumentFragment();
    items.forEach(function (row) {
      var tile = document.createElement("button");
      tile.className = "btn tile";
      tile.classList.toggle("active", !!(state.outfit && state.outfit.single && state.outfit.looktype === row.looktype));
      tile.title = (row.name || "Sem nome") + " — looktype " + row.looktype + (row.kind ? " (" + row.kind + ")" : "");
      var img = document.createElement("img");
      img.loading = "lazy";
      img.alt = "";
      watchThumb(img);
      img.src = TibiaWalk.url({ looktype: row.looktype, idle: true, format: "png" });
      var label = document.createElement("span");
      label.textContent = row.name ? row.name + " (" + row.looktype + ")" : "#" + row.looktype;
      tile.appendChild(img);
      tile.appendChild(label);
      tile.addEventListener("click", function () {
        applyLooktype(row);
        render();
        renderTiles();
      });
      fragment.appendChild(tile);
    });
    tiles.appendChild(fragment);
  }

  var SOURCES = {
    npcs: "api/characters?kind=npc",
    monsters: "api/characters?kind=monster",
    unknown: "api/looktypes?kind=unknown",
    all: "api/looktypes?kind=all"
  };

  function renderTiles() {
    $("boss-only").hidden = tab !== "monsters";
    if (SOURCES[tab]) {
      var current = tab;
      var draw = current === "unknown" || current === "all" ? renderLooktypeTiles : renderCharacterTiles;
      if (!lists[current]) {
        draw(null);
        getJson(SOURCES[current]).then(function (list) {
          lists[current] = current === "all" ? list.sort(function (a, b) { return a.looktype - b.looktype; }) : list;
          if (tab === current) renderTiles();
        });
        return;
      }
      draw(lists[current]);
      return;
    }
    var q = $("search").value.trim().toLowerCase();
    var tiles = $("tiles");
    tiles.innerHTML = "";
    var items = tab === "outfits"
      ? outfits.filter(function (o) { return !q || o.name.toLowerCase().indexOf(q) >= 0; })
      : [{ looktype: 0, name: "Sem montaria" }].concat(mounts.filter(function (m) {
        return !q || m.name.toLowerCase().indexOf(q) >= 0;
      }));
    showCount(tab === "outfits" ? items.length : items.length - 1, tab === "outfits" ? outfits.length : mounts.length,
      tab === "outfits" ? "outfits" : "montarias");
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
        watchThumb(img);
        img.src = TibiaWalk.url({ looktype: id, idle: true, format: "png" });
      } else {
        img.classList.add("ready"); // "Sem montaria" não tem imagem
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
    $("speed").addEventListener("input", function (e) { $("speed-value").textContent = e.target.value + " ms"; });
    $("speed").addEventListener("change", function (e) { state.frameMs = +e.target.value; render(); });
    $("mount-on").addEventListener("change", function (e) { state.mountOn = e.target.checked; render(); renderTiles(); });
    $("rotate-left").addEventListener("click", function () { state.direction = (state.direction + 3) % 4; render(); });
    $("rotate-right").addEventListener("click", function () { state.direction = (state.direction + 1) % 4; render(); });
    $("randomize").addEventListener("click", randomize);
    $("goto-outfit").addEventListener("click", goToPlayerOutfit);
    $("use-mount").addEventListener("click", useMount);
    $("boss-only").addEventListener("click", function () {
      bossOnly = !bossOnly;
      $("boss-only").setAttribute("aria-pressed", String(bossOnly));
      renderTiles();
    });
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
        render(); // a aba Montarias muda o que aparece nos previews
        renderTiles();
      });
    });
    $("search").addEventListener("input", renderTiles);
    $("copy-embed").addEventListener("click", function () { copy($("embed-code").value, "Código copiado"); });
    $("copy-link").addEventListener("click", function () {
      copy(new URL(TibiaWalk.url(params("gif")), location.href).href, "Link copiado");
    });
    $("preview").addEventListener("tibiawalk-error", function (e) { toast(e.detail); });
    watchPreview($("preview"), $("preview-box"));
    watchPreview($("mount-preview"), $("mount-preview-box"));
  }

  function showTabCounts() {
    var byTab = {
      outfits: outfits.length, mounts: mounts.length, npcs: counts.npcs, monsters: counts.monsters,
      unknown: counts.unknown, all: counts.all
    };
    document.querySelectorAll(".tab").forEach(function (button) {
      var n = byTab[button.dataset.tab];
      button.querySelector(".count").textContent = n == null ? "" : "(" + n + ")";
    });
    $("boss-only").title = "Mostrar só os bosses" + (counts.bosses ? " (" + counts.bosses + ")" : "");
  }

  Promise.all([
    getJson("api/palette"),
    getJson("api/looktypes?kind=player"),
    getJson("api/mounts"),
    getJson("api/info")
  ]).then(function (results) {
    palette = results[0];
    outfits = groupOutfits(results[1]);
    mounts = results[2].map(function (m) { return { looktype: m.looktype, name: m.name }; });
    counts = results[3].counts || {};
    showTabCounts();
    state.outfit = outfits.find(function (o) { return o.male === 128; }) || outfits[0];
    state.mount = mounts.length ? mounts[0].looktype : 0;
    readHash();
    buildPalette();
    bind();
    render();
    renderTiles();
  }).catch(function (e) {
    $("preview-box").classList.remove("busy");
    $("outfit-name").textContent = "Erro ao falar com o servidor: " + e.message;
  });
})();
