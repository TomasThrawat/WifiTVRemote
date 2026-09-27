import fs from "node:fs";

const serial = process.argv[2];
if (!serial) throw new Error("Android serial is required");
const base = "http://127.0.0.1:4723";
const app = "app/build/outputs/apk/debug/app-debug.apk";

async function request(path, init = {}) {
  const r = await fetch(base + path, {headers: {"content-type":"application/json"}, ...init});
  const body = await r.json();
  if (!r.ok || (body.value && body.value.error)) throw new Error(JSON.stringify(body));
  return body;
}
async function createSession() {
  const body = await request("/session", {
    method:"POST",
    body:JSON.stringify({capabilities:{alwaysMatch:{
      platformName:"Android",
      "appium:automationName":"UiAutomator2",
      "appium:deviceName":serial,
      "appium:udid":serial,
      "appium:app":process.cwd()+"/"+app,
      "appium:autoGrantPermissions":true,
      "appium:noReset":false
    },firstMatch:[{}]}})
  });
  return body.value.sessionId || body.sessionId;
}
async function source(id) { return (await request("/session/"+id+"/source")).value; }
async function click(id, elementId) { await request("/session/"+id+"/element/"+encodeURIComponent(elementId)+"/click",{method:"POST",body:"{}"}); }
async function find(id, using, value) {
  const b=await request("/session/"+id+"/element",{method:"POST",body:JSON.stringify({using,value})});
  return b.value;
}
async function close(id) { try { await request("/session/"+id,{method:"DELETE"}); } catch {} }

const first = await createSession();
const xml = await source(first);
await close(first);

const nodes = [];
const re = /<([A-Za-z0-9_.$:-]+)([^>]*?clickable="true"[^>]*)>/g;
let m;
while ((m=re.exec(xml))) {
  const attrs={};
  for (const a of m[2].matchAll(/([\w:-]+)="([^"]*)"/g)) attrs[a[1]]=a[2];
  const key=attrs["resource-id"]||attrs["content-desc"]||attrs.text||attrs["class"]+"@"+attrs.bounds;
  if (key && !nodes.some(n=>n.key===key)) nodes.push({key,attrs});
}

const results=[];
for (const n of nodes) {
  const id=await createSession();
  try {
    let using, value;
    if (n.attrs["resource-id"]) { using="id"; value=n.attrs["resource-id"]; }
    else if (n.attrs["content-desc"]) { using="accessibility id"; value=n.attrs["content-desc"]; }
    else if (n.attrs.text) { using="xpath"; value='//*[@text='+JSON.stringify(n.attrs.text)+']'; }
    else { using="xpath"; value='//*[@bounds='+JSON.stringify(n.attrs.bounds||"")+']'; }
    const el=await find(id,using,value);
    const elementId=el["element-6066-11e4-a52e-4f735466cecf"]||el.ELEMENT||el;
    await click(id,elementId);
    results.push({key:n.key,selector:{using,value},ok:true});
  } catch (e) {
    results.push({key:n.key,ok:false,error:String(e)});
  } finally { await close(id); }
}

const report={serial,clickableCount:nodes.length,results,allClickableActionsSucceeded:results.length>0&&results.every(x=>x.ok)};
fs.writeFileSync("ui-qa-report.json",JSON.stringify(report,null,2));
if (!report.allClickableActionsSucceeded) process.exitCode=1;
