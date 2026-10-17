#!/usr/bin/env python3
"""Export the design previews as one offline HTML and ZIP (Python stdlib only)."""
from html import escape
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1] / 'docs'
DESIGN = ROOT / 'design'


def embedded_page(name):
    page = (DESIGN / name).read_text(encoding='utf-8')
    page = page.replace('<link rel="stylesheet" href="preview.css">',
                        '<style>' + (DESIGN / 'preview.css').read_text(encoding='utf-8') + '</style>')
    for script in ('preview.js', 'dashboard.js'):
        page = page.replace(f'<script src="{script}"></script>',
                            '<script>' + (DESIGN / script).read_text(encoding='utf-8') + '</script>')
    for layout in ('desktop', 'android'):
        page = page.replace(f'href="{layout}.html"',
                            f'href="#" onclick="parent.selectLayout(\'{layout}\');return false"')
    return escape(page, quote=True)


def export():
    document = '''<!doctype html><html lang="pt-PT"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Investment Radar — dashboards v2</title>
<style>body{margin:0;background:#20252e;color:#edf1f7;font:14px "Segoe UI",Arial,sans-serif}header{padding:14px 20px;display:flex;align-items:center;gap:18px;flex-wrap:wrap}header strong{margin-right:auto}button{padding:11px 20px;background:#333b49;color:#edf1f7;border:1px solid #5a6577;border-radius:7px;cursor:pointer}button.active{background:#c4ef8b;color:#182014}button:focus-visible{outline:2px solid #c4ef8b;outline-offset:3px}small{color:#c5cbd5}iframe{display:block;border:0;width:100%;height:calc(100dvh - 77px);background:#0b0e13}iframe.android{width:min(100%,430px);margin:auto;height:calc(100dvh - 77px)}[hidden]{display:none!important}</style>
<header><strong>Investment Radar · dashboards v2</strong><button class="active" data-layout="desktop" onclick="selectLayout('desktop')">Desktop</button><button data-layout="android" onclick="selectLayout('android')">Android</button><small>Dados de demonstração · sem APIs</small></header>
'''
    for name in ('desktop', 'android'):
        document += f'<iframe id="{name}" class="{name}" title="Layout {name}" srcdoc="{embedded_page(name + ".html")}" {"hidden" if name == "android" else ""}></iframe>\n'
    document += '''<script>function selectLayout(name){for(const id of ['desktop','android'])document.getElementById(id).hidden=id!==name;document.querySelectorAll('button[data-layout]').forEach(b=>b.classList.toggle('active',b.dataset.layout===name))}</script></html>'''
    for name in ('investment-radar-layouts.html', 'investment-radar-layouts-v2.html'):
        (ROOT / name).write_text(document, encoding='utf-8')
    for name in ('investment-radar-layouts.zip', 'investment-radar-layouts-v2.zip'):
        with zipfile.ZipFile(ROOT / name, 'w', zipfile.ZIP_DEFLATED) as bundle:
            for file in sorted(DESIGN.iterdir()):
                if file.is_file():
                    bundle.write(file, file.relative_to(ROOT))
            for file in ('UI_UX_RESEARCH.md', 'FRONTEND_DECISION.md', 'investment-radar-layouts-v2.html'):
                bundle.write(ROOT / file, file)
    print('Exported standalone HTML and ZIP, including v2 filenames.')


if __name__ == '__main__':
    export()
