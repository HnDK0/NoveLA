import JSZip from 'jszip';
import { Book, Chapter } from '../types';

export async function parseEpubFile(file: File): Promise<{ book: Book; chapters: Chapter[] }> {
  const zip = new JSZip();
  const loadedZip = await zip.loadAsync(file);

  let title = file.name.replace(/\.[^/.]+$/, '');
  let author = 'Unknown Author';
  let description = 'Imported local book';
  let coverImageUrl = 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80';
  const chapters: Chapter[] = [];
  const bookUrl = `local:${Date.now()}_${encodeURIComponent(title.toLowerCase().replace(/\s+/g, '-'))}`;

  // Find container.xml to locate root .opf
  let opfPath = '';
  const containerXml = loadedZip.file('META-INF/container.xml');
  if (containerXml) {
    const text = await containerXml.async('text');
    const match = text.match(/full-path=["']([^"']+\.opf)["']/i);
    if (match) opfPath = match[1];
  }

  // Parse OPF if found
  if (opfPath && loadedZip.file(opfPath)) {
    const opfText = await loadedZip.file(opfPath)!.async('text');
    const titleMatch = opfText.match(/<dc:title[^>]*>([^<]+)<\/dc:title>/i);
    if (titleMatch) title = titleMatch[1].trim();

    const creatorMatch = opfText.match(/<dc:creator[^>]*>([^<]+)<\/dc:creator>/i);
    if (creatorMatch) author = creatorMatch[1].trim();

    const descMatch = opfText.match(/<dc:description[^>]*>([^<]+)<\/dc:description>/i);
    if (descMatch) description = descMatch[1].trim();
  }

  // Find html/xhtml chapter files
  const htmlFiles: { name: string; file: JSZip.JSZipObject }[] = [];
  loadedZip.forEach((relativePath, zipEntry) => {
    if (!zipEntry.dir && /\.(html|xhtml|htm)$/i.test(relativePath)) {
      htmlFiles.push({ name: relativePath, file: zipEntry });
    }
  });

  // Sort files naturally
  htmlFiles.sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' }));

  let pos = 1;
  for (const item of htmlFiles) {
    const rawHtml = await item.file.async('text');
    // Strip tags and clean
    const tempDiv = document.createElement('div');
    tempDiv.innerHTML = rawHtml;
    // Extract title if present in h1/h2 or title
    const header = tempDiv.querySelector('h1, h2, h3, title');
    const chapterTitle = header?.textContent?.trim() || `Chapter ${pos}`;
    const chapterContent = tempDiv.innerText.trim();

    if (chapterContent.length > 50) {
      chapters.push({
        title: chapterTitle,
        url: `${bookUrl}:c${pos}`,
        bookUrl,
        position: pos,
        read: false,
        lastReadPosition: 0,
        lastReadOffset: 0,
        uploaded: Date.now(),
        volume: 'Volume 1',
        content: chapterContent,
        isDownloaded: true,
      });
      pos++;
    }
  }

  if (chapters.length === 0) {
    // If no html files or plain text, create single chapter
    chapters.push({
      title: 'Chapter 1: Imported Content',
      url: `${bookUrl}:c1`,
      bookUrl,
      position: 1,
      read: false,
      lastReadPosition: 0,
      lastReadOffset: 0,
      uploaded: Date.now(),
      volume: 'Volume 1',
      content: `Content imported from ${file.name}.\n\nWelcome to your offline book.`,
      isDownloaded: true,
    });
  }

  const book: Book = {
    title,
    author,
    url: bookUrl,
    completed: false,
    inLibrary: true,
    coverImageUrl,
    description,
    lastReadEpochTimeMilli: 0,
    addedToLibraryEpochTimeMilli: Date.now(),
    lastUpdateEpochTimeMilli: Date.now(),
    category: 'Reading',
    genres: 'Local,Imported',
    rating: '5.0',
    status: 'Ongoing',
    lastUpdateDate: 'Just now',
    contentType: 'NOVEL',
    sourceId: 'localsource',
    sourceName: 'Local Books',
    totalChapters: chapters.length,
  };

  return { book, chapters };
}

export async function exportBookToEpub(book: Book, chapters: Chapter[]): Promise<Blob> {
  const zip = new JSZip();

  // mimetype
  zip.file('mimetype', 'application/epub+zip', { compression: 'STORE' });

  // META-INF/container.xml
  zip.folder('META-INF')!.file(
    'container.xml',
    `<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>`
  );

  const oebps = zip.folder('OEBPS')!;

  // Chapter files
  const manifestItems: string[] = [];
  const spineItems: string[] = [];

  chapters.forEach((ch, idx) => {
    const filename = `chapter_${idx + 1}.xhtml`;
    manifestItems.push(`<item id="ch_${idx + 1}" href="${filename}" media-type="application/xhtml+xml"/>`);
    spineItems.push(`<itemref idref="ch_${idx + 1}"/>`);

    const paragraphs = (ch.content || 'No content.')
      .split('\n\n')
      .map((p) => `<p>${escapeXml(p.trim())}</p>`)
      .join('\n');

    const xhtmlContent = `<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${escapeXml(ch.title)}</title>
  <style>
    body { font-family: sans-serif; line-height: 1.6; padding: 1em; }
    h1 { color: #333; margin-bottom: 1em; }
    p { margin-bottom: 0.8em; text-indent: 1.5em; }
  </style>
</head>
<body>
  <h1>${escapeXml(ch.title)}</h1>
  ${paragraphs}
</body>
</html>`;

    oebps.file(filename, xhtmlContent);
  });

  // content.opf
  const opf = `<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookID" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>${escapeXml(book.title)}</dc:title>
    <dc:creator>${escapeXml(book.author || 'Unknown Author')}</dc:creator>
    <dc:language>en</dc:language>
    <dc:identifier id="BookID">urn:uuid:${encodeURIComponent(book.url)}</dc:identifier>
    <dc:description>${escapeXml(book.description || '')}</dc:description>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    ${manifestItems.join('\n    ')}
  </manifest>
  <spine toc="ncx">
    ${spineItems.join('\n    ')}
  </spine>
</package>`;

  oebps.file('content.opf', opf);

  // toc.ncx
  const navPoints = chapters
    .map(
      (ch, idx) => `
    <navPoint id="navPoint-${idx + 1}" playOrder="${idx + 1}">
      <navLabel><text>${escapeXml(ch.title)}</text></navLabel>
      <content src="chapter_${idx + 1}.xhtml"/>
    </navPoint>`
    )
    .join('');

  const ncx = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="${encodeURIComponent(book.url)}"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>${escapeXml(book.title)}</text></docTitle>
  <navMap>
    ${navPoints}
  </navMap>
</ncx>`;

  oebps.file('toc.ncx', ncx);

  return await zip.generateAsync({ type: 'blob', mimeType: 'application/epub+zip' });
}

export function exportBookToTxt(book: Book, chapters: Chapter[]): Blob {
  let content = `Title: ${book.title}\nAuthor: ${book.author || 'Unknown'}\nSource: ${book.sourceName}\nURL: ${book.url}\n\nDescription:\n${book.description}\n\n=========================================\n\n`;

  chapters.forEach((ch, idx) => {
    content += `\n\n-----------------------------------------\n`;
    content += `${ch.title}\n`;
    content += `-----------------------------------------\n\n`;
    content += `${ch.content || ''}\n`;
  });

  return new Blob([content], { type: 'text/plain;charset=utf-8' });
}

function escapeXml(unsafe: string): string {
  return unsafe.replace(/[<>&'"]/g, (c) => {
    switch (c) {
      case '<':
        return '&lt;';
      case '>':
        return '&gt;';
      case '&':
        return '&amp;';
      case '\'':
        return '&apos;';
      case '"':
        return '&quot;';
      default:
        return c;
    }
  });
}
