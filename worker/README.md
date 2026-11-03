# Token worker (Cloudflare)

Keeps the Kick client secret out of the APK. Create a Worker in the Cloudflare dashboard, paste `worker.js`,
add `KICK_CLIENT_ID` (text) and `KICK_CLIENT_SECRET` (secret) in its settings, and put the worker address in the
GitHub secret `KICK_WORKER_URL`. Full steps are in the main README.
