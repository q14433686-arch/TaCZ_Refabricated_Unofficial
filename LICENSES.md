# Licensing and source notice

This is an unofficial Fabric port. Code is GPL-3.0 unless a component's own notice says otherwise; see the root `LICENSE`. Existing upstream attribution is retained.

## Reused porting work

This receiver integrates the already Java-21/remap-adapted 1.21.11 work at
[`bb12ad2b3b81ec893bba932fd8d902b55f3e1dae`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/bb12ad2b3b81ec893bba932fd8d902b55f3e1dae),
which shares baseline `aac343a`. The original functionality comes from Sh1roCu/TACZ-Refabricated and the repository's 26.3/26.1.2 porting lines. Integration, lifecycle corrections and additional acceptance tests in this branch are derivative work under the same license. Prior CI receipts are not receipts for this integration.

## Default gun-pack assets

Default textures, models, sounds, animations and related gun-pack resources retain **TACZ Dev Team** attribution and the pack's **CC BY-NC-ND 4.0** declaration. The code license does not relicense these assets. In particular, it does not grant permission to redistribute modified assets contrary to the NoDerivatives restriction.

See [`docs/DEFAULT_PACK_120_LICENSE.md`](docs/DEFAULT_PACK_120_LICENSE.md), [`DEFAULT_PACK_120.tsv`](DEFAULT_PACK_120.tsv), [`RESOURCE_IMPORT_120.tsv`](RESOURCE_IMPORT_120.tsv) and the embedded `gunpack_info.json` for provenance, adaptations and original notices. The immutable historical resource bundle is not replaced or unpacked wholesale into Git.

## Libraries and other code

- Mayday Animation Engine **1.1.1**, `com.maydaymemory:mae`, is bundled under MIT, not under the assets' license.
- Existing LRTactical and TacZ Mesh Loader port code retain their original authorship and GPL notices.
- Other nested dependencies retain the licenses/notices distributed in their own JARs.

No new artwork is generated, and no additional redistribution rights or upstream endorsement are claimed.
