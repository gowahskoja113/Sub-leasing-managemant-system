const fs = require('fs');
const inv = JSON.parse(fs.readFileSync('f:/Capstone/slms2026/entity_inventory.json', 'utf8'));

function fieldLine(f) {
  const parts = [];
  parts.push(`- **${f.javaField}** → \`${f.columnName}\` : \`${f.javaType}\``);
  const meta = [];
  if (f.id) meta.push('@Id');
  if (f.generatedValue) meta.push(`@GeneratedValue(${f.generatedValue})`);
  if (f.nullable !== undefined) meta.push(`nullable=${f.nullable}`);
  if (f.unique !== undefined) meta.push(`unique=${f.unique}`);
  if (f.length) meta.push(`length=${f.length}`);
  if (f.precision) meta.push(`precision=${f.precision}`);
  if (f.scale) meta.push(`scale=${f.scale}`);
  if (f.columnDefinition) meta.push(`columnDefinition=${f.columnDefinition}`);
  if (f.enumerated) meta.push(`@Enumerated(${f.enumerated})`);
  if (f.lob) meta.push('@Lob');
  if (f.temporal) meta.push(`@Temporal(${f.temporal})`);
  if (f.embeddedId) meta.push('@EmbeddedId');
  if (f.embedded) meta.push('@Embedded');
  if (f.version) meta.push('@Version');
  if (f.creationTimestamp) meta.push('@CreationTimestamp');
  if (f.updateTimestamp) meta.push('@UpdateTimestamp');
  if (f.elementCollection) {
    const ct = f.collectionTable ? f.collectionTable.name : '?';
    meta.push(`@ElementCollection → table \`${ct}\``);
    if (f.elementColumn) meta.push(`element column=${f.elementColumn}`);
    if (f.attributeOverrides) meta.push(f.attributeOverrides);
    if (f.attributeOverride) meta.push(f.attributeOverride);
  }
  if (f.relation) {
    const r = f.relation;
    let s = `@${r.type} → ${r.targetEntity}`;
    if (r.joinColumn) s += ` JoinColumn(${r.joinColumn.name}${r.joinColumn.nullable !== undefined ? ', nullable=' + r.joinColumn.nullable : ''})`;
    if (r.mappedBy) s += ` mappedBy=${r.mappedBy}`;
    if (r.joinTable) s += ` JoinTable(${r.joinTable.name})`;
    if (r.mapsId) s += ' @MapsId';
    if (r.fetch) s += ` ${r.fetch}`;
    meta.push(s);
  }
  if (meta.length) parts.push(`  - ${meta.join('; ')}`);
  return parts.join('\n');
}

const ents = inv.filter(e => e.kind === 'Entity').sort((a, b) => a.class.localeCompare(b.class));
const lines = [];
lines.push('# JPA Entity Inventory (DDL-oriented)');
lines.push('');
lines.push(`Total @Entity classes: **${ents.length}**`);
lines.push('Skipped non-entities: ContractEvidencePhoto (@Embeddable), InvoiceUnlockFailCounterId (IdClass key), NotificationListener');
lines.push('');
lines.push('## Inheritance');
lines.push('None of the entities use `@Inheritance` / discriminator columns. Profile entities (Admin, Owner, Tenant, OperationManagement) use shared-PK `@MapsId` `@OneToOne` to `User`.');
lines.push('');

for (const e of ents) {
  lines.push(`## ${e.class}`);
  lines.push(`- **@Table**: \`${e.table}\``);
  if (e.uniqueConstraints) lines.push(`- **uniqueConstraints**: \`${e.uniqueConstraints}\``);
  if (e.indexes) lines.push(`- **indexes**: \`${e.indexes}\``);
  if (e.idClass) lines.push(`- **@IdClass**: InvoiceUnlockFailCounterId (managerId: UUID, invoiceId: Long)`);
  if (e.entityListeners) lines.push(`- **@EntityListeners**: present`);
  if (e.extends) lines.push(`- **extends**: ${e.extends}`);
  lines.push('- **Fields**:');
  for (const f of e.fields) {
    lines.push(fieldLine(f));
  }
  lines.push('');
}

// Embeddables used
lines.push('## Supporting types (not @Entity)');
lines.push('### ContractEvidencePhoto (@Embeddable)');
lines.push('- imageUrl → `image_url` : String (nullable=false)');
lines.push('- capturedAt → `captured_at` : LocalDateTime');
lines.push('Used by TenantContract.roomConditionPhotos ElementCollection.');
lines.push('');
lines.push('### InvoiceUnlockFailCounterId (Serializable IdClass)');
lines.push('- managerId: UUID');
lines.push('- invoiceId: Long');
lines.push('');

fs.writeFileSync('f:/Capstone/slms2026/entity_inventory.md', lines.join('\n'));
console.log('Wrote entity_inventory.md, lines=', lines.length);
console.log('chars=', lines.join('\n').length);
