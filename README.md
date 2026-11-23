# Moddy worlds

The **levels** of [Moddy the Wish Watch](https://github.com/jivinstev/ModdyTheWishWatch): each one a world with its
own rules and its own game, shipped as its own mod (jar) that runs on the public Moddy core. Licence: [MPL-2.0](LICENSE).

## Keeping this repository free of private work

This repository is public (or will be), and the private side of Moddy must never reach it: no hosted-server settings,
no operations tooling, no player ids or players' words, no credentials. Every check runs the same rules:

- **git hooks** — `./tools/git-hooks/install.sh` once per clone. `pre-commit` runs the secret scan and the leak scan
  over what you stage; `pre-push` scans every outgoing commit (so something added and deleted again is still
  refused). A maintainer adds the private forbidden list, kept OUTSIDE the repository:
  `./tools/git-hooks/install.sh --forbidden ~/.moddy/forbidden.txt`.
- **CI** (`.github/workflows/ci.yml`, on pull requests to `main` and by hand) — the leak scan over the tree and
  every commit, the secret scan over the tree and the history, and (once a level lands) its build, unit tests and jar check.
- **The rules**: `tools/leak-scan.py`'s built-in shapes (emails, routable IPs, bucket URLs, every credential shape in
  `tools/secret_patterns.py`, children's-data file names, the private side's packages), plus
  [`tools/leak-rules.txt`](tools/leak-rules.txt) (committed, shapes only), plus the private list (never committed;
  CI reads it from the `FORBIDDEN_LIST` secret). A finding names the file, line and rule, never what matched.

Never bypass a refusal with `--no-verify`. A real credential that reached any commit is rotated first.
