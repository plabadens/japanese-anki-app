{
  description = "Japanese Anki Card Creator Android build environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; config.allowUnfree = true; };
      jdk = pkgs.jdk17;
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
        packages = [ jdk pkgs.gradle android.androidsdk android.platform-tools pkgs.git ];
        JAVA_HOME = jdk.home;
        ANDROID_HOME = "${android.androidsdk}/libexec/android-sdk";
        ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
        # Gradle's Java fallback avoids native-service failures in restricted build containers.
        GRADLE_OPTS = "-Dorg.gradle.native=false";
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
