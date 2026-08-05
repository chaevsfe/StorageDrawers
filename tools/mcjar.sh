#!/bin/sh
#   tools/mcjar.sh find <regex>            -- list matching class names
#   tools/mcjar.sh sig  <binary/name>      -- javap the class (public+protected)
#   tools/mcjar.sh all  <binary/name>      -- javap everything incl. private
#   tools/mcjar.sh uses <regex>            -- classes whose bytecode mentions <regex>
JAR="$HOME/.gradle/caches/fabric-loom/${MCVER:-26.1}/minecraft-client.jar"
case "$1" in
  find) unzip -Z1 "$JAR" '*.class' | sed 's/\.class$//' | grep -E "$2" ;;
  sig)  javap -cp "$JAR" "$(echo "$2" | tr / .)" ;;
  all)  javap -p -cp "$JAR" "$(echo "$2" | tr / .)" ;;
  uses) unzip -Z1 "$JAR" '*.class' | sed 's/\.class$//' | while read -r c; do
          javap -p -c -cp "$JAR" "$(echo "$c" | tr / .)" 2>/dev/null | grep -q "$2" && echo "$c"; done ;;
  *) echo "usage: mcjar.sh find|sig|all|uses <arg>" >&2; exit 2 ;;
esac
