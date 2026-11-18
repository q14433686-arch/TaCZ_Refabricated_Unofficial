# 26.3 Snowstorm runtime port — implementation and test ledger

This is a source port, not a claim of universal pack compatibility. Source provenance and
LGPL notices are in `LICENSES/SimpleBedrockModel.NOTICE.md`. No default-pack artwork was altered.
The old native muzzle profiles remain supported independently; unknown effects never become smoke.

## Implemented code paths

| Area | Implementation | Runtime checks |
|---|---|---|
| Pack loading | `assets/<namespace>/particle_definitions/*.json`, pack overlays through the gun asset scanner; lookup by resource path or declared identifier; clear instances on reload | External authored acceptance pack, inspect dispatch |
| Molang | Relocated compiler/interpreter, independent emitter variable storage, particle/emitter ages and randoms, math, particle query context; `pre_effect_script` before emitter initialization | Math, two emitters with different variable.count, query.delta_time |
| Components | Instant/steady/manual rates; once/loop/expression lifetime; point/box/sphere/disc/entity AABB; scalar/vector initial speed; spin; billboard/UV/flipbook; lighting; dynamic tint/gradients; dynamic/parametric motion; collision and block expiry; initialization and lifetime events | Basic instant/lifetime, visible magenta probe; more complex combinations require additional cases |
| Curves | Linear, Bezier, Catmull–Rom, Bezier-chain evaluation; normalize variable.* / v.* keys; rebind per particle | Linear variable namespace assertion |
| Scripts | Emitter creation/update, particle creation/update/render; child-event pre-effect expressions execute in the child's environment | Real inspect pre-effect count; isolated compiler expressions |
| Rendering | Immutable particle snapshots submitted through 26.3 collectors, world and first-person geometry paths; per-locator updates; particle material selects cutout, alpha or additive path | Actual visible magenta pixels, not only submission counts |
| Ownership | Finite owner queues, level/reload clear, expiry, gun-swap emitter stop, world-vs-hand separation | Expired emitters released; tracked-owner S2C test |
| Remote animation events | Client sends only event fingerprint and gun ID; server derives sender, checks held gun, rate-limits and forwards to capable tracking clients within 128 blocks. Recipient resolves fingerprint against **its own** installed animation prototypes and uses that owner's main-hand locator. No received scripts/coordinates are executed, no shared local runner is consumed | Undeclared token rejection, real S2C delivery, tracked main-hand geometry, expiry |
| Animation transitions | Incoming runner owns its own event queue; event-only transition probe checks exactly-once consumption | Controller transition-target test |

## Work limits and security

- At most 128 emitters, 16,384 particles per emitter and a 16,384 total admission/scheduling
  budget; rates and manual work are bounded. Unknown components/material names are diagnostic
  parse failures, not silently successful fallbacks. Add-ons may register component deserializers
  before resources load; duplicate registrations fail.
- Cosmetic network requests are limited to 64 per sender per 20 ticks; recipient queues to 32 per
  owner and 128 owners, expiring after 20 ticks. Fingerprints cover effect, locator and script.
  A different pack/script on a recipient will not resolve and is ignored.
- This relay requires both peers to support the new channels. It does not infer arbitrary custom
  animation choices on old peers, replay events to newly tracking players, or provide animation
  particles for non-player NPC scripts that never generate a client animation event. Pack-defined
  muzzle effects additionally use the existing authoritative tracked gun-fire path.
- Bounded particle counts do not make arbitrary pack scripts a security sandbox. Install trusted packs.

## Test status

`SnowstormSmoke`, `RemoteParticleSmoke`, and the extended `CoreCompletionSmoke` are executable
client game tests, wired into both the ordinary and Iris smoke paths. This new batch's successful
compilation is not equivalent to those tests passing.

- `44401153019`: compile-check **passed**, source `2eaae88`.
- `44401153034`: Iris/Sodium client suite **passed** on `2eaae88`, including muzzle
  effects with smoke disabled, Snowstorm visible-magenta/lifetime/isolated-variable checks,
  transition target consumption, and tracked-owner S2C delivery/expiry. Shader off/on
  restored 2,217 heat pixels. This is the authored CI shader, not a third-party shader certification.
- The matching non-shader Vulkan build reached the client but failed the translucent slime
  thermal check (0 bright pixels). Original frames are retained under
  `build-reports/visual/44399210018-tacz_lens_slime_hot.png` and the Iris counterpart.
  Pinned 26.3 bytecode confirms blended models invoke `FeatureRenderPhase.submit`, while
  the thermal hook intercepted only `SimpleFeatureRenderPhase.submit`. The additional
  interface-site hook is awaiting a full non-shader regression; the threshold is unchanged.
- The short-lived flash fix advances existing particles before emitter updates, so newborn
  20ms particles are not killed by the same 50ms client tick before extraction. A dedicated
  assertion checks both the initial render opportunity and expiry on the next tick.

Optional Sable/Simulated/EMI/Accelerated Rendering 26.3 dependency/API availability remains an
independent integration prerequisite, not something this particle source port can substitute for.
The sampled public EMI release still contains 1.21.1 artifacts; public Sable and AR branch lists
provide no verified 26.3 artifact. These observations do not prove no compatible artifact exists.
