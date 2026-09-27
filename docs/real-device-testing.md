# Real-device Android TV testing

This repository keeps the production app independent from any testing vendor. Real-device testing is an optional verification layer.

## Recommended stack

- **zebrunner/mcloud**: self-hosted device-farm infrastructure for physical Android/iOS devices including TVs, with Appium Grid, parallel execution, browser-based manual device access, video recording, screenshots, and device artifacts.
- **callstack/agent-device**: UI/agent verification for Android TV and physical Android devices, with semantic UI interaction, snapshots/screenshots, video, logs/traces, and parallel session coordination.
- **Vercel BrowserStack bridge**: the existing Vercel project `browserstack-vercel-mcp-bridge` provides the MCP transport endpoint used to reach the BrowserStack integration when its connected credentials are available.

The recommended architecture is:

mcloud -> physical device allocation and Appium infrastructure
agent-device -> UI inspection, interaction, verification, and evidence
Vercel bridge -> BrowserStack MCP transport when BrowserStack is authenticated

## Vercel bridge

Current production deployment:

`https://browserstack-vercel-mcp-bridge-5hohm9xw4-hyouka1.vercel.app`

MCP endpoint:

`https://browserstack-vercel-mcp-bridge-5hohm9xw4-hyouka1.vercel.app/api/mcp`

The Vercel deployment was verified through BrowserUse on 2026-09-27 and the `/api/mcp` endpoint identified itself as the `browserstack-vercel-mcp-bridge` MCP server.

The root URL is API-only and returns 404 by design. The MCP endpoint is the relevant route.

## agent-device quick start

Install the CLI:

```bash
npm install -g agent-device@latest
```

Launch the WifiTVRemote application on a provisioned Android TV target:

```bash
agent-device open com.tomasthrawat.wifitvremote --platform android --target tv
```

Capture an interactive UI snapshot:

```bash
agent-device snapshot -i
```

List button-like controls before writing targeted assertions:

```bash
agent-device find role button list
```

Shut down the session when finished:

```bash
agent-device shutdown
```

## CI configuration

The manual workflow `.github/workflows/real-device-agent-device.yml` is intentionally separate from the normal APK build.

Configure the agent-device connection through GitHub Actions secrets:

- `AGENT_DEVICE_DAEMON_BASE_URL` or `AGENT_DEVICE_CLOUD_BASE_URL`
- `AGENT_DEVICE_DAEMON_AUTH_TOKEN` when the daemon requires authentication
- `AGENT_DEVICE_ANDROID_DEVICE_ALLOWLIST` when a specific device/TV should be selected

For parallel CI sessions, also configure:

- `AGENT_DEVICE_SESSION`
- `AGENT_DEVICE_SESSION_LOCK`

The workflow builds the native APK first, then launches the already provisioned app on the real-device environment, captures an interactive UI snapshot, verifies that remote-oriented controls are present, and stores the evidence as a GitHub Actions artifact.

## Separation from production

The Android application does not need an Appium dependency, a device-farm SDK, or a cloud-provider runtime. The testing stack remains outside the APK.

This keeps the production app's networking and UI implementation unchanged while allowing the real-device lab to evolve independently.

## Verification notes

BrowserUse verification of the current public documentation confirmed that mcloud supports physical TVs, Appium Grid, parallel execution, browser-based manual device access, video recording, and artifacts.

BrowserUse verification also confirmed that agent-device supports Android TV targets, physical Android devices, semantic UI interaction, snapshots/screenshots, video, logs/traces, and multi-device session coordination.

The Vercel BrowserStack bridge production deployment is `READY`. Its `/api/mcp` endpoint is reachable and identified itself as an MCP server.

The connected direct BrowserStack upload operation returned HTTP 403 during the verification run, and the BrowserUse BrowserStack dashboard session was not authenticated. BrowserStack is therefore kept optional rather than made a hard dependency of the repository workflow.
