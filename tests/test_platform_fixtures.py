"""Offline authored-fixture validation only; native probes remain the runtime acceptance gate."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class PlatformFixtureContracts(unittest.TestCase):
    def test_bedrock_models_supply_bounds_required_by_real_loader(self):
        models = list((ROOT / 'verification/packaged-runtime/fixtures/platform').glob('assets/*/geo_models/**/*.json'))
        self.assertTrue(models)
        for model in models:
            with self.subTest(model=model):
                data = json.loads(model.read_text())
                for geometry in data['minecraft:geometry']:
                    desc = geometry['description']
                    self.assertEqual(len(desc['visible_bounds_offset']), 3)
                    self.assertGreater(desc['visible_bounds_width'], 0)
                    self.assertGreater(desc['visible_bounds_height'], 0)
                    self.assertGreater(desc['texture_width'], 0)
                    self.assertGreater(desc['texture_height'], 0)
                    self.assertTrue(geometry['bones'])

    def test_sprite_early_return_retains_properties_and_authored_transform(self):
        source = (ROOT / 'src/main/java/com/tacz/guns/client/renderer/item/TaczDynamicItemModel.java').read_text()
        update = source[source.index('    public void update('):source.index('    private static Object identityKeyOf(')]
        self.assertLess(update.index('this.properties.applyToLayer('), update.index('DroppedGunSprite.select('))
        self.assertIn('new DroppedGunSprite.Argument(sprite, this.transformation)', update)
        # Retain the separate full-rig path and its context-specific frozen stack.
        self.assertIn('new RenderArgument(stack.copy(), displayContext, this.transformation)', update)


if __name__ == '__main__':
    unittest.main()
