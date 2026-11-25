# Working on Moddy worlds

Guidance for coding agents (and people) working in this repository. The [README](README.md) says what a world is, how
it is laid out, built, played and released; [CONTRIBUTING.md](CONTRIBUTING.md) has the licence rules. This file holds
what the README does not: how to get the work done quickly and what goes wrong.

## The three checks, cheapest first

```bash
./gradlew -p <world> build                 # compile, unit tests, the jar, checkLevelJar, licenceCheck  (seconds once warm)
tools/play.sh <world> --build --only NAME  # one real-client playtest                                    (2 to 5 minutes)
tools/play.sh <world> --build              # every playtest of the world, before a pull request that changes play
```

Put a decision that needs no Minecraft in a plain class with a unit test; a playtest proves what a player sees.
A world never needs a client mod, never adds a top-level command, and never edits the core: a missing hook is an issue
on the core's repository.

## A fresh machine: `tools/cloud-setup.sh <world>`

Run it once at the start of any session on a new machine, before the first build. It is safe to run again, and
`--check` says what is missing without changing anything. What it saves, measured on a 4-CPU cloud machine:

| step | without it | with it |
|---|---|---|
| Minecraft's files for the first playtest | 35 to 40 min (one file at a time) | about half a minute (parallel) |
| the first build | about 17 min, two failures on Maven Central 429s | about 5 min, no retry |

It costs no GitHub Actions minutes and stores nothing on GitHub: the files come from the hosts a player's Launcher
uses. It deliberately does NOT keep a shared copy of Minecraft anywhere (a release asset, a cache, a bucket):
Minecraft's files are not ours to redistribute, and a half-minute download needs no cache.

## When to give work its own machine

One machine runs ONE heavy job at a time: a Gradle build or a client playtest. Two at once on a 4-CPU machine has
restarted the machine and lost the work. So:

- **Use a separate cloud session** for a tranche of work on one world that will build and playtest many times (a
  feature with its playtests, a full playtest pass before a release, a pin bump's verification), and whenever another
  heavy job is already running where you are. Setup is a few minutes, so anything over about half an hour of work
  pays for it. Give it one branch, ask it to push work in progress often, and have it report its playtest tally.
- **Stay on the machine you have** for a small fix, a unit-test-only change, docs, and anything that needs what
  only that machine holds.
- **Several worlds at once** (`tools/play.sh isle,arena --build`) is one job; it does not need a machine per world.

### When that is not available

- A separate session cannot be started, or cannot clone MMVU or fetch the playtest carrier (GitHub refusing or
  rate-limiting): do the work on the machine you have, one heavy job at a time. Nothing in this repository requires
  a second machine.
- `tools/cloud-setup.sh` could not finish a step: it names the step. Builds and unit tests need only step 2 at most;
  playtests need MMVU, xvfb and the instance. `PLAY_PREFETCH=off` and `CLOUD_SETUP_MIRROR=off` turn the two
  speed-ups off if either ever misbehaves; the slow path underneath is unchanged.
- GitHub Actions is out of minutes or not running: CI's checks are the same commands as above
  (`./gradlew -p <world> build`, `python3 tools/leak-scan.py --tree`, `python3 tools/licence-scan.py --tree`,
  `python3 tools/core-pins.py --check`); run them yourself and say in the pull request what you ran. Playtests never
  run in Actions, so they are unaffected.

## Traps

- **Wait on the report, not the process.** `tools/play.sh` prints `[play] PASS|FAIL <name>` per playtest; read those
  lines. A client that never started and a playtest that failed are different results.
- **A playtest that passes alone and fails in the full run** is usually the machine being busy: check nothing else
  heavy was running before calling it a flake or a bug.
- **`-PcoreJar=<jar>` builds against an unreleased core** and marks the jar; it is for trying a core change, and such
  a jar is never released. The pin (`tools/core-pins.py --set`) is the only way a world moves to a new core.
- **Work lands on `main`.** `neoforge-1.21.8` is the release branch and takes only what `main` has.
