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

## Token storage

MSAL's serialized token cache is stored under VoxyQuest's private Android app files directory in `auth/msal_cache.json`. Minecraft account data is stored in the app-private `accounts/` directory. Tokens are not exposed to GDScript or shown in the launcher UI.

Never commit token-cache files, account JSON files, refresh/access tokens, signing keys, or Microsoft client secrets to Git.

## Launcher UI

The Godot launcher is deliberately a normal 2D Android/Quest application. It does not start OpenXR. The sign-in page can:

- start Microsoft device-code login;
- display the code and verification URL;
- open the verification URL in the system browser;
- copy the user code;
- show Microsoft/Xbox/Minecraft authentication progress;
- show the signed-in Minecraft username and UUID.

Minecraft VR/OpenXR startup belongs to the game runtime, not to the Godot launcher.
