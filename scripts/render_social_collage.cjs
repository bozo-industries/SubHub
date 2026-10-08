/* Render share-ready layouts from genuine Android screenshots. No screen content is redrawn. */
const fs = require('node:fs');
const path = require('node:path');
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const sharp = require(option('--sharp-module', 'sharp'));
const root = path.resolve(__dirname, '..');
const input = path.resolve(option('--screenshots', path.join(root, 'docs/screenshots/ui-map/page-map')));
const output = path.resolve(option('--output', path.join(root, 'docs/marketing')));
const screens = [
  {file:'05a-settings-appearance.png', title:'CENSOR', detail:'Choose your look'},
  {file:'02-limits.png', title:'APP LIMITS', detail:'Set the boundaries'},
  {file:'23-wizard-features.png', title:'PACK MAKER', detail:'Make it your own'},
];
const shield = fs.readFileSync(path.join(root, 'app/src/main/res/drawable/ic_nav_censor.xml'), 'utf8')
  .match(/android:pathData="([^"]+)"/)[1];
const escape = value => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
const text = (x,y,size,value,extra='') => `<text x="${x}" y="${y}" font-size="${size}" ${extra}>${escape(value)}</text>`;

async function render(format, height) {
  const portrait = height > 1080;
  const phoneWidth = portrait ? 292 : 260;
  const gap = portrait ? 40 : 64;
  const first = (1080 - phoneWidth * 3 - gap * 2) / 2;
  const phoneTop = portrait ? 437 : 353;
  const screenWidth = phoneWidth - 18;
  const definitions = [];
  const phones = [];
  for (let i = 0; i < screens.length; i++) {
    const screen = screens[i], x = first + i * (phoneWidth + gap);
    const file = path.join(input, screen.file);
    const meta = await sharp(file).metadata();
    if (!meta.width || !meta.height || meta.height <= meta.width) throw new Error(`Expected portrait capture: ${screen.file}`);
    const screenHeight = Math.round(screenWidth * meta.height / meta.width);
    const phoneHeight = screenHeight + 18;
    if (phoneTop + phoneHeight + 35 > height - 130) throw new Error(`Capture aspect ratio does not fit: ${screen.file}`);
    const data = fs.readFileSync(file).toString('base64');
    definitions.push(`<clipPath id="screen-${i}"><rect x="${x+9}" y="${phoneTop+9}" width="${screenWidth}" height="${screenHeight}" rx="22"/></clipPath>`);
    phones.push(text(x+phoneWidth/2,phoneTop-27,23,screen.title,'text-anchor="middle" fill="#d7bedf" font-weight="700" letter-spacing="2"'));
    phones.push(`<rect x="${x}" y="${phoneTop}" width="${phoneWidth}" height="${phoneHeight}" rx="30" fill="#08070d" stroke="#876a9c" stroke-width="2" filter="url(#shadow)"/>`);
    phones.push(`<image href="data:image/png;base64,${data}" x="${x+9}" y="${phoneTop+9}" width="${screenWidth}" height="${screenHeight}" clip-path="url(#screen-${i})"/>`);
    phones.push(text(x+phoneWidth/2,phoneTop+phoneHeight+35,21,screen.detail,'text-anchor="middle" fill="#eee4f2"'));
  }
  const headlineY = portrait ? 260 : 232;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="${height}" viewBox="0 0 1080 ${height}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#21142e"/><stop offset=".5" stop-color="#0c0911"/><stop offset="1" stop-color="#27152e"/></linearGradient>
    <radialGradient id="glow"><stop stop-color="#74508b" stop-opacity=".35"/><stop offset="1" stop-color="#74508b" stop-opacity="0"/></radialGradient>
    <filter id="shadow" x="-30%" y="-10%" width="160%" height="125%"><feDropShadow dx="0" dy="18" stdDeviation="18" flood-color="#000" flood-opacity=".65"/></filter>
    ${definitions.join('\n')}
  </defs>
  <rect width="1080" height="${height}" fill="url(#bg)"/>
  <ellipse cx="760" cy="370" rx="700" ry="550" fill="url(#glow)"/>
  <path d="M-40 ${height-120} Q530 ${height-410} 1120 ${height-65}" fill="none" stroke="#866493" stroke-width="1" opacity=".25"/>
  <path d="M-40 ${height-70} Q530 ${height-320} 1120 ${height-15}" fill="none" stroke="#866493" stroke-width="1" opacity=".15"/>
  <g transform="translate(93,75) scale(3.4)"><path d="${shield}" fill="none" stroke="#c7a4d5" stroke-width="1.1" stroke-linecap="round" stroke-linejoin="round"/></g>
  <g font-family="Segoe UI, Arial, sans-serif" fill="#f6eff8">
    ${text(212,92,17,'PRIVATE ANDROID CONTROL','fill="#bfa3ca" letter-spacing="4"')}
    ${text(209,160,75,'SUBHUB','font-family="Georgia, serif" letter-spacing="10"')}
    ${portrait ? text(75,headlineY,54,'Set the terms.','font-weight="700"')+text(75,headlineY+66,54,'Hand over control.','font-weight="700" fill="#c8a7d6"') : text(540,headlineY,43,'Set the terms. Hand over control.','text-anchor="middle" font-weight="700"')}
    ${text(portrait?75:540,portrait?headlineY+115:headlineY+45,23,'Censor. Limit. Build your own setup.',portrait?'fill="#bdaac6"':'text-anchor="middle" fill="#bdaac6"')}
    ${phones.join('\n')}
    <line x1="75" y1="${height-104}" x2="1005" y2="${height-104}" stroke="#5b4268"/>
    ${text(540,height-67,20,'Subliminals · Optional Wallet · Achievements','text-anchor="middle" fill="#baa5c4"')}
    ${text(540,height-29,19,'Get SubHub → github.com/confiteor48/SubHub','text-anchor="middle" fill="#d9c8e1"')}
  </g>
  </svg>`;
  fs.mkdirSync(output, {recursive:true});
  const basename = `subhub-social-${format}`;
  fs.writeFileSync(path.join(output, `${basename}.svg`), svg, 'utf8');
  await sharp(Buffer.from(svg)).png().toFile(path.join(output, `${basename}.png`));
  console.log(`${basename}: 1080×${height}; real screenshot sources: ${screens.map(s=>s.file).join(', ')}`);
}
(async () => { await render('square', 1080); await render('portrait', 1350); })()
  .catch(error => { console.error(error.message); process.exitCode = 1; });
