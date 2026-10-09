import {chromium} from 'playwright';
import {spawn} from 'node:child_process';
import {mkdir,writeFile} from 'node:fs/promises';
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
  // Exercise the real OIDC adapter against intercepted, test-only responses.
  const authenticated = await browser.newContext({viewport:{width:1440,height:1000},locale:'fr-CA'});
  await authenticated.addInitScript(() => {localStorage.setItem('i18nextLng','fr');});
  let nonce;
  let preference = 'LIGHT';
  const subject = '11111111-1111-4111-8111-111111111111';
  const jwt = claims => `${Buffer.from(JSON.stringify({alg:'none',typ:'JWT'})).toString('base64url')}.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.test-only`;
  const organizations = [{id:'22222222-2222-4222-8222-222222222222',number:'ORG-001',name:'Clinique du Parc',legalName:'Clinique du Parc',status:'ACTIVE'},{id:'33333333-3333-4333-8333-333333333333',number:'ORG-002',name:'Centre médical Horizon',status:'ACTIVE'}];
  await authenticated.route('**/*',async route => {
    const url = new URL(route.request().url());
    if (url.pathname.includes('/3p-cookies/')) return route.fulfill({contentType:'text/html',body:`<script>parent.postMessage('supported','*')</script>`});
    if (url.pathname.endsWith('/openid-connect/auth')) {
      nonce = url.searchParams.get('nonce');
      const target = `${url.searchParams.get('redirect_uri')}#code=visual-test-code&state=${url.searchParams.get('state')}&session_state=visual-test-session`;
      return route.fulfill({status:302,headers:{location:target}});
    }
    if (url.pathname.endsWith('/openid-connect/token')) {
      if (route.request().method() === 'OPTIONS') return route.fulfill({status:204,headers:{'access-control-allow-origin':origin,'access-control-allow-credentials':'true','access-control-allow-methods':'POST,OPTIONS','access-control-allow-headers':'content-type'}});
      const now = Math.floor(Date.now()/1000);
      const token = jwt({sub:subject,iat:now,exp:now+3600,nonce,name:'Camille Martin',preferred_username:'camille',realm_access:{roles:['admin']}});
      return route.fulfill({contentType:'application/json',headers:{'access-control-allow-origin':origin,'access-control-allow-credentials':'true'},body:JSON.stringify({access_token:token,id_token:token,refresh_token:token,token_type:'Bearer',expires_in:3600})});
    }
    if (url.origin !== origin) return route.abort();
    if (!url.pathname.startsWith('/api/')) return route.continue();
    let data;
    if (url.pathname === '/api/v1/persons/me') data = {id:subject,firstName:'Camille',lastName:'Martin',personNumber:'PER-001',contacts:[],addresses:[],emergencyContacts:[],status:'ACTIVE'};
    else if (url.pathname === '/api/v1/persons/me/preferences') {
      if (route.request().method() === 'PUT') preference = route.request().postDataJSON().theme;
      data = {theme:preference,avatarUrl:null};
    } else if (url.pathname === '/api/v1/organizations/types') data = [{id:'44444444-4444-4444-8444-444444444444',code:'CLINIC',label:'Clinique'}];
    else if (url.pathname === '/api/v1/organizations') data = {content:organizations,page:{number:0,size:20,totalElements:2,totalPages:1,first:true,last:true}};
    else if (url.pathname === '/api/v1/notifications/unread-count') data = {unreadCount:2};
    else data = [];
    return route.fulfill({contentType:'application/json',body:JSON.stringify(data)});
  });
  const workspace = await authenticated.newPage();
  const diagnostics = [];
  workspace.on('console', message => diagnostics.push(`console ${message.type()}: ${message.text()}`));
  workspace.on('pageerror', error => diagnostics.push(`pageerror: ${error.message}`));
  workspace.on('requestfailed', request => diagnostics.push(`requestfailed: ${request.url()} ${request.failure()?.errorText}`));
  workspace.on('response', response => {if (response.url().includes('/openid-connect/') || response.url().includes('/api/')) diagnostics.push(`response ${response.status()}: ${response.url()}`);});
  await workspace.goto(`${origin}/organizations`);
  try {await workspace.getByRole('cell',{name:'Clinique du Parc',exact:true}).waitFor();}
  catch (error) {
    await workspace.screenshot({path:`${screenshotDirectory}/authenticated-failure.png`,fullPage:true});
    diagnostics.push(`final URL: ${workspace.url()}`,await workspace.locator('body').innerText());
    await writeFile(`${screenshotDirectory}/authenticated-diagnostics.txt`,diagnostics.join('\n'));
    console.error(diagnostics.join('\n'));
    throw error;
  }
  for (const [device,width,height] of [['desktop',1440,1000],['mobile',390,844]]) {
    await workspace.setViewportSize({width,height});
    for (const theme of ['light','dark']) {
      await workspace.getByRole('combobox',{name:'Thème d’affichage'}).selectOption(theme);
      await workspace.waitForFunction(expected => document.documentElement.dataset.theme === expected,theme);
      assert.equal(await workspace.evaluate(()=>document.documentElement.scrollWidth > innerWidth),false,`Organizations ${device}/${theme}: horizontal overflow`);
      await workspace.screenshot({path:`${screenshotDirectory}/organizations-${device}-${theme}.png`,fullPage:true});
      console.log(`PASS organizations ${device}/${theme}`);
    }
  }
  await workspace.setViewportSize({width:1440,height:1000});
  await workspace.goto(`${origin}/organizations/new`);
  await workspace.locator('form.entity-form').waitFor();
  await workspace.getByRole('option',{name:'Clinique',exact:true}).waitFor({state:'attached'});
  for (const [device,width,height] of [['desktop',1440,1000],['mobile',390,844]]) {
    await workspace.setViewportSize({width,height});
    for (const theme of ['light','dark']) {
    await workspace.getByRole('combobox',{name:'Thème d’affichage'}).selectOption(theme);
    await workspace.waitForFunction(expected => document.documentElement.dataset.theme === expected,theme);
    assert.equal(await workspace.evaluate(()=>document.documentElement.scrollWidth > innerWidth),false);
    await workspace.screenshot({path:`${screenshotDirectory}/organization-form-${device}-${theme}.png`,fullPage:true});
    console.log(`PASS organization form ${device}/${theme}`);
    }
  }
  await authenticated.close();
} finally {
  await browser?.close();
  server.kill('SIGTERM');
}
