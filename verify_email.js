const http = require('http');

function makeRequest(host, port, path, method) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: host,
      port: port,
      path: path,
      method: method,
    };
    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        resolve({ status: res.statusCode, body: data, headers: res.headers });
      });
    });
    req.on('error', reject);
    req.end();
  });
}

async function main() {
  const email = 'youneselamrani015@gmail.com';
  
  // Get all messages
  const mailResp = await makeRequest('localhost', 8025, '/api/v1/messages?page=1&size=20', 'GET');
  const mailData = JSON.parse(mailResp.body);
  console.log('Total messages :', mailData.total);
  
  let code = null;
  for (const msg of mailData.messages || []) {
    const toAddresses = msg.To ? msg.To.map(t => t.Address).join(', ') : 'N/A';
    console.log(`Message: ID=${msg.ID}, To=${toAddresses}, Subject="${msg.Subject}"`);
    
    if (msg.To && msg.To.some(t => t.Address === email)) {
      const text = (msg.Content && (msg.Content.Text || msg.Content.HTML || '')) || '';
      console.log('  Contenu:', text.substring(0, 300));
      const codeMatch = text.match(/\b\d{6}\b/);
      if (codeMatch) {
        code = codeMatch[0];
        console.log('  >>> CODE DE VERIFICATION :', code);
      }
    }
  }

  if (code) {
    console.log('\n=== Vérification de l\'email ===');
    const http2 = require('http');
    function makeAuthRequest(method, path, body) {
      return new Promise((resolve, reject) => {
        const options = {
          hostname: 'localhost',
          port: 8080,
          path: path,
          method: method,
          headers: { 'Content-Type': 'application/json' },
        };
        const req = http.request(options, (res) => {
          let data = '';
          res.on('data', (chunk) => { data += chunk; });
          res.on('end', () => {
            resolve({ status: res.statusCode, body: data });
          });
        });
        req.on('error', reject);
        if (body) req.write(JSON.stringify(body));
        req.end();
      });
    }
    
    const verifyResp = await makeAuthRequest('POST', '/v1/auth/verify-email', { token: code });
    console.log('Statut :', verifyResp.status);
    console.log('Réponse :', verifyResp.body);
  }
}

main();
