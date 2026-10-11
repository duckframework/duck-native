/*
 * DuckNative: the JavaScript side of Duck Bridge for Android.
 *
 * The app injects this script into pages from trusted origins only. It wraps the
 * native AndroidDuckBridge object in a Promise-based API:
 *
 *   await DuckNative.call("toast", { message: "Hello" });
 *   DuckNative.on("resume", () => reconnect());
 *
 * Errors are Error objects with a stable `code` such as UNKNOWN_ACTION.
 * Pages should feature-detect: `if (window.DuckNative) { ... }`.
 */
(function () {
  "use strict";

  // Inject only once per document
  if (window.DuckNative) {
    return;
  }

  const bridge = window.AndroidDuckBridge;
  const pending = new Map();
  const listeners = new Map();
  let nextId = 1;

  function makeError(code, message) {
    const error = new Error(message);
    error.code = code;
    return error;
  }

  // Removes and returns a pending request, or null if it already finished
  function take(id) {
    const entry = pending.get(id);
    if (!entry) {
      return null;
    }
    pending.delete(id);
    clearTimeout(entry.timer);
    return entry;
  }

  /**
   * Calls a native action.
   *
   * @param {string} action Action name, for example "clipboard.read".
   * @param {object} [payload] Action arguments.
   * @param {{timeoutMs?: number}} [options] Reject with TIMEOUT after this long.
   * @returns {Promise<any>} The native result.
   */
  function call(action, payload, options) {
    return new Promise((resolve, reject) => {
      // Fail fast when the native side is missing
      if (!bridge || typeof bridge.request !== "function") {
        reject(makeError("NOT_SUPPORTED", "Duck Bridge is not available"));
        return;
      }

      // Serialize first so a bad payload never leaves a pending entry behind
      const id = String(nextId++);
      let requestJson;
      try {
        requestJson = JSON.stringify({ id, action, payload: payload ?? {} });
      } catch (error) {
        reject(makeError("INVALID_PAYLOAD", "Payload is not serializable"));
        return;
      }

      // Track the request, with an optional timeout
      const entry = { resolve, reject, timer: null };
      const timeoutMs = options && options.timeoutMs;
      if (timeoutMs > 0) {
        entry.timer = setTimeout(() => {
          const expired = take(id);
          if (expired) {
            expired.reject(makeError("TIMEOUT", "Native call timed out"));
          }
        }, timeoutMs);
      }
      pending.set(id, entry);

      // Send it; native answers later through resolve()
      try {
        bridge.request(requestJson);
      } catch (error) {
        const failed = take(id);
        if (failed) {
          failed.reject(makeError("NATIVE_ERROR", "Native bridge call failed"));
        }
      }
    });
  }

  /**
   * Completes a pending call. Called by native code, not by applications.
   *
   * @param {string} id Request id.
   * @param {{ok: boolean, result?: any, error?: {code: string, message: string}}} response
   */
  function resolve(id, response) {
    const entry = take(String(id));
    if (!entry) {
      return;
    }

    if (response && response.ok) {
      entry.resolve(response.result ?? null);
      return;
    }

    entry.reject(
      makeError(
        response?.error?.code ?? "NATIVE_ERROR",
        response?.error?.message ?? "Native operation failed"
      )
    );
  }

  /**
   * Subscribes to a native event such as "resume" or "networkChanged".
   *
   * @param {string} event Event name.
   * @param {(payload: object) => void} callback Called with the event payload.
   * @returns {() => void} Function that unsubscribes.
   */
  function on(event, callback) {
    let callbacks = listeners.get(event);
    if (!callbacks) {
      callbacks = new Set();
      listeners.set(event, callbacks);
    }
    callbacks.add(callback);
    return () => off(event, callback);
  }

  /**
   * Removes a subscription made with on().
   */
  function off(event, callback) {
    listeners.get(event)?.delete(callback);
  }

  /**
   * Delivers a native event to subscribers. Called by native code.
   */
  function dispatch(event, payload) {
    for (const callback of Array.from(listeners.get(event) ?? [])) {
      try {
        callback(payload ?? {});
      } catch (error) {
        console.error("DuckNative listener failed", error);
      }
    }
  }

  window.DuckNative = Object.freeze({ call, resolve, on, off, dispatch });
  window.dispatchEvent(new Event("DuckNativeReady"));
})();
