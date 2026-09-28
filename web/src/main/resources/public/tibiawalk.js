/*
 * <tibia-outfit> — mostra um outfit do Tibia renderizado pelo servidor TibiaWalk.
 *
 *   <script src="https://SEU-SERVIDOR/tibiawalk.js"></script>
 *   <tibia-outfit looktype="Citizen" female addons="3" head="78" body="69" legs="58" feet="76"
 *                 mount="Widow Queen" direction="west" scale="2"></tibia-outfit>
 *
 *   <tibia-outfit npc="A Bearded Woman"></tibia-outfit>   (NPC/monstro já com as cores e addons dele)
 *
 * Atributos: looktype (número ou nome) ou npc / monster (nome), female, addons (0-3), head/body/legs/feet (0-132 ou hex),
 * mount (número ou nome), direction (north/east/south/west), idle, frame-ms, format (gif/png),
 * scale (ampliação na tela) e server (endereço do servidor; padrão: de onde veio este script).
 */
(function () {
  "use strict";

  var script = document.currentScript;
  var DEFAULT_SERVER = script && script.src ? new URL(script.src).origin : location.origin;

  var PARAMS = ["looktype", "npc", "monster", "addons", "head", "body", "legs", "feet", "mount", "direction"];
  var FLAGS = ["female", "idle"];

  /** Monta a URL da imagem. params: {looktype, addons, head, ..., female, idle, frameMs, format} */
  function url(params, server) {
    var base = (server || DEFAULT_SERVER).replace(/\/$/, "");
    var format = params.format === "png" ? "png" : "gif";
    var query = new URLSearchParams();
    PARAMS.forEach(function (key) {
      var value = params[key];
      if (value !== undefined && value !== null && value !== "" && !(key === "mount" && +value === 0)) {
        query.set(key, String(value));
      }
    });
    FLAGS.forEach(function (key) {
      if (params[key]) {
        query.set(key, "1");
      }
    });
    if (params.frameMs && format === "gif" && !params.idle) {
      query.set("frameMs", String(params.frameMs));
    }
    return base + "/api/outfit." + format + "?" + query.toString();
  }

  class TibiaOutfit extends HTMLElement {
    static get observedAttributes() {
      return PARAMS.concat(FLAGS, ["frame-ms", "format", "scale", "server"]);
    }

    constructor() {
      super();
      var root = this.attachShadow({ mode: "open" });
      root.innerHTML =
        "<style>:host{display:inline-block;line-height:0}" +
        "img{image-rendering:pixelated;image-rendering:crisp-edges}</style>" +
        '<img part="image" alt="">';
      this._img = root.querySelector("img");
      this._img.addEventListener("load", this._resize.bind(this));
      this._img.addEventListener("error", this._error.bind(this));
    }

    connectedCallback() {
      this._update();
    }

    attributeChangedCallback() {
      if (this.isConnected) {
        this._update();
      }
    }

    /** URL da imagem atual (útil para links de download). */
    get src() {
      return this._img.src;
    }

    _params() {
      var params = {};
      var self = this;
      PARAMS.forEach(function (key) {
        if (self.hasAttribute(key)) {
          params[key] = self.getAttribute(key);
        }
      });
      FLAGS.forEach(function (key) {
        params[key] = self.hasAttribute(key) && self.getAttribute(key) !== "false";
      });
      params.frameMs = this.getAttribute("frame-ms");
      params.format = this.getAttribute("format");
      return params;
    }

    _update() {
      if (!this.hasAttribute("looktype") && !this.hasAttribute("npc") && !this.hasAttribute("monster")) {
        return;
      }
      var next = url(this._params(), this.getAttribute("server"));
      if (this._img.getAttribute("src") !== next) {
        this._img.style.visibility = "";
        this._img.src = next;
      }
      var name = this.getAttribute("npc") || this.getAttribute("monster") || this.getAttribute("looktype");
      this._img.alt = "Outfit " + name;
      this._resize();
    }

    _resize() {
      var scale = parseFloat(this.getAttribute("scale")) || 1;
      if (this._img.naturalWidth) {
        this._img.style.width = this._img.naturalWidth * scale + "px";
        this._img.style.height = this._img.naturalHeight * scale + "px";
      }
    }

    _error() {
      var self = this;
      this._img.style.visibility = "hidden"; // sem o ícone de imagem quebrada
      // Busca a mensagem de erro da API para quem estiver ouvindo o evento.
      fetch(this._img.src)
        .then(function (r) { return r.json(); })
        .then(function (body) { return body.error; })
        .catch(function () { return "Falha ao carregar o outfit"; })
        .then(function (message) {
          self.title = message;
          self.dispatchEvent(new CustomEvent("tibiawalk-error", { detail: message, bubbles: true }));
        });
    }
  }

  if (!customElements.get("tibia-outfit")) {
    customElements.define("tibia-outfit", TibiaOutfit);
  }
  window.TibiaWalk = { url: url, server: DEFAULT_SERVER };
})();
