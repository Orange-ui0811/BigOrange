"""Experimental, reversible README projections; never rewrites stored source documents."""
from array import array
from collections import Counter
import re

from markdown_it import MarkdownIt

PARSER = MarkdownIt('commonmark')
DECOR = '\U0001f680\U0001f389\U0001f38a\U0001f525\U0001f4a5\U0001f4ab\u2728\U0001f31f'
BADGE = re.compile(r'shields\.io|badge[./_-]|/badges/|badge\.svg|codecov\.io|img\.shields|github\.com/[^\s)]+/actions/[^\s)]+/badge', re.I)


class Projection:
    def __init__(self, text):
        self.original = text
        self.text = text
        self.mapping = array('i', range(len(text)))
        self.events = Counter()

    def edit(self, edits):
        """Non-overlapping (start,end,replacement,event); inserted text is not original evidence."""
        if not edits:
            return
        edits.sort(key=lambda v: (v[0], -v[1]))
        parts, mapping, pos = [], array('i'), 0
        for a, b, replacement, reason in edits:
            if replacement == self.text[a:b]:
                continue
            if a < pos:
                continue
            parts.append(self.text[pos:a]); mapping.extend(self.mapping[pos:a])
            parts.append(replacement); mapping.extend([-1] * len(replacement))
            self.events[reason] += 1
            pos = b
        parts.append(self.text[pos:]); mapping.extend(self.mapping[pos:])
        self.text = ''.join(parts); self.mapping = mapping

    def spans(self):
        out = []
        start = 0
        for i in range(1, len(self.mapping) + 1):
            if i < len(self.mapping) and ((self.mapping[start] == -1 and self.mapping[i] == -1)
                    or self.mapping[start] >= 0 and self.mapping[i] == self.mapping[i-1] + 1):
                continue
            if i > start:
                original = self.mapping[start]
                out.append({'start': start, 'end': i, 'originalStart': original,
                            'originalEnd': original + i - start if original >= 0 else -1})
            start = i
        return out


def code_ranges(text):
    starts = [0]
    for m in re.finditer('\n', text): starts.append(m.end())
    starts.append(len(text))
    ranges = []
    for token in PARSER.parse(text):
        if token.type in {'fence', 'code_block'} and token.map:
            a, b = token.map
            ranges.append((starts[a], starts[min(b, len(starts)-1)]))
    for m in re.finditer(r'(`+)([^\n]*?)\1', text):
        if not any(a <= m.start() < b for a, b in ranges): ranges.append(m.span())
    return sorted(ranges)


def protected(a, b, ranges):
    return any(a < y and b > x for x, y in ranges)


def outside_edits(p, pattern, replacement, reason, flags=0):
    ranges = code_ranges(p.text)
    edits = []
    for m in re.finditer(pattern, p.text, flags):
        if not protected(m.start(), m.end(), ranges):
            edits.append((m.start(), m.end(), replacement(m) if callable(replacement) else replacement, reason))
    p.edit(edits)


def unwrap_document(p):
    # Narrow repair: full-document markdown/html wrapper containing multiple Markdown headings,
    # not an ordinary HTML example. All interior bytes retain their original mapping.
    m = re.fullmatch(r'\s*(`{3,}|~{3,})(markdown|md|html)\s*\n([\s\S]*?)\n\1\s*', p.text, re.I)
    if not m:
        return
    inner = m.group(3)
    heading_count = len(re.findall(r'^ {0,3}#{1,6}\s+\S', inner, re.M))
    if heading_count >= 2 and not re.search(r'<(?:!doctype|html|head|body)\b', inner, re.I):
        p.edit([(0, m.start(3), '', 'unwrap_document'), (m.end(3), len(p.text), '', 'unwrap_document')])


def clean(text, variant='light'):
    p = Projection(text)
    if variant == 'raw': return p
    if variant not in {'light', 'structured', 'no_code_ablation', 'selective'}: raise ValueError(variant)
    if variant != 'light': unwrap_document(p)
    # Remove badge images, not the explanation/heading on the same line.
    outside_edits(p, r'\[!\[[^\]\n]*\]\([^\n]*?\)\]\([^\n]*?\)',
                  lambda m: '' if disposable_badge(m.group()) else m.group(), 'linked_badge')
    outside_edits(p, r'!\[[^\]\n]*\]\([^\n]*?\)',
                  lambda m: '' if disposable_badge(m.group()) else m.group(), 'badge')
    # Standalone reference-style status badges; textual license/copyright is kept elsewhere.
    if variant == 'selective':
        # Only an entire syntactically bounded badge; never consume prose after it.
        outside_edits(p, r'(?m)^[ \t]*\[!\[[^\]\n]*\]\[[^\]\n]*\]\](?:\[[^\]\n]*\]|\([^\n)]*\))[ \t]*$',
            lambda m: m.group() if re.search(r'licen[cs]e|copyright|security|OpenSSF|维护|maintenance|CLA|Covenant|DOI|foundation|基金会|许可证|版权',m.group(),re.I) else '', 'reference_badge')
    else:
        outside_edits(p, r'(?m)^[ \t]*\[!\[[^\n]*\]\[[^\n]*\]\][^\n]*$',
                      lambda m: m.group() if re.search(r'licen[cs]e|copyright|security', m.group(), re.I) else '', 'reference_badge')
        outside_edits(p, r'<!--(?:(?!-->)[\s\S])*-->',
                      lambda m: m.group() if re.search(r'SPDX|copyright|licen[cs]e|许可证|版权', m.group(), re.I) else '', 'comment')
    # Only explicitly decorative leading heading icons; status and warning icons remain untouched.
    outside_edits(p, r'(?m)(?<= )['+DECOR+r'\ufe0f]+(?=\s+)',
                  lambda m: '' if re.match(r'^\s*#{1,6}\s*$', p.text[p.text.rfind('\n', 0, m.start())+1:m.start()]) else m.group(), 'heading_decoration')
    if variant in {'structured', 'no_code_ablation'}:
        outside_edits(p, r'<(script|style)\b[^>]*>[\s\S]*?</\1\s*>', '', 'script_style', re.I)
        # Ordinary images remain intact: alt labels are not independently verified facts.
        # Preserve HTML tables and anchors too: cells and href values carry meaning.
        # Drop tag presentation; keep child text and code samples, preserve block separation.
        outside_edits(p, r'</?(?:p|div|section|article|details|summary|br|hr|span|b|strong|em|i)\b[^>]*>',
                      lambda m: '\n' if re.match(r'</?(?:p|div|section|article|details|summary|br|hr)\b', m.group(), re.I) else '', 'html_layout', re.I)
        # Keep non-badge URLs and link labels; they may encode API routes or license provenance.
        outside_edits(p, r'(?m)^[ \t]*\[(?:badge-[^\]]+|[^\]]*badge[^\]]*)\]:[^\n]*$',
                      lambda m: '' if disposable_badge(m.group()) else m.group(), 'badge_definition')
    if variant == 'no_code_ablation':
        ranges = code_ranges(p.text)
        p.edit([(a, b, '', 'REMOVE_CODE_ABLATION') for a, b in ranges])
    # Collapse blank separators only outside code; never change numeric/operator/negation text.
    outside_edits(p, r'\n(?:[ \t]*\n){2,}', '\n\n', 'blank_lines')
    return p


def disposable_badge(text):
    return bool(BADGE.search(text)) and not re.search(r'licen[cs]e|copyright|security|deprecated|unsupported|DOI|Covenant|foundation|基金会|许可证|版权', text, re.I)


def quality(text):
    stripped = text.strip()
    flags = []
    if not stripped: flags.append('EMPTY')
    if re.fullmatch(r'(?:\.{0,2}/)?[\w./-]*README(?:\.[\w-]+)?', stripped, re.I): flags.append('README_POINTER')
    if len(stripped) < 200: flags.append('SHORT_SOURCE')
    if re.search(r'(开发已迁移|项目已迁移|development.{0,20}moved|repository.{0,20}moved)', stripped, re.I): flags.append('MIGRATION_NOTICE')
    if re.search(r'(curated\s+(?:list|collection)|collection of.{0,20}(?:lists|resources)|资源合集|资源清单|精选.{0,10}列表|awesome-)', stripped[:3000], re.I): flags.append('RESOURCE_COLLECTION_HINT')
    return flags
