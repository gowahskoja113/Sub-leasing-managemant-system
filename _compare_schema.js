const fs = require('fs');
const inv = JSON.parse(fs.readFileSync('f:/Capstone/slms2026/entity_inventory.json', 'utf8'));
const schema = fs.readFileSync('f:/Capstone/slms2026/src/main/resources/db/schema.sql', 'utf8');
const schemaTables = [...schema.matchAll(/CREATE TABLE IF NOT EXISTS\s+("?[^\s"(]+"?)/gi)]
  .map(m => m[1].replace(/"/g, '').toLowerCase());
const ents = inv.filter(e => e.kind === 'Entity');
const entityTables = ents.map(e => ({
  c: e.class,
  t: (e.table || '').replace(/`/g, '').toLowerCase(),
}));
const schemaSet = new Set(schemaTables);
const entitySet = new Set(entityTables.map(x => x.t));

console.log('=== Entity tables NOT in schema.sql ===');
for (const x of entityTables) {
  if (![...schemaSet].some(s => s === x.t)) console.log(x.c, '->', x.t);
}

// Element collection tables from entities
const ecTables = new Set();
for (const e of ents) {
  for (const f of e.fields) {
    if (f.collectionTable && f.collectionTable.name) ecTables.add(f.collectionTable.name.toLowerCase());
  }
}
// Join tables
const jtTables = new Set();
for (const e of ents) {
  for (const f of e.fields) {
    if (f.relation && f.relation.joinTable && f.relation.joinTable.name) {
      jtTables.add(f.relation.joinTable.name.toLowerCase());
    }
  }
}

console.log('=== schema.sql tables with no matching @Entity (may be EC/JoinTable/legacy) ===');
for (const t of schemaTables) {
  if (!entitySet.has(t) && !ecTables.has(t) && !jtTables.has(t)) {
    console.log(t);
  }
}

console.log('=== ElementCollections ===');
for (const e of ents) {
  for (const f of e.fields) {
    if (f.elementCollection) {
      console.log(e.class + '.' + f.javaField, 'table=', f.collectionTable && f.collectionTable.name, 'type=', f.javaType, 'elemCol=', f.elementColumn);
    }
  }
}

console.log('=== JoinTables ===');
for (const e of ents) {
  for (const f of e.fields) {
    if (f.relation && f.relation.joinTable) {
      console.log(e.class + '.' + f.javaField, JSON.stringify(f.relation.joinTable));
    }
  }
}

console.log('=== Inheritance / IdClass / EmbeddedId / MapsId ===');
for (const e of ents) {
  const maps = e.fields.filter(f => f.relation && f.relation.mapsId).map(f => f.javaField);
  if (e.inheritance || e.idClass || e.fields.some(f => f.embeddedId) || maps.length) {
    console.log(e.class, JSON.stringify({
      inheritance: e.inheritance,
      idClass: e.idClass,
      embeddedId: e.fields.filter(f => f.embeddedId).map(f => f.javaField),
      mapsId: maps,
    }));
  }
}

console.log('=== Indexes / UniqueConstraints ===');
for (const e of ents) {
  if (e.indexes || e.uniqueConstraints) {
    console.log(e.class, e.table, '\n  idx=', e.indexes, '\n  uq=', e.uniqueConstraints);
  }
}

// Spot-check: fields that look wrong (0 fields, or OneToMany counted as columns)
console.log('=== OneToMany/ManyToMany (mappedBy - no FK column on this table) ===');
for (const e of ents) {
  for (const f of e.fields) {
    if (f.relation && (f.relation.type === 'OneToMany' || f.relation.type === 'ManyToMany')) {
      console.log(e.class + '.' + f.javaField, f.relation.type, 'mappedBy=', f.relation.mappedBy, 'joinTable=', f.relation.joinTable && f.relation.joinTable.name);
    }
  }
}
