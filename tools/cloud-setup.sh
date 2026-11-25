#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# cloud-setup.sh — make a FRESH Linux machine (a cloud coding session, a CI runner, a new VM) ready to build a world
# and run its real-client playtests, in minutes instead of the best part of an hour.
#
#   tools/cloud-setup.sh WORLD[,WORLD]      # e.g.  tools/cloud-setup.sh arena      tools/cloud-setup.sh isle,arena
#   tools/cloud-setup.sh --check            # say what is missing; change nothing
#
# What it does, each step safe to run again and each one saying how long it took:
#   1. packages   the client needs a display and a software renderer: xvfb, mesa (installed with apt-get when it can)
#   2. gradle     behind an HTTPS proxy, pins Gradle's daemon to the LIVE proxy port (a daemon does not inherit the
#                 environment's proxy), and points Maven Central at Google's mirror of it (the same artifacts): Central
#                 answers a burst with 429, and Gradle fails the build on a 429 instead of trying the next repository.
#                 Machine-local files only (~/.gradle); nothing in the build changes. CLOUD_SETUP_MIRROR=off skips it.
#   3. MMVU       jivinstev/mc-mod-version-upgrade cloned beside this repository (the playtest driver), or updated
#   4. build      ./gradlew -p WORLD jar for each world, retried twice (a first build downloads a lot)
#   5. instance   tools/play.sh WORLD --build --install-only: Minecraft (its files fetched in parallel), NeoForge, the
#                 pinned core, the world's jar and the playtest carrier, in a test instance. No playtest is run.
# Then:  tools/play.sh WORLD --build [--only NAME]
#
# It uses no GitHub Actions minutes and no GitHub storage: everything comes from the same public hosts a player's
# Launcher uses, plus two git clones and two release downloads. WHEN A STEP CANNOT RUN it says which and what still
# works: without step 2 a build may need a retry; without step 5's parallel fetch the installer fetches one file at a
# time (slow, correct); without MMVU or a display there are no playtests on this machine, so run them on another one
# (tools/play.sh works on macOS and on any Linux with xvfb) and keep this machine for builds and unit tests.
# Exit 0 ready · 1 --check found something missing · 2 a step failed (named).
# ─────────────────────────────────────────────────────────────────────────────
set -u
REPO="$(cd "$(dirname "$0")/.." && pwd)"
say() { printf '[setup] %s\n' "$*"; }
die() { say "FAILED: $*"; exit 2; }
T0=$SECONDS; took() { printf '%ss' "$((SECONDS - ${1:-$T0}))"; }
CHECK=0; WORLDS_ARG=""
case "${1:-}" in
    -h|--help|"") sed -n '2,28p' "$0" | sed 's/^# \{0,1\}//'; [ -n "${1:-}" ]; exit $? ;;
    --check) CHECK=1 ;;
    *) WORLDS_ARG="$1" ;;
esac
MMVU="${MMVU:-$(cd "$REPO/.." && pwd)/mc-mod-version-upgrade}"
MMVU_URL="${MMVU_URL:-https://github.com/jivinstev/mc-mod-version-upgrade}"
GUH="${GRADLE_USER_HOME:-$HOME/.gradle}"
INIT="$GUH/init.d/worlds-central-mirror.gradle"
MIRROR="${CLOUD_SETUP_MIRROR_URL:-https://maven-central.storage-download.googleapis.com/maven2/}"
MISSING=0

# ── 1. packages ──
t=$SECONDS
need=""
command -v xvfb-run >/dev/null || need="$need xvfb"
command -v glxinfo >/dev/null || need="$need mesa-utils libgl1-mesa-dri"
command -v python3 >/dev/null || need="$need python3"
command -v git >/dev/null || need="$need git"
if [ -n "$need" ]; then
    if [ "$CHECK" = 1 ]; then say "packages   MISSING:$need"; MISSING=1
    elif command -v apt-get >/dev/null && [ "$(id -u)" = 0 ]; then
        apt-get update -qq && apt-get install -y -qq $need >/dev/null || die "apt-get install$need"
        say "packages   installed$need ($(took $t))"
    else
        say "packages   MISSING:$need (install them; without xvfb there are no playtests on this machine)"; MISSING=1
    fi
else
    say "packages   ok"
fi

# ── 2. gradle: the live proxy and the Central mirror ──
proxy="${HTTPS_PROXY:-${https_proxy:-}}"
port="$(printf '%s' "$proxy" | sed -n -E 's#^[a-zA-Z]+://([^@/]*@)?([^:/]+):([0-9]+).*#\3#p')"
host="$(printf '%s' "$proxy" | sed -n -E 's#^[a-zA-Z]+://([^@/]*@)?([^:/]+):([0-9]+).*#\2#p')"
PROPS="$GUH/gradle.properties"
BEGIN="# >>> managed by tools/cloud-setup.sh (the live proxy) >>>"
END="# <<< managed by tools/cloud-setup.sh <<<"
if [ -n "$port" ]; then
    pinned="$(sed -n 's/^systemProp\.https\.proxyPort=//p' "$PROPS" 2>/dev/null | tail -1)"
    if [ "$pinned" = "$port" ]; then say "gradle     proxy ok (port $port)"
    elif [ "$CHECK" = 1 ]; then say "gradle     proxy STALE: live $port, pinned ${pinned:-none}"; MISSING=1
    else
        mkdir -p "$GUH"; tmp="$(mktemp)"
        [ -f "$PROPS" ] && awk -v b="$BEGIN" -v e="$END" '$0==b{s=1;next} $0==e{s=0;next} s{next}
            /^systemProp\.https?\.(proxyHost|proxyPort|nonProxyHosts)=/{next} {print}' "$PROPS" > "$tmp"
        nonproxy="$(printf '%s' "${JAVA_TOOL_OPTIONS:-}" | sed -n -E 's#.*-Dhttp\.nonProxyHosts=([^ ]+).*#\1#p')"
        { printf '%s\n' "$BEGIN"
          printf 'systemProp.https.proxyHost=%s\nsystemProp.https.proxyPort=%s\n' "$host" "$port"
          printf 'systemProp.http.proxyHost=%s\nsystemProp.http.proxyPort=%s\n' "$host" "$port"
          printf 'systemProp.http.nonProxyHosts=%s\n' "${nonproxy:-localhost|127.0.0.1|::1}"
          printf '%s\n' "$END"; } >> "$tmp"
        mv "$tmp" "$PROPS"
        (cd "$REPO" && ./gradlew --stop >/dev/null 2>&1)
        say "gradle     proxy pinned to $host:$port (was ${pinned:-none}); daemon stopped"
    fi
else
    say "gradle     no HTTPS proxy here, nothing to pin"
fi
if [ "${CLOUD_SETUP_MIRROR:-on}" = off ]; then
    [ "$CHECK" = 1 ] || rm -f "$INIT"; say "gradle     Central mirror off (CLOUD_SETUP_MIRROR=off)"
elif [ -f "$INIT" ]; then say "gradle     Central mirror ok"
elif [ "$CHECK" = 1 ]; then say "gradle     Central NOT mirrored (a build may fail on a 429 and need a retry)"; MISSING=1
else
    # Ask the mirror before trusting it: a redirect to an unreachable mirror would fail EVERY build.
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "${MIRROR%/}/org/ow2/asm/asm/9.7/asm-9.7.pom" 2>/dev/null)"
    if [ "$code" = 200 ]; then
        mkdir -p "$GUH/init.d"
        cat > "$INIT" <<GRADLE
// Written by tools/cloud-setup.sh of the Moddy Worlds repository; delete this file to undo it.
// Every Maven Central repository reads Google's mirror of Central (the same artifacts) instead, so a 429 burst from
// Central cannot fail a build. One invocation without it: CLOUD_SETUP_MIRROR=off ./gradlew ...
if (System.getenv('CLOUD_SETUP_MIRROR') == 'off') { return }
def mirror = '${MIRROR}'
def central = ['repo.maven.apache.org', 'repo1.maven.org'] as Set
def rewire = { repos -> repos.configureEach { repo ->
    if (repo instanceof org.gradle.api.artifacts.repositories.MavenArtifactRepository
            && repo.url != null && central.contains(repo.url.host)) { repo.url = mirror }
} }
beforeSettings { settings ->
    rewire(settings.buildscript.repositories)
    // The plugin portal answers a plugin it does not host with a redirect to Central that no client setting can
    // rewrite, so the places such plugins really live are asked first.
    settings.pluginManagement.repositories.maven { url = 'https://maven.neoforged.net/releases' }
    settings.pluginManagement.repositories.maven { url = mirror }
    rewire(settings.pluginManagement.repositories)
    rewire(settings.dependencyResolutionManagement.repositories)
}
allprojects { project ->
    rewire(project.buildscript.repositories)
    rewire(project.repositories)
}
GRADLE
        say "gradle     Maven Central -> $MIRROR ($INIT)"
    else
        say "gradle     the mirror answered ${code:-000}, not 200: NOT redirecting (a build may need a retry on a 429)"
    fi
fi

# ── 3. MMVU ──
t=$SECONDS
if [ -f "$MMVU/tools/real-install.py" ]; then
    [ "$CHECK" = 1 ] || git -C "$MMVU" pull -q --ff-only 2>/dev/null || say "MMVU       could not update $MMVU; using it as it is"
    say "MMVU       ok ($MMVU)"
elif [ "$CHECK" = 1 ]; then say "MMVU       MISSING at $MMVU"; MISSING=1
else
    git clone -q --depth 50 "$MMVU_URL" "$MMVU" && say "MMVU       cloned to $MMVU ($(took $t))" \
        || { say "MMVU       could not clone $MMVU_URL: no playtests on this machine (builds and unit tests still work)"; MISSING=1; }
fi

if [ "$CHECK" = 1 ]; then
    [ "$MISSING" = 0 ] && say "ready" || say "not ready: run tools/cloud-setup.sh WORLD"
    exit "$MISSING"
fi

# ── 4. build, 5. instance ──
IFS=',' read -r -a WORLDS <<< "$WORLDS_ARG"
for w in "${WORLDS[@]}"; do
    [ -f "$REPO/$w/core.lock" ] || die "no world '$w'"
    t=$SECONDS; ok=0
    for try in 1 2 3; do
        (cd "$REPO" && ./gradlew -q -p "$w" jar) && { ok=1; break; }
        say "build      $w: try $try failed; trying again"
    done
    [ "$ok" = 1 ] || die "build of $w (three tries)"
    say "build      $w ok ($(took $t))"
done
if [ -f "$MMVU/tools/real-install.py" ] && command -v xvfb-run >/dev/null; then
    t=$SECONDS
    "$REPO/tools/play.sh" "$WORLDS_ARG" --build --install-only --mmvu "$MMVU" || die "the test instance (tools/play.sh $WORLDS_ARG --build --install-only)"
    say "instance   ok ($(took $t))"
    say "ready in $(took). Next: tools/play.sh $WORLDS_ARG --build [--only NAME]"
else
    say "ready to BUILD in $(took); playtests need MMVU and xvfb (see above), so run those on another machine"
fi
