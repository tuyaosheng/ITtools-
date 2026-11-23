// 把 docs/使用说明书.md 生成 Word 版说明书（版式沿用旧版：A4、封面、目录、深蓝表头、页眉页脚页码）
// 用法：node md2docx.js <md> <out.docx> <封面日期文字>
const fs = require('fs');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, Table, TableRow, TableCell,
  WidthType, ShadingType, BorderStyle, LevelFormat, TableOfContents, Header, Footer, PageNumber,
  PageBreak,
} = require('docx');

const [, , mdPath, outPath, dateText] = process.argv;
const lines = fs.readFileSync(mdPath, 'utf8').replace(/\r/g, '').split('\n');

const BODY = '宋体', HEAD = '黑体', MONO = 'Consolas';
const BLUE = '1F4E79';
const PAGE_W = 11906, MARGIN = 1300, CONTENT_W = PAGE_W - MARGIN * 2;

// 行内：**粗体**、`代码`
function runs(text, base = {}) {
  const out = [];
  const re = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let last = 0, m;
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(new TextRun({ text: text.slice(last, m.index), ...base }));
    const t = m[0];
    if (t.startsWith('**')) out.push(new TextRun({ text: t.slice(2, -2), bold: true, ...base }));
    else out.push(new TextRun({ text: t.slice(1, -1), font: MONO, shading: { type: ShadingType.CLEAR, fill: 'F2F2F2', color: 'auto' }, ...base }));
    last = m.index + t.length;
  }
  if (last < text.length) out.push(new TextRun({ text: text.slice(last), ...base }));
  return out;
}

const cellBorder = { style: BorderStyle.SINGLE, size: 4, color: 'BFBFBF' };
const borders = { top: cellBorder, bottom: cellBorder, left: cellBorder, right: cellBorder };

function table(rows) {
  const cells = rows.map(r => r.replace(/^\||\|$/g, '').split('|').map(c => c.trim()));
  const header = cells[0], body = cells.slice(2);
  const n = header.length;
  // 列宽按各列最长文字估算，首列偏窄的表也不至于挤
  const len = s => [...s.replace(/\*\*|`/g, '')].reduce((a, ch) => a + (ch.charCodeAt(0) > 255 ? 2 : 1), 0);
  const w = header.map((h, i) => Math.max(len(h), ...body.map(r => len(r[i] || ''))));
  const capped = w.map(x => Math.min(Math.max(x, 6), 60));
  const sum = capped.reduce((a, b) => a + b, 0);
  let widths = capped.map(x => Math.floor(CONTENT_W * x / sum));
  widths[n - 1] += CONTENT_W - widths.reduce((a, b) => a + b, 0);
  const mk = (text, i, opts) => new TableCell({
    borders, width: { size: widths[i], type: WidthType.DXA },
    shading: opts.fill ? { type: ShadingType.CLEAR, fill: opts.fill, color: 'auto' } : undefined,
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [new Paragraph({ spacing: { before: 0, after: 0, line: 300 }, children: runs(text, opts.run || {}) })],
  });
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA }, columnWidths: widths,
    rows: [
      new TableRow({ tableHeader: true, children: header.map((h, i) => mk(h, i, { fill: BLUE, run: { bold: true, color: 'FFFFFF', font: HEAD } })) }),
      ...body.map((r, ri) => new TableRow({ children: header.map((_, i) => mk(r[i] || '', i, { fill: ri % 2 ? 'F5F8FC' : undefined })) })),
    ],
  });
}

let numInstance = 0;
const children = [];
let title = 'ITtools 使用说明书';
for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  if (!line.trim()) continue;
  if (/^# /.test(line)) { title = line.slice(2).trim(); continue; }
  if (/^> /.test(line)) continue;                         // 维护说明，不进 Word
  if (/^## /.test(line)) { children.push(new Paragraph({ heading: HeadingLevel.HEADING_1, pageBreakBefore: true, children: [new TextRun(line.slice(3).trim())] })); continue; }
  if (/^### /.test(line)) { children.push(new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(line.slice(4).trim())] })); continue; }
  if (/^\|/.test(line)) {
    const rows = [];
    while (i < lines.length && /^\|/.test(lines[i])) rows.push(lines[i++]);
    i--;
    children.push(table(rows));
    children.push(new Paragraph({ spacing: { before: 0, after: 60 }, children: [] }));
    continue;
  }
  if (/^```/.test(line)) {
    const code = [];
    i++;
    while (i < lines.length && !/^```/.test(lines[i])) code.push(lines[i++]);
    code.forEach((c, k) => children.push(new Paragraph({
      shading: { type: ShadingType.CLEAR, fill: 'F2F2F2', color: 'auto' },
      spacing: { before: k === 0 ? 80 : 0, after: k === code.length - 1 ? 120 : 0, line: 280 },
      indent: { left: 200, right: 200 },
      children: [new TextRun({ text: c || ' ', font: MONO, size: 19 })],
    })));
    continue;
  }
  let m;
  if ((m = line.match(/^(\s*)[-*] (.*)$/))) {
    children.push(new Paragraph({ numbering: { reference: 'bullets', level: m[1].length >= 2 ? 1 : 0 }, children: runs(m[2]) }));
    continue;
  }
  if ((m = line.match(/^(\s*)(\d+)\. (.*)$/))) {
    // 每段编号列表单独起号：前一行不是编号项时换一个新的编号实例
    const prev = lines[i - 1] || '';
    if (!/^\s*\d+\. /.test(prev) && !/^\s+[-*] /.test(prev)) numInstance++;
    children.push(new Paragraph({ numbering: { reference: 'numbers', level: 0, instance: numInstance }, children: runs(m[3]) }));
    continue;
  }
  children.push(new Paragraph({ children: runs(line.trim()) }));
}

const parts = title.split(/\s+/);
const cover = [
  new Paragraph({ spacing: { before: 3200, after: 200 }, alignment: AlignmentType.CENTER, children: [new TextRun({ text: parts[0], font: HEAD, size: 72, bold: true, color: BLUE })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [new TextRun({ text: parts.slice(1, -1).join(' ') || '信息技术教学平台', font: HEAD, size: 40, color: '404040' })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 2400 }, children: [new TextRun({ text: [...(parts[parts.length - 1] || '使用说明书')].join(' '), font: HEAD, size: 56, bold: true })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: '适用对象：管理员 · 教师 · 学生', size: 26, color: '595959' })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: dateText, size: 26, color: '595959' })] }),
  new Paragraph({ children: [new PageBreak()] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 240 }, children: [new TextRun({ text: '目    录', font: HEAD, size: 36, bold: true })] }),
  new TableOfContents('目录', { hyperlink: true, headingStyleRange: '1-2' }),
];

const doc = new Document({
  creator: 'ITtools', title,
  features: { updateFields: true },
  styles: {
    default: { document: { run: { font: { ascii: 'Calibri', hAnsi: 'Calibri', eastAsia: BODY }, size: 21 }, paragraph: { spacing: { line: 340, after: 80 } } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: { ascii: 'Calibri', hAnsi: 'Calibri', eastAsia: HEAD }, size: 32, bold: true, color: BLUE },
        paragraph: { spacing: { before: 240, after: 200 }, keepNext: true, outlineLevel: 0,
          border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: BLUE, space: 4 } } } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: { ascii: 'Calibri', hAnsi: 'Calibri', eastAsia: HEAD }, size: 26, bold: true, color: '2E2E2E' },
        paragraph: { spacing: { before: 280, after: 120 }, keepNext: true, outlineLevel: 1 } },
    ],
  },
  numbering: {
    config: [
      { reference: 'bullets', levels: [
        { level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 420, hanging: 280 } } } },
        { level: 1, format: LevelFormat.BULLET, text: '◦', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 840, hanging: 280 } } } },
      ] },
      { reference: 'numbers', levels: [
        { level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 420, hanging: 300 } } } },
      ] },
    ],
  },
  sections: [{
    properties: { page: { size: { width: PAGE_W, height: 16838 }, margin: { top: 1300, bottom: 1200, left: MARGIN, right: MARGIN } }, titlePage: true },
    headers: { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: 'BFBFBF', space: 2 } }, children: [new TextRun({ text: title, size: 18, color: '7F7F7F' })] })] }) },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ children: ['第 ', PageNumber.CURRENT, ' 页'], size: 18, color: '7F7F7F' })] })] }) },
    children: [...cover, ...children],
  }],
});
Packer.toBuffer(doc).then(b => { fs.writeFileSync(outPath, b); console.log('written', outPath, b.length); });
