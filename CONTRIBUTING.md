# Contributing

## Third-party code and text

Nothing written by someone else is copied, ported, translated or closely paraphrased into this repository unless its
licence is on the allow-list:

- **Code and text:** MIT, BSD-2-Clause, BSD-3-Clause, Apache-2.0, MPL-2.0, ISC, Zlib, the Unlicense, CC0 / public
  domain, and Sun's fdlibm notice (SunPro).
- **Art and presets only** (textures, models, sounds, structures, world presets): CC BY 4.0.

Anything else stops the work: GPL, LGPL, AGPL, EPL, CC BY-SA / NC / ND, a font licence, "all rights reserved", no
licence at all, or code whose origin you do not know (a Stack Overflow answer is CC BY-SA). Ask the maintainers, with
the source and its licence, **before** committing it. Reading copyleft code to understand a problem is fine; writing
ours from it is a port. **Pushing a branch to this repository publishes it**, draft or not.

Something we keep under an allowed licence gets a row in [THIRD_PARTY.md](THIRD_PARTY.md) (source, licence, why) in
the same pull request, and its licence text travels with it. A datapack a world ships is pinned in that world's
`datapacks.lock.json` with its licence, and its notice goes in the world's `THIRD_PARTY_NOTICES.txt`.

`tools/licence-scan.py` checks this at commit and push (`./tools/git-hooks/install.sh`), in CI, and in every world's
`./gradlew -p <world> check`. A line that only names a licence carries `licence-scan: allow <reason>`; a file that
discusses licences goes in the `exempt` list of `tools/licence-rules.json` with its reason. Never bypass a hook with
`--no-verify`.
