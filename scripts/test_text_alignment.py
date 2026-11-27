#!/usr/bin/env python3
"""Offline regression tests for the 26.3 upstream-text guards (no fetched refs needed)."""
import contextlib
import io
import json
import subprocess
import sys
import unittest
from unittest.mock import patch

import align_config_text as config
import port_upstream_lang as lang


class TextAlignmentTest(unittest.TestCase):
    def test_config_reads_multiline_comments_defaults_and_ranges(self):
        parsed = config.keys('''
            builder.push("render");
            builder.comment("First line", "Second line");
            VALUE = builder.defineInRange("Value", 0.1, 0, 1);
            FLAG = builder.comment("Flag").define("Flag", true);
            builder.pop();
        ''')
        self.assertEqual(parsed['render.Value']['comment'], 'First line\nSecond line')
        self.assertEqual(parsed['render.Value']['default'], '0.1, 0, 1')
        self.assertEqual(parsed['render.Flag']['comment'], 'Flag')
        self.assertEqual(config.norm_default('1.0, 0.0, (double) Float.MAX_VALUE'), '1.0, 0.0, Float.MAX_VALUE')

    def config_check(self, upstream, local):
        with patch.object(config, 'collect', side_effect=[upstream, local]), patch.object(sys, 'argv', ['align_config_text.py']), contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(SystemExit) as result:
                config.main()
        return result.exception.code

    def test_registered_comment_difference_does_not_hide_default_change(self):
        key = ('RenderConfig.java', 'render.CrosshairType')
        upstream = {key: {'comment': 'upstream', 'default': 'CrosshairType.DOT_1'}}
        local = {key: {'comment': 'local', 'default': 'CrosshairType.DOT_1'}}
        self.assertEqual(self.config_check(upstream, local), 0)
        local[key]['default'] = 'CrosshairType.EMPTY'
        self.assertEqual(self.config_check(upstream, local), 1)

    def test_missing_and_unregistered_comment_changes_fail(self):
        key = ('AmmoConfig.java', 'ammo.DestroyGlass')
        upstream = {key: {'comment': 'accurate', 'default': 'true'}}
        self.assertEqual(self.config_check(upstream, {}), 1)
        self.assertEqual(self.config_check(upstream, {key: {'comment': 'outdated', 'default': 'true'}}), 1)

    def build_lang(self, locale, upstream, local):
        with patch.object(lang, 'git', return_value=json.dumps(upstream)), patch.object(lang, 'effective', return_value=local):
            return lang.build(locale)[0]

    def test_hit_description_is_corrected_and_other_locales_fall_back(self):
        key = lang.HIT_DESCRIPTION
        for locale in ('en_us.json', 'zh_cn.json'):
            self.assertEqual(self.build_lang(locale, {key: 'wrong'}, {key: 'wrong'})[key], lang.OWN_TEXT[locale, key])
        self.assertNotIn(key, self.build_lang('zh_tw.json', {key: 'wrong'}, {key: 'wrong'}))

    def test_local_view_settings_are_preserved_without_hiding_unrelated_drift(self):
        key = 'config.tacz.client.render.crosshair_type.desc'
        result = self.build_lang('en_us.json', {key: 'upstream', 'other': 'upstream'}, {key: 'local editor', 'other': 'wrong', 'addon.key': 'keep'})
        self.assertEqual(result, {key: 'local editor', 'other': 'upstream', 'addon.key': 'keep'})

    def test_labels_adapt_to_fabric_263_and_absent_features_are_removed(self):
        key = 'message.tacz.converter.universal.skipped'
        self.assertIn('26.3', lang.adapt(key, 'Minecraft 1.21.1 overlay'))
        self.assertEqual(lang.adapt('tag', '#forge:glass #forge:glass_panes'), '#c:glass_blocks #forge:glass_panes')
        absent = next(iter(lang.ABSENT_FEATURE))
        self.assertNotIn(absent, self.build_lang('en_us.json', {absent: 'not supported'}, {absent: 'not supported'}))

    def test_bad_upstream_ref_fails_closed(self):
        def failed(*args, **kwargs):
            raise subprocess.CalledProcessError(128, args)
        with patch.object(subprocess, 'run', side_effect=failed):
            with self.assertRaises(subprocess.CalledProcessError):
                config.git('show', 'missing-ref:file')
            with self.assertRaises(subprocess.CalledProcessError):
                lang.git('show', 'missing-ref:file')


if __name__ == '__main__':
    unittest.main()
