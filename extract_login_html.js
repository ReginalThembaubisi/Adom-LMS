const fs = require('fs');

const logPath = 'C:\\Users\\HP\\.gemini\\antigravity\\brain\\2e08af11-8be2-417f-9273-4963a5a47aef\\.system_generated\\logs\\transcript_full.jsonl';

const fileStream = fs.createReadStream(logPath, { encoding: 'utf8' });
let remaining = '';

fileStream.on('data', (chunk) => {
    remaining += chunk;
    let index = remaining.indexOf('\n');
    while (index > -1) {
        const line = remaining.substring(0, index);
        remaining = remaining.substring(index + 1);
        processLine(line);
        index = remaining.indexOf('\n');
    }
});

fileStream.on('end', () => {
    if (remaining.length > 0) {
        processLine(remaining);
    }
});

function processLine(line) {
    if (!line.trim()) return;
    try {
        const obj = JSON.parse(line);
        if (obj.type === 'VIEW_FILE' && obj.content && (obj.content.includes('login.html') || obj.content.includes('Facilitator Login') || obj.content.includes('facilitator-login-form'))) {
            console.log(`--- STEP ${obj.step_index} ---`);
            console.log(obj.content);
        }
    } catch (e) {
        // Ignore
    }
}
