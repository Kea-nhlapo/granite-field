-- PostGIS is a PROVISIONING responsibility, not a schema change: CREATE EXTENSION
-- needs superuser (or a provider-specific elevated role) that the application's
-- migration role does not have and should not be granted. See docs/adr/0002.
--
-- This migration therefore REQUIRES the extension rather than creating it, so a
-- database provisioned without PostGIS fails at deploy time with a message that
-- names the fix, instead of at runtime inside an unrelated spatial query.
--
-- Note the limit: a versioned migration runs once per database, ever. This is a
-- one-shot deploy gate, not a standing guarantee.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        RAISE EXCEPTION
            'PostGIS is not installed in this database. Provisioning must run '
            'CREATE EXTENSION postgis as a superuser before the application is '
            'deployed. Locally this is provided by the postgis/postgis image in '
            'infra/containers/docker-compose.yml.';
    END IF;
END
$$;
