import unittest
from rag_readme_cleaning import clean, quality


class CleanerTest(unittest.TestCase):
    def test_meaning_preserved(self):
        text = '# 🚀 Demo\nRequires Python >=3.10; not Windows. ❌ Docker ✅ Linux ⚠ deprecated\n```python\n# 🚀 not a heading\nx = "![b](https://shields.io/a)"\n```\n`a != b`\n|RAM|Version|\n|--|--|\n|2GB|v1.2|\n'
        for variant in ('light', 'structured'):
            p = clean(text, variant)
            for part in ('Python >=3.10; not Windows', '❌ Docker ✅ Linux ⚠ deprecated', '# 🚀 not a heading', 'a != b', '|2GB|v1.2|'):
                self.assertIn(part, p.text)
            self.assertNotIn('# 🚀 Demo', p.text)
            self.assert_mapping(p)

    def assert_mapping(self, p):
        for s in p.spans():
            if s['originalStart'] >= 0:
                self.assertEqual(p.text[s['start']:s['end']], p.original[s['originalStart']:s['originalEnd']])

    def test_badges_license_and_image(self):
        text = '# app\n[![build](https://shields.io/build)](https://ci.org)\n![License MIT](https://shields.io/license)\n![architecture](diagram.png)\n[docs](https://example.org)'
        p = clean(text, 'structured')
        self.assertNotIn('https://ci.org', p.text)
        self.assertIn('License MIT', p.text)
        self.assertIn('![architecture](diagram.png)', p.text)
        self.assertIn('[docs](https://example.org)', p.text)
        self.assert_mapping(p)

    def test_wrapper(self):
        text = '```html\n# Music\nserver\n## Install\nDocker\n```'
        p = clean(text, 'structured')
        self.assertEqual(p.text, '# Music\nserver\n## Install\nDocker')
        self.assert_mapping(p)
        actual = '```html\n<html>\n# first\n## second\n</html>\n```'
        self.assertEqual(clean(actual, 'structured').text, actual)

    def test_noop_mapping_and_html_table(self):
        text = '<!-- MIT license -->\n<table><tr><td>RAM</td><td>2GB</td></tr></table>'
        p = clean(text, 'structured')
        self.assertEqual(p.text, text)
        self.assertEqual(list(p.mapping), list(range(len(text))))

    def test_does_not_invent_content(self):
        self.assertEqual(clean('./app/README.md', 'structured').text, './app/README.md')
        self.assertIn('README_POINTER', quality('./app/README.md'))

    def test_ablation_demonstrates_loss(self):
        text = '# API\n```js\ngui.addColor(obj, "color");\n```\n'
        self.assertIn('addColor', clean(text, 'structured').text)
        self.assertNotIn('addColor', clean(text, 'no_code_ablation').text)

    def test_selective_keeps_badge_neighbor_and_warning(self):
        text='[![build][status]][ci] Requires Python 3.10\n<!-- Warning: do not use master -->\n[![License][license]][url]\n'
        p=clean(text,'selective')
        self.assertEqual(p.text,text)
        self.assert_mapping(p)

    def test_selective_keeps_semantic_html(self):
        text='<details><summary>Hardware requirements</summary>RAM >= 2GB</details>'
        self.assertEqual(clean(text,'selective').text,text)

if __name__ == '__main__':
    unittest.main()
