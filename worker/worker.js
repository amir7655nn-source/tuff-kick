// Cloudflare Worker: holds the Kick client secret so it never has to be inside the APK.
//
// Settings of the worker (Settings -> Variables and Secrets):
//   KICK_CLIENT_ID      (text)
//   KICK_CLIENT_SECRET  (secret)
//
// Routes used by the app:
//   GET  /app-token                       -> app token (public data)
//   POST /token    {code, redirect_uri, code_verifier}   -> user token
//   POST /refresh  {refresh_token}                       -> new user token

const TOKEN_URL = "https://id.kick.com/oauth/token";
const ALLOWED_REDIRECT = "https://localhost/callback";

async function kick(env, grant, extra) {
  const body = new URLSearchParams({
    grant_type: grant,
    client_id: env.KICK_CLIENT_ID,
    client_secret: env.KICK_CLIENT_SECRET,
    ...extra,
  });
  const r = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  return new Response(await r.text(), {
    status: r.status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

const bad = (msg) => new Response(JSON.stringify({ error: msg }), { status: 400, headers: { "Content-Type": "application/json" } });

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);

    if (request.method === "GET" && pathname === "/") return new Response("TUFF KICK token worker: ok");
    if (request.method === "GET" && pathname === "/app-token") return kick(env, "client_credentials", {});

    if (request.method === "POST" && (pathname === "/token" || pathname === "/refresh")) {
      let j;
      try { j = await request.json(); } catch (e) { return bad("invalid json"); }
      if (pathname === "/token") {
        if (!j.code || !j.code_verifier) return bad("missing fields");
        if (j.redirect_uri !== ALLOWED_REDIRECT) return bad("redirect_uri not allowed");
        return kick(env, "authorization_code", { code: j.code, code_verifier: j.code_verifier, redirect_uri: j.redirect_uri });
      }
      if (!j.refresh_token) return bad("missing fields");
      return kick(env, "refresh_token", { refresh_token: j.refresh_token });
    }
    return new Response("not found", { status: 404 });
  },
};
