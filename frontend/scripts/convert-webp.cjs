/**
 * Convert all PNG assets to WebP format for bandwidth optimization.
 * Usage: node scripts/convert-webp.js
 * 
 * Original PNGs are kept as fallback. WebP files are output alongside them.
 * Total estimated reduction: ~39MB → ~10-12MB
 */
const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const ASSETS_DIR = path.resolve(__dirname, '../src/assets');
const QUALITY = 75; // Good balance: ~70-80% smaller than PNG

async function convertPngToWebp(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  if (ext !== '.png') return null;

  const outPath = filePath.replace(/\.png$/i, '.webp');
  // Skip if already converted and newer than source
  if (fs.existsSync(outPath)) {
    const srcStat = fs.statSync(filePath);
    const outStat = fs.statSync(outPath);
    if (outStat.mtimeMs >= srcStat.mtimeMs) {
      return { file: path.basename(filePath), skipped: true };
    }
  }

  try {
    const img = sharp(filePath);
    const metadata = await img.metadata();
    
    // Resize large backgrounds to max 1920px width (they're displayed at 110vw max)
    let pipeline = img;
    if (metadata.width && metadata.width > 1920) {
      pipeline = pipeline.resize(1920, undefined, { fit: 'inside', withoutEnlargement: true });
    }

    await pipeline
      .webp({ quality: QUALITY, effort: 6 })
      .toFile(outPath);

    const srcSize = fs.statSync(filePath).size;
    const outSize = fs.statSync(outPath).size;
    const reduction = ((1 - outSize / srcSize) * 100).toFixed(1);
    
    return {
      file: path.basename(filePath),
      original: (srcSize / 1024 / 1024).toFixed(2) + ' MB',
      webp: (outSize / 1024 / 1024).toFixed(2) + ' MB',
      reduction: reduction + '%',
    };
  } catch (err) {
    console.error(`  ✗ Error converting ${path.basename(filePath)}:`, err.message);
    return { file: path.basename(filePath), error: err.message };
  }
}

async function main() {
  const files = fs.readdirSync(ASSETS_DIR)
    .filter(f => f.toLowerCase().endsWith('.png'))
    .map(f => path.join(ASSETS_DIR, f));

  console.log(`\n📦 Found ${files.length} PNG files to convert\n`);

  const results = [];
  for (const file of files) {
    process.stdout.write(`  Converting ${path.basename(file)}... `);
    const result = await convertPngToWebp(file);
    results.push(result);
    if (result.skipped) {
      console.log('⏭  (up to date)');
    } else if (result.reduction) {
      console.log(`✓ ${result.original} → ${result.webp} (${result.reduction} reduction)`);
    } else {
      console.log(`✗ ${result.error || 'unknown error'}`);
    }
  }

  console.log('\n📊 Summary:');
  let totalOriginal = 0;
  let totalWebp = 0;
  for (const r of results) {
    if (r.original) {
      const origMB = parseFloat(r.original);
      const webpMB = parseFloat(r.webp);
      totalOriginal += origMB;
      totalWebp += webpMB;
    }
  }
  const totalReduction = ((1 - totalWebp / totalOriginal) * 100).toFixed(1);
  console.log(`  Original total: ${totalOriginal.toFixed(2)} MB`);
  console.log(`  WebP total:     ${totalWebp.toFixed(2)} MB`);
  console.log(`  Reduction:      ${totalReduction}%\n`);
}

main().catch(console.error);
