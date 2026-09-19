# Microsoft Login

VoxyQuest uses Microsoft device-code authentication for the launcher account flow.

The flow is:

```text
Godot 2D launcher
  -> VoxyQuestBridge
  -> MSAL4J device-code login
  -> Xbox Live authentication
  -> XSTS
  -> Minecraft Services
  -> Minecraft Java profile
```

## VoxyQuest needs its own Microsoft application ID

Do not use QuestCraft's Microsoft application/client ID. VoxyQuest is a separate application and must use its own Microsoft Entra application registration.

For the VoxyQuest registration:

1. Create a Microsoft Entra app registration that supports Microsoft personal accounts.
2. Enable public client flows for the application.
3. Record the Application (client) ID.
4. Do not create or commit a client secret. VoxyQuest is a public/native client and device-code flow does not use an application secret.

Microsoft's device-code flow is designed for input-constrained devices: the launcher displays a user code and verification URL, then receives the token after the user completes Microsoft sign-in.

## Build configuration

Supply the VoxyQuest client ID when building the Android bridge:

```bash
VOXYQUEST_MICROSOFT_CLIENT_ID="your-client-id" \
  gradle -p android/bridge :plugin:syncToGodot
```

or:

```bash
gradle -p android/bridge \
  -PvoxyquestMicrosoftClientId="your-client-id" \
  :plugin:syncToGodot
```

The client ID is compiled into `VoxyQuestBridge`. A Microsoft client ID identifies the public application; it is not an application secret.

If no client ID is provided, VoxyQuest still builds successfully but the Microsoft sign-in button is disabled and explains that configuration is missing.

## Token storage and session restoration

MSAL's serialized token cache is stored under VoxyQuest's private Android app files directory in `auth/msal_cache.json`. Minecraft account data is stored in the app-private `accounts/` directory. Tokens are not exposed to GDScript or shown in the launcher UI.

Token-cache updates are written through a temporary file and replaced atomically when the filesystem supports it. This reduces the chance of a partial cache if the app is interrupted while credentials are being persisted.

At launcher startup, VoxyQuest restores the most recently used Minecraft profile when its cached token is still valid. If the Minecraft token is expired, VoxyQuest attempts a silent Microsoft refresh from the MSAL cache on a background thread. If that refresh is unavailable or expired, the launcher returns to the normal sign-in state instead of blocking startup.

Never commit token-cache files, account JSON files, refresh/access tokens, signing keys, or Microsoft client secrets to Git.

## Launcher UI

The Godot launcher is deliberately a normal 2D Android/Quest application. It does not start OpenXR. The sign-in page can:

- start Microsoft device-code login;
- display the code and verification URL;
- open the verification URL in the system browser;
- copy the user code;
- show Microsoft/Xbox/Minecraft authentication progress;
- restore a cached account without keeping the launcher in a permanent polling loop;
- show the signed-in Minecraft username and UUID.

Minecraft VR/OpenXR startup belongs to the game runtime, not to the Godot launcher.

Clicking the home account card now requests a device code and opens the system browser once that fresh code is available. On Quest, Android routes this to the headset's browser. The account window keeps the user code and address visible and has Copy Code and Open Microsoft buttons for manual entry or retry. Background authentication stays in Pojlib while the browser is open. Cancelling prevents a delayed automatic browser open.

For GitHub Actions builds, set the repository Actions variable `VOXYQUEST_MICROSOFT_CLIENT_ID` to VoxyQuest's registered public client ID. The workflow forwards it to the bridge build. It must support Microsoft personal accounts and public client/device-code authentication. No client secret or QuestCraft application ID is used. Actual Microsoft/Xbox/Minecraft authorization still depends on this application's registration and service access; UI tests cannot certify a live login.
