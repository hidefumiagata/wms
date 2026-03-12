#!/usr/bin/env node
/**
 * build-docs.js
 * Markdownファイルから静的HTMLドキュメントポータルを生成する
 * 使い方: npm run docs
 */

const fs = require('fs');
const path = require('path');
const { marked } = require('marked');

const DOCS_DIR = path.join(__dirname, '..');

const CATEGORIES = [
  { dir: 'project-plan',           title: 'プロジェクト計画書',       titleEn: 'Project Plan',               icon: '📋' },
  { dir: 'architecture-blueprint', title: 'アーキテクチャブループリント', titleEn: 'Architecture Blueprint',     icon: '🏗️' },
  { dir: 'functional-requirements', title: '機能要件定義書',           titleEn: 'Functional Requirements',    icon: '📝' },
  { dir: 'data-model',             title: 'データモデル定義',          titleEn: 'Data Model',                 icon: '🗄️' },
  { dir: 'functional-design',      title: '機能設計書',               titleEn: 'Functional Design',          icon: '⚙️' },
  { dir: 'architecture-design',    title: 'アーキテクチャ設計書',      titleEn: 'Architecture Design',        icon: '🔧' },
  { dir: 'test-specifications',    title: 'テスト仕様書',             titleEn: 'Test Specifications',        icon: '🧪' },
];

const CSS = `
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: 'Segoe UI', 'Hiragino Sans', 'Yu Gothic', sans-serif; background: #f5f7fa; color: #333; line-height: 1.7; }
  header { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%); color: white; padding: 40px 60px; }
  header h1 { font-size: 2rem; font-weight: 700; letter-spacing: 0.05em; }
  header p { margin-top: 8px; opacity: 0.8; font-size: 0.95rem; }
  nav.breadcrumb { background: white; padding: 12px 60px; border-bottom: 1px solid #e0e0e0; font-size: 0.9rem; }
  nav.breadcrumb a { color: #0f3460; text-decoration: none; }
  nav.breadcrumb a:hover { text-decoration: underline; }
  main { max-width: 1100px; margin: 40px auto; padding: 0 40px; }
  .card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 24px; margin-top: 32px; }
  .card { background: white; border-radius: 12px; padding: 28px; box-shadow: 0 2px 12px rgba(0,0,0,0.08); transition: transform 0.2s, box-shadow 0.2s; text-decoration: none; color: inherit; display: block; border: 1px solid #eee; }
  .card:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,0.12); border-color: #0f3460; }
  .card .icon { font-size: 2.5rem; margin-bottom: 16px; }
  .card h2 { font-size: 1.1rem; font-weight: 700; color: #1a1a2e; margin-bottom: 6px; }
  .card .subtitle { font-size: 0.85rem; color: #888; margin-bottom: 12px; }
  .card .file-count { font-size: 0.8rem; color: #aaa; }
  .badge { display: inline-block; background: #e8f0fe; color: #0f3460; font-size: 0.75rem; padding: 2px 10px; border-radius: 20px; }
  .badge.empty { background: #f5f5f5; color: #aaa; }
  .section-header { margin-bottom: 32px; }
  .section-header h1 { font-size: 1.6rem; color: #1a1a2e; display: flex; align-items: center; gap: 12px; }
  .doc-list { background: white; border-radius: 12px; box-shadow: 0 2px 12px rgba(0,0,0,0.08); overflow: hidden; }
  .doc-item { padding: 20px 28px; border-bottom: 1px solid #f0f0f0; display: flex; align-items: center; justify-content: space-between; }
  .doc-item:last-child { border-bottom: none; }
  .doc-item a { text-decoration: none; color: #0f3460; font-weight: 500; font-size: 1rem; }
  .doc-item a:hover { text-decoration: underline; }
  .doc-item .meta { font-size: 0.8rem; color: #aaa; }
  .markdown-body { background: white; border-radius: 12px; padding: 48px 56px; box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
  .markdown-body h1 { font-size: 1.8rem; color: #1a1a2e; border-bottom: 3px solid #0f3460; padding-bottom: 12px; margin: 0 0 28px; }
  .markdown-body h2 { font-size: 1.3rem; color: #16213e; border-bottom: 1px solid #e0e0e0; padding-bottom: 8px; margin: 36px 0 16px; }
  .markdown-body h3 { font-size: 1.1rem; color: #333; margin: 24px 0 12px; }
  .markdown-body table { width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 0.9rem; }
  .markdown-body th { background: #1a1a2e; color: white; padding: 10px 16px; text-align: left; }
  .markdown-body td { padding: 10px 16px; border-bottom: 1px solid #f0f0f0; }
  .markdown-body tr:hover td { background: #f8f9ff; }
  .markdown-body code { background: #f0f4ff; color: #0f3460; padding: 2px 8px; border-radius: 4px; font-size: 0.88em; font-family: 'Consolas', 'Monaco', monospace; }
  .markdown-body pre { background: #1a1a2e; color: #e0e0e0; padding: 20px 24px; border-radius: 8px; overflow-x: auto; margin: 16px 0; }
  .markdown-body pre code { background: none; color: inherit; padding: 0; font-size: 0.88em; }
  .markdown-body blockquote { border-left: 4px solid #0f3460; background: #f0f4ff; padding: 12px 20px; margin: 16px 0; border-radius: 0 8px 8px 0; }
  .markdown-body ul, .markdown-body ol { padding-left: 24px; margin: 12px 0; }
  .markdown-body li { margin: 4px 0; }
  .markdown-body p { margin: 12px 0; }
  .doc-separator { border: none; border-top: 2px dashed #e0e8ff; margin: 48px 0; }
  .drawio-wrapper { margin: 20px 0; }
  .drawio-label { font-size: 0.85rem; color: #888; margin-bottom: 8px; }
  footer { text-align: center; padding: 32px; color: #aaa; font-size: 0.85rem; margin-top: 60px; border-top: 1px solid #e0e0e0; }
`;

function htmlTemplate({ title, breadcrumb, body, generatedAt, hasDrawio = false }) {
  const drawioScript = hasDrawio
    ? `<script type="text/javascript" src="https://viewer.diagrams.net/js/viewer-static.min.js"></script>`
    : '';
  return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title} - WMS ShowCase</title>
  <style>${CSS}</style>
</head>
<body>
<header>
  <h1>🏭 WMS ShowCase</h1>
  <p>Warehouse Management System — Built with Claude Code</p>
</header>
${breadcrumb}
<main>
${body}
</main>
<footer>Generated by Claude Code — ${generatedAt}</footer>
${drawioScript}
</body>
</html>`;
}

/**
 * Markdown → HTML 変換後、.drawio リンクをインラインビューアに置換する
 * @param {string} html - marked() 変換後のHTML
 * @param {string} baseDir - .drawioファイルの基点ディレクトリ
 * @returns {{ html: string, hasDrawio: boolean }}
 */
function embedDrawioLinks(html, baseDir) {
  let hasDrawio = false;
  // <p> ごと置換することで div-in-p の不正HTML構造を回避する
  const result = html.replace(/<p>[^<]*<a href="([^"]+\.drawio)"[^>]*>([^<]*)<\/a>[^<]*<\/p>/g, (match, href, label) => {
    const drawioPath = path.join(baseDir, href);
    if (!fs.existsSync(drawioPath)) return match;

    const xml = fs.readFileSync(drawioPath, 'utf8');
    const jsonData = JSON.stringify({
      highlight: '#0000ff',
      nav: true,
      resize: true,
      toolbar: 'zoom layers tags lightbox',
      edit: '_blank',
      xml,
    });
    // HTML属性内でのエスケープ: & → &amp; を最初に行わないと &#xa; 等が
    // ブラウザのHTMLパーサーに解釈されて制御文字になりJSON.parseが失敗する
    const escapedJson = jsonData
      .replace(/&/g, '&amp;')
      .replace(/"/g, '&quot;');
    hasDrawio = true;
    return `<div class="drawio-wrapper">
      <p class="drawio-label">📊 ${label}</p>
      <div class="mxgraph" style="max-width:100%;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden;" data-mxgraph="${escapedJson}"></div>
    </div>`;
  });
  return { html: result, hasDrawio };
}

function getMdFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir)
    .filter(f => f.endsWith('.md'))
    .sort();
}

function buildCategoryPage(category) {
  const dir = path.join(DOCS_DIR, category.dir);
  const files = getMdFiles(dir);

  if (files.length === 0) {
    const body = `
      <div class="section-header"><h1>${category.icon} ${category.title}</h1></div>
      <p style="color:#aaa">ドキュメントはまだ作成されていません。</p>`;
    return htmlTemplate({
      title: category.title,
      breadcrumb: `<nav class="breadcrumb"><a href="../index.html">Home</a> &rsaquo; ${category.title}</nav>`,
      body,
      generatedAt: new Date().toLocaleString('ja-JP'),
    });
  }

  let hasDrawio = false;
  const sections = files.map(file => {
    const content = fs.readFileSync(path.join(dir, file), 'utf8');
    const raw = marked(content);
    const { html, hasDrawio: fileHasDrawio } = embedDrawioLinks(raw, dir);
    if (fileHasDrawio) hasDrawio = true;
    const name = path.basename(file, '.md');
    return `<div class="markdown-body" id="${name}">${html}</div>`;
  }).join('<hr class="doc-separator">');

  const tocItems = files.map(file => {
    const name = path.basename(file, '.md');
    return `<li><a href="#${name}">${name}</a></li>`;
  }).join('');

  const body = `
    <div class="section-header">
      <h1>${category.icon} ${category.title}</h1>
      <p style="color:#888;margin-top:8px;font-size:0.9rem">${category.titleEn}</p>
    </div>
    <div style="background:white;border-radius:12px;padding:20px 28px;margin-bottom:32px;box-shadow:0 2px 12px rgba(0,0,0,0.08)">
      <strong style="color:#1a1a2e">目次</strong>
      <ul style="margin-top:12px;padding-left:24px">${tocItems}</ul>
    </div>
    ${sections}`;

  return htmlTemplate({
    title: category.title,
    breadcrumb: `<nav class="breadcrumb"><a href="../index.html">Home</a> &rsaquo; ${category.title}</nav>`,
    body,
    hasDrawio,
    generatedAt: new Date().toLocaleString('ja-JP'),
  });
}

function buildIndex() {
  const cards = CATEGORIES.map(cat => {
    const dir = path.join(DOCS_DIR, cat.dir);
    const files = getMdFiles(dir);
    const isEmpty = files.length === 0;
    const badge = isEmpty
      ? `<span class="badge empty">準備中</span>`
      : `<span class="badge">${files.length} ドキュメント</span>`;

    return `
    <a class="card" href="${cat.dir}/index.html">
      <div class="icon">${cat.icon}</div>
      <h2>${cat.title}</h2>
      <p class="subtitle">${cat.titleEn}</p>
      <div class="file-count">${badge}</div>
    </a>`;
  }).join('');

  const body = `
    <div class="section-header">
      <h1 style="font-size:1.4rem;color:#1a1a2e">ドキュメント一覧</h1>
      <p style="color:#888;margin-top:8px;font-size:0.9rem">各カテゴリをクリックしてドキュメントを確認してください</p>
    </div>
    <div class="card-grid">${cards}</div>`;

  return htmlTemplate({
    title: 'ドキュメントポータル',
    breadcrumb: `<nav class="breadcrumb">Home</nav>`,
    body,
    generatedAt: new Date().toLocaleString('ja-JP'),
  });
}

// --- Main ---
console.log('📄 Building documentation portal...\n');

// index.html
fs.writeFileSync(path.join(DOCS_DIR, 'index.html'), buildIndex());
console.log('✅ docs/index.html');

// Each category
for (const cat of CATEGORIES) {
  const outPath = path.join(DOCS_DIR, cat.dir, 'index.html');
  fs.writeFileSync(outPath, buildCategoryPage(cat));
  const count = getMdFiles(path.join(DOCS_DIR, cat.dir)).length;
  console.log(`✅ docs/${cat.dir}/index.html (${count} files)`);
}

console.log('\n🎉 Done! Open docs/index.html in your browser.');
