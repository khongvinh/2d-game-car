const fs = require('fs');
const { Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell, WidthType,
  ShadingType, BorderStyle, LevelFormat, AlignmentType, Footer, PageNumber, TableOfContents } = require('docx');
const [src, out] = process.argv.slice(2);
const lines = fs.readFileSync(src, 'utf8').split('\n');
const FONT = 'Arial';
const W = 9906; // A4 text width (11906 - 2*1000)

function runs(text, base = {}) {
  // **bold** support
  const parts = text.split(/(\*\*[^*]+\*\*)/g).filter(Boolean);
  return parts.map(p => p.startsWith('**') ? new TextRun({ text: p.slice(2, -2), bold: true, font: FONT, ...base })
                                         : new TextRun({ text: p, font: FONT, ...base }));
}
const border = { style: BorderStyle.SINGLE, size: 4, color: 'B0B7C3' };
const borders = { top: border, bottom: border, left: border, right: border };

function table(rows) {
  const cells = rows.map(r => r.replace(/^\||\|$/g, '').split('|').map(s => s.trim()));
  const n = cells[0].length;
  // first column a bit wider for 2-col tables
  let widths;
  if (n === 2) widths = [3300, W - 3300];
  else { const w = Math.floor(W / n); widths = Array(n).fill(w); widths[n - 1] = W - w * (n - 1); }
  return new Table({
    width: { size: W, type: WidthType.DXA }, columnWidths: widths,
    rows: cells.map((r, i) => new TableRow({
      tableHeader: i === 0,
      children: r.map((c, j) => new TableCell({
        borders, width: { size: widths[j], type: WidthType.DXA },
        shading: i === 0 ? { fill: '1F3B57', type: ShadingType.CLEAR, color: 'auto' } : (i % 2 === 0 ? { fill: 'F2F5F9', type: ShadingType.CLEAR, color: 'auto' } : undefined),
        margins: { top: 60, bottom: 60, left: 100, right: 100 },
        children: [new Paragraph({ children: runs(c, i === 0 ? { bold: true, color: 'FFFFFF', size: 19 } : { size: 19 }) })],
      })),
    })),
  });
}

const children = [];
let i = 0;
while (i < lines.length) {
  const l = lines[i];
  if (l.startsWith('# ')) {
    children.push(new Paragraph({ heading: HeadingLevel.TITLE, alignment: AlignmentType.CENTER, children: runs(l.slice(2)) }));
    i++;
    // subtitle paragraph + TOC after it
    while (i < lines.length && lines[i].trim() === '') i++;
    if (i < lines.length && !lines[i].startsWith('#')) {
      children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 240 }, children: runs(lines[i], { italics: true, color: '555555' }) }));
      i++;
    }
    continue;
  }
  if (l.startsWith('## ')) { children.push(new Paragraph({ heading: HeadingLevel.HEADING_1, children: runs(l.slice(3)) })); i++; continue; }
  if (l.startsWith('### ')) { children.push(new Paragraph({ heading: HeadingLevel.HEADING_2, children: runs(l.slice(4)) })); i++; continue; }
  if (l.startsWith('|')) {
    const rows = [];
    while (i < lines.length && lines[i].startsWith('|')) { if (!/^\|[-| ]+\|$/.test(lines[i])) rows.push(lines[i]); i++; }
    children.push(table(rows));
    children.push(new Paragraph({ spacing: { after: 60 }, children: [] }));
    continue;
  }
  if (l.startsWith('```')) {
    i++;
    while (i < lines.length && !lines[i].startsWith('```')) {
      children.push(new Paragraph({ shading: { fill: 'EEF1F5', type: ShadingType.CLEAR, color: 'auto' }, spacing: { after: 0 },
        children: [new TextRun({ text: lines[i] || ' ', font: 'Consolas', size: 18 })] }));
      i++;
    }
    i++; children.push(new Paragraph({ children: [] }));
    continue;
  }
  if (l.startsWith('- ')) { children.push(new Paragraph({ numbering: { reference: 'bullets', level: 0 }, children: runs(l.slice(2)) })); i++; continue; }
  if (l.trim() === '') { i++; continue; }
  children.push(new Paragraph({ spacing: { after: 120 }, children: runs(l) }));
  i++;
}

const doc = new Document({
  creator: 'Claude', title: 'Tổng hợp tiến trình dự án Đua Xe Sinh Tồn',
  styles: {
    default: { document: { run: { font: FONT, size: 21 } } },
    paragraphStyles: [
      { id: 'Title', name: 'Title', basedOn: 'Normal', run: { size: 40, bold: true, color: '1F3B57', font: FONT }, paragraph: { spacing: { after: 120 } } },
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 30, bold: true, color: '1F3B57', font: FONT }, paragraph: { spacing: { before: 300, after: 140 }, outlineLevel: 0,
          border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: 'D35400', space: 4 } } } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 24, bold: true, color: 'D35400', font: FONT }, paragraph: { spacing: { before: 220, after: 100 }, outlineLevel: 1 } },
    ],
  },
  numbering: { config: [{ reference: 'bullets', levels: [{ level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
    style: { paragraph: { indent: { left: 520, hanging: 260 } } } }] }] },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1000, bottom: 1000, left: 1000, right: 1000 } } },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: 'Đua Xe Sinh Tồn — Tổng hợp tiến trình — Trang ', size: 16, color: '777777' }), new TextRun({ children: [PageNumber.CURRENT], size: 16, color: '777777' })] })] }) },
    children,
  }],
});
Packer.toBuffer(doc).then(b => { fs.writeFileSync(out, b); console.log('ok', out); });
