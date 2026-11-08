#!/usr/bin/env sh
# Builds the web version in site/ (page + jar + maps and images) to be published on GitHub Pages.
# To try it locally: node web/serve.mjs  then open http://localhost:8080/
set -e
cd "$(dirname "$0")"
sh ./build.sh
rm -rf site
mkdir -p site/work
cp web/index.html site/
cp dist/bank-robbery.jar site/
# The JPEG decoder does not work in CheerpJ: tiles are converted to PNG (same file names)
java tools/ConvertTiles.java work/Tiles site/work/Tiles
cp -r work/Immagini_agenti work/Immagini_menu work/Matrice_mappa.txt work/Matrice_mappa_calpestabile.txt site/work/
touch site/.nojekyll
echo "Site ready in site/"
