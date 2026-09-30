const fs = require('fs');
const path = require('path');
const entityDir = 'f:/Capstone/slms2026/src/main/java/com/sep490/slms2026/entity';
const files = fs.readdirSync(entityDir).filter(f => f.endsWith('.java')).sort();

function stripComments(src) {
  return src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
}

function extractAnnotations(text) {
  const anns = [];
  let i = 0;
  while (i < text.length) {
    while (i < text.length && /\s/.test(text[i])) i++;
    if (text[i] !== '@') break;
    let j = i + 1;
    while (j < text.length && /[A-Za-z0-9_.]/.test(text[j])) j++;
    if (j < text.length && text[j] === '(') {
      let depth = 0, k = j;
      while (k < text.length) {
        if (text[k] === '(') depth++;
        else if (text[k] === ')') {
          depth--;
          if (depth === 0) { k++; break; }
        }
        k++;
      }
      anns.push(text.slice(i, k).replace(/\s+/g, ' ').trim());
      i = k;
    } else {
      anns.push(text.slice(i, j));
      i = j;
    }
  }
  return anns;
}

function parseAnnAttrs(ann) {
  const attrs = {};
  const idx = ann.indexOf('(');
  if (idx < 0) return attrs;
  let body = ann.slice(idx + 1);
  if (body.endsWith(')')) body = body.slice(0, -1);
  let i = 0;
  while (i < body.length) {
    while (i < body.length && /[\s,]/.test(body[i])) i++;
    if (i >= body.length) break;
    const m = body.slice(i).match(/^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*/);
    if (!m) break;
    const key = m[1];
    i += m[0].length;
    let val;
    if (body[i] === '"') {
      let j = i + 1;
      while (j < body.length) {
        if (body[j] === '\\') { j += 2; continue; }
        if (body[j] === '"') { j++; break; }
        j++;
      }
      val = body.slice(i, j);
      i = j;
    } else if (body[i] === '{') {
      let depth = 0, j = i;
      while (j < body.length) {
        if (body[j] === '{') depth++;
        else if (body[j] === '}') {
          depth--;
          if (depth === 0) { j++; break; }
        }
        j++;
      }
      val = body.slice(i, j);
      i = j;
    } else {
      let depth = 0, j = i;
      while (j < body.length) {
        const c = body[j];
        if ('([{'.includes(c)) depth++;
        else if (')]}'.includes(c)) depth--;
        else if (c === ',' && depth === 0) break;
        j++;
      }
      val = body.slice(i, j).trim();
      i = j;
    }
    attrs[key] = val;
  }
  return attrs;
}

function annName(a) {
  const m = a.match(/^@([A-Za-z0-9_.]+)/);
  return m ? m[1].split('.').pop() : a;
}

function unquote(s) {
  if (s == null) return s;
  return String(s).replace(/^["'`]|["'`]$/g, '');
}

function defaultColumnName(fieldName) {
  return fieldName.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase();
}

function targetFromType(javaType) {
  const listM = javaType.match(/(?:List|Set|Collection|Map)\s*<\s*(?:[^,>]+,\s*)?([^>]+)\s*>/);
  if (listM) return listM[1].trim();
  return javaType.replace(/\s+/g, '');
}

function summarizeField(f) {
  const anns = f.annotations;
  const by = {};
  for (const a of anns) by[annName(a)] = a;

  const col = by.Column ? parseAnnAttrs(by.Column) : {};
  const join = by.JoinColumn ? parseAnnAttrs(by.JoinColumn) : {};

  let columnName;
  if (col.name) columnName = unquote(col.name);
  else if (by.JoinColumn && join.name) columnName = unquote(join.name);
  else if (by.JoinColumn || ((by.ManyToOne || by.OneToOne) && !by.MapsId && !(by.Id && by.OneToOne))) {
    columnName = defaultColumnName(f.name) + '_id';
  } else if (by.MapsId || (by.Id && (by.OneToOne || by.ManyToOne))) {
    columnName = defaultColumnName(f.name); // often same as PK col via MapsId
  } else {
    columnName = defaultColumnName(f.name);
  }

  const out = {
    javaField: f.name,
    javaType: f.type,
    columnName,
  };

  for (const k of ['nullable', 'unique', 'length', 'precision', 'scale', 'columnDefinition', 'insertable', 'updatable']) {
    if (col[k] !== undefined) out[k] = unquote(col[k]);
  }
  if (col.name) out.columnNameExplicit = true;

  if (by.Id) out.id = true;
  if (by.GeneratedValue) {
    const g = parseAnnAttrs(by.GeneratedValue);
    out.generatedValue = g.strategy || 'GenerationType.AUTO';
    if (g.generator) out.generator = unquote(g.generator);
  }
  if (by.Enumerated) {
    const raw = by.Enumerated;
    if (raw.includes('STRING')) out.enumerated = 'STRING';
    else if (raw.includes('ORDINAL')) out.enumerated = 'ORDINAL';
    else out.enumerated = raw;
  }

  for (const rel of ['ManyToOne', 'OneToOne', 'OneToMany', 'ManyToMany']) {
    if (by[rel]) {
      const r = parseAnnAttrs(by[rel]);
      out.relation = {
        type: rel,
        targetEntity: targetFromType(f.type),
      };
      if (r.fetch) out.relation.fetch = r.fetch;
      if (r.cascade) out.relation.cascade = r.cascade;
      if (r.mappedBy) out.relation.mappedBy = unquote(r.mappedBy);
      if (r.orphanRemoval) out.relation.orphanRemoval = r.orphanRemoval;
      if (r.optional) out.relation.optional = r.optional;
      if (by.JoinColumn) {
        out.relation.joinColumn = {
          name: unquote(join.name) || columnName,
        };
        if (join.referencedColumnName) out.relation.joinColumn.referencedColumnName = unquote(join.referencedColumnName);
        if (join.nullable !== undefined) out.relation.joinColumn.nullable = join.nullable;
        if (join.unique !== undefined) out.relation.joinColumn.unique = join.unique;
        if (join.insertable !== undefined) out.relation.joinColumn.insertable = join.insertable;
        if (join.updatable !== undefined) out.relation.joinColumn.updatable = join.updatable;
      }
      if (by.JoinColumns) out.relation.joinColumnsRaw = by.JoinColumns;
      if (by.JoinTable) {
        const jt = parseAnnAttrs(by.JoinTable);
        out.relation.joinTable = {
          name: unquote(jt.name),
          joinColumns: jt.joinColumns,
          inverseJoinColumns: jt.inverseJoinColumns,
        };
      }
      if (by.MapsId) out.relation.mapsId = true;
      if (by.PrimaryKeyJoinColumn) {
        out.relation.primaryKeyJoinColumn = parseAnnAttrs(by.PrimaryKeyJoinColumn);
      }
    }
  }

  if (by.EmbeddedId) out.embeddedId = true;
  if (by.Embedded) out.embedded = true;
  if (by.ElementCollection) {
    out.elementCollection = true;
    if (by.CollectionTable) {
      const ct = parseAnnAttrs(by.CollectionTable);
      out.collectionTable = {
        name: unquote(ct.name),
        joinColumns: ct.joinColumns,
      };
    }
    if (by.AttributeOverrides) out.attributeOverrides = by.AttributeOverrides;
    if (by.AttributeOverride) out.attributeOverride = by.AttributeOverride;
    if (by.OrderColumn) out.orderColumn = parseAnnAttrs(by.OrderColumn);
    if (col.name) out.elementColumn = unquote(col.name);
  }
  if (by.Lob) out.lob = true;
  if (by.Temporal) {
    if (by.Temporal.includes('TIMESTAMP')) out.temporal = 'TIMESTAMP';
    else if (by.Temporal.includes('DATE')) out.temporal = 'DATE';
    else if (by.Temporal.includes('TIME')) out.temporal = 'TIME';
    else out.temporal = by.Temporal;
  }
  if (by.CreationTimestamp) out.creationTimestamp = true;
  if (by.UpdateTimestamp) out.updateTimestamp = true;
  if (by.Formula) out.formula = by.Formula;
  if (by.Transient) out.transient = true;
  if (by.Version) out.version = true;

  out.jpaAnnotations = anns.map(annName).filter(n =>
    !['Data','Builder','Getter','Setter','NoArgsConstructor','AllArgsConstructor','RequiredArgsConstructor',
      'EqualsAndHashCode','ToString','Slf4j','JsonIgnore','JsonProperty','JsonManagedReference',
      'JsonBackReference','NotNull','Size','Min','Max','Email','Valid','Nullable','DecimalMin','DecimalMax'].includes(n)
  );

  return out;
}

/** Line-based field extractor — avoids catastrophic backtracking */
function extractFields(src) {
  const classMatch = src.match(/class\s+\w+[^{]*\{/);
  if (!classMatch) return [];
  let start = classMatch.index + classMatch[0].length;
  let depth = 1, end = start;
  for (let i = start; i < src.length; i++) {
    if (src[i] === '{') depth++;
    else if (src[i] === '}') {
      depth--;
      if (depth === 0) { end = i; break; }
    }
  }
  const body = src.slice(start, end);
  const lines = body.split('\n');
  const fields = [];
  let annBuf = [];
  let pending = '';

  const fieldDeclRe = /^(?:private|protected|public)\s+(?:(?:static|final|transient|volatile)\s+)*(.+?)\s+(\w+)\s*(?:=.*)?;\s*$/;

  for (let li = 0; li < lines.length; li++) {
    let line = lines[li].trim();
    if (!line) continue;

    // skip method bodies roughly: if we see method signature, skip until matching braces handled by line logic
    if (/^(public|private|protected)\s+/.test(line) && line.includes('(') && !line.includes('=')) {
      // method — clear anns
      annBuf = [];
      pending = '';
      continue;
    }

    if (line.startsWith('@')) {
      // may be multi-line annotation
      pending = line;
      let d = (line.match(/\(/g) || []).length - (line.match(/\)/g) || []).length;
      while (d > 0 && li + 1 < lines.length) {
        li++;
        const n = lines[li].trim();
        pending += ' ' + n;
        d += (n.match(/\(/g) || []).length - (n.match(/\)/g) || []).length;
      }
      annBuf.push(...extractAnnotations(pending));
      pending = '';
      continue;
    }

    // continuation of field type across lines is rare; handle single-line fields
    // accumulate until ;
    if (!line.endsWith(';') && /^(private|protected|public)\s+/.test(line)) {
      let acc = line;
      while (!acc.endsWith(';') && li + 1 < lines.length) {
        li++;
        acc += ' ' + lines[li].trim();
      }
      line = acc;
    }

    const m = line.match(fieldDeclRe);
    if (m) {
      let javaType = m[1].replace(/\s+/g, ' ').trim();
      const fieldName = m[2];
      if (fieldName !== 'serialVersionUID' && !javaType.includes('(')) {
        fields.push({ name: fieldName, type: javaType, annotations: annBuf.slice() });
      }
      annBuf = [];
    } else {
      // not a field — reset annotations unless it's a modifier-only continuation
      if (!line.startsWith('@')) annBuf = [];
    }
  }
  return fields;
}

function extractClassInfo(src) {
  const m = src.match(/(?:public\s+)?(?:abstract\s+)?class\s+(\w+)(?:\s+extends\s+(\w+))?(?:\s+implements\s+[^{]+)?\s*\{/);
  if (!m) return null;
  const className = m[1];
  const extendsCls = m[2] || null;
  const before = src.slice(0, m.index);
  const markers = ['@Entity', '@Table', '@Embeddable', '@MappedSuperclass', '@Data', '@Getter', '@Setter',
    '@Builder', '@NoArgsConstructor', '@AllArgsConstructor', '@RequiredArgsConstructor',
    '@EqualsAndHashCode', '@ToString', '@Slf4j', '@EntityListeners', '@Inheritance',
    '@DiscriminatorColumn', '@DiscriminatorValue', '@IdClass'];
  let allAnnsStart = -1;
  for (const mk of markers) {
    const idx = before.indexOf(mk);
    if (idx >= 0 && (allAnnsStart < 0 || idx < allAnnsStart)) allAnnsStart = idx;
  }
  const block = allAnnsStart >= 0 ? before.slice(allAnnsStart) : before;
  const classAnns = extractAnnotations(block.replace(/\n/g, ' '));
  return { className, extendsCls, classAnns };
}

const results = [];
for (const file of files) {
  process.stderr.write('Parsing ' + file + '\n');
  const raw = fs.readFileSync(path.join(entityDir, file), 'utf8');
  const src = stripComments(raw);
  const info = extractClassInfo(src);
  if (!info) {
    results.push({ file, kind: 'unknown' });
    continue;
  }
  const { className, extendsCls, classAnns } = info;
  const isEntity = classAnns.some(a => annName(a) === 'Entity');
  const isEmbeddable = classAnns.some(a => annName(a) === 'Embeddable');
  const isMapped = classAnns.some(a => annName(a) === 'MappedSuperclass');

  if (!isEntity) {
    results.push({
      file,
      class: className,
      kind: isEmbeddable ? 'Embeddable' : (className.includes('Listener') ? 'Listener' : (isMapped ? 'MappedSuperclass' : 'non-entity')),
      extends: extendsCls,
      fields: (isEmbeddable || /Id$/.test(className)) ? extractFields(src).map(summarizeField) : undefined,
    });
    continue;
  }

  const tableAnn = classAnns.find(a => annName(a) === 'Table');
  const tableAttrs = tableAnn ? parseAnnAttrs(tableAnn) : {};
  const inheritance = classAnns.find(a => annName(a) === 'Inheritance');
  const discCol = classAnns.find(a => annName(a) === 'DiscriminatorColumn');
  const discVal = classAnns.find(a => annName(a) === 'DiscriminatorValue');
  const idClass = classAnns.find(a => annName(a) === 'IdClass');
  const listeners = classAnns.find(a => annName(a) === 'EntityListeners');

  const fields = extractFields(src).map(summarizeField).filter(f => !f.transient);

  results.push({
    file,
    class: className,
    kind: 'Entity',
    table: tableAttrs.name ? unquote(tableAttrs.name) : null,
    uniqueConstraints: tableAttrs.uniqueConstraints || null,
    indexes: tableAttrs.indexes || null,
    inheritance: inheritance || null,
    discriminatorColumn: discCol || null,
    discriminatorValue: discVal || null,
    idClass: idClass ? parseAnnAttrs(idClass) : null,
    entityListeners: listeners || null,
    extends: extendsCls,
    fields,
  });
}

fs.writeFileSync('f:/Capstone/slms2026/entity_inventory.json', JSON.stringify(results, null, 2));
const ents = results.filter(r => r.kind === 'Entity');
console.log('files', files.length);
console.log('entities', ents.length);
console.log('skipped', results.filter(r => r.kind !== 'Entity').map(r => `${r.class}:${r.kind}`).join(', '));
console.log('---TABLES---');
for (const e of ents) {
  console.log(`${e.class}\t${e.table}\tfields=${e.fields.length}`);
}
