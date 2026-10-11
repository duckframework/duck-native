package com.duckframework.bridge

/**
 * Built-in test page, opened from the connect screen of debug builds.
 *
 * It is served from memory at [URL] (see `DuckWebViewClient`), so it has a real
 * https origin that the trust policy can verify and needs no server. The script
 * avoids the dollar sign so it can live in a Kotlin raw string.
 */
object BridgePage {

    const val URL = "https://duck.local/"

    val html: String = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Duck Native Bridge</title>
  <style>
    body { font-family: sans-serif; margin: 0; padding: 32px 24px;
           background: #fafafa; color: #222; }
    h1 { margin-top: 0; }
    button { font-size: 16px; padding: 12px 18px; margin: 0 8px 8px 0;
             border: 0; border-radius: 8px; background: #f5a623; color: #000; }
    pre { background: #222; color: #eee; padding: 12px; border-radius: 8px;
          white-space: pre-wrap; min-height: 120px; }
  </style>
</head>
<body>
  <h1>Duck Native Bridge</h1>
  <p>Each button calls <code>DuckNative.call()</code>, which Kotlin dispatches.
     Results are logged below. Buttons marked <b>debug</b> need a debug build.</p>
  <div id="buttons"></div>
  <pre id="log"></pre>

  <script>
    var logEl = document.getElementById("log");

    function log(line) {
      logEl.textContent = line + "\n" + logEl.textContent;
    }

    // Resolves once the bridge script has been injected
    function bridge() {
      if (window.DuckNative) { return Promise.resolve(window.DuckNative); }
      return new Promise(function (resolve) {
        window.addEventListener("DuckNativeReady", function () {
          resolve(window.DuckNative);
        }, { once: true });
      });
    }

    // Runs a test; expected is an error code the call must fail with
    function run(name, work, expected) {
      return bridge().then(work).then(
        function (result) {
          var ok = !expected;
          log((ok ? "PASS " : "FAIL ") + name + " " + JSON.stringify(result));
        },
        function (error) {
          var ok = error.code === expected;
          var detail = "[" + error.code + "] " + error.message;
          log((ok ? "PASS " : "FAIL ") + name + " " + detail);
        }
      );
    }

    var finishOrder = [];

    function delayed(native, ms, label) {
      return native.call("debug.delay", { ms: ms, label: label }).then(function (r) {
        finishOrder.push(r.label);
        return r;
      });
    }

    var tests = [
      ["Toast", function (n) {
        return n.call("toast", { message: "Hello from Duck Native!" });
      }],
      ["Vibrate", function (n) { return n.call("vibrate", { duration: 100 }); }],
      ["Clipboard", function (n) {
        return n.call("clipboard.write", { text: "Copied by Duck" }).then(function () {
          return n.call("clipboard.read");
        });
      }],
      ["Share", function (n) { return n.call("share", { text: "Hello from Duck" }); }],
      ["Device info", function (n) { return n.call("device.info"); }],
      ["Capabilities", function (n) { return n.call("bridge.capabilities"); }],
      ["Unknown action", function (n) { return n.call("does.not.exist"); },
        "UNKNOWN_ACTION"],
      ["Bad payload", function (n) { return n.call("toast", { message: 42 }); },
        "INVALID_PAYLOAD"],
      ["Concurrent (debug)", function (n) {
        finishOrder = [];
        return Promise.all([
          delayed(n, 300, "a"), delayed(n, 100, "b"), delayed(n, 200, "c")
        ]).then(function () {
          return "finished " + finishOrder.join(",") + " (want b,c,a)";
        });
      }],
      ["Async 1.5s (debug)", function (n) {
        return n.call("debug.delay", { ms: 1500, label: "slow" });
      }]
    ];

    tests.forEach(function (test) {
      var button = document.createElement("button");
      button.textContent = test[0];
      button.onclick = function () { run(test[0], test[1], test[2]); };
      document.getElementById("buttons").appendChild(button);
    });

    bridge().then(function (n) {
      ["resume", "pause", "networkChanged"].forEach(function (name) {
        n.on(name, function (payload) {
          log("EVENT " + name + " " + JSON.stringify(payload));
        });
      });
    });
  </script>
</body>
</html>
""".trimIndent()
}
