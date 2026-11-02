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
  // Try different Mailpit API endpoints
  const endpoints = [
    '/api/v1/messages',
    '/api/messages',
    '/messages',
    '/api/v1/search',
  ];
  
  for (const ep of endpoints) {
    try {
      const resp = await makeRequest('localhost', 8025, ep, 'GET');
      console.log(`GET ${ep} -> ${resp.status}`);
      console.log('Content-Type:', resp.headers['content-type']);
      console.log('Body preview:', resp.body.substring(0, 200));
      console.log('---');
    } catch (err) {
      console.log(`GET ${ep} -> ERROR: ${err.message}`);
    }
  }
}

main();
