const minimumSecretLength = 32;

function readEnv(key) {
	return process.env[key]?.trim() || '';
}

function ensureApiBaseUrl() {
	const apiBaseUrl = readEnv('API_BASE_URL');
	const viteApiBaseUrl = readEnv('VITE_API_BASE_URL');

	if (!apiBaseUrl && !viteApiBaseUrl) {
		console.error(
			'Missing required environment variable: API_BASE_URL. VITE_API_BASE_URL may also be provided as a client-exposed alias.'
		);
		process.exit(1);
	}
}

function validateSessionSecret(secret) {
	const classes = [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/];
	const strength = classes.filter((pattern) => pattern.test(secret)).length;

	if (secret.length < minimumSecretLength || strength < 3 || /change-me|changeme/i.test(secret)) {
		console.error(
			`APP_SESSION_SECRET must be at least ${minimumSecretLength} characters, use at least three character classes, and not use a placeholder value.`
		);
		process.exit(1);
	}
}

ensureApiBaseUrl();

const sessionSecret = readEnv('APP_SESSION_SECRET');
if (!sessionSecret) {
	console.error('Missing required environment variable: APP_SESSION_SECRET');
	process.exit(1);
}

validateSessionSecret(sessionSecret);
