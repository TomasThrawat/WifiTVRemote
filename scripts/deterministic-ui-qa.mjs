import fs from "node:fs";
import { execFileSync } from "node:child_process";

const base = "http://127.0.0.1:4723";
const appPackage = "com.tomasthrawat.wifitvremote";
const appPath = process.cwd() + "/wifi-tv-remote.apk";
const results = [];

async function req(path, init = {}) {
  const r = await fetch(base + path, {
    headers: {"content-type":"application/json", ...(init.headers || {})},
    ...init
  });
  const body = await r.json();
  if (!r.ok || (body.value && body.value.error)) {
    throw new Error(JSON.stringify(body));
  }
  return body;
}

async function createSession() {
  const body = await req("/session", {
    method:"POST",
    body:JSON.stringify({
      capabilities:{
        alwaysMatch:{
          platformName:"Android",
          "appium:automationName":"UiAutomator2",
          "appium:deviceName":"Android Emulator",
          "appium:app":appPath,
          "appium:appPackage":appPackage,
          "appium:noReset":false,
          "appium:autoGrantPermissions":true,
          "appium:newCommandTimeout":120
        },
        firstMatch:[{}]
      }
    })
  });
  return body.value?.sessionId || body.sessionId;
}

async function closeSession(id) {
  try { await req("/session/"+encodeURIComponent(id), {method:"DELETE"}); } catch {}
}

async function source(id) {
  return (await req("/session/"+encodeURIComponent(id)+"/source")).value;
}

async function screenshot(id, path) {
  const body = await req("/session/"+encodeURIComponent(id)+"/screenshot");
  fs.writeFileSync(path, Buffer.from(body.value, "base64"));
}

async function find(id, using, value) {
  return (await req("/session/"+encodeURIComponent(id)+"/element", {
    method:"POST",
    body:JSON.stringify({using,value})
  })).value;
}

async function clickElement(id, raw) {
  const el = raw["element-6066-11e4-a52e-4f735466cecf"] || raw.ELEMENT || raw;
  await req("/session/"+encodeURIComponent(id)+"/element/"+encodeURIComponent(el)+"/click", {
    method:"POST",
    body:"{}"
  });
}

async function findAny(id, label) {
  const escaped = label.replace(/"/g,'\\\"');
  const selectors = [
    ["accessibility id", label],
    ["-android uiautomator", 'new UiSelector().description("' + escaped + '")'],
    ["-android uiautomator", 'new UiSelector().text("' + escaped + '")'],
    ["xpath", '//*[@text="' + escaped + '"]'],
    ["xpath", '//*[@content-desc="' + escaped + '"]']
  ];
  let last;
  for (const [using, value] of selectors) {
    try { return await find(id, using, value); } catch (e) { last = e; }
  }
  throw last || new Error("Not found: " + label);
}

async function visible(id, label) {
  const xml = await source(id);
  return xml.includes('text="' + label + '"') || xml.includes('content-desc="' + label + '"');
}

async function testOne(name, fn) {
  try {
    await fn();
    results.push({name, ok:true});
  } catch (e) {
    results.push({name, ok:false, error:String(e)});
  }
}

async function isolated(name, fn) {
  const id = await createSession();
  try { await testOne(name, () => fn(id)); }
  finally { await closeSession(id); }
}

async function resetApp(id) {
  await req("/session/"+encodeURIComponent(id)+"/appium/device/terminate_app", {
    method:"POST", body:JSON.stringify({appId:appPackage})
  }).catch(() => {});
  await req("/session/"+encodeURIComponent(id)+"/appium/device/activate_app", {
    method:"POST", body:JSON.stringify({appId:appPackage})
  });
}

const main = await createSession();
try {
  await screenshot(main, "ui-dark.png");
  fs.writeFileSync("ui-source.xml", await source(main));

  const mainLabels = [
    "Power","Mute","SOURCE","▲","◀","▶","▼","OK",
    "EXIT","CH-LIST","Back","Home","PLAY","BACK",
    "Volume up","Volume down","Channel up","Channel menu","Channel down",
    "Numeric keypad"
  ];

  for (const label of mainLabels) {
    await testOne("main:" + label, async () => {
      if (!(await visible(main, label))) throw new Error("Not visible: " + label);
      await clickElement(main, await findAny(main, label));
    });
  }
} finally {
  await closeSession(main);
}

for (const label of ["SETUP"]) {
  await isolated("flow:" + label + " opens Settings", async id => {
    await clickElement(id, await findAny(id, label));
    if (!(await visible(id, "Settings"))) throw new Error("Settings page did not open");
  });
}

await isolated("flow:numeric keypad opens", async id => {
  await clickElement(id, await findAny(id, "Numeric keypad"));
  if (!(await visible(id, "Numeric keypad panel"))) throw new Error("Numeric keypad panel did not open");
});

const numeric = await createSession();
try {
  await clickElement(numeric, await findAny(numeric, "Numeric keypad"));
  for (const k of ["Key 1","Key 2","Key 3","Key 4","Key 5","Key 6","Key 7","Key 8","Key 9","Key *","Key 0","Key #"]) {
    await testOne("numeric:" + k.slice(4), async () => clickElement(numeric, await findAny(numeric, k)));
  }
  await testOne("numeric:Close", async () => clickElement(numeric, await findAny(numeric, "Close numeric keypad")));
} finally {
  await closeSession(numeric);
}

for (const label of ["Remote","Apps","Cast","Settings"]) {
  await isolated("navigation:" + label, async id => {
    await clickElement(id, await findAny(id, label));
    if (!(await visible(id, label))) throw new Error("Page not visible: " + label);
  });
}

await isolated("navigation:Disconnect TV", async id => {
  await clickElement(id, await findAny(id, "Settings"));
  await clickElement(id, await findAny(id, "Disconnect TV"));
});

for (const label of ["YouTube","NETFLIX","prime video"]) {
  await isolated("shortcut:" + label, async id => {
    await clickElement(id, await findAny(id, label));
  });
}

execFileSync("adb", ["shell","cmd","uimode","night","no"], {stdio:"ignore"});
const light = await createSession();
try {
  await screenshot(light, "ui-light.png");
} finally {
  await closeSession(light);
}

const failed = results.filter(x => !x.ok);
const report = {
  totalChecks: results.length,
  passed: results.length - failed.length,
  failed: failed.length,
  allChecksPassed: failed.length === 0,
  results
};
fs.writeFileSync("ui-qa-report.json", JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
if (failed.length) process.exitCode = 1;
