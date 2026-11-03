const http = require('http');

function makeRequest(method, path, body) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'localhost',
      port: 8080,
      path: path,
      method: method,
      headers: {
        'Content-Type': 'application/json',
      },
    };

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        resolve({ status: res.statusCode, body: data, headers: res.headers });
      });
    });

    req.on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

async function main() {
  const email = 'youneselamrani015@gmail.com';
  const password = 'younes2004';

  console.log('=== ÉTAPE 1 : Inscription ===');
  try {
    const regResp = await makeRequest('POST', '/v1/auth/register', {
      email: email,
      password: password,
      termsAccepted: true,
      marketingConsent: false,
      fullName: 'Younes Elamrani'
    });
    console.log('Statut d\'inscription :', regResp.status);
    console.log('Réponse :', regResp.body);

    if (regResp.status === 409) {
      console.log('\nLe compte existe déjà. Il faudra utiliser le workflow de réinitialisation de mot de passe.');
    } else if (regResp.status === 201) {
      console.log('\nCompte créé avec succès. Vérification de l\'email via Mailpit...');
      
      // Attendre un peu puis vérifier Mailpit
      await new Promise(r => setTimeout(r, 2000));
      
      const mailResp = await makeRequest('GET', 'http://localhost:8025/api/v1/messages?page=1&size=10');
      const mailData = JSON.parse(mailResp.body);
      console.log('Messages trouvés :', mailData.total);
      
      let code = null;
      for (const msg of mailData.messages || []) {
        if (msg.To && msg.To.some(t => t.Address === email)) {
          const text = (msg.Content && (msg.Content.Text || msg.Content.HTML)) || '';
          const codeMatch = text.match(/\b\d{6}\b/);
          if (codeMatch) {
            code = codeMatch[0];
            console.log('Code de vérification trouvé :', code);
            break;
          }
        }
      }

      if (code) {
        console.log('\n=== ÉTAPE 2 : Vérification de l\'email ===');
        const verifyResp = await makeRequest('POST', '/v1/auth/verify-email', { token: code });
        console.log('Statut vérification :', verifyResp.status);
        console.log('Réponse :', verifyResp.body);
      } else {
        console.log('Aucun code de vérification trouvé dans les emails.');
      }
    }
  } catch (err) {
    console.error('Erreur :', err.message);
  }
}

main();
