{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";
    utils.url = "github:numtide/flake-utils";
    sbt.url = "github:zaninime/sbt-derivation";
    sbt.inputs.nixpkgs.follows = "nixpkgs";
  };

  outputs = { self, nixpkgs, utils, sbt}:
  utils.lib.eachDefaultSystem (system:
  let
    pkgs = import nixpkgs { inherit system; };
  in {
    # ---------------------------------------------------------------------------
    #devShells.default = pkgs.mkShell {
    #  buildInputs = [pkgs.sbt pkgs.metals pkgs.jdk22 pkgs.hello];
    #};

    # ---------------------------------------------------------------------------
    packages.default = sbt.mkSbtDerivation.${system} {
      pname = "nix-web-echo";
      version = "1.0.0";
      depsSha256 = "sha256-nT/IQhgUzyBdsfhuE8THa7gcMEKOv6Xb5Z5j2GpxEmg=";

      src = ./.;

      buildInputs = [pkgs.sbt pkgs.jdk22_headless pkgs.makeWrapper];

      buildPhase = "sbt Universal/packageZipTarball";

      installPhase = ''
          tar xf target/universal/web-echo.tgz --directory $out
          wrapProgram $out/bin/web-echo \
            --set PATH ${pkgs.lib.makeBinPath [ pkgs.gnused pkgs.coreutils pkgs.jdk22_headless pkgs.bash ]}
      '';
    };
    # ---------------------------------------------------------------------------

  });
}
