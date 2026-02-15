# Runtime APK Packaging

This project supports direct runtime packaging for a hosted web app URL.

## Prebuilt template APK

A prebuilt runtime template APK is available from workflow run `22029116211` and downloaded locally to:

```text
.tmp/runtime-template/Runtime-template-v1.0.0/runtime-release.apk
```

## Local one-line packaging

```powershell
gradle :runtime:assembleRelease `
  -PRUNTIME_APP_NAME="My App" `
  -PRUNTIME_APP_URL="https://example.vercel.app/" `
  -PRUNTIME_PACKAGE_SUFFIX="my_app" `
  -PRUNTIME_VERSION_NAME="1.0.0" `
  -PRUNTIME_VERSION_CODE="1"
```

Output APK path:

```text
runtime/build/outputs/apk/release/
```

You can also use the helper script:

```powershell
.\_ci\package-runtime.ps1 `
  -AppName "My App" `
  -AppUrl "https://example.vercel.app/" `
  -PackageSuffix "my_app" `
  -VersionName "1.0.0" `
  -VersionCode 1
```

## Local bridge mode (used by APP developer auto packaging)

Start the local bridge:

```powershell
.\_ci\runtime-packager-bridge.ps1 `
  -BindAddress "127.0.0.1" `
  -Port 17856 `
  -AuthToken "<optional-token>"
```

Then set app build config inputs:

```text
RUNTIME_PACKAGER_BASE_URL=http://127.0.0.1:17856
RUNTIME_PACKAGER_TOKEN=<optional-token>
```

The app will call:

- `POST /v1/runtime/builds`
- `GET /v1/runtime/builds/{requestId}`
- `GET /v1/runtime/builds/{requestId}/artifact`

When APP developer triggers packaging, the request now declares:

- `runtimeTemplate: "builtin_shell_plugin"`
- `runtimeShellPackage`
- `runtimeShellDownloadUrl`

`runtimeShellPackage` is validated by the bridge in `builtin_shell_plugin` mode.
Users only need to download this Runtime shell template from Apps before APP developer can run packaging. Installation is not required.
The local packager signs generated APKs automatically:

- If `SIGNING_*` variables are provided, it uses your signing config.
- Otherwise it creates/uses a local debug-style keystore in `.tmp/runtime-signing/`.

## GitHub Actions packaging

Use workflow **Package Runtime APK** (`.github/workflows/runtime-packager.yml`) with:

- `app_name`
- `app_url`
- `package_suffix`
- optional `version_name`
- optional `version_code`

The workflow uploads an artifact containing the packaged runtime APK.

## Notes

- `app_url` should be a stable HTTPS URL (for example, deployed by Vercel).
- `package_suffix` should be unique if you want to install multiple runtime APKs side-by-side.
