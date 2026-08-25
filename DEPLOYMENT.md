# Spedite deployment

The app is packaged as three containers: Angular/nginx, Spring Boot, and PostgreSQL.
The frontend serves the site and forwards `/api` to the backend, so the browser uses
one origin and no production API URL is compiled into the JavaScript.

## Before you deploy

Staff authentication is not implemented yet. Do not expose the staff workspace and
API directly to the public internet. Put this release behind a private network,
VPN, or an access gateway such as Cloudflare Access until application sign-in and
authorization are added.

## Start the release

1. Copy `.env.example` to `.env`.
2. Replace `DB_PASSWORD` with a long, unique password.
3. Run `docker compose up --build -d`.
4. Open `http://localhost:8088` (or the port set in `APP_PORT`).

The PostgreSQL data is kept in the `spedite_postgres_data` Docker volume. Back up
that volume before upgrades.

## Verification

```bash
cd spedite-frontend
npm ci
npm test -- --run
npm run build

cd ../spedite-logistics
./mvnw test
```

Angular 21 requires Node.js 20.19+ or 22.12+. The Docker build already uses Node 22.

## Shareable bilty links (next release)

This is a good fit for the existing LR/PDF flow. Use an unguessable, revocable share
token instead of putting the LR number alone in a public URL:

`https://your-domain.example/bilty/s/<random-token>`

That page can show a read-only bilty preview, shipment details, and a Print / Download
PDF action. The token record should support an expiry date, revocation, and an audit
timestamp. Sensitive internal fields, driver contact details, and freight amounts
should be hidden by default and enabled per link only when required.
