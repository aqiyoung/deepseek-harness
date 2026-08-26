# DeepSeek Harness Android Changelog

## Unreleased

Sidebar (v1.0.88): root-cause fix for search overlap + right gap. The applyDrawerStyles inline style was forcing searchArea to width:100%, which made the search box span the full sidebar width and overlap the session list — now set to width:auto so the magnifying-glass icon sizes to its content. Removed speculative .qDHVXG_search/.qDHVXG_searchButton CSS rules whose hash-based class names may not match the live Web DOM. Added padding:0 + box-sizing:border-box to listArea so the session list flushes to the right edge.

Sidebar (v1.0.87): restore the search button — injects a flex-shrink:0 magnifying-glass container so the search icon always shows in the sidebar header instead of disappearing; the search input is no longer force-expanded on open, matching the web default (tap the icon to reveal the input). Session rows now span full sidebar width (width:100% + border-box) so the list flushes to the right edge with no empty margin. Section-header padding aligned to entries.

Sidebar search (v1.0.86): removed the forced auto-expand (doToggle no longer clicks the search button on open) and the CSS that forced display:block on the search input, so the sidebar now matches the web default — a magnifying-glass icon that expands to a search box only when tapped. Section-header padding aligned to entries (8px 14px) so the header row sits flush with the session list instead of being offset.

Immersive status bar (final): the window background is now themed — android:background #030303 in values-night, #FAFBFC in values — so with the already-transparent status bar the top area renders in the app canvas color instead of the system default white. Edge-to-edge stays enabled and content sits below the bar via systemBarsPadding(); selected session rows use --dsw-alias-interactive-bg-selected for dark-mode legibility.

Sidebar alignment fixes: entry gap reduced 12px→8px and right padding 16px→14px so session rows no longer feel too wide on the right; regionArea inner padding removed (0 8px→0) so entries control their own horizontal inset; search area padding aligned to entries (4px 14px) and search input height raised to 36px to match other controls; entry label text now explicitly uses --dsw-alias-label-primary so it stays legible in dark mode instead of inheriting a too-faint color.

Reverts the immersive status bar experiment: the transparent/edge-to-edge status bar pushed chat content behind the bar, so the status bar is restored to its default opaque behavior with systemBarsPadding() keeping content below it. Status bar icon color still follows the theme (light icons on dark, dark on light).

Settings page UI: adds a ← back button to the settings home page (was missing — only the system back handler worked), tightens the vertical spacing (8→4dp top, 10→6dp title gap, 18→12dp between sections, 24→14dp bottom) so the page no longer feels vertically sparse, and adds a 1.5dp shadow to DshSoftPanel containers for a raised/3D feel. The home page now uses the same ← back arrow as all sub-pages (was a ✕ close icon). Adds WebView forced-dark support (FORCE_DARK_AUTO) so web content follows the system dark theme instead of always rendering light.

Replaces the ugly Chrome default offline page (ERR_ADDRESS_UNREACHABLE) with a self-contained native overlay: centered card, animated ring icon, server address, dark/light theme, and a retry button that calls DshAppBridge.refreshPage(). Triggered from onReceivedError (network failures) and onReceivedHttpError (5xx). Also fixes the sidebar drawer open/close race (external-collapse safety net mis-firing during the adapter's own open flow) and makes doToggle survive a missing .hHd-Xa_toggle element.

Fixes a race where the external-collapse safety net mis-fired during the
sidebar's own open flow (styles applied then immediately cleared, leaving
the drawer stuck closed). Gates the check on an explicit ADAPTER_THINKS_OPEN
flag so only true external collapses (App/Esc folding the rail while the
adapter thinks it is open) trigger cleanup. Also makes doToggle survive a
missing .hHd-Xa_toggle element (backend hash churn) by falling back to
directly toggling the hHd-Xa_collapsed class.

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

Sidebar (v1.0.94): aggressive full-width fix — set width:100% + margin:0 + box-sizing:border-box on ALL elements inside the opened sidebar's regionArea via CSS !important, instead of relying on class-name substring selectors like [class*=listArea]/[class*=sessionRow] that may not match hash-based class names. Also applies width:100% inline to every direct child of regionArea and every <li> element inside it, as a fallback for elements the CSS selectors miss. SVG/input elements excluded from width:100% to preserve icon sizes.

Sidebar (v1.0.95): dark mode full-text color override — set color:#E4E4E7 on all sidebar descendants in dark mode (not just root), so running-tasks and session text are legible. Added sidebar root padding:0 + safe-area bottom padding to eliminate inherited left/right padding that could cause asymmetric gaps. Added dark-mode search icon color (#E4E4E7).

Sidebar (v1.0.96): restored New Session button (removed display:none override that hid .hHd-Xa_newSession). Removed aggressive width:100%+margin:0 CSS on all sidebar descendants and the JS-level regionChildren/allLIs forced-width loops — let Web layout render naturally inside the mobile full-width drawer. Session row CSS kept but scoped to actual sessionRow class only.

Sidebar (v1.0.97): hide search input box by default — CSS rule [class*=search] input { display:none } so the magnifying-glass icon shows instead of an expanded search field, matching Web behavior. Search area *:not(input) stays visible.

Sidebar (v1.0.98): narrowed search CSS from [class*=search] to [class*=searchButton]/[class*=searchIcon] with fixed 36px container + 24px icon + 20px SVG. sectionHeader now justify-content:space-between so search/options on right, title on left. Added dropdown menu CSS: [class*=dropdown]/[class*=popup]/[class*=popover]/[class*=contextMenu] set position:fixed z-index:10000 to escape sidebar overflow:hidden. Added menu option/item styling. Removed searchArea JS padding:4px 14px.
