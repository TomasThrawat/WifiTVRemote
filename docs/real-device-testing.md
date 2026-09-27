# Real-device Android TV testing

This repository keeps the production app independent from any testing vendor. Real-device testing is an optional verification layer.

## Recommended stack

- **zebrunner/mcloud**: self-hosted device-farm infrastructure for physical Android/iOS devices including TVs, with Appium Grid, parallel execution, browser-based manual device access, video recording, screenshots, and device artifacts.
- **callstack/agent-device**: UI/agent verification for Android TV and physical Android devices, with semantic UI interaction, snapshots/screenshots, video, logs/traces, and parallel session coordination.

The recommended architecture is:

mcloud -> physical device allocation and Appium infrastructure
agent-device -> UI inspection, interaction, verification, and evidence

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

The connected BrowserStack upload endpoint returned HTTP 403 during the verification run, and a BrowserUse session opened to the BrowserStack dashboard was not authenticated. BrowserStack is therefore kept optional rather than made a hard dependency of this repository workflow.
