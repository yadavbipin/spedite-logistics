# Render test deployment

This root `Dockerfile` deploys the Angular frontend and Spring Boot API as one
Render web service. Create PostgreSQL as a separate Render database in the same
region. Do not deploy the PostgreSQL container from `docker-compose.yml` to Render.

## Render setup

1. Push this project to a **private** GitHub repository. Do not commit `.env`.
2. In Render, create a new **PostgreSQL** database.
3. Create a new **Web Service** from the GitHub repository. Select **Docker**,
   leave the root directory as the repository root, and use `Dockerfile`.
4. Add the following web-service environment variables from the connection details
   shown for the Render PostgreSQL database:

   ```text
   JDBC_DATABASE_URL=jdbc:postgresql://DATABASE_HOST:DATABASE_PORT/DATABASE_NAME
   DB_USERNAME=DATABASE_USER
   DB_PASSWORD=DATABASE_PASSWORD
   JPA_DDL_AUTO=update
   ```

   Replace the placeholders with the database's internal host, port, database name,
   username, and password. The JDBC URL uses a slash before the database name, for
   example `jdbc:postgresql://host:5432/database`.

5. Deploy. Render supplies the public port automatically; do not set `PORT`.

## Import existing local data (optional)

Create a portable dump from the local Docker database:

```bash
docker compose exec -T database pg_dump -U spedite -d logistics_db -Fc --no-owner > logistics-backup.dump
```

Restore it into the empty Render PostgreSQL database using `pg_restore` and its
temporary public connection URL. Disable public database access again after import.

## Important

The current staff application has no authentication. Treat the Render URL as a
private test link only; do not use it for public or customer access yet.
