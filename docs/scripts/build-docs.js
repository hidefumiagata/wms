#!/usr/bin/env node
/**
 * build-docs.js
 * WMSドキュメントポータルのHTMLを生成する
 *
 * 使い方:
 *   node docs/scripts/build-docs.js              # 全フォルダ再生成
 *   node docs/scripts/build-docs.js functional-requirements  # 指定フォルダのみ
 *
 * 仕様: docs/scripts/spec.md を参照
 */

const fs   = require('fs');
const path = require('path');
const https = require('https');

const SCRIPTS_DIR   = __dirname;
const DOCS_DIR      = path.join(SCRIPTS_DIR, '..');
const MERMAID_LIB   = path.join(SCRIPTS_DIR, 'lib', 'mermaid.min.js');
const MERMAID_CDN   = 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js';
const DRAWIO_LIB    = path.join(SCRIPTS_DIR, 'lib', 'viewer-static.min.js');
const DRAWIO_CDN    = 'https://viewer.diagrams.net/js/viewer-static.min.js';

const CATEGORIES = [
  { dir: 'project-plan',           title: 'プロジェクト計画書',           titleEn: 'Project Plan',            icon: '📋' },
  { dir: 'architecture-blueprint', title: 'アーキテクチャブループリント', titleEn: 'Architecture Blueprint',  icon: '🏗️' },
  { dir: 'functional-requirements', title: '機能要件定義書',               titleEn: 'Functional Requirements', icon: '📝' },
  { dir: 'data-model',             title: 'データモデル定義',              titleEn: 'Data Model',              icon: '🗄️' },
  { dir: 'functional-design',      title: '機能設計書',                   titleEn: 'Functional Design',       icon: '⚙️' },
  { dir: 'architecture-design',    title: 'アーキテクチャ設計書',          titleEn: 'Architecture Design',     icon: '🔧' },
  { dir: 'test-specifications',    title: 'テスト仕様書',                  titleEn: 'Test Specifications',     icon: '🧪' },
];

// ─── Markdown → HTML ────────────────────────────────────────────────────────

function escHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function inlineFormat(s) {
  s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>');
  s = s.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>');
  s = s.replace(/\*\*(.+?)\*\*/g,     '<strong>$1</strong>');
  s = s.replace(/\*([^*]+)\*/g,       '<em>$1</em>');
  s = s.replace(/~~(.+?)~~/g,         '<del>$1</del>');
  return s;
}

function mdToHtml(src, catDir) {
  // Normalize line endings
  src = src.replace(/\r\n/g, '\n');

  // 0. Extract drawio links (entire line → block-level placeholder)
  const drawioBlocks = [];
  src = src.replace(/^.*\[([^\]]*)\]\(([^)]+\.drawio)\).*$/gm, (_, label, diagramSrc) => {
    const i = drawioBlocks.length;
    drawioBlocks.push({ src: diagramSrc, label });
    return `\x00DIO${i}\x00`;
  });

  // 1. Extract fenced code blocks (including mermaid)
  const codeBlocks = [];
  src = src.replace(/```([^\n]*)\n([\s\S]*?)```/g, (_m, lang, code) => {
    const i = codeBlocks.length;
    const trimmed = code.replace(/\n$/, '');
    if (lang.trim() === 'mermaid') {
      codeBlocks.push(`<div class="mermaid">${trimmed}</div>`);
    } else {
      codeBlocks.push(`<pre><code class="lang-${escHtml(lang.trim())}">${escHtml(trimmed)}</code></pre>`);
    }
    return `\x00CODE${i}\x00`;
  });

  // 2. Extract inline code
  const inlineCodes = [];
  src = src.replace(/`([^`]+)`/g, (_m, code) => {
    const i = inlineCodes.length;
    inlineCodes.push(`<code>${escHtml(code)}</code>`);
    return `\x00IC${i}\x00`;
  });

  // 3. Tables
  src = src.replace(/((?:^\|.+\|\n)+)/gm, (tableBlock) => {
    const lines = tableBlock.trim().split('\n');
    let out = '<table>\n';
    let inBody = false;
    for (const line of lines) {
      if (/^\|[-|: ]+\|$/.test(line)) {
        out += '<tbody>\n';
        inBody = true;
        continue;
      }
      const cells = line.split('|').slice(1, -1).map(c => c.trim());
      if (!inBody) {
        out += '<thead><tr>' + cells.map(c => `<th>${inlineFormat(c)}</th>`).join('') + '</tr></thead>\n';
      } else {
        out += '<tr>' + cells.map(c => `<td>${inlineFormat(c)}</td>`).join('') + '</tr>\n';
      }
    }
    if (!inBody) out += '<tbody>\n</tbody>\n';
    else out += '</tbody>\n';
    out += '</table>';
    return out + '\n';
  });

  // 4. Headings
  src = src.replace(/^#### (.+)$/gm, (_, t) => `<h4>${inlineFormat(t)}</h4>`);
  src = src.replace(/^### (.+)$/gm,  (_, t) => `<h3>${inlineFormat(t)}</h3>`);
  src = src.replace(/^## (.+)$/gm,   (_, t) => `<h2>${inlineFormat(t)}</h2>`);
  src = src.replace(/^# (.+)$/gm,    (_, t) => `<h1>${inlineFormat(t)}</h1>`);

  // 5. Horizontal rules
  src = src.replace(/^---+$/gm, '<hr>');

  // 6. Blockquotes
  src = src.replace(/((?:^> .+\n?)+)/gm, (block) => {
    const inner = block.replace(/^> /gm, '').trim();
    return `<blockquote>${inlineFormat(inner)}</blockquote>\n`;
  });

  // 7. Unordered lists
  src = src.replace(/((?:^[ \t]*[-*] .+\n?)+)/gm, (block) => {
    const items = block.trim().split('\n').map(l => {
      const indent = (l.match(/^([ \t]*)/) || ['', ''])[1].length;
      const text   = l.replace(/^[ \t]*[-*] /, '');
      return `<li style="margin-left:${indent * 10}px">${inlineFormat(text)}</li>`;
    });
    return '<ul>' + items.join('') + '</ul>\n';
  });

  // 8. Ordered lists
  src = src.replace(/((?:^[ \t]*\d+\. .+\n?)+)/gm, (block) => {
    const items = block.trim().split('\n').map(l => {
      const text = l.replace(/^[ \t]*\d+\. /, '');
      return `<li>${inlineFormat(text)}</li>`;
    });
    return '<ol>' + items.join('') + '</ol>\n';
  });

  // 9. Paragraphs
  const lines = src.split('\n');
  const out   = [];
  let pBuf    = [];
  const flushP = () => {
    if (pBuf.length) {
      out.push(`<p>${pBuf.join('<br>')}</p>`);
      pBuf = [];
    }
  };
  for (const line of lines) {
    if (/^<[huptobr]|^\x00CODE|^\x00/.test(line) || line.trim() === '') {
      flushP();
      if (line.trim()) out.push(line);
    } else {
      pBuf.push(inlineFormat(line));
    }
  }
  flushP();
  src = out.join('\n');

  // 10. Restore placeholders
  codeBlocks.forEach((b, i)  => { src = src.replace(`\x00CODE${i}\x00`, b); });
  inlineCodes.forEach((b, i) => { src = src.replace(new RegExp(`\x00IC${i}\x00`, 'g'), b); });

  // 11. Restore drawio blocks (embed XML if catDir provided)
  drawioBlocks.forEach(({ src: diagramSrc, label }, i) => {
    let block;
    if (catDir) {
      const fullPath = path.join(catDir, diagramSrc);
      if (fs.existsSync(fullPath)) {
        const xml    = fs.readFileSync(fullPath, 'utf-8');
        const config = JSON.stringify({ highlight: '#0000ff', nav: true, resize: true, toolbar: 'zoom layers lightbox', xml });
        block = `<div class="drawio-container">
  <div class="drawio-title">📊 ${escHtml(label)}</div>
  <div class="mxgraph" style="max-width:100%;" data-mxgraph="${escHtml(config)}"></div>
</div>`;
      } else {
        block = `<p><a href="${escHtml(diagramSrc)}" target="_blank">📊 ${escHtml(label)}</a> <em style="color:#c00">(ファイルが見つかりません: ${escHtml(diagramSrc)})</em></p>`;
      }
    } else {
      block = `<p><a href="${escHtml(diagramSrc)}" target="_blank">📊 ${escHtml(label)}</a></p>`;
    }
    src = src.replace(`\x00DIO${i}\x00`, block);
  });

  return src;
}

// ─── Get markdown files in a folder ─────────────────────────────────────────

function getMdFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir)
    .filter(f => f.endsWith('.md'))
    .sort();
}

function getFirstLine(filePath) {
  const content = fs.readFileSync(filePath, 'utf-8');
  const firstLine = content.split(/\r?\n/)[0].trim();
  return firstLine.replace(/^#+\s*/, '');
}

// ─── Shared CSS ──────────────────────────────────────────────────────────────

const COMMON_CSS = `
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: 'Segoe UI', 'Hiragino Sans', 'Yu Gothic', sans-serif; background: #f5f7fa; color: #333; line-height: 1.7; }
  header { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%); color: white; padding: 40px 60px; }
  header h1 { font-size: 2rem; font-weight: 700; letter-spacing: 0.05em; }
  header p { margin-top: 8px; opacity: 0.8; font-size: 0.95rem; }
  nav.breadcrumb { background: white; padding: 12px 60px; border-bottom: 1px solid #e0e0e0; font-size: 0.9rem; }
  nav.breadcrumb a { color: #0f3460; text-decoration: none; }
  nav.breadcrumb a:hover { text-decoration: underline; }
  main { max-width: 1100px; margin: 40px auto; padding: 0 40px; }
  .toc { background: white; border-radius: 12px; padding: 20px 28px; margin-bottom: 32px; box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
  .toc strong { color: #1a1a2e; display: block; margin-bottom: 12px; font-size: 1rem; }
  .toc ol { padding-left: 24px; }
  .toc li { margin: 6px 0; }
  .toc a { color: #0f3460; text-decoration: none; }
  .toc a:hover { text-decoration: underline; }
  .doc-separator { border: none; border-top: 2px dashed #e0e8ff; margin: 48px 0; }
  .markdown-body { background: white; border-radius: 12px; padding: 48px 56px; box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
  .markdown-body h1 { font-size: 1.8rem; color: #1a1a2e; border-bottom: 3px solid #0f3460; padding-bottom: 12px; margin: 0 0 28px; }
  .markdown-body h2 { font-size: 1.3rem; color: #16213e; border-bottom: 1px solid #e0e0e0; padding-bottom: 8px; margin: 36px 0 16px; }
  .markdown-body h3 { font-size: 1.1rem; color: #333; margin: 24px 0 12px; }
  .markdown-body h4 { font-size: 1rem; color: #555; margin: 16px 0 8px; }
  .markdown-body table { width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 0.9rem; }
  .markdown-body th { background: #1a1a2e; color: white; padding: 10px 16px; text-align: left; }
  .markdown-body td { padding: 10px 16px; border-bottom: 1px solid #f0f0f0; }
  .markdown-body tr:hover td { background: #f8f9ff; }
  .markdown-body code { background: #f0f4ff; color: #0f3460; padding: 2px 8px; border-radius: 4px; font-size: 0.88em; font-family: 'Consolas', monospace; }
  .markdown-body pre { background: #1a1a2e; color: #e0e0e0; padding: 20px 24px; border-radius: 8px; overflow-x: auto; margin: 16px 0; }
  .markdown-body pre code { background: none; color: inherit; padding: 0; }
  .markdown-body blockquote { border-left: 4px solid #0f3460; background: #f0f4ff; padding: 12px 20px; margin: 16px 0; border-radius: 0 8px 8px 0; }
  .markdown-body ul, .markdown-body ol { padding-left: 24px; margin: 12px 0; }
  .markdown-body li { margin: 4px 0; }
  .markdown-body p { margin: 12px 0; }
  .markdown-body hr { border: none; border-top: 1px solid #ddd; margin: 24px 0; }
  .markdown-body a { color: #0f3460; }
  .mermaid { background: #f8f9ff; border-radius: 8px; padding: 24px; margin: 16px 0; text-align: center; overflow-x: auto; }
  .drawio-container { margin: 20px 0; border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden; }
  .drawio-title { background: #f0f4ff; color: #1a1a2e; font-size: 0.88rem; font-weight: 600; padding: 8px 16px; border-bottom: 1px solid #e0e0e0; }
  .drawio-container .mxgraph { background: white; padding: 16px; border-radius: 0; border: none; margin: 0; text-align: left; }
  footer { text-align: center; padding: 32px; color: #aaa; font-size: 0.85rem; margin-top: 60px; border-top: 1px solid #e0e0e0; }
`;

const MERMAID_SCRIPT = `
  <script src="../scripts/lib/mermaid.min.js"></script>
  <script>
    mermaid.initialize({ startOnLoad: false, theme: 'default' });
    document.addEventListener('DOMContentLoaded', function () {
      mermaid.run({ nodes: document.querySelectorAll('.mermaid') });
    });
  </script>
`;

const DRAWIO_SCRIPT = `
  <script src="../scripts/lib/viewer-static.min.js"></script>
`;

// ─── Category page builder ───────────────────────────────────────────────────

function buildCategoryPage(cat) {
  const catDir  = path.join(DOCS_DIR, cat.dir);
  const mdFiles = getMdFiles(catDir);
  const hasMermaid = mdFiles.some(f => {
    const content = fs.readFileSync(path.join(catDir, f), 'utf-8');
    return content.includes('```mermaid');
  });
  const hasDrawio = mdFiles.some(f => {
    const content = fs.readFileSync(path.join(catDir, f), 'utf-8');
    return content.includes('.drawio)');
  });

  const sections = mdFiles.map(f => {
    const filePath = path.join(catDir, f);
    const id       = f.replace('.md', '');
    const title    = getFirstLine(filePath);
    const md       = fs.readFileSync(filePath, 'utf-8');
    const html     = mdToHtml(md, catDir);
    return { id, title, html };
  });

  const tocItems = sections.map((s, i) =>
    `<li><a href="#${s.id}">${i + 1}. ${escHtml(s.title)}</a></li>`
  ).join('\n        ');

  const sectionsHtml = sections.map(s => `
<hr class="doc-separator">
<div class="markdown-body" id="${s.id}">
${s.html}
</div>`).join('\n');

  const mockupsLink = cat.dir === 'functional-design' ? `
  <div style="text-align:right; margin-bottom: 24px;">
    <a href="mockups/index.html" style="display:inline-block; background:#0f3460; color:white; padding:10px 20px; border-radius:8px; text-decoration:none; font-size:0.9rem;">
      🖥️ モックアップを見る
    </a>
  </div>` : '';

  const mermaidBlock = hasMermaid ? MERMAID_SCRIPT : '';
  const drawioBlock  = hasDrawio  ? DRAWIO_SCRIPT  : '';

  return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${escHtml(cat.title)} — WMS ShowCase</title>
  <style>${COMMON_CSS}</style>
</head>
<body>
<header>
  <h1>🏭 WMS ShowCase</h1>
  <p>Warehouse Management System — Built with Claude Code</p>
</header>
<nav class="breadcrumb">
  <a href="../index.html">Home</a> &rsaquo; ${escHtml(cat.title)}
</nav>
<main>
  <div style="margin-bottom:24px;">
    <h1 style="font-size:1.6rem;color:#1a1a2e;">${cat.icon} ${escHtml(cat.title)}</h1>
    <p style="color:#888;margin-top:8px;font-size:0.9rem;">${escHtml(cat.titleEn)}</p>
  </div>
  ${mockupsLink}
  <div class="toc">
    <strong>目次</strong>
    <ol>
        ${tocItems}
    </ol>
  </div>
${sectionsHtml}
</main>
<footer>WMS ShowCase — Generated by build-docs.js</footer>
${drawioBlock}${mermaidBlock}
</body>
</html>`;
}

// ─── Mockups page builder ─────────────────────────────────────────────────────

function buildMockupsPage() {
  const mockupsDir = path.join(DOCS_DIR, 'functional-design', 'mockups');
  if (!fs.existsSync(mockupsDir)) {
    console.warn('⚠  mockups/ フォルダが見つかりません。スキップします。');
    return;
  }

  const htmlFiles = fs.readdirSync(mockupsDir)
    .filter(f => f.endsWith('.html') && !f.startsWith('_') && f !== 'index.html')
    .sort();

  const mockups = htmlFiles.map(f => {
    const content = fs.readFileSync(path.join(mockupsDir, f), 'utf-8');
    const m = content.match(/<title>([^<]+)<\/title>/i);
    const title = m ? m[1].trim() : f.replace('.html', '');
    return { file: f, title };
  });

  const sidebarItems = mockups.map(m =>
    `<div class="sidebar-item" onclick="scrollTo('frame-${escHtml(m.file)}')">${escHtml(m.title)}</div>`
  ).join('\n      ');

  const frames = mockups.map(m => `
    <div class="mockup-section" id="section-${escHtml(m.file)}">
      <div class="mockup-title">${escHtml(m.title)}</div>
      <iframe
        id="frame-${escHtml(m.file)}"
        src="${escHtml(m.file)}"
        class="mockup-frame"
        scrolling="no"
        onload="resizeFrame(this)"
        title="${escHtml(m.title)}"
      ></iframe>
    </div>`).join('\n');

  return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>モックアップ — WMS ShowCase</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Segoe UI', 'Hiragino Sans', 'Yu Gothic', sans-serif; background: #f5f7fa; color: #333; display: flex; flex-direction: column; height: 100vh; overflow: hidden; }
    header { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%); color: white; padding: 18px 32px; flex-shrink: 0; display: flex; align-items: center; gap: 16px; }
    header h1 { font-size: 1.3rem; font-weight: 700; }
    header a { color: rgba(255,255,255,0.7); text-decoration: none; font-size: 0.85rem; }
    header a:hover { color: white; }
    .layout { display: flex; flex: 1; overflow: hidden; }
    .sidebar { width: 280px; min-width: 280px; background: white; border-right: 1px solid #e0e0e0; overflow-y: auto; flex-shrink: 0; }
    .sidebar-header { padding: 12px 16px; font-size: 0.72rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.1em; color: white; background: #1a1a2e; position: sticky; top: 0; }
    .sidebar-item { padding: 10px 16px; cursor: pointer; border-left: 3px solid transparent; border-bottom: 1px solid #f5f5f5; font-size: 0.82rem; color: #333; transition: background 0.1s; }
    .sidebar-item:hover { background: #f0f4ff; border-left-color: #0f3460; }
    .content { flex: 1; overflow-y: auto; padding: 32px 40px; }
    .mockup-section { margin-bottom: 48px; }
    .mockup-title { font-size: 1rem; font-weight: 700; color: #1a1a2e; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 2px solid #0f3460; }
    .mockup-frame { width: 100%; border: 1px solid #e0e0e0; border-radius: 8px; display: block; background: white; min-height: 400px; }
    footer { text-align: center; padding: 16px; color: #aaa; font-size: 0.8rem; background: white; border-top: 1px solid #e0e0e0; flex-shrink: 0; }
  </style>
</head>
<body>
  <header>
    <div>
      <h1>🖥️ モックアップ</h1>
    </div>
    <a href="../index.html">← 機能設計書に戻る</a>
  </header>
  <div class="layout">
    <div class="sidebar">
      <div class="sidebar-header">画面一覧（${mockups.length}画面）</div>
      ${sidebarItems}
    </div>
    <div class="content" id="main-content">
${frames}
    </div>
  </div>
  <footer>WMS ShowCase — Generated by build-docs.js</footer>
  <script>
    function resizeFrame(iframe) {
      try {
        const h = iframe.contentDocument.documentElement.scrollHeight;
        if (h > 100) iframe.style.height = h + 'px';
      } catch (e) {
        iframe.style.height = '800px';
      }
    }
    function scrollTo(frameId) {
      const el = document.getElementById('section-' + frameId.replace('frame-', ''));
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  </script>
</body>
</html>`;
}

// ─── Main portal builder ──────────────────────────────────────────────────────

function buildMainPortal() {
  const cards = CATEGORIES.map(cat => {
    const catDir  = path.join(DOCS_DIR, cat.dir);
    const mdCount = getMdFiles(catDir).length;
    const countStr = mdCount > 0 ? `${mdCount} ファイル` : '準備中';
    const link = mdCount > 0 ? `${cat.dir}/index.html` : '#';
    const style = mdCount > 0 ? '' : 'opacity:0.5; pointer-events:none;';
    return `
    <a href="${link}" class="card" style="${style}">
      <div class="card-icon">${cat.icon}</div>
      <div class="card-title">${escHtml(cat.title)}</div>
      <div class="card-en">${escHtml(cat.titleEn)}</div>
      <div class="card-count">${countStr}</div>
    </a>`;
  }).join('\n');

  const generatedAt = new Date().toLocaleString('ja-JP');

  return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>WMS ShowCase — Documentation</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Segoe UI', 'Hiragino Sans', 'Yu Gothic', sans-serif; background: #f5f7fa; color: #333; min-height: 100vh; }
    header { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%); color: white; padding: 60px; text-align: center; }
    header h1 { font-size: 2.5rem; font-weight: 700; letter-spacing: 0.05em; }
    header p { margin-top: 12px; opacity: 0.8; font-size: 1rem; }
    main { max-width: 1100px; margin: 60px auto; padding: 0 40px; }
    h2 { font-size: 1.2rem; color: #1a1a2e; margin-bottom: 24px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; }
    .card { display: block; background: white; border-radius: 12px; padding: 28px; text-decoration: none; color: inherit; box-shadow: 0 2px 12px rgba(0,0,0,0.08); transition: box-shadow 0.2s, transform 0.2s; }
    .card:hover { box-shadow: 0 8px 24px rgba(0,0,0,0.15); transform: translateY(-2px); }
    .card-icon { font-size: 2rem; margin-bottom: 12px; }
    .card-title { font-size: 1rem; font-weight: 700; color: #1a1a2e; }
    .card-en { font-size: 0.78rem; color: #888; margin-top: 4px; }
    .card-count { font-size: 0.8rem; color: #0f3460; margin-top: 12px; font-weight: 600; }
    footer { text-align: center; padding: 32px; color: #aaa; font-size: 0.85rem; margin-top: 60px; border-top: 1px solid #e0e0e0; }
  </style>
</head>
<body>
<header>
  <h1>🏭 WMS ShowCase</h1>
  <p>Warehouse Management System — Built with Claude Code</p>
</header>
<main>
  <h2>📚 ドキュメント一覧</h2>
  <div class="grid">
${cards}
  </div>
</main>
<footer>WMS ShowCase — Generated by Claude Code — ${generatedAt}</footer>
</body>
</html>`;
}

// ─── Library downloader ───────────────────────────────────────────────────────

function downloadFile(url, dest, label) {
  return new Promise((resolve) => {
    if (fs.existsSync(dest)) { resolve(true); return; }
    const libDir = path.dirname(dest);
    if (!fs.existsSync(libDir)) fs.mkdirSync(libDir, { recursive: true });
    console.log(`  📥 ${label} をダウンロード中...`);
    const attempt = (u) => {
      const file = fs.createWriteStream(dest);
      https.get(u, res => {
        if (res.statusCode === 301 || res.statusCode === 302) {
          file.close();
          fs.unlink(dest, () => {});
          attempt(res.headers.location);
          return;
        }
        res.pipe(file);
        file.on('finish', () => {
          file.close();
          console.log(`  ✓  ${label} ダウンロード完了`);
          resolve(true);
        });
      }).on('error', () => {
        fs.unlink(dest, () => {});
        console.warn(`  ⚠  ${label} のダウンロードに失敗しました。`);
        resolve(false);
      });
    };
    attempt(url);
  });
}

function ensureMermaid()      { return downloadFile(MERMAID_CDN, MERMAID_LIB, 'mermaid.min.js'); }
function ensureDrawioViewer() { return downloadFile(DRAWIO_CDN,  DRAWIO_LIB,  'viewer-static.min.js (diagrams.net)'); }

// ─── Main ────────────────────────────────────────────────────────────────────

async function main() {
  const target = process.argv[2];

  // Determine what to build
  let categoriesToBuild;
  if (target) {
    categoriesToBuild = CATEGORIES.filter(c => c.dir === target);
    if (categoriesToBuild.length === 0) {
      console.error(`エラー: フォルダ "${target}" は未定義です。`);
      console.error('有効なフォルダ:', CATEGORIES.map(c => c.dir).join(', '));
      process.exit(1);
    }
  } else {
    categoriesToBuild = CATEGORIES;
  }

  // Check if any category needs mermaid / drawio
  let needsMermaid = false, needsDrawio = false;
  for (const cat of categoriesToBuild) {
    const catDir = path.join(DOCS_DIR, cat.dir);
    for (const f of getMdFiles(catDir)) {
      const content = fs.readFileSync(path.join(catDir, f), 'utf-8');
      if (content.includes('```mermaid'))  needsMermaid = true;
      if (content.includes('.drawio)'))    needsDrawio  = true;
    }
  }

  await Promise.all([
    needsMermaid ? ensureMermaid()      : Promise.resolve(),
    needsDrawio  ? ensureDrawioViewer() : Promise.resolve(),
  ]);

  // Build category pages
  for (const cat of categoriesToBuild) {
    const catDir = path.join(DOCS_DIR, cat.dir);
    const mdFiles = getMdFiles(catDir);
    if (mdFiles.length === 0) {
      console.log(`⚪ ${cat.dir}/ — .md ファイルなし、スキップ`);
      continue;
    }
    const html    = buildCategoryPage(cat);
    const outPath = path.join(catDir, 'index.html');
    fs.writeFileSync(outPath, html, 'utf-8');
    const kb = (fs.statSync(outPath).size / 1024).toFixed(0);
    console.log(`✓  ${cat.dir}/index.html (${kb} KB, ${mdFiles.length} files)`);

    // Build mockups page for functional-design
    if (cat.dir === 'functional-design') {
      const mockupsHtml = buildMockupsPage();
      if (mockupsHtml) {
        const mockupsOut = path.join(catDir, 'mockups', 'index.html');
        fs.writeFileSync(mockupsOut, mockupsHtml, 'utf-8');
        const mkb = (fs.statSync(mockupsOut).size / 1024).toFixed(0);
        console.log(`✓  functional-design/mockups/index.html (${mkb} KB)`);
      }
    }
  }

  // Build main portal (only when building all, or explicitly)
  if (!target) {
    const portalHtml = buildMainPortal();
    const portalOut  = path.join(DOCS_DIR, 'index.html');
    fs.writeFileSync(portalOut, portalHtml, 'utf-8');
    const kb = (fs.statSync(portalOut).size / 1024).toFixed(0);
    console.log(`✓  index.html (${kb} KB)`);
  }

  console.log('\n✅ 完了！');
}

main().catch(err => { console.error(err); process.exit(1); });
