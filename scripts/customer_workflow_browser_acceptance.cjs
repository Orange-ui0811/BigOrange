// Real local UI and backend. Fixture credentials enter via stdin and are never persisted.
const fs = require('node:fs');
const {chromium, expect} = require('../orange-web/node_modules/@playwright/test');
(async () => {
  const fixture = JSON.parse(fs.readFileSync(0, 'utf8'));
  const browser = await chromium.launch({headless:true});
  const context = await browser.newContext({viewport:{width:1440,height:1000}});
  await context.addInitScript(({token,account})=>{
    sessionStorage.setItem('appToken',token);sessionStorage.setItem('myId',String(account));
    localStorage.setItem('tour_done_trend_'+account,'1');
  },fixture);
  const page=await context.newPage();const errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  try {
    await page.goto('http://127.0.0.1:15173/customer');
    const notice=page.getByRole('dialog',{name:'OrangeVC 功能更新'});
    await expect(notice).toBeVisible({timeout:15000});
    await notice.getByRole('button',{name:'知道了',exact:true}).click();
    await page.getByRole('button',{name:fixture.sessionTitle,exact:true}).click();
    await expect(page.getByRole('button',{name:'确认沿用',exact:true})).toBeVisible();
    await page.getByRole('button',{name:'确认沿用',exact:true}).click();
    await expect(page.locator('.assistant-markdown').last()).toContainText('已沿用这些有原话依据的需求',{timeout:20000});
    await page.screenshot({path:fixture.output+'/browser-legacy-confirmed.png',fullPage:true});
    await page.reload();
    await page.getByRole('button',{name:fixture.sessionTitle,exact:true}).click();
    await expect(page.locator('.assistant-markdown').last()).toContainText('已沿用这些有原话依据的需求');
    await expect(page.getByRole('button',{name:'确认沿用',exact:true})).toHaveCount(0);
    await page.setViewportSize({width:390,height:844});
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth+1);
    await page.screenshot({path:fixture.output+'/browser-mobile.png',fullPage:true});
    console.log(JSON.stringify({realBackend:true,legacyConfirmation:true,reloadRestores:true,obsoleteQuestionHidden:true,mobileOverflow:overflow,pageErrors:errors,passed:!overflow&&!errors.length}));
  } finally {await browser.close();}
})().catch(error=>{console.error(error.message);process.exitCode=1;});
