# Third-party files

Every committed file that carries a copyright that is not ours, or is copied from someone else's work, has a row here:
where it came from, its licence and why it is here. `tools/licence-scan.py --tree` (CI) fails a foreign copyright in a
file with no row, a row that matches no tracked file, and a licence that is not on the allow-list in
`tools/licence-rules.json` (see [CONTRIBUTING.md](CONTRIBUTING.md)). A path may be a glob.

What the world jars SHIP from others is checked separately: the datapacks pinned in each `<world>/datapacks.lock.json`
(`licence-scan.py --bundled --lock`), each with its notice in `<world>/THIRD_PARTY_NOTICES.txt`.

| file | source | licence | why |
|---|---|---|---|
| `gradlew` | Gradle wrapper script, github.com/gradle/gradle | Apache-2.0 | the build's wrapper |
| `gradlew.bat` | Gradle wrapper script, github.com/gradle/gradle | Apache-2.0 | the build's wrapper on Windows |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle wrapper, github.com/gradle/gradle | Apache-2.0 | downloads the pinned Gradle |
| `isle/src/main/resources/data/moddingfromamod/structure/isle/starter_island.nbt` | Standard SkyBlock by BluePsychoRanger (BPR02), github.com/BPR02/SkyBlock_Collection, byte for byte | MIT | the Isle's starter island |
| `isle/src/main/resources/data/moddingfromamod/structure/isle/starter_island.LICENSE.txt` | that pack's LICENSE, with where the island came from | MIT | the licence travels with the copy |
| `isle/THIRD_PARTY_NOTICES.txt` | the LICENSE of BPR02's SkyBlock packs | MIT | the notice for the packs the Isle jar carries |
| `isle/datapacks.lock.json` | names, versions, sha1s and copyright lines of BPR02's SkyBlock packs | MIT | the pin the build checks; it records each pack's notice |
