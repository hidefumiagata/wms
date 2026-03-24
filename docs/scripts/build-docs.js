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

// functional-design のサブカテゴリ定義
const FUNCTIONAL_DESIGN_SUBCATS = [
  {
    id: 'screen',
    title: '画面設計',
    titleEn: 'Screen Design',
    icon: '🖥️',
    mdPattern:     f => f.startsWith('SCR-'),
    reviewPattern: f => f.startsWith('SCR-'),
  },
  {
    id: 'api',
    title: 'API設計',
    titleEn: 'API Design',
    icon: '🔌',
    mdPattern:     f => f.startsWith('API-'),
    reviewPattern: f => f.startsWith('API-'),
  },
  {
    id: 'report',
    title: '帳票設計',
    titleEn: 'Report Design',
    icon: '📊',
    mdPattern:     f => f.startsWith('RPT-'),
    reviewPattern: f => f.startsWith('RPT-'),
  },
  {
    id: 'other',
    title: 'その他設計',
    titleEn: 'Other Design',
    icon: '📄',
    mdPattern:     f => !f.startsWith('SCR-') && !f.startsWith('API-') && !f.startsWith('RPT-') && !f.startsWith('_'),
    reviewPattern: f => !f.startsWith('SCR-') && !f.startsWith('API-') && !f.startsWith('RPT-'),
  },
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
    .filter(f => f.endsWith('.md') && !f.startsWith('_'))
    .sort();
}

function getReviewsMdFiles(reviewsDir) {
  if (!fs.existsSync(reviewsDir)) return [];
  return fs.readdirSync(reviewsDir)
    .filter(f => f.endsWith('.md') && !f.endsWith('.tmp.md'))
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

// reviews/ サブフォルダ用（../../scripts/lib/ 相対パス）
const MERMAID_SCRIPT_REVIEWS = `
  <script src="../../scripts/lib/mermaid.min.js"></script>
  <script>
    mermaid.initialize({ startOnLoad: false, theme: 'default' });
    document.addEventListener('DOMContentLoaded', function () {
      mermaid.run({ nodes: document.querySelectorAll('.mermaid') });
    });
  </script>
`;

const DRAWIO_SCRIPT_REVIEWS = `
  <script src="../../scripts/lib/viewer-static.min.js"></script>
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

  const reviewsDir = path.join(catDir, 'reviews');
  const reviewsLink = getReviewsMdFiles(reviewsDir).length > 0 ? `
  <div style="text-align:right; margin-bottom: 24px;">
    <a href="reviews/index.html" style="display:inline-block; background:#16213e; color:white; padding:10px 20px; border-radius:8px; text-decoration:none; font-size:0.9rem;">
      📋 レビュー記録を見る
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
  ${reviewsLink}
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

// ─── Reviews page builder ─────────────────────────────────────────────────────
// opts: { files?: string[], pageTitle?: string, backLink?: string }
// files が指定されない場合は全件。backLink は「戻る」ボタンのhref。

function buildReviewsPage(cat, opts = {}) {
  const reviewsDir = path.join(DOCS_DIR, cat.dir, 'reviews');
  const allFiles   = getReviewsMdFiles(reviewsDir);
  const mdFiles    = opts.files || allFiles;
  if (mdFiles.length === 0) return null;

  const pageTitle  = opts.pageTitle || `${cat.title} — レビュー記録`;
  const backLink   = opts.backLink  || '../index.html';

  const hasMermaid = mdFiles.some(f => {
    const content = fs.readFileSync(path.join(reviewsDir, f), 'utf-8');
    return content.includes('```mermaid');
  });
  const hasDrawio = mdFiles.some(f => {
    const content = fs.readFileSync(path.join(reviewsDir, f), 'utf-8');
    return content.includes('.drawio)');
  });

  const sections = mdFiles.map(f => {
    const filePath = path.join(reviewsDir, f);
    const id       = f.replace('.md', '');
    const title    = getFirstLine(filePath);
    const md       = fs.readFileSync(filePath, 'utf-8');
    const html     = mdToHtml(md, reviewsDir);
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

  const mermaidBlock = hasMermaid ? MERMAID_SCRIPT_REVIEWS : '';
  const drawioBlock  = hasDrawio  ? DRAWIO_SCRIPT_REVIEWS  : '';

  return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${escHtml(pageTitle)} — WMS ShowCase</title>
  <style>${COMMON_CSS}</style>
</head>
<body>
<header>
  <h1>🏭 WMS ShowCase</h1>
  <p>Warehouse Management System — Built with Claude Code</p>
</header>
<nav class="breadcrumb">
  <a href="../../index.html">Home</a> &rsaquo; <a href="../index.html">${escHtml(cat.title)}</a> &rsaquo; ${escHtml(pageTitle)}
</nav>
<main>
  <div style="margin-bottom:24px; display:flex; align-items:center; justify-content:space-between;">
    <div>
      <h1 style="font-size:1.6rem;color:#1a1a2e;">📋 ${escHtml(pageTitle)}</h1>
      <p style="color:#888;margin-top:8px;font-size:0.9rem;">${mdFiles.length} 件のレビュー記録票</p>
    </div>
    <a href="${escHtml(backLink)}" style="display:inline-block; background:#16213e; color:white; padding:10px 20px; border-radius:8px; text-decoration:none; font-size:0.9rem;">
      ← ドキュメントに戻る
    </a>
  </div>
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

// ─── Functional Design sub-page builder ───────────────────────────────────────

function buildFunctionalDesignSubPage(cat, subcat, mdFiles) {
  const catDir = path.join(DOCS_DIR, cat.dir);

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

  // モックアップリンクは画面設計のみ
  const mockupsLink = subcat.id === 'screen' ? `
  <div style="text-align:right; margin-bottom: 24px;">
    <a href="mockups/index.html" style="display:inline-block; background:#0f3460; color:white; padding:10px 20px; border-radius:8px; text-decoration:none; font-size:0.9rem;">
      🖥️ モックアップを見る
    </a>
  </div>` : '';

  // レビューリンク（サブカテゴリごと）
  const reviewsDir  = path.join(catDir, 'reviews');
  const reviewFiles = getReviewsMdFiles(reviewsDir).filter(f => subcat.reviewPattern(f));
  const reviewsLink = reviewFiles.length > 0 ? `
  <div style="text-align:right; margin-bottom: 24px;">
    <a href="reviews/index-${subcat.id}.html" style="display:inline-block; background:#16213e; color:white; padding:10px 20px; border-radius:8px; text-decoration:none; font-size:0.9rem;">
      📋 レビュー記録を見る
    </a>
  </div>` : '';

  const mermaidBlock = hasMermaid ? MERMAID_SCRIPT : '';
  const drawioBlock  = hasDrawio  ? DRAWIO_SCRIPT  : '';

  return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${escHtml(cat.title)} — ${escHtml(subcat.title)} — WMS ShowCase</title>
  <style>${COMMON_CSS}</style>
</head>
<body>
<header>
  <h1>🏭 WMS ShowCase</h1>
  <p>Warehouse Management System — Built with Claude Code</p>
</header>
<nav class="breadcrumb">
  <a href="../index.html">Home</a> &rsaquo; <a href="index.html">${escHtml(cat.title)}</a> &rsaquo; ${escHtml(subcat.title)}
</nav>
<main>
  <div style="margin-bottom:24px;">
    <h1 style="font-size:1.6rem;color:#1a1a2e;">${subcat.icon} ${escHtml(subcat.title)}</h1>
    <p style="color:#888;margin-top:8px;font-size:0.9rem;">${escHtml(subcat.titleEn)}</p>
  </div>
  ${mockupsLink}
  ${reviewsLink}
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

// ─── Functional Design hub page builder ───────────────────────────────────────

function buildFunctionalDesignHub(cat, subcatsData) {
  const cards = subcatsData.map(({ subcat, count }) => {
    const hasFiles = count > 0;
    const link  = hasFiles ? `index-${subcat.id}.html` : '#';
    const style = hasFiles ? '' : 'opacity:0.5; pointer-events:none;';
    return `
    <a href="${link}" class="card" style="${style}">
      <div class="card-icon">${subcat.icon}</div>
      <div class="card-title">${escHtml(subcat.title)}</div>
      <div class="card-en">${escHtml(subcat.titleEn)}</div>
      <div class="card-count">${hasFiles ? `${count} ファイル` : '準備中'}</div>
    </a>`;
  }).join('\n');

  return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${escHtml(cat.title)} — WMS ShowCase</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Segoe UI', 'Hiragino Sans', 'Yu Gothic', sans-serif; background: #f5f7fa; color: #333; min-height: 100vh; }
    header { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%); color: white; padding: 40px 60px; }
    header h1 { font-size: 2rem; font-weight: 700; letter-spacing: 0.05em; }
    header p { margin-top: 8px; opacity: 0.8; font-size: 0.95rem; }
    nav.breadcrumb { background: white; padding: 12px 60px; border-bottom: 1px solid #e0e0e0; font-size: 0.9rem; }
    nav.breadcrumb a { color: #0f3460; text-decoration: none; }
    nav.breadcrumb a:hover { text-decoration: underline; }
    main { max-width: 1100px; margin: 60px auto; padding: 0 40px; }
    h2 { font-size: 1.2rem; color: #1a1a2e; margin-bottom: 24px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 20px; }
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
<nav class="breadcrumb">
  <a href="../index.html">Home</a> &rsaquo; ${escHtml(cat.title)}
</nav>
<main>
  <div style="margin-bottom:40px;">
    <h1 style="font-size:1.6rem;color:#1a1a2e;">${cat.icon} ${escHtml(cat.title)}</h1>
    <p style="color:#888;margin-top:8px;font-size:0.9rem;">${escHtml(cat.titleEn)}</p>
  </div>
  <h2>設計書カテゴリ</h2>
  <div class="grid">
${cards}
  </div>
</main>
<footer>WMS ShowCase — Generated by build-docs.js</footer>
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
    `<div class="sidebar-item" data-file="${escHtml(m.file)}" onclick="scrollToMockup('${escHtml(m.file)}')">${escHtml(m.title)}</div>`
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
    .sidebar-item.active { background: #f0f4ff; border-left-color: #0f3460; color: #0f3460; font-weight: 600; }
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
    <a href="../index-screen.html">← 画面設計に戻る</a>
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
    function scrollToMockup(file) {
      const el = document.getElementById('section-' + file);
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        const file = entry.target.id.replace('section-', '');
        document.querySelectorAll('.sidebar-item').forEach(el => el.classList.remove('active'));
        const active = document.querySelector('.sidebar-item[data-file="' + file + '"]');
        if (active) active.classList.add('active');
      });
    }, { threshold: 0.1 });
    document.addEventListener('DOMContentLoaded', function () {
      document.querySelectorAll('.mockup-section').forEach(el => observer.observe(el));
    });
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
    const catDir  = path.join(DOCS_DIR, cat.dir);
    const mdFiles = getMdFiles(catDir);
    if (mdFiles.length === 0) {
      console.log(`⚪ ${cat.dir}/ — .md ファイルなし、スキップ`);
      continue;
    }

    // ── functional-design: サブカテゴリ分割 ───────────────────────────────
    if (cat.dir === 'functional-design') {
      // サブカテゴリごとにファイルを分類
      const subcatsData = FUNCTIONAL_DESIGN_SUBCATS.map(subcat => ({
        subcat,
        files: mdFiles.filter(f => subcat.mdPattern(f)),
      }));

      // ハブページ (index.html)
      const hubHtml = buildFunctionalDesignHub(cat, subcatsData.map(d => ({ subcat: d.subcat, count: d.files.length })));
      fs.writeFileSync(path.join(catDir, 'index.html'), hubHtml, 'utf-8');
      console.log(`✓  functional-design/index.html (hub)`);

      // サブカテゴリページ (index-{id}.html)
      for (const { subcat, files } of subcatsData) {
        if (files.length === 0) continue;
        const subHtml = buildFunctionalDesignSubPage(cat, subcat, files);
        const subOut  = path.join(catDir, `index-${subcat.id}.html`);
        fs.writeFileSync(subOut, subHtml, 'utf-8');
        const kb = (fs.statSync(subOut).size / 1024).toFixed(0);
        console.log(`✓  functional-design/index-${subcat.id}.html (${kb} KB, ${files.length} files)`);
      }

      // モックアップページ
      const mockupsHtml = buildMockupsPage();
      if (mockupsHtml) {
        const mockupsOut = path.join(catDir, 'mockups', 'index.html');
        fs.writeFileSync(mockupsOut, mockupsHtml, 'utf-8');
        const mkb = (fs.statSync(mockupsOut).size / 1024).toFixed(0);
        console.log(`✓  functional-design/mockups/index.html (${mkb} KB)`);
      }

      // reviews: 全件 index.html + サブカテゴリ別 index-{id}.html
      const reviewsDir  = path.join(catDir, 'reviews');
      const allReviews  = getReviewsMdFiles(reviewsDir);
      if (allReviews.length > 0) {
        // 全件
        const allHtml = buildReviewsPage(cat);
        fs.writeFileSync(path.join(reviewsDir, 'index.html'), allHtml, 'utf-8');
        console.log(`✓  functional-design/reviews/index.html (全${allReviews.length}件)`);

        // サブカテゴリ別
        for (const { subcat, files } of subcatsData) {
          if (files.length === 0) continue;          // 設計ファイルなしのサブカテゴリはレビューページも生成しない
          const revFiles = allReviews.filter(f => subcat.reviewPattern(f));
          if (revFiles.length === 0) continue;
          const revHtml = buildReviewsPage(cat, {
            files:     revFiles,
            pageTitle: `${cat.title} — ${subcat.title} レビュー記録`,
            backLink:  `../index-${subcat.id}.html`,
          });
          const revOut = path.join(reviewsDir, `index-${subcat.id}.html`);
          fs.writeFileSync(revOut, revHtml, 'utf-8');
          const rkb = (fs.statSync(revOut).size / 1024).toFixed(0);
          console.log(`✓  functional-design/reviews/index-${subcat.id}.html (${rkb} KB, ${revFiles.length}件)`);
        }
      }
      continue;
    }

    // ── 通常カテゴリ ──────────────────────────────────────────────────────
    const html    = buildCategoryPage(cat);
    const outPath = path.join(catDir, 'index.html');
    fs.writeFileSync(outPath, html, 'utf-8');
    const kb = (fs.statSync(outPath).size / 1024).toFixed(0);
    console.log(`✓  ${cat.dir}/index.html (${kb} KB, ${mdFiles.length} files)`);

    // reviews
    const reviewsHtml = buildReviewsPage(cat);
    if (reviewsHtml) {
      const reviewsOut = path.join(catDir, 'reviews', 'index.html');
      fs.writeFileSync(reviewsOut, reviewsHtml, 'utf-8');
      const rkb = (fs.statSync(reviewsOut).size / 1024).toFixed(0);
      console.log(`✓  ${cat.dir}/reviews/index.html (${rkb} KB)`);
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

// ─── ID Registry Validation ──────────────────────────────────────────────────

const ID_REGISTRY_PATH = path.join(DOCS_DIR, 'functional-design', '_id-registry.md');

function parseRegistryTable(content, sectionHeader) {
  const sectionRegex = new RegExp(`#{2,4}\\s+${sectionHeader.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}[\\s\\S]*?(?=\\n#{2,4}\\s|$)`);
  const section = content.match(sectionRegex);
  if (!section) return [];
  const rows = [];
  const lines = section[0].split('\n');
  let inTable = false;
  for (const line of lines) {
    if (line.startsWith('|') && !line.includes('---')) {
      if (!inTable) { inTable = true; continue; } // skip header
      const cells = line.split('|').map(c => c.trim()).filter(Boolean);
      if (cells.length > 0 && !cells[0].startsWith('※')) rows.push(cells);
    } else if (inTable && !line.startsWith('|')) {
      inTable = false;
    }
  }
  return rows;
}

function parseMarkdownTables(content, headerPattern) {
  const rows = [];
  const regex = new RegExp(`^\\|[^|]*${headerPattern}[^|]*\\|`, 'gm');
  const lines = content.split('\n');
  let inTable = false;
  let skipNext = false;
  for (const line of lines) {
    if (line.match(/^\|.*API ID.*\|/) || line.match(/^\|.*画面ID.*\|/) || line.match(/^\|.*RPT ID.*\|/)) {
      inTable = true; skipNext = true; continue;
    }
    if (skipNext && line.match(/^\|[\s-:|]+\|$/)) { skipNext = false; continue; }
    if (inTable && line.startsWith('|')) {
      const cells = line.split('|').map(c => c.trim()).filter(Boolean);
      if (cells.length > 0 && cells[0] && !cells[0].startsWith('※')) rows.push(cells);
    } else if (inTable && !line.startsWith('|')) {
      inTable = false;
    }
  }
  return rows;
}

async function validate() {
  console.log('🔍 ID Registry Validation\n');

  if (!fs.existsSync(ID_REGISTRY_PATH)) {
    console.error('❌ _id-registry.md not found');
    process.exit(1);
  }

  const registry = fs.readFileSync(ID_REGISTRY_PATH, 'utf-8');
  const fdDir = path.join(DOCS_DIR, 'functional-design');
  let errors = 0;
  let warnings = 0;

  // ─── V-001: Screen IDs → SCR-*.md files exist ───
  console.log('V-001: Screen ID → SCR file existence check');
  const screenSections = ['認証', 'マスタ管理', '入荷管理', '在庫管理', '出荷管理', 'バッチ管理', 'システム管理', '在庫引当', '返品管理'];
  const screenFiles = fs.readdirSync(fdDir).filter(f => f.startsWith('SCR-') && f.endsWith('.md'));
  const screenIdToFile = {};
  // Map screen prefixes to SCR files
  const prefixToSCR = {
    'AUTH': 'SCR-01', 'MST-0': 'SCR-02', 'MST-01': 'SCR-03', 'MST-02': 'SCR-04',
    'MST-03': 'SCR-05', 'MST-06': 'SCR-06', 'INB': 'SCR-07', 'INV-0': 'SCR-08',
    'INV-01': 'SCR-09', 'OUT': 'SCR-10', 'BAT': 'SCR-11', 'SYS': 'SCR-12',
    'ALL': 'SCR-13', 'RTN': 'SCR-14', 'IF': 'SCR-15'
  };
  for (const sf of screenFiles) {
    const scrNum = sf.match(/SCR-(\d+)/);
    if (scrNum) screenIdToFile[`SCR-${scrNum[1]}`] = sf;
  }
  const expectedSCRs = ['SCR-01','SCR-02','SCR-03','SCR-04','SCR-05','SCR-06','SCR-07','SCR-08','SCR-09','SCR-10','SCR-11','SCR-12','SCR-13','SCR-14','SCR-15'];
  for (const scr of expectedSCRs) {
    if (!screenIdToFile[scr]) {
      console.error(`  ❌ ERROR: ${scr} file not found`);
      errors++;
    } else {
      console.log(`  ✓ ${scr} → ${screenIdToFile[scr]}`);
    }
  }

  // ─── V-002: API IDs → API-*.md files contain definition ───
  console.log('\nV-002: API ID → API file definition check');
  const apiRows = [];
  const apiSectionRegex = /### 3\.2 API ID一覧[\s\S]*?(?=### 3\.3|$)/;
  const apiSection = registry.match(apiSectionRegex);
  if (apiSection) {
    const lines = apiSection[0].split('\n');
    for (const line of lines) {
      const match = line.match(/^\|\s*`(API-[A-Z]+-(?:[A-Z]+-)*\d+)`\s*\|/);
      if (match) apiRows.push(match[1]);
    }
  }
  // V-002: Search API-*.md, IF-*.md, SCR-15 (interface screen), architecture-design/09, and _id-registry.md itself
  const apiFiles = fs.readdirSync(fdDir).filter(f =>
    (f.startsWith('API-') || f.startsWith('IF-') || f === 'SCR-15-interface.md' || f === '_id-registry.md') && f.endsWith('.md')
  );
  // Also check architecture-design for I/F API definitions
  const archIfFile = path.join(DOCS_DIR, 'architecture-design', '09-interface-architecture.md');
  if (fs.existsSync(archIfFile)) apiFiles.push('__arch_09__');
  let apiCheckErrors = 0;
  for (const apiId of apiRows) {
    let found = false;
    for (const af of apiFiles) {
      const filePath = af === '__arch_09__'
        ? path.join(DOCS_DIR, 'architecture-design', '09-interface-architecture.md')
        : path.join(fdDir, af);
      const content = fs.readFileSync(filePath, 'utf-8');
      if (content.includes(apiId)) { found = true; break; }
    }
    if (!found) {
      console.error(`  ❌ ERROR: ${apiId} not found in any API-*.md`);
      errors++; apiCheckErrors++;
    }
  }
  if (apiCheckErrors === 0) console.log(`  ✓ All ${apiRows.length} API IDs verified`);

  // ─── V-003: SCR event API paths → exist in registry ───
  console.log('\nV-003: Screen event API path → Registry cross-reference');
  const registryPaths = new Set();
  for (const apiId of apiRows) {
    // Extract paths from registry
  }
  if (apiSection) {
    const lines = apiSection[0].split('\n');
    for (const line of lines) {
      const match = line.match(/`(\/api\/v1\/[^`]+)`/);
      if (match) registryPaths.add(match[1].replace(/\{[^}]+\}/g, '{id}'));
    }
  }
  let pathWarnings = 0;
  for (const sf of screenFiles) {
    const content = fs.readFileSync(path.join(fdDir, sf), 'utf-8');
    const apiRefs = content.matchAll(/(?:GET|POST|PUT|PATCH|DELETE)\s+(\/api\/v1\/[^\s|`]+)/g);
    for (const ref of apiRefs) {
      let rawPath = ref[1];
      // 1. Strip query string (?...)
      rawPath = rawPath.split('?')[0];
      // 2. Strip trailing non-path characters (Japanese parentheses, etc.)
      rawPath = rawPath.replace(/[^\w/\-{}:.]+$/, '');
      // 3. Strip trailing dots or ellipsis
      rawPath = rawPath.replace(/\.+$/, '');
      // 4. Normalize path variables
      const normalized = rawPath.replace(/:[a-zA-Z]+/g, '{id}').replace(/\{[a-zA-Z]+\}/g, '{id}');
      if (!registryPaths.has(normalized)) {
        // Check with less strict matching (path prefix — first 4-5 segments)
        const segments = normalized.split('/').filter(Boolean);
        // Try matching with 4 segments (e.g., /api/v1/master/buildings)
        const prefix4 = '/' + segments.slice(0, 4).join('/');
        const prefix5 = '/' + segments.slice(0, 5).join('/');
        const hasPrefix = [...registryPaths].some(p =>
          p === prefix4 || p === prefix5 || p.startsWith(prefix4 + '/') || p.startsWith(prefix5 + '/')
        );
        if (!hasPrefix) {
          console.warn(`  ⚠ WARNING: ${sf} references ${ref[1]} — not found in registry`);
          warnings++; pathWarnings++;
        }
      }
    }
  }
  if (pathWarnings === 0) console.log(`  ✓ All screen API references verified`);

  // ─── V-004: RPT IDs → RPT-*.md files exist ───
  console.log('\nV-004: RPT ID → RPT file existence check');
  const rptRows = [];
  const rptSectionRegex = /### 3\.3 レポートID一覧[\s\S]*?(?=### 3\.4|$)/;
  const rptSection = registry.match(rptSectionRegex);
  if (rptSection) {
    const lines = rptSection[0].split('\n');
    for (const line of lines) {
      const match = line.match(/^\|\s*RPT-(\d+)\s*\|/);
      if (match) rptRows.push(`RPT-${match[1]}`);
    }
  }
  for (const rptId of rptRows) {
    const rptFile = fs.readdirSync(fdDir).find(f => f.startsWith(rptId + '-') && f.endsWith('.md'));
    if (!rptFile) {
      console.error(`  ❌ ERROR: ${rptId} file not found`);
      errors++;
    } else {
      console.log(`  ✓ ${rptId} → ${rptFile}`);
    }
  }

  // ─── V-005: Category file counts match ───
  console.log('\nV-005: Document category file count check');
  const catRegex = /## 1\. ドキュメントカテゴリ一覧[\s\S]*?(?=---)/;
  const catSection = registry.match(catRegex);
  if (catSection) {
    const lines = catSection[0].split('\n');
    for (const line of lines) {
      const match = line.match(/^\|\s*[^|]+\|\s*([^|]+)\|\s*(\d+)\s*\|/);
      if (match) {
        const folderPattern = match[1].trim();
        const expectedCount = parseInt(match[2]);
        let actualCount = 0;
        if (folderPattern.includes('*')) {
          // Pattern like functional-design/SCR-*
          const parts = folderPattern.split('/');
          const dir = path.join(DOCS_DIR, parts[0]);
          const prefix = parts[1].replace('*', '');
          if (fs.existsSync(dir)) {
            actualCount = fs.readdirSync(dir).filter(f => f.startsWith(prefix) && f.endsWith('.md')).length;
          }
        } else if (folderPattern.endsWith('/')) {
          const dir = path.join(DOCS_DIR, folderPattern.replace('/', ''));
          if (fs.existsSync(dir)) {
            actualCount = fs.readdirSync(dir).filter(f => f.endsWith('.md') && !f.startsWith('_')).length;
          }
        }
        if (actualCount > 0 && actualCount !== expectedCount) {
          console.warn(`  ⚠ WARNING: ${folderPattern} expected ${expectedCount} files, found ${actualCount}`);
          warnings++;
        } else if (actualCount > 0) {
          console.log(`  ✓ ${folderPattern} ${actualCount} files`);
        }
      }
    }
  }

  // ─── V-006: No duplicate IDs within same prefix ───
  console.log('\nV-006: Duplicate ID check');
  const allIds = new Set();
  let dupCount = 0;
  for (const apiId of apiRows) {
    if (allIds.has(apiId)) {
      console.error(`  ❌ ERROR: Duplicate API ID: ${apiId}`);
      errors++; dupCount++;
    }
    allIds.add(apiId);
  }
  for (const rptId of rptRows) {
    if (allIds.has(rptId)) {
      console.error(`  ❌ ERROR: Duplicate RPT ID: ${rptId}`);
      errors++; dupCount++;
    }
    allIds.add(rptId);
  }
  if (dupCount === 0) console.log(`  ✓ No duplicate IDs found (${allIds.size} unique IDs)`);

  // ─── V-007: All prefixes have at least one ID ───
  console.log('\nV-007: Prefix coverage check');
  const apiPrefixRegex = /### 2\.3 API IDプレフィックス[\s\S]*?(?=###|$)/;
  const apiPrefixSection = registry.match(apiPrefixRegex);
  if (apiPrefixSection) {
    const lines = apiPrefixSection[0].split('\n');
    for (const line of lines) {
      const match = line.match(/^\|\s*`(API-[A-Z-]+)`\s*\|/);
      if (match) {
        const prefix = match[1];
        const hasId = apiRows.some(id => id.startsWith(prefix));
        if (!hasId) {
          console.warn(`  ⚠ WARNING: Prefix ${prefix} has no IDs in the registry`);
          warnings++;
        } else {
          console.log(`  ✓ ${prefix} has IDs`);
        }
      }
    }
  }

  // ─── Summary ───
  console.log('\n' + '─'.repeat(50));
  if (errors === 0 && warnings === 0) {
    console.log('✅ All validation checks passed!');
  } else {
    if (errors > 0) console.error(`❌ ${errors} ERROR(s) found`);
    if (warnings > 0) console.warn(`⚠  ${warnings} WARNING(s) found`);
  }

  return errors > 0 ? 1 : 0;
}

// ─── Document Map Generator (YAML → Markdown) ──────────────────────────────

const DOCUMENT_MAP_YAML = path.join(DOCS_DIR, 'document-map.yaml');
const DOCUMENT_MAP_MD   = path.join(DOCS_DIR, 'document-map.md');

/**
 * Minimal YAML parser for document-map.yaml.
 * Handles the specific structure: modules[] and cross-cutting[] with nested docs.
 */
function parseDocumentMapYaml(yamlText) {
  const lines = yamlText.split('\n');
  const result = { modules: [], 'cross-cutting': [] };

  let currentList = null;   // 'modules' or 'cross-cutting'
  let currentItem = null;   // current module/cross-cutting entry
  let currentDocs = null;   // current docs object (for modules)
  let currentDocKey = null;  // current doc category key
  let currentDocList = null; // current doc list (for cross-cutting)

  for (const rawLine of lines) {
    // Skip comments and empty lines
    if (rawLine.match(/^\s*#/) || rawLine.trim() === '') continue;

    const indent = rawLine.search(/\S/);
    const line = rawLine.trim();

    // Top-level keys
    if (indent === 0 && line === 'modules:') {
      currentList = 'modules';
      currentItem = null;
      continue;
    }
    if (indent === 0 && line === 'cross-cutting:') {
      currentList = 'cross-cutting';
      currentItem = null;
      continue;
    }

    // List item start (- id: xxx)
    if (line.startsWith('- id:')) {
      const id = line.replace('- id:', '').trim();
      if (currentList === 'modules') {
        currentItem = { id, name: '', name_en: '', docs: {} };
        result.modules.push(currentItem);
        currentDocs = null;
        currentDocKey = null;
      } else if (currentList === 'cross-cutting') {
        currentItem = { id, name: '', name_en: '', docs: [] };
        result['cross-cutting'].push(currentItem);
        currentDocList = null;
      }
      continue;
    }

    if (!currentItem) continue;

    // Scalar fields
    const nameMatch = line.match(/^name:\s*(.+)/);
    if (nameMatch) { currentItem.name = nameMatch[1]; continue; }
    const nameEnMatch = line.match(/^name_en:\s*(.+)/);
    if (nameEnMatch) { currentItem.name_en = nameEnMatch[1]; continue; }

    // docs: key (for modules)
    if (line === 'docs:') {
      if (currentList === 'modules') {
        currentDocs = currentItem.docs;
      }
      currentDocKey = null;
      currentDocList = null;
      continue;
    }

    // Doc category key (e.g., "requirements:", "api:")
    if (currentList === 'modules' && currentDocs && line.endsWith(':') && !line.startsWith('-')) {
      currentDocKey = line.replace(':', '');
      currentDocs[currentDocKey] = [];
      continue;
    }

    // Doc list item (- path/to/file.md)
    if (line.startsWith('- ')) {
      const value = line.substring(2).replace(/#.*$/, '').trim(); // strip inline comments
      if (currentList === 'modules' && currentDocs && currentDocKey) {
        currentDocs[currentDocKey].push(value);
      } else if (currentList === 'cross-cutting' && currentItem) {
        currentItem.docs.push(value);
      }
      continue;
    }
  }

  return result;
}

/**
 * Resolve display name from file path (read first heading from .md file).
 */
function resolveDocTitle(relPath) {
  const absPath = path.join(DOCS_DIR, relPath);
  if (!fs.existsSync(absPath)) return relPath;
  try {
    const content = fs.readFileSync(absPath, 'utf-8');
    const match = content.match(/^#\s+(.+)/m);
    return match ? match[1].trim() : relPath;
  } catch { return relPath; }
}

/** Doc category labels */
const DOC_CATEGORY_LABELS = {
  requirements:  { ja: '要件定義',    icon: '📝' },
  api:           { ja: 'API設計',     icon: '🔌' },
  screen:        { ja: '画面設計',    icon: '🖥️' },
  report:        { ja: '帳票設計',    icon: '📊' },
  batch:         { ja: 'バッチ設計',  icon: '⚙️' },
  interface:     { ja: 'I/F設計',     icon: '🔗' },
  'data-model':  { ja: 'データモデル', icon: '🗄️' },
  test:          { ja: 'テスト仕様',  icon: '🧪' },
  architecture:  { ja: 'アーキテクチャ', icon: '🏗️' },
};

function generateDocumentMapMd(mapData) {
  const lines = [];
  lines.push('# ドキュメント関連マップ (Document Relationship Map)');
  lines.push('');
  lines.push('> **このファイルは `docs/document-map.yaml` から自動生成されています。直接編集しないでください。**');
  lines.push('> ');
  lines.push('> 再生成: `node docs/scripts/build-docs.js --generate-map`');
  lines.push('');

  // ── Matrix table ──
  lines.push('## モジュール × ドキュメント種別 マトリクス');
  lines.push('');
  lines.push('| モジュール | 要件定義 | API設計 | 画面設計 | 帳票 | バッチ | I/F | データモデル | テスト | アーキテクチャ |');
  lines.push('|-----------|---------|---------|---------|------|--------|-----|-------------|--------|--------------|');

  const docKeys = ['requirements', 'api', 'screen', 'report', 'batch', 'interface', 'data-model', 'test', 'architecture'];

  for (const mod of mapData.modules) {
    const cells = docKeys.map(key => {
      const files = (mod.docs[key] || []);
      if (files.length === 0) return '—';
      return files.map(f => {
        const basename = path.basename(f, '.md');
        return basename;
      }).join(', ');
    });
    lines.push(`| **${mod.name}** | ${cells.join(' | ')} |`);
  }

  lines.push('');

  // ── Module detail sections ──
  lines.push('## モジュール別 詳細');
  lines.push('');

  for (const mod of mapData.modules) {
    lines.push(`### ${mod.name} (${mod.name_en})`);
    lines.push('');

    for (const key of docKeys) {
      const files = mod.docs[key] || [];
      if (files.length === 0) continue;
      const label = DOC_CATEGORY_LABELS[key] || { ja: key, icon: '📄' };
      lines.push(`**${label.icon} ${label.ja}:**`);
      for (const f of files) {
        lines.push(`- [${path.basename(f, '.md')}](${f})`);
      }
      lines.push('');
    }
  }

  // ── Cross-cutting concerns ──
  lines.push('## 横断的関心事 (Cross-Cutting Concerns)');
  lines.push('');
  lines.push('全モジュールに共通して参照されるドキュメント。');
  lines.push('');

  for (const cc of mapData['cross-cutting']) {
    lines.push(`### ${cc.name} (${cc.name_en})`);
    lines.push('');
    for (const f of cc.docs) {
      lines.push(`- [${path.basename(f, '.md')}](${f})`);
    }
    lines.push('');
  }

  return lines.join('\n');
}

function generateDocumentMap() {
  if (!fs.existsSync(DOCUMENT_MAP_YAML)) {
    console.error('❌ document-map.yaml が見つかりません');
    process.exit(1);
  }

  const yamlText = fs.readFileSync(DOCUMENT_MAP_YAML, 'utf-8');
  const mapData  = parseDocumentMapYaml(yamlText);

  console.log(`📄 Parsed: ${mapData.modules.length} modules, ${mapData['cross-cutting'].length} cross-cutting concerns`);

  // Validate file existence
  let warnings = 0;
  for (const mod of mapData.modules) {
    for (const [, files] of Object.entries(mod.docs)) {
      for (const f of files) {
        if (!fs.existsSync(path.join(DOCS_DIR, f))) {
          console.warn(`  ⚠ ${mod.id}: ファイルが存在しません — ${f}`);
          warnings++;
        }
      }
    }
  }
  for (const cc of mapData['cross-cutting']) {
    for (const f of cc.docs) {
      if (!fs.existsSync(path.join(DOCS_DIR, f))) {
        console.warn(`  ⚠ ${cc.id}: ファイルが存在しません — ${f}`);
        warnings++;
      }
    }
  }

  // Generate Markdown
  const md = generateDocumentMapMd(mapData);
  fs.writeFileSync(DOCUMENT_MAP_MD, md, 'utf-8');
  console.log(`✓  document-map.md 生成完了 (${(Buffer.byteLength(md) / 1024).toFixed(1)} KB)`);

  if (warnings > 0) {
    console.warn(`⚠  ${warnings} 件のファイル参照警告があります`);
  }

  return mapData;
}

// ─── Entry Point ─────────────────────────────────────────────────────────────

async function entry() {
  const args = process.argv.slice(2);
  if (args.includes('--validate')) {
    const exitCode = await validate();
    process.exit(exitCode);
  } else if (args.includes('--generate-map')) {
    generateDocumentMap();
  } else {
    await main();
  }
}

entry().catch(err => { console.error(err); process.exit(1); });
