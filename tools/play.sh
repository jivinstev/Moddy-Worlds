#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# play.sh — play a world locally, in a REAL Minecraft client, on the PUBLIC core alone.
#
#   tools/play.sh WORLD [--tag TAG | --jars DIR | --build] [--only NAME] [--video] [--mmvu DIR] [--no-install]
#   tools/play.sh isle --build                 # this checkout's Isle jar beside the pinned core, every playtest
#   tools/play.sh isle,arena --build           # SEVERAL worlds in one instance: playtests/ at the repository root
#
# 1. the jars. The WORLD jar: a pre-release of this repository (gh's REST API; the newest `WORLD-*` one by
#    default, --tag for one; SHA256SUMS checked), or --build (this checkout's ./gradlew -p WORLD jar), or --jars DIR (a
#    folder holding <modid>-*.jar, and optionally the core jar). The CORE jar: the release WORLD/core.lock pins,
#    fetched by tools/fetch-core.sh and checked against its sha256 (a core jar in --jars DIR is used as it is).
# 2. a test instance with MMVU's tools/real-install.py (Minecraft and the NeoForge in WORLD/gradle.properties, the two
#    jars and the Gate C carrier). It never touches your own mods folder or Launcher profiles.
# 3. every WORLD/playtests/*.playtest (or --only NAME) with MMVU's tools/playtest.py, in the real client, each as a new
#    player (an empty Moddy home) and within 900 s, or what its `# play-timeout: <seconds>` line says. MMVU's carrier
#    makes its own world (no Create World screen, so no World Type and none of the world's packs), so the world is
#    switched on the way a server's launcher does it: its world id and rules file (read out of the jar) as -D
#    properties, plus WORLD/playtests/play.props: one `key=value` per line, passed as moddingfromamod.<key>; a value
#    `@manifest:a.b` is read from the jar's worlds/WORLD.json. A world YOU make with its World Type needs none of them.
#
# SEVERAL WORLDS (WORLD is a comma-separated list): every world's jar goes into the one instance (--build builds
# each; --jars DIR must hold them all; no release mode), with the one core they all pin (their core.lock files must
# agree), and the playtests are the repository's own playtests/*.playtest, which make and switch worlds themselves:
# no world is switched on by properties.
#
# MMVU is jivinstev/mc-mod-version-upgrade, cloned BESIDE this repository (../mc-mod-version-upgrade), or --mmvu DIR.
# Runs on macOS (bash 3.2; --video records the game window) and on headless Linux (it wraps itself in xvfb-run when
# there is no DISPLAY). Exit 0 every playtest passed · 1 one failed · 2 could not run.
# ─────────────────────────────────────────────────────────────────────────────
set -u
REPO="$(cd "$(dirname "$0")/.." && pwd)"
say() { printf '[play] %s\n' "$*"; }
die() { say "$*"; exit 2; }
case "${1:-}" in -h|--help|"") sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'; [ -n "${1:-}" ]; exit $? ;; esac
WORLDS_ARG="$1"; shift
IFS=',' read -r -a WORLDS <<< "$WORLDS_ARG"
prop() { sed -n "s/^$1=//p" "$2" | head -1; }
for w in "${WORLDS[@]}"; do
    [ -f "$REPO/$w/core.lock" ] && [ -f "$REPO/$w/gradle.properties" ] || die "no world '$w' (a folder with core.lock and gradle.properties)"
done
WORLD="${WORLDS[0]}"
HERE="$REPO/$WORLD"
MULTI=0; [ "${#WORLDS[@]}" -gt 1 ] && MULTI=1
MC="$(prop minecraft_version "$HERE/gradle.properties")"
MODID="$(prop mod_id "$HERE/gradle.properties")"
GH_REPO="${PLAY_REPO:-jivinstev/moddy-worlds}"
TAG=""; JARS=""; ONLY=""; VIDEO=""; INSTALL=1; BUILD=0
MMVU="${MMVU:-$(cd "$REPO/.." && pwd)/mc-mod-version-upgrade}"
WORK="${PLAY_WORK:-$HOME/.mmvu/$(printf '%s' "$WORLDS_ARG" | tr ',' '+')-play}"

while [ $# -gt 0 ]; do
    case "$1" in
        --tag) TAG="${2:-}"; shift 2 ;;
        --jars) JARS="${2:-}"; shift 2 ;;
        --build) BUILD=1; shift ;;
        --only) ONLY="${2:-}"; shift 2 ;;
        --video) VIDEO="--video"; shift ;;
        --mmvu) MMVU="${2:-}"; shift 2 ;;
        --no-install) INSTALL=0; shift ;;
        *) die "unknown argument '$1' (see --help)" ;;
    esac
done

# Headless Linux: the client needs a display. Re-run this script inside xvfb-run, once.
if [ "$(uname)" = Linux ] && [ -z "${DISPLAY:-}" ] && [ -z "${PLAY_XVFB:-}" ]; then
    command -v xvfb-run >/dev/null || die "no DISPLAY and no xvfb-run (apt-get install -y xvfb mesa-utils libgl1-mesa-dri)"
    PLAY_XVFB=1 exec xvfb-run -a -s "-screen 0 1280x720x24" "$0" "$WORLDS_ARG" ${TAG:+--tag "$TAG"} ${JARS:+--jars "$JARS"} \
        $([ "$BUILD" = 1 ] && echo --build) ${ONLY:+--only "$ONLY"} $VIDEO --mmvu "$MMVU" $([ "$INSTALL" = 0 ] && echo --no-install)
fi

command -v python3 >/dev/null || die "needs python3"
[ -f "$MMVU/tools/real-install.py" ] || die "no MMVU at $MMVU: git clone https://github.com/jivinstev/mc-mod-version-upgrade \"$MMVU\" (or --mmvu DIR)"
NEO="$(prop neo_version "$HERE/gradle.properties")"
[ -n "$MC" ] && [ -n "$NEO" ] && [ -n "$MODID" ] || die "no minecraft_version / neo_version / mod_id in $HERE/gradle.properties"
mkdir -p "$WORK"

sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | awk '{print $1}'; }

# ── 1. the jars ──
if [ "$MULTI" = 1 ]; then
    [ "$BUILD" = 1 ] || [ -n "$JARS" ] || die "several worlds need --build or --jars DIR (no release mode)"
    first="$(prop core_asset "$HERE/core.lock")@$(prop core_sha256 "$HERE/core.lock")"
    for w in "${WORLDS[@]}"; do
        [ "$(prop core_asset "$REPO/$w/core.lock")@$(prop core_sha256 "$REPO/$w/core.lock")" = "$first" ] \
            || die "$w/core.lock pins another core than $WORLD/core.lock: one instance runs one core"
    done
fi
if [ "$BUILD" = 1 ]; then
    JARS="$WORK/jars/local"; rm -rf "$JARS"; mkdir -p "$JARS"
    for w in "${WORLDS[@]}"; do
        m="$(prop mod_id "$REPO/$w/gradle.properties")"
        (cd "$REPO" && ./gradlew -q -p "$w" jar) || die "./gradlew -p $w jar failed"
        cp "$REPO/$w"/build/libs/"$m"-*.jar "$JARS/" || die "the build left no $m jar"
    done
    say "world jar(s) built from this checkout"
elif [ -z "$JARS" ]; then
    command -v gh >/dev/null || die "needs the GitHub CLI (gh) for the release, or --jars DIR / --build"
    if [ -z "$TAG" ]; then
        TAG="$(gh api "repos/$GH_REPO/releases?per_page=30" --jq "[.[] | select(.prerelease and (.tag_name | startswith(\"$WORLD-\")))][0].tag_name")" \
            || die "could not list the releases of $GH_REPO"
        [ -n "$TAG" ] && [ "$TAG" != null ] || die "no $WORLD-* pre-release on $GH_REPO; pass --tag, --jars or --build"
    fi
    JARS="$WORK/jars/$TAG"
    if [ ! -f "$JARS/.complete" ]; then
        rm -rf "$JARS"; mkdir -p "$JARS"
        # REST only (gh's `release download` is GraphQL, which some environments refuse): list the assets, fetch each
        gh api "repos/$GH_REPO/releases/tags/$TAG" -q '.assets[] | "\(.id) \(.name)"' > "$JARS/.assets" \
            || die "could not read release $TAG of $GH_REPO"
        while read -r id name; do
            case "$name" in
                "$MODID"-*.jar|SHA256SUMS)
                    gh api "repos/$GH_REPO/releases/assets/$id" -H 'Accept: application/octet-stream' > "$JARS/$name" \
                        || die "could not download $name from $TAG" ;;
            esac
        done < "$JARS/.assets"
        rm -f "$JARS/.assets"
        if [ -f "$JARS/SHA256SUMS" ]; then
            while read -r sum name; do
                [ -n "$name" ] || continue
                [ "$(sha256 "$JARS/$name")" = "$sum" ] || die "SHA256 mismatch for $name (release $TAG)"
            done < "$JARS/SHA256SUMS"
            say "SHA256SUMS: every jar matches"
        fi
        touch "$JARS/.complete"
    fi
    say "release $TAG"
fi
CORE=""; LEVEL=""; LEVEL_JARS=()
for w in "${WORLDS[@]}"; do
    m="$(prop mod_id "$REPO/$w/gradle.properties")"
    found=""
    for j in "$JARS"/"$m"-*.jar; do [ -f "$j" ] && found="$j"; done
    [ -n "$found" ] || die "no $m-*.jar in $JARS"
    LEVEL_JARS+=("$found")
done
LEVEL="${LEVEL_JARS[0]}"
for j in "$JARS"/moddingfromamod-*.jar; do [ -f "$j" ] && CORE="$j"; done
if [ -z "$CORE" ]; then
    CORE="$("$REPO/tools/fetch-core.sh" "$WORLD")" || die "could not fetch the core core.lock pins (a private core needs GH_TOKEN)"
fi
say "core $(basename "$CORE") · world(s) $(for j in "${LEVEL_JARS[@]}"; do printf '%s ' "$(basename "$j")"; done)· Minecraft $MC · NeoForge $NEO"
HOME_DIR="$WORK/moddy-home"; mkdir -p "$HOME_DIR"      # the mod's own home, never your ~/.moddingfromamod
if [ "$MULTI" = 1 ]; then
    # several worlds: the root playtests make and switch worlds themselves, so no world is switched on by properties
    PROPS=(--prop "moddingfromamod.home=$HOME_DIR")
    SUITE="$REPO/playtests"
else

SUITE="$HERE/playtests"
# The world's rules ride in its jar; the game reads them from a file, as on a server.
RULES="$WORK/$WORLD.rules.json"
python3 - "$LEVEL" "$RULES" "$WORLD" <<'PY' || die "the world jar carries no worlds/rules/$WORLD.rules.json"
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    open(sys.argv[2], "wb").write(z.read("worlds/rules/" + sys.argv[3] + ".rules.json"))
PY
PROPS=(--prop "moddingfromamod.world.id=$WORLD" --prop "moddingfromamod.world.rules=$RULES"
       --prop "moddingfromamod.home=$HOME_DIR")
if [ -f "$HERE/playtests/play.props" ]; then
    while IFS= read -r line; do
        [ -n "$line" ] && [ "${line#\#}" = "$line" ] && PROPS+=(--prop "moddingfromamod.$line")
    done < <(python3 - "$LEVEL" "$WORLD" "$HERE/playtests/play.props" <<'PY'
import json, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    manifest = json.loads(z.read("worlds/" + sys.argv[2] + ".json"))
for raw in open(sys.argv[3], encoding="utf-8"):
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    k, v = line.split("=", 1)
    if v.startswith("@manifest:"):
        cur = manifest
        for part in v[len("@manifest:"):].split("."):
            cur = cur[part]
        v = str(cur)
    print(f"{k.strip()}={v.strip()}")
PY
    ) || die "could not read $HERE/playtests/play.props"
fi
fi

# ── 2. the instance ──
RI=(python3 "$MMVU/tools/real-install.py")
JAR_ARGS=(--jar "$MC=$CORE")
for j in "${LEVEL_JARS[@]}"; do JAR_ARGS+=(--jar "$MC=$j"); done
COMMON=(--mc "$MC" "${JAR_ARGS[@]}" --neoforge "$MC=$NEO" --with-carrier --label "$MODID-play" --work "$WORK/ri")
# CARRIER_DIR=<folder of gatec-carrier-<mc>.jar + SHA256SUMS>: where the GitHub API cannot list MMVU's releases
[ -n "${CARRIER_DIR:-}" ] && COMMON+=(--carrier-dir "$CARRIER_DIR")
if [ "$INSTALL" = 1 ]; then
    "${RI[@]}" plan "${COMMON[@]}" || die "real-install could not plan the instance"
    "${RI[@]}" install "${COMMON[@]}" || die "real-install could not make the instance"
fi
PLAN="$WORK/ri/plan.json"
[ -f "$PLAN" ] || die "no plan at $PLAN (run without --no-install once)"

# ── 3. the playtests ──
FAILED=0; RAN=0
for script in "$SUITE"/*.playtest; do
    [ -f "$script" ] || continue
    name="$(basename "$script" .playtest)"
    if [ -n "$ONLY" ]; then case "$name" in *"$ONLY"*) ;; *) continue ;; esac; fi
    RAN=$((RAN + 1))
    say "playtest $name"
    # every playtest starts as a new player: an empty Moddy home (settings, the Magician, his wishes and shelf)
    rm -rf "$HOME_DIR"; mkdir -p "$HOME_DIR"
    rm -rf "$WORK/reports/$name"      # this run's report alone (frames from an earlier run would mix in)
    # a long script names its own time limit in a comment line: `# play-timeout: <seconds>` (default 900)
    TIMEOUT="$(sed -n 's/^# play-timeout: *\([0-9][0-9]*\).*/\1/p' "$script" | head -1)"
    if python3 "$MMVU/tools/playtest.py" run "$script" --plan "$PLAN" --mc "$MC" $VIDEO "${PROPS[@]}" \
            --timeout "${TIMEOUT:-900}" --report "$WORK/reports/$name"; then
        say "PASS $name ($WORK/reports/$name/report.html)"
    else
        say "FAIL $name ($WORK/reports/$name/report.html)"
        FAILED=$((FAILED + 1))
    fi
done
[ "$RAN" -gt 0 ] || die "no playtest matched '${ONLY:-*}' in $SUITE"
say "$((RAN - FAILED))/$RAN playtests passed"
[ "$FAILED" -eq 0 ]
