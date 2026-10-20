{
  description = "Japanese Anki Card Creator Android build";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; config.allowUnfree = true; };
      jdk = pkgs.jdk17;
      mecabSource = pkgs.fetchFromGitHub {
        owner = "taku910";
        repo = "mecab";
        rev = "5a7db65493a0b57d5fc31734e65300320aaf94c8";
        hash = "sha256-elB0Zr8DDkw3IZvvqVG+OBspZxFLPnvUSM9SRSILYWs=";
        rootDir = "mecab";
      };
      tessdataJpn = pkgs.fetchurl {
        url = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/80d92b7db61cb6ddc519049990be34e4c913566f/jpn.traineddata";
        hash = "sha256-H13pI20uhfX99LPFAPLUkm+NlEnyj1OURy2ejYO5G00=";
        name = "jpn.traineddata";
      };
      tessdataJpnVert = pkgs.fetchurl {
        url = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/923915d4ced2a7235221788285785a29c4a42d4a/jpn_vert.traineddata";
        hash = "sha256-vx4mQJVGkXl+LcFPOFM+YBtZ7jeVhpiuDwuB3G8Jxxs=";
        name = "jpn_vert.traineddata";
      };
      unidicArchive = pkgs.fetchurl {
        url = "https://clrd.ninjal.ac.jp/unidic_archive/cwj/3.1.1/unidic-cwj-3.1.1.zip";
        hash = "sha256-b1R6PnFWOaPgF4oi35QiOhR9lazrESpZDy1oTGRK0xA=";
      };
      unidicRuntime = pkgs.runCommand "unidic-cwj-3.1.1-runtime.zip" {
        nativeBuildInputs = [ pkgs.python3 ];
      } ''
        python3 ${./scripts/package_unidic.py} --archive ${unidicArchive} --output "$out"
      '';
      androidEnv = pkgs.callPackage "${nixpkgs}/pkgs/development/mobile/androidenv" {
        inherit pkgs;
        licenseAccepted = true;
      };
      android = androidEnv.composeAndroidPackages {
        platformVersions = [ "35" "37" ];
        buildToolsVersions = [ "36.0.0" ];
        cmakeVersions = [ "4.1.2" ];
        includeNDK = true;
        ndkVersions = [ "27.2.12479018" ];
        includeEmulator = false;
        includeSystemImages = false;
      };
      emulatorSdk = androidEnv.composeAndroidPackages {
        platformVersions = [ "35" ];
        systemImageTypes = [ "google_apis" ];
        abiVersions = [ "x86_64" ];
        includeEmulator = true;
        includeSystemImages = true;
        includeNDK = false;
      };
      emulatorSdk37 = androidEnv.composeAndroidPackages {
        platformVersions = [ "37" ];
        systemImageTypes = [ "google_apis" ];
        abiVersions = [ "x86_64" ];
        includeEmulator = true;
        includeSystemImages = true;
        includeNDK = false;
      };
      emulatorPackage37 = androidEnv.emulateApp {
        name = "japanese-anki-emulator-api37";
        platformVersion = "37";
        abiVersion = "x86_64";
        systemImageType = "google_apis";
        sdkExtraArgs = {
          includeEmulator = true;
          includeSystemImages = true;
          includeNDK = false;
        };
      };
      sdkRoot = "${android.androidsdk}/libexec/android-sdk";
      emulatorSdkRoot = "${emulatorSdk.androidsdk}/libexec/android-sdk";
      emulatorPackage = androidEnv.emulateApp {
        name = "japanese-anki-emulator";
        platformVersion = "35";
        abiVersion = "x86_64";
        systemImageType = "google_apis";
        sdkExtraArgs = {
          includeEmulator = true;
          includeSystemImages = true;
          includeNDK = false;
        };
      };
      emulatorWrapper = pkgs.writeShellApplication {
        name = "japanese-anki-emulator";
        runtimeInputs = [ pkgs.coreutils pkgs.gnugrep pkgs.gnused ];
        text = builtins.readFile ./scripts/emulator.sh;
        runtimeEnv = {
          ANDROID_SDK_ROOT = emulatorSdkRoot;
          ADB_SERVER_SOCKET = "localfilesystem:/home/pierre/Development/japanese-anki-app/.tmp-nix/adb.sock";
        };
      };

      mkAndroidApk = { apkPath, outputName, task }:
        pkgs.stdenv.mkDerivation (finalAttrs: {
          pname = "japanese-anki-card-creator";
          version = "0.1.0";
          src = self;

          nativeBuildInputs = [ jdk pkgs.gradle ];
          buildInputs = [ android.androidsdk ];
          env = {
            JAVA_HOME = "${jdk}";
            ANDROID_HOME = sdkRoot;
            ANDROID_SDK_ROOT = sdkRoot;
            ANDROID_NDK_HOME = "${sdkRoot}/ndk/27.2.12479018";
            MECAB_SOURCE_DIR = "${mecabSource}";
            UNIDIC_RUNTIME_ARCHIVE = "${unidicRuntime}";
            TESSDATA_JPN = "${tessdataJpn}";
            TESSDATA_JPN_VERT = "${tessdataJpnVert}";
            GRADLE_OPTS = "-Dorg.gradle.native=false";
          };

          gradleBuildTask = task;
          gradleCheckTask = "testDebugUnitTest";
          gradleFlags = [
            "-Pandroid.aapt2FromMavenOverride=${sdkRoot}/build-tools/36.0.0/aapt2"
            "-Dorg.gradle.java.home=${jdk}"
          ];
          doCheck = true;

          mitmCache = pkgs.gradle.fetchDeps {
            inherit (finalAttrs) pname;
            pkg = finalAttrs.finalPackage;
            data = ./nix/gradle/deps.json;
            silent = false;
            useBwrap = false;
          };

          installPhase = ''
            install -D -m 0644 "${apkPath}" "$out/${outputName}"
          '';

          meta = {
            description = "Japanese Anki Card Creator Android APK";
            platforms = [ system ];
          };
        });

      releaseApk = mkAndroidApk {
        task = "assembleRelease";
        apkPath = "app/build/outputs/apk/release/app-release-unsigned.apk";
        outputName = "japanese-anki-unsigned.apk";
      };
      debugApk = mkAndroidApk {
        task = "assembleDebug";
        apkPath = "app/build/outputs/apk/debug/app-debug.apk";
        outputName = "japanese-anki-debug.apk";
      };
      coreTests = pkgs.runCommand "japanese-anki-core-tests" {
        nativeBuildInputs = [ jdk pkgs.gradle pkgs.python3 ];
      } ''
        cp -r ${self} source
        chmod -R u+w source
        cd source
        python3 scripts/test-build-dictionary.py
        python3 scripts/test-dictionary-asset.py
        python3 scripts/test-package-unidic.py
        bash scripts/test-core.sh
        mkdir -p "$out"
        printf 'Core tests passed.\n' > "$out/result"
      '';
    in {
      packages.${system} = {
        default = releaseApk;
        apk = releaseApk;
        debugApk = debugApk;
        emulator = emulatorPackage;
        emulator-api37 = emulatorPackage37;
        emulator-wrapper = emulatorWrapper;
      };

      checks.${system}.core = coreTests;

      devShells.${system}.default = pkgs.mkShell {
        packages = [ jdk pkgs.gradle android.androidsdk android.platform-tools pkgs.git pkgs.python3 ];
        JAVA_HOME = jdk.home;
        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        MECAB_SOURCE_DIR = "${mecabSource}";
        UNIDIC_RUNTIME_ARCHIVE = "${unidicRuntime}";
        TESSDATA_JPN = "${tessdataJpn}";
        TESSDATA_JPN_VERT = "${tessdataJpnVert}";
        GRADLE_OPTS = "-Dorg.gradle.native=false";
        LANG = "C.UTF-8";
        LC_ALL = "C.UTF-8";
        shellHook = ''
          export PATH="$ANDROID_HOME/platform-tools:$PATH"
          printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
        '';
      };
    };
}
