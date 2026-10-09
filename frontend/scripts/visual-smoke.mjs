import {chromium} from 'playwright';
import {spawn} from 'node:child_process';
import {mkdir} from 'node:fs/promises';
import assert from 'node:assert/strict';

const origin = 'http://127.0.0.1:4173';
const screenshotDirectory = 'ui-screenshots';
const server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', 'preview', '--host', '127.0.0.1', '--port', '4173', '--strictPort'], {stdio:'inherit'});
let browser;
async function waitForServer() {
  for (let attempt = 0; attempt < 50; attempt++) {
    try {if ((await fetch(origin)).ok) return;} catch { /* Preview is still starting. */ }
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  throw new Error('The built frontend preview did not start.');
}
try {
  await mkdir(screenshotDirectory, {recursive:true});
  await waitForServer();
  browser = await chromium.launch({headless:true});
  const page = await browser.newPage({viewport:{width:1440,height:1000},locale:'fr-CA'});
  // This smoke test never contacts a real identity provider or reuses a session.
  await page.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.abort());
  await page.addInitScript(() => {localStorage.setItem('i18nextLng','fr');localStorage.setItem('healthys.theme','light');});
  await page.goto(`${origin}/login`);
  await page.locator('.login-card button').first().waitFor();
  await page.waitForFunction(() => !document.querySelector('.login-card button').disabled);
  for (const [device,width,height] of [['desktop',1440,1000],['mobile',390,844]]) {
    await page.setViewportSize({width,height});
    for (const theme of ['light','dark']) {
      await page.getByRole('combobox',{name:'Thème d’affichage'}).selectOption(theme);
      await page.waitForFunction(expected => document.documentElement.dataset.theme === expected, theme);
      const metrics = await page.evaluate(() => ({overflow:document.documentElement.scrollWidth > innerWidth,story:document.querySelector('.welcome-story').getBoundingClientRect().toJSON(),card:document.querySelector('.login-card').getBoundingClientRect().toJSON(),font:getComputedStyle(document.body).fontSize,theme:document.documentElement.dataset.theme,passwordFields:document.querySelectorAll('input[type=password]').length}));
      assert.equal(metrics.overflow,false,`${device}/${theme}: horizontal overflow`);
      assert.equal(metrics.font,'14px');
      assert.equal(metrics.passwordFields,0,'Passwords must stay in Keycloak.');
      if (device === 'desktop') assert.ok(metrics.card.x >= metrics.story.right,'Desktop uses two columns.');
      else assert.ok(metrics.card.y >= metrics.story.bottom,'Mobile stacks the authentication card.');
      await page.screenshot({path:`${screenshotDirectory}/login-${device}-${theme}.png`,fullPage:true});
      console.log(`PASS login ${device}/${theme}`);
    }
  }
} finally {
  await browser?.close();
  server.kill('SIGTERM');
}
