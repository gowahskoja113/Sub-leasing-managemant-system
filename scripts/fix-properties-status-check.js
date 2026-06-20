const { Client } = require('pg');

(async () => {
  const client = new Client({
    host: 'localhost',
    database: 'slms2026_db1',
    user: 'postgres',
    password: '12345678',
  });
  await client.connect();

  await client.query('ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_status_check');
  await client.query(`
    ALTER TABLE properties ADD CONSTRAINT properties_status_check
    CHECK (status IN (
      'DRAFT',
      'PENDING',
      'UNDER_RENOVATION',
      'PENDING_EQUIPMENT_INSTALLATION',
      'RENOVATION_COMPLETED',
      'PENDING_HOST_REVIEW',
      'PENDING_OPERATION_MANAGER',
      'ACTIVE',
      'DISABLED',
      'MAINTENANCE',
      'INACTIVE'
    ))
  `);

  const result = await client.query(`
    SELECT pg_get_constraintdef(oid) AS def
    FROM pg_constraint
    WHERE conname = 'properties_status_check'
  `);
  console.log('Updated constraint:', result.rows[0].def);
  await client.end();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
