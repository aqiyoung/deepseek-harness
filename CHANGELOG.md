# DeepSeek Harness Android Changelog

## Unreleased

Adds an external-collapse safety net for the sidebar: if the app or keyboard Esc toggles the rail closed outside the adapter's own toggle path, open-state inline styles would leak and the drawer would stick open on top of the content (CSS can't override inline !important). The merged shell observer now detects this mismatch on each DOM change and clears the styles. Removes a stale comment about the gear icon being in the logo row.

## v1.0.75 - 2026-08-24

Repackages the v1.0.74 baseline as a clean release build. Includes the redesigned sidebar drawer settings entry (SVG gear + chevron, theme-variable colors, gesture-bar safe-area padding), merged DOM observers, plugin manifest parsing hardening, and the associated unit test.

Fixes the model-picker menu losing its background/border/shadow (orphaned CSS fragment) and the sidebar drawer sticking open after opening native settings from its bottom entry (collapse now goes through the adapter so inline styles are restored). Redesigns that entry: SVG gear + chevron, theme-variable colors, gesture-bar safe-area padding, merged DOM observers for less main-thread churn.


Recovers gracefully from the 12-hour server session expiry: expired sessions no longer surface as "response parse failed" in native settings (the nginx login redirect is detected, not followed), errors carry actionable messages with RPC codes, and the app automatically returns to the login screen instead of appearing logged-in while every request fails.

Hardens credential handling end-to-end: switching servers clears the previous session cookie before it can leak cross-origin, server URLs must be https, intent:// navigation is sanitized, and degraded encrypted storage never persists secrets in plaintext.

Fixes stale session reuse after logout or server switch, concurrent duplicate session creation, control-character handling in login requests, false session-expiry detection, lost file-picker callbacks, WebView background playback drain, empty licenses page, language switching not applying, fake connection status, and JS adapter polling/observer overhead.


## 2026.7.4 - 2026-07-30

Adds inline audio/video playback and uploads, session dashboards, run telemetry, chat rewind/fork, a Settings repair assistant, and Wear instant Talk.

Improves the working agent, collapsible details, Skill Workshop flows, and generated images.

Fixes reconnect/session state, Talk transcripts, manual gateway ports, large-text onboarding, reduced motion, and Wear pairing/reply reliability.

Thanks @IWhatsskill, @NianJiuZst, @masatohoshino, @cygnostik, @licheer-zte, and @metaforismo.

## 2026.7.3 - 2026-07-20

Adds a Wear OS companion for sessions, transcripts, text and voice replies, realtime Talk, Gateway controls, notifications, settings, and a launch Tile.

Adds foreground, on-device Voice Wake with editable Gateway-synced wake words, plus copy and save-as-PNG actions for rendered chat widgets.

Fixes composer media leaking across chats and malformed agent or profile initials when display names begin with emoji.

Thanks @sibbl, @IWhatsskill, and @Leon-SK668.

## 2026.7.2 - 2026-07-13

Adds Automations and Skills management with search, filters, editing, run tracking, install safety, and DeepSeek Harness Hub risk review.

Improves chat with per-device history, durable approval status, session search, sharing, and agent avatars.

Adds provider model details, build identity, safer permission recovery, fresh Installed Apps consent, and Gateway protocol v3/v4 support.

Thanks @snowzlmbot, @IWhatsskill, @NianJiuZst, and @guarismo.

## 2026.7.1 - 2026-07-08

Adds multi-gateway switching with isolated credentials, history, queues, and notification routing.

Upgrades chat with offline recovery, session search and groups, model and agent pickers, voice notes, actions, link previews, code and math rendering.

Adds workspace files, Cron details, terminal access, and Listen playback.

Improves onboarding, reconnects, keyboards, notification filtering, location, canvas safety, and voice reliability.

Thanks @IWhatsskill, @ioridev, and @narcissus0702.

## 2026.6.11 - 2026-07-01

Improves Android gateway setup with localized onboarding, QR pairing fixes, and support for local mDNS gateway hosts.

Adds clearer recovery guidance for TLS fingerprint timeouts, mobile protocol mismatches, and gateway auth states.

Refreshes native Android localization coverage, including Swedish app naming and localized gateway trust flows.

## 2026.6.2 - 2026-06-02

DeepSeek Harness is now available on Android.

Connect to your DeepSeek Harness Gateway to chat with your assistant, use realtime Talk mode, review approvals, and bring Android device capabilities like camera, location, screen, and notifications into your private automation workflows.
