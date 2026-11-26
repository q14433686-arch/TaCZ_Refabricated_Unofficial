"""Offline authored-fixture validation only; native probes remain the runtime acceptance gate."""
import json
import re
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

    def test_cloth_labels_and_tooltips_exist_in_both_shipped_locales(self):
        keys = set()
        for name in ('client/RenderClothConfig.java', 'common/GunClothConfig.java', 'common/OtherClothConfig.java'):
            text = (ROOT / 'src/main/java/com/tacz/guns/compat/cloth' / name).read_text()
            keys.update(re.findall(r'Component.translatable\("([^"]+)"\)', text))
        for locale in ('en_us', 'zh_cn'):
            translations = json.loads((ROOT / f'src/main/resources/assets/tacz/lang/{locale}.json').read_text())
            with self.subTest(locale=locale):
                self.assertFalse(keys - translations.keys())
                self.assertTrue(all(isinstance(translations[key], str) and translations[key] for key in keys))

    def test_default_advancement_and_sidebar_labels_exist_in_both_locales(self):
        keys = {f'gui.tacz.creative_pack_filter.{name}' for name in ('all', 'previous', 'next')}
        advancements = list((ROOT / 'src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/advancement').glob('*.json'))
        self.assertEqual(len(advancements), 23)
        for path in advancements:
            display = json.loads(path.read_text())['display']
            keys.update(display[field]['translate'] for field in ('title', 'description'))
        self.assertEqual(len(keys), 49)
        for locale in ('en_us', 'zh_cn'):
            labels = json.loads((ROOT / f'src/main/resources/assets/tacz/lang/{locale}.json').read_text())
            with self.subTest(locale=locale):
                self.assertFalse(keys - labels.keys())
                self.assertTrue(all(isinstance(labels[key], str) and labels[key].strip() and labels[key] != key for key in keys))

    def test_sprite_early_return_retains_properties_and_authored_transform(self):
        source = (ROOT / 'src/main/java/com/tacz/guns/client/renderer/item/TaczDynamicItemModel.java').read_text()
        update = source[source.index('    public void update('):source.index('    private static Object identityKeyOf(')]
        self.assertLess(update.index('this.properties.applyToLayer('), update.index('DroppedGunSprite.select('))
        self.assertIn('new DroppedGunSprite.Argument(sprite, this.transformation)', update)
        # Retain the separate full-rig path and its context-specific frozen stack.
        self.assertIn('new RenderArgument(stack.copy(), displayContext, this.transformation)', update)


if __name__ == '__main__':
    unittest.main()
