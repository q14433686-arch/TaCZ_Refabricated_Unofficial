"""Harness contract checks only, not a substitute for the actual packaged runtime audit."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
SPEC = importlib.util.spec_from_file_location('player_probe', SCRIPTS / 'test_12111_player.py')
PLAYER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PLAYER)

PIPELINE = '''public class Pipeline {
  public void beginHand();
    Code:
       0: invokevirtual // RenderTargets.copyPreHandDepth:()V
  public void beginTranslucents();
    Code:
       0: return
  public void finalizeLevelRendering();
    Code:
       0: iconst_0
       1: putfield // isRenderingWorld:Z
       2: invokevirtual // CompositeRenderer.renderAll:()V
       3: invokevirtual // FinalPassRenderer.renderFinalPass:()V
}
'''
HAND = '''public class HandRenderer {
  public void renderSolid();
    Code:
       0: invokevirtual // iris$renderHandsWithCustomRenderer
       1: invokestatic // RenderSystem.restoreProjectionMatrix
  public void renderTranslucent();
    Code:
       0: invokevirtual // iris$renderHandsWithCustomRenderer
       1: invokestatic // RenderSystem.restoreProjectionMatrix
}
'''


class IrisRuntimeAuditTest(unittest.TestCase):
    def audit(self, pipeline=PIPELINE, hand=HAND):
        with tempfile.TemporaryDirectory() as temp, patch.object(PLAYER.subprocess, 'check_output', side_effect=[pipeline, hand]):
            PLAYER.audit_iris_hooks(Path('runtime.jar'), Path(temp))
            text = (Path(temp) / 'iris-runtime-hooks.txt').read_text()
            self.assertIn('copyPreHandDepth', text)
            self.assertNotIn('beginTranslucents', text)

    def test_expected_sections_are_saved(self):
        self.audit()

    def test_world_composite_order_must_match(self):
        with self.assertRaises(AssertionError):
            self.audit(PIPELINE.replace('CompositeRenderer.renderAll', 'FinalPassRenderer.renderFinalPass')
                       .replace('3: invokevirtual // FinalPassRenderer.renderFinalPass', '3: invokevirtual // CompositeRenderer.renderAll'))

    def test_prehand_depth_boundary_required(self):
        with self.assertRaises(AssertionError):
            self.audit(PIPELINE.replace('copyPreHandDepth', 'copyPreTranslucentDepth'))

    def test_each_hand_pass_must_restore_projection(self):
        with self.assertRaises(AssertionError):
            self.audit(hand=HAND.replace('RenderSystem.restoreProjectionMatrix', 'other', 1))


if __name__ == '__main__':
    unittest.main()
