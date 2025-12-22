{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
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
      buildInputs = [pkgs.sbt pkgs.metals pkgs.jdk21 pkgs.hello];
    };

    # ---------------------------------------------------------------------------
    # nix build
    packages.default = sbt.mkSbtDerivation.${system} {
      pname = "nix-web-echo";
      version = builtins.elemAt (builtins.match ''[^"]+"(.*)".*'' (builtins.readFile ./version.sbt)) 0;
      depsSha256 = "sha256-qQiJkk3pyLXlQa1bFVB77lwjbA+43LfhUCMXB6419UE=";

      src = ./.;

      buildInputs = [pkgs.sbt pkgs.jdk21_headless pkgs.makeWrapper];

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
              pkgs.jdk21_headless
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
          ip = lib.mkOption {
            type = lib.types.str;
            description = "Listening network interface - 0.0.0.0 for all interfaces";
            default = "127.0.0.1";
          };
          port = lib.mkOption {
            type = lib.types.int;
            description = "Service web echo listing port";
            default = 8080;
          };
          url = lib.mkOption {
            type = lib.types.str;
            description = "How this service is known/reached from outside";
            default = "http://127.0.0.1:8080";
          };
          prefix = lib.mkOption {
            type = lib.types.str;
            description = "Service web echo url prefix";
            default = "";
          };
          memSize = lib.mkOption {
            type = lib.types.str;
            description = "Memory sizing";
            default = "512M";
          };
          datastore = lib.mkOption {
            type = lib.types.str;
            description = "where web echo stores its data";
            default = "/tmp/web-echo-cache-data";
          };
          websocketsDefaultDuration = lib.mkOption {
            type = lib.types.str;
            description = "Default duration for websockets";
            default = "15m";
          };
          websocketsMaxDuration = lib.mkOption {
            type = lib.types.str;
            description = "Max duration for websockets";
            default = "4h";
          };
          shaGoal = lib.mkOption {
            type = lib.types.int;
            description = "SHA goal for proof of work (0 to disable)";
            default = 0;
          };
          storageHandleTtl = lib.mkOption {
            type = lib.types.str;
            description = "TTL for storage handles";
            default = "24h";
          };
          ssrfProtectionEnabled = lib.mkOption {
            type = lib.types.bool;
            description = "Enable SSRF protection";
            default = true;
          };
          keycloak = {
            enable = lib.mkEnableOption "Keycloak integration";
            url = lib.mkOption {
              type = lib.types.str;
              description = "Keycloak server base URL";
              default = "http://localhost:8081";
            };
            realm = lib.mkOption {
              type = lib.types.str;
              description = "Keycloak realm";
              default = "web-echo";
            };
            strictIssuerCheck = lib.mkOption {
              type = lib.types.bool;
              description = "Enable strict issuer check";
              default = false;
            };
            resource = lib.mkOption {
              type = lib.types.str;
              description = "Keycloak client/resource ID";
              default = "web-echo";
            };
          };
        };
      };
      config = lib.mkIf config.services.web-echo.enable {
        systemd.tmpfiles.rules = [
              "d ${config.services.web-echo.datastore} 0750 ${config.services.web-echo.user} ${config.services.web-echo.user} - -"
        ];
        systemd.services.web-echo = {
          description = "Record your json data coming from websockets or webhooks";
          environment = {
            WEB_ECHO_LISTEN_IP   = config.services.web-echo.ip;
            WEB_ECHO_LISTEN_PORT = (toString config.services.web-echo.port);
            WEB_ECHO_PREFIX      = config.services.web-echo.prefix;
            WEB_ECHO_URL         = config.services.web-echo.url;
            WEB_ECHO_STORE_PATH  = config.services.web-echo.datastore;
            WEB_ECHO_WEBSOCKETS_DEFAULT_DURATION = config.services.web-echo.websocketsDefaultDuration;
            WEB_ECHO_WEBSOCKETS_MAX_DURATION     = config.services.web-echo.websocketsMaxDuration;
            WEB_ECHO_SHA_GOAL                    = (toString config.services.web-echo.shaGoal);
            WEB_ECHO_STORAGE_HANDLE_TTL          = config.services.web-echo.storageHandleTtl;
            WEB_ECHO_SECURITY_SSRF_PROTECTION_ENABLED = (if config.services.web-echo.ssrfProtectionEnabled then "true" else "false");
            WEB_ECHO_SECURITY_KEYCLOAK_ENABLED        = (if config.services.web-echo.keycloak.enable then "true" else "false");
            WEB_ECHO_SECURITY_KEYCLOAK_URL            = config.services.web-echo.keycloak.url;
            WEB_ECHO_SECURITY_KEYCLOAK_REALM          = config.services.web-echo.keycloak.realm;
            WEB_ECHO_SECURITY_KEYCLOAK_STRICT_ISSUER_CHECK = (if config.services.web-echo.keycloak.strictIssuerCheck then "true" else "false");
            WEB_ECHO_SECURITY_KEYCLOAK_RESOURCE       = config.services.web-echo.keycloak.resource;
            JAVA_OPTS            =
            "-Xms${config.services.web-echo.memSize} -Xmx${config.services.web-echo.memSize}"
            + " -XX:+UseG1GC"
            + " -Xlog:gc*:stdout:uptime";
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
