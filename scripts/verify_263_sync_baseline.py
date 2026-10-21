#!/usr/bin/env python3
"""Static checks for the 26.3 fixes ported to the 1.21.11 baseline (not a game test)."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/tacz/guns'
LOOT = ROOT / 'src/main/resources/data/tacz/loot_table/blocks'


class BaselineChecks(unittest.TestCase):
    def test_two_half_tables_drop_only_from_root_and_copy_block_id(self):
        for name, state in [('gun_smith_table', {'part': 'foot'}),
                            ('workbench_b', {'part': 'foot'}),
                            ('workbench_c', {'half': 'lower'})]:
            with self.subTest(table=name):
                data = json.loads((LOOT / f'{name}.json').read_text())
                self.assertEqual(data['type'], 'minecraft:block')
                entries = [e for pool in data['pools'] for e in pool['entries']]
                self.assertEqual(len(entries), 1)
                conditions = entries[0].get('conditions', [])
                self.assertTrue(any(c.get('condition') == 'minecraft:block_state_property'
                                    and c.get('properties') == state for c in conditions))
                functions = [f for pool in data['pools'] for f in pool.get('functions', [])]
                functions += entries[0].get('functions', [])
                if name != 'gun_smith_table':  # fixed base table has no per-pack BlockId
                    self.assertTrue(any(f.get('function') == 'minecraft:copy_custom_data'
                                    and any(op.get('source') == 'BlockId'
                                            and op.get('target') == 'BlockId' for op in f.get('ops', []))
                                    for f in functions))
        source = (JAVA / 'block/AbstractGunSmithTableBlock.java').read_text()
        body = source.split('BlockState playerWillDestroy(', 1)[1].split('public abstract boolean isRoot', 1)[0]
        body = re.sub(r'/\*.*?\*/|//[^\n]*', '', body, flags=re.S)
        self.assertNotIn('popResource(', body)

    def test_registry_codec_defers_legacy_ingredient_decode(self):
        codec = (JAVA / 'crafting/GunSmithTableSerializer.java').read_text()
        ingredient = (JAVA / 'crafting/GunSmithTableIngredient.java').read_text()
        self.assertIn('ExtraCodecs.JSON.fieldOf("item")', codec)
        self.assertNotIn('Ingredient.CODEC.fieldOf("item")', re.sub(r'//[^\n]*', '', codec))
        self.assertIn('GunSmithTableIngredient::new', codec)
        self.assertIn('Ingredient.CODEC.encodeStart(ops, resolved)', codec)
        self.assertIn('getRawItem()', ingredient)
        self.assertIn('normalizeLegacyIngredientJson(raw)', ingredient)


if __name__ == '__main__':
    unittest.main(verbosity=2)
