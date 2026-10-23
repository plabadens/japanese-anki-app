{
  description = "Japanese Anki Card Creator Android build";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; config.allowUnfree = true; };
      jdk = pkgs.jdk17;
      robolectricJdk = pkgs.jdk21;
      gradle = pkgs.gradle_9;
      mecabSource = pkgs.fetchFromGitHub {
        owner = "taku910";
        repo = "mecab";
        rev = "5a7db65493a0b57d5fc31734e65300320aaf94c8";
        hash = "sha256-elB0Zr8DDkw3IZvvqVG+OBspZxFLPnvUSM9SRSILYWs=";
        rootDir = "mecab";
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
      robolectricAndroidAll = pkgs.fetchurl {
        url = "https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/17-robolectric-15733970-i7/android-all-instrumented-17-robolectric-15733970-i7.jar";
        hash = "sha256-MKFSQ7qaagFjYYdvrMdUMvTsXbD54n1JhKSmsWe0Mr0=";
      };
      robolectricDependencyDir = pkgs.runCommand "robolectric-android-all-api37" {} ''
        mkdir -p "$out"
        ln -s ${robolectricAndroidAll} "$out/android-all-instrumented-17-robolectric-15733970-i7.jar"
      '';
      cjkFontconfig = pkgs.makeFontsConf {
        fontDirectories = [ pkgs.noto-fonts-cjk-sans ];
      };
      androidEnv = pkgs.callPackage "${nixpkgs}/pkgs/development/mobile/androidenv" {
        inherit pkgs;
        licenseAccepted = true;
      };

      # Declared Android target: API 37 (Android 17) platform 37.0, build-tools 36.0.0
      # (the version app/build.gradle.kts pins), NDK 27.2, emulator 37.1.11 and the
      # API-37.0 google_apis x86_64 system image used for the v0.3 device acceptance.
      # Everything lives in a single SDK root so `nix develop` and the emulator wrapper
      # see the same, flake-declared SDK instead of a hand-provisioned one.
      android = androidEnv.composeAndroidPackages {
        platformVersions = [ "37.0" ];
        buildToolsVersions = [ "36.0.0" ];
        cmakeVersions = [ "4.1.2" ];
        includeNDK = true;
        ndkVersions = [ "27.2.12479018" ];
        includeEmulator = true;
        emulatorVersion = "37.1.11";
        includeSystemImages = true;
        systemImageTypes = [ "google_apis" ];
        abiVersions = [ "x86_64" ];
      };
      sdkRoot = "${android.androidsdk}/libexec/android-sdk";
      toolchainEnv = {
        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        ANDROID_NDK_HOME = "${sdkRoot}/ndk/27.2.12479018";
        MECAB_SOURCE_DIR = "${mecabSource}";
        ROBOLECTRIC_JAVA_HOME = "${robolectricJdk}";
        ROBOLECTRIC_DEPENDENCY_DIR = "${robolectricDependencyDir}";
        FONTCONFIG_FILE = "${cjkFontconfig}";
        GRADLE_OPTS = "-Dorg.gradle.native=false";
      };

      emulatorAdb = pkgs.writeShellApplication {
        name = "emulator-adb";
        runtimeInputs = [ pkgs.coreutils ];
        text = builtins.readFile ./scripts/emulator-adb;
      };

      emulatorWrapper = pkgs.writeShellApplication {
        name = "japanese-anki-emulator";
        runtimeInputs = [ pkgs.coreutils pkgs.gnugrep pkgs.gnused emulatorAdb ];
        text = builtins.readFile ./scripts/emulator.sh;
        runtimeEnv = {
          ANDROID_SDK_ROOT = sdkRoot;
          EMULATOR_ADB = "${emulatorAdb}/bin/emulator-adb";
        };
      };

      # Explicit APK/test inputs. `app/build` is deliberately excluded — only `app/src` is source,
      # and a checkout carrying Gradle outputs must not feed them into the sandbox build. These
      # three docs are test fixtures read by CopyCatalogueTest, so include them in the check source.
      apkSource = pkgs.lib.fileset.toSource {
        root = ./.;
        fileset = pkgs.lib.fileset.unions [
          ./app/build.gradle.kts
          ./app/src
          ./build.gradle.kts
          ./docs/card-flow-contract.md
          ./docs/legacy-copy.md
          ./docs/sentence-mining-flow.md
          ./gradle
          ./gradle.properties
          ./settings.gradle.kts
        ];
      };

      coreCheckSource = pkgs.lib.fileset.toSource {
        root = ./.;
        fileset = pkgs.lib.fileset.unions [
          ./app/src/main/assets/pitch_ajt.sqlite.gz
          ./app/src/main/assets/dictionary.sqlite.gz
          ./app/src/main/java
          ./app/src/test/java
          ./datasets/jmdict/JMdict_e.gz
          ./datasets/kanjium/accents.txt
          ./scripts/build_dictionary.py
          ./scripts/build_ajt_pitch.py
          ./scripts/package_unidic.py
          ./scripts/test-build-dictionary.py
          ./scripts/test-build-ajt-pitch.py
          ./scripts/test-ajt-pitch-asset.py
          ./scripts/test-core.sh
          ./scripts/test-dictionary-asset.py
          ./scripts/test-package-unidic.py
        ];
      };

      mkAndroidApk = { apkPath, outputName, task }:
        pkgs.stdenv.mkDerivation (finalAttrs: {
          pname = "japanese-anki-card-creator";
          version = "0.1.0";
          src = apkSource;

          nativeBuildInputs = [ jdk robolectricJdk gradle ];
          buildInputs = [ android.androidsdk ];
          env = toolchainEnv // {
            JAVA_HOME = "${jdk}";
          };

          gradleBuildTask = task;
          gradleCheckTask = "verifyFast";
          gradleFlags = [
            "--max-workers=1"
            "-Pandroid.aapt2FromMavenOverride=${sdkRoot}/build-tools/36.0.0/aapt2"
            "-Dorg.gradle.java.home=${jdk}"
          ];
          doCheck = true;

          mitmCache = gradle.fetchDeps {
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
        apkPath = "app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk";
        outputName = "japanese-anki-0.4-arm64-v8a-unsigned.apk";
      };
      debugApk = mkAndroidApk {
        task = "assembleDebug";
        apkPath = "app/build/outputs/apk/debug/app-x86_64-debug.apk";
        outputName = "japanese-anki-0.4-x86_64-debug.apk";
      };
      coreTests = pkgs.runCommand "japanese-anki-core-tests" {
        nativeBuildInputs = [ jdk gradle pkgs.python3 ];
      } ''
        cp -r ${coreCheckSource} source
        chmod -R u+w source
        cd source
        python3 scripts/test-build-dictionary.py
        python3 scripts/test-build-ajt-pitch.py
        python3 scripts/test-ajt-pitch-asset.py
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
        unidicRuntime = unidicRuntime;
        emulator-wrapper = emulatorWrapper;
      };

      checks.${system}.core = coreTests;

      devShells.${system}.default = pkgs.mkShell (toolchainEnv // {
        packages = [ jdk robolectricJdk gradle android.androidsdk android.platform-tools pkgs.git pkgs.python3 pkgs.noto-fonts-cjk-sans ];
        EMULATOR_ADB = "${emulatorAdb}/bin/emulator-adb";
        JAVA_HOME = jdk.home;
        LANG = "C.UTF-8";
        LC_ALL = "C.UTF-8";
        shellHook = ''
          export PATH="$ANDROID_HOME/platform-tools:$PATH"
          printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
        '';
      });
    };
}
