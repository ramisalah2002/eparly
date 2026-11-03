const http = require('http');

function makeRequest(method, path, body, cookies) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'localhost',
      port: 3000,
      path: path,
      method: method,
      headers: { 'Content-Type': 'application/json' },
    };
    if (cookies) options.headers['Cookie'] = cookies;
    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        const setCookie = res.headers['set-cookie'];
        resolve({ status: res.statusCode, body: data, setCookie });
      });
    });
    req.on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

async function main() {
  console.log('=== 1. Login Next.js ===');
  const loginResp = await makeRequest('POST', '/api/auth/login', {
    email: 'youneselamrani015@gmail.com',
    password: 'younes2004'
  });
  console.log('Statut :', loginResp.status);
  console.log('Réponse :', loginResp.body);
  
  if (loginResp.setCookie) {
    const cookieHeader = loginResp.setCookie.map(c => c.split(';')[0]).join('; ');
    console.log('\nCookies :', loginResp.setCookie.map(c => c.split('=')[0] + '=' + c.split(';')[1].split('=')[0]).join(', '));
    
    console.log('\n=== 2. Dashboard ===');
    const dashResp = await makeRequest('GET', '/dashboard', null, cookieHeader);
    console.log('Statut :', dashResp.status);
    console.log('Contient dashboard :', dashResp.body.includes('Tableau de bord'));
    console.log('Longueur :', dashResp.body.length);
    
    console.log('\n=== 3. Catalogue offres ===');
    const offersResp = await makeRequest('GET', '/catalog/offers', null, cookieHeader);
    console.log('Statut :', offersResp.status);
    console.log('Contient offres :', offersResp.body.includes('Offres commerciales'));
    
    console.log('\n=== 4. Centre de validation ===');
    const reviewsResp = await makeRequest('GET', '/catalog/reviews', null, cookieHeader);
    console.log('Statut :', reviewsResp.status);
    console.log('Contient reviews :', reviewsResp.body.includes('Centre de validation'));
  }
}

main();
