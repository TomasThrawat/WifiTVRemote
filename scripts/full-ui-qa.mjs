import fs from "node:fs";

const serial = process.argv[2];
if (!serial) throw new Error("Android serial is required");
const base = "http://127.0.0.1:4723";
const app = process.cwd() + "/app/build/outputs/apk/debug/app-debug.apk";
const results = [];

async function request(path, init = {}) {
  const r = await fetch(base + path, {
    headers: {"content-type": "application/json", ...(init.headers || {})},
    ...init
  });
  const body = await r.json();
  if (!r.ok || (body.value && body.value.error)) throw new Error(JSON.stringify(body));
  return body;
}
async function createSession() {
  const body = await request("/session", {
    method: "POST",
    body: JSON.stringify({
      capabilities: {
        alwaysMatch: {
          platformName: "Android",
          "appium:automationName": "UiAutomator2",
          "appium:deviceName": serial,
          "appium:udid": serial,
          "appium:app": app,
          "appium:autoGrantPermissions": true,
          "appium:noReset": false,
          "appium:newCommandTimeout": 120
        },
        firstMatch: [{}]
      }
    })
  });
  return body.value?.sessionId || body.sessionId;
}
async function closeSession(id) {
  try { await request("/session/" + encodeURIComponent(id), {method: "DELETE"}); } catch {}
}
async function source(id) {
  return (await request("/session/" + encodeURIComponent(id) + "/source")).value;
}
async function find(id, using, value) {
  return (await request("/session/" + encodeURIComponent(id) + "/element", {
    method: "POST",
    body: JSON.stringify({using, value})
  })).value;
}
async function clickElement(id, element) {
  const elementId = element["element-6066-11e4-a52e-4f735466cecf"] || element.ELEMENT || element;
  await request("/session/" + encodeURIComponent(id) + "/element/" + encodeURIComponent(elementId) + "/click", {method: "POST", body: "{}"});
}
async function clickDesc(id, desc) {
  await clickElement(id, await find(id, "accessibility id", desc));
}
async function hasText(id, text) {
  const xml = await source(id);
  return xml.includes('text="' + text + '"') || xml.includes('content-desc="' + text + '"');
}
async function record(name, fn) {
  try { await fn(); results.push({name, ok: true}); }
  catch (error) { results.push({name, ok: false, error: String(error)}); }
}
async function isolated(name, fn) {
  const id = await createSession();
  try { await record(name, () => fn(id)); }
  finally { await closeSession(id); }
}

const visible = [
  "Power","Mute","SETUP","SOURCE","▲","◀","▶","▼","OK",
  "EXIT","CH-LIST","Back","Home","Volume up","Volume down",
  "Channel up","Channel menu","Channel down","PLAY","BACK"
];

const main = await createSession();
try {
  for (const d of visible) await record("visible:" + d, () => clickDesc(main, d));
} finally { await closeSession(main); }

await isolated("quick:numeric keypad", async id => clickDesc(id, "Numeric keypad"));

const keypad = await createSession();
try {
  await clickDesc(keypad, "Numeric keypad");
  for (const k of ["Key 1","Key 2","Key 3","Key 4","Key 5","Key 6","Key 7","Key 8","Key 9","Key *","Key 0","Key #"]) {
    await record("numeric:" + k.slice(4), () => clickDesc(keypad, k));
  }
} finally { await closeSession(keypad); }

const nav = await createSession();
try {
  await record("navigation:Apps", async () => { await clickDesc(nav, "Apps"); if (!(await hasText(nav, "Apps"))) throw new Error("Apps page not visible"); });
  await record("navigation:Cast", async () => { await clickDesc(nav, "Cast"); if (!(await hasText(nav, "Cast"))) throw new Error("Cast page not visible"); });
  await record("navigation:Settings", async () => { await clickDesc(nav, "Settings"); if (!(await hasText(nav, "Settings"))) throw new Error("Settings page not visible"); });
  await record("navigation:Disconnect TV", async () => clickDesc(nav, "Disconnect TV"));
} finally { await closeSession(nav); }

await isolated("setup:opens settings", async id => {
  await clickDesc(id, "SETUP");
  if (!(await hasText(id, "Settings"))) throw new Error("SETUP did not open Settings");
});

for (const appName of ["YouTube","NETFLIX","prime video"]) {
  await isolated("shortcut:" + appName, async id => clickDesc(id, appName));
}

const failures = results.filter(x => !x.ok);
const report = {
  serial,
  totalChecks: results.length,
  passed: results.length - failures.length,
  failed: failures.length,
  allChecksPassed: failures.length === 0,
  results
};
fs.writeFileSync("ui-qa-report.json", JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
if (failures.length) process.exitCode = 1;
