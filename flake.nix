{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";
    utils.url = "github:numtide/flake-utils";
    sbt.url = "github:zaninime/sbt-derivation";
    sbt.inputs.nixpkgs.follows = "nixpkgs";
  };

  outputs = { self, nixpkgs, utils, sbt }:
  utils.lib.eachDefaultSystem (system:
  let
    pkgs = import nixpkgs { inherit system; };
  in {
    # ---------------------------------------------------------------------------
    # nix develop
    devShells.default = pkgs.mkShell {
      buildInputs = [pkgs.sbt pkgs.metals pkgs.jdk22 pkgs.hello];
    };

    # ---------------------------------------------------------------------------
    # nix build
    packages.default = sbt.mkSbtDerivation.${system} {
      pname = "nix-web-echo";
      version = "1.0.0";
      depsSha256 = "sha256-nT/IQhgUzyBdsfhuE8THa7gcMEKOv6Xb5Z5j2GpxEmg=";

      src = ./.;

      buildInputs = [pkgs.sbt pkgs.jdk22_headless pkgs.makeWrapper];

      buildPhase = "sbt Universal/packageZipTarball";

      installPhase = ''
          mkdir -p $out
          tar xf target/universal/web-echo.tgz --directory $out
          makeWrapper $out/bin/web-echo $out/bin/nix-web-echo \
            --set PATH ${pkgs.lib.makeBinPath [
              pkgs.gnused
              pkgs.gawk
              pkgs.coreutils
              pkgs.bash
              pkgs.jdk22_headless
            ]}
      '';
    };

    # ---------------------------------------------------------------------------
    # simple nixos services integration
    nixosModules.default = { config, pkgs, lib, ... }: {
      options = {
        services.web-echo = {
          enable = lib.mkEnableOption "web-echo";
          user = lib.mkOption {
            type = lib.types.str;
            description = "User name that will run the web echo service";
          };
          port = lib.mkOption {
            type = lib.types.int;
            description = "Service web echo listing port";
            default = 8080;
          };
          prefix = lib.mkOption {
            type = lib.types.str;
            description = "Service web echo url prefix";
            default = "";
          };
        };
      };
      config = lib.mkIf config.services.web-echo.enable {
        systemd.services.web-echo = {
          description = "Record your json data coming from websockets or webhooks";
          unitConfig = {
            Type = "simple";
          };
          environment = {
            WEB_ECHO_LISTEN_PORT = (toString config.services.web-echo.port);
            WEB_ECHO_PREFIX = config.services.web-echo.prefix;
          };
          serviceConfig = {
            ExecStart = "${self.packages.${pkgs.system}.default}/bin/nix-web-echo";
            User = config.services.web-echo.user;
            Restart = "on-failure";
          };
          wantedBy = [ "multi-user.target" ];
        };
      };
    };
    # ---------------------------------------------------------------------------

  });
}
