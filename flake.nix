{
  description = "Japanese Anki Card Creator Android build environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; config.allowUnfree = true; };
      jdk = pkgs.jdk17;
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
        includeNDK = true;
        ndkVersions = [ "27.2.12479018" ];
        includeEmulator = false;
        includeSystemImages = false;
      };
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = [ jdk pkgs.gradle android.androidsdk android.platform-tools pkgs.git pkgs.python3 ];
        JAVA_HOME = jdk.home;
        ANDROID_HOME = "${android.androidsdk}/libexec/android-sdk";
        ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
        # Gradle's Java fallback avoids native-service failures in restricted build containers.
        GRADLE_OPTS = "-Dorg.gradle.native=false";
        UNIDIC_RUNTIME_ARCHIVE = "${unidicRuntime}";
        LANG = "C.UTF-8";
        LC_ALL = "C.UTF-8";
        shellHook = ''
          export PATH="$ANDROID_HOME/platform-tools:$PATH"
          if [ ! -f local.properties ]; then
            printf 'sdk.dir=%s\\n' "$ANDROID_HOME" > local.properties
          fi
        '';
      };
    };
}
