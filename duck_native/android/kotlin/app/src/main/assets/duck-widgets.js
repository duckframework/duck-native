/*
 * DuckNativeWidgets: turns placeholder elements into native Android widgets.
 *
 * Python widgets render an element with these attributes:
 *
 *   data-native-widget  Registered widget name, e.g. "switch".
 *   data-native-props   JSON props, kept up to date by Python.
 *   data-native-inline  "true" when the widget is drawn inside the page.
 *
 * This script mounts, updates and unmounts the native widget as the element
 * appears, changes and disappears. Native events come back as DOM events named
 * "native-<event>", which Lively forwards to the Python handler. It never edits
 * the placeholder, so Lively's own bookkeeping stays untouched.
 *
 * It needs window.DuckNative, so it is injected right after duck-native.js.
 */
(function () {
  "use strict";

  if (!window.DuckNative || window.DuckNativeWidgets) {
    return;
  }

  const SELECTOR = "[data-native-widget]";
  const PROPS_ATTR = "data-native-props";
  const EVENT_PREFIX = "native-";
  const LAYOUT_EPSILON = 0.5;

  // Widget id -> { el, name, raw, inline, rect, failed }
  const mounted = new Map();
  let scanQueued = false;
  let layoutRunning = false;

  function warn(error) {
    console.warn("DuckNative widget:", error && error.message);
  }

  function readProps(el) {
    try {
      return JSON.parse(el.getAttribute(PROPS_ATTR) || "{}");
    } catch (error) {
      return {};
    }
  }

  function mount(el) {
    const entry = {
      el,
      name: el.dataset.nativeWidget,
      raw: el.getAttribute(PROPS_ATTR) || "{}",
      inline: el.dataset.nativeInline === "true",
      rect: null,
      failed: false,
    };
    mounted.set(el.id, entry);

    DuckNative.call("widget.mount", { id: el.id, name: entry.name, props: readProps(el) })
      .then(startLayout)
      .catch((error) => {
        // Not running in Duck Bridge, or the widget is unknown: leave the placeholder alone
        entry.failed = true;
        warn(error);
      });
  }

  // Mounts new placeholders, pushes changed props and unmounts removed ones
  function scan() {
    scanQueued = false;
    const seen = new Set();

    for (const el of document.querySelectorAll(SELECTOR)) {
      if (!el.id) {
        console.warn("DuckNative widget needs an id");
        continue;
      }
      seen.add(el.id);

      const entry = mounted.get(el.id);
      if (!entry) {
        mount(el);
        continue;
      }

      // Lively may replace the element, so keep the same native widget
      entry.el = el;
      const raw = el.getAttribute(PROPS_ATTR) || "{}";
      if (raw !== entry.raw && !entry.failed) {
        entry.raw = raw;
        DuckNative.call("widget.update", { id: el.id, props: readProps(el) }).catch(warn);
      }
    }

    for (const [id, entry] of Array.from(mounted)) {
      if (seen.has(id)) {
        continue;
      }
      mounted.delete(id);
      if (!entry.failed) {
        DuckNative.call("widget.unmount", { id }).catch(warn);
      }
    }
  }

  function queueScan() {
    if (!scanQueued) {
      scanQueued = true;
      requestAnimationFrame(scan);
    }
  }

  // Reports an inline widget's placeholder box, only when it moved
  function sendLayout(id, entry) {
    const box = entry.el.getBoundingClientRect();
    const shown = entry.el.checkVisibility
      ? entry.el.checkVisibility()
      : entry.el.offsetParent !== null;
    const next = {
      x: box.left,
      y: box.top,
      width: box.width,
      height: box.height,
      visible:
        shown &&
        box.width > 0 &&
        box.height > 0 &&
        box.bottom > 0 &&
        box.right > 0 &&
        box.top < window.innerHeight &&
        box.left < window.innerWidth,
    };

    const last = entry.rect;
    if (
      last &&
      last.visible === next.visible &&
      Math.abs(last.x - next.x) < LAYOUT_EPSILON &&
      Math.abs(last.y - next.y) < LAYOUT_EPSILON &&
      Math.abs(last.width - next.width) < LAYOUT_EPSILON &&
      Math.abs(last.height - next.height) < LAYOUT_EPSILON
    ) {
      return;
    }
    entry.rect = next;
    DuckNative.call("widget.layout", { id, ...next }).catch(warn);
  }

  // Runs every frame while inline widgets exist, so they follow scrolling
  function frame() {
    let inline = 0;
    for (const [id, entry] of mounted) {
      if (entry.inline && !entry.failed) {
        inline++;
        sendLayout(id, entry);
      }
    }
    layoutRunning = inline > 0;
    if (layoutRunning) {
      requestAnimationFrame(frame);
    }
  }

  function startLayout() {
    if (!layoutRunning) {
      layoutRunning = true;
      requestAnimationFrame(frame);
    }
  }

  // Native events become DOM events; __send_raw_detail makes Lively send the value as is
  DuckNative.on("widget", ({ id, event, value }) => {
    const entry = mounted.get(id);
    if (!entry) {
      return;
    }
    const custom = new CustomEvent(EVENT_PREFIX + event, { detail: value ?? null });
    custom.__send_raw_detail = true;
    entry.el.dispatchEvent(custom);
  });

  function start() {
    new MutationObserver(queueScan).observe(document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: [PROPS_ATTR],
    });
    queueScan();
  }

  if (document.documentElement) {
    start();
  } else {
    document.addEventListener("DOMContentLoaded", start, { once: true });
  }

  window.DuckNativeWidgets = Object.freeze({ rescan: queueScan });
})();
