// The Kotlin-shaped half of the picker design generators.
//
// `picker-design-core.mjs` is shared byte-for-byte with the other SeatLayer
// SDKs and may never learn what a Kotlin literal looks like. Everything that
// knows about `const val`, ARGB longs or `$` escaping lives here instead, so
// the shared walker can be re-copied from the reference SDK at any time
// without a merge.

/** @param {unknown} value */
export function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

/**
 * `motion.duration.pressSweep` → `PRESS_SWEEP`.
 *
 * @param {string} name
 */
export function screamingSnake(name) {
  return name
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2')
    .replace(/[^A-Za-z0-9]+/g, '_')
    .toUpperCase();
}

/** @param {string} value */
export function kotlinString(value) {
  return `"${value
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\$/g, '\\$')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t')}"`;
}

/**
 * `#AARRGGBB` (the canonical notation) as the `Long` Compose reads as ARGB.
 *
 * @param {string} value
 */
export function argbLong(value) {
  const match = /^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.exec(value.trim());
  if (!match) throw new Error(`Colour token ${value} is not #RRGGBB or #AARRGGBB.`);
  const hex = match[1].length === 6 ? `FF${match[1]}` : match[1];
  return `0x${hex.toUpperCase()}`;
}

/** @param {number} value */
export function kotlinFloat(value) {
  if (!Number.isFinite(value)) throw new Error(`Token ${value} is not a finite number.`);
  return Number.isInteger(value) ? `${value}.0f` : `${value}f`;
}

/** @param {number} value */
export function kotlinInt(value) {
  if (!Number.isInteger(value)) throw new Error(`Token ${value} is not a whole number.`);
  return `${value}`;
}

/**
 * The four digests the lock names, repeated in every generated file.
 *
 * A regeneration that was never run cannot pass as a fresh one, because the
 * header would still carry the previous source's digests.
 *
 * @param {{ generator: string, sha256: Record<string, string> }} options
 */
export function generatedHeader({ generator, sha256 }) {
  return [
    '// GENERATED — do not edit.',
    '//',
    '// Source: Design/tokens.json, Design/locale_strings.json,',
    '//         Design/components.md, Design/picker-spec.md',
    `// Regenerate: node tool/${generator}`,
    '//',
    `// tokens.json         SHA-256: ${sha256.tokens}`,
    `// locale_strings.json SHA-256: ${sha256.localeStrings}`,
    `// components.md       SHA-256: ${sha256.components}`,
    `// picker-spec.md      SHA-256: ${sha256.spec}`,
    '',
    'package io.seatlayer.android.compose',
    '',
  ].join('\n');
}

/**
 * The digests of the four locked files, read back off the loaded bytes.
 *
 * @param {{ raw: Record<string, Buffer> }} design
 * @param {(bytes: Buffer) => string} sha256
 */
export function sourceDigests(design, sha256) {
  return {
    tokens: sha256(design.raw.tokens),
    localeStrings: sha256(design.raw.locales),
    components: sha256(design.raw.components),
    spec: sha256(design.raw.spec),
  };
}

/**
 * Every leaf of `tree` as a sorted list of dotted key/value pairs.
 *
 * @param {unknown} tree
 * @param {string} [prefix]
 * @returns {Array<[string, string | number | boolean]>}
 */
export function flatten(tree, prefix = '') {
  /** @type {Array<[string, string | number | boolean]>} */
  const flat = [];
  if (!isRecord(tree)) return flat;
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (isRecord(value)) flat.push(...flatten(value, path));
    else if (!Array.isArray(value)) flat.push([path, value]);
  }
  return flat.sort(([left], [right]) => (left < right ? -1 : left > right ? 1 : 0));
}

/**
 * The structural guard the shared walker does not carry.
 *
 * The walker proves the files are the ones the lock names; this proves they
 * still have the shape these emitters read. A design source that grew a group
 * is fine; one that lost `color` or turned a string into a number is not, and
 * saying so here beats a Kotlin file that will not compile.
 *
 * @param {Record<string, unknown>} tokens
 * @param {Record<string, unknown>} locales
 */
export function validateDesign(tokens, locales) {
  const groups = [
    'color', 'size', 'radius', 'elevation', 'opacity',
    'type', 'motion', 'haptics', 'strings',
  ];
  for (const group of groups) {
    if (!isRecord(tokens[group])) throw new Error(`tokens.json is missing the ${group} group.`);
  }
  for (const [key, value] of Object.entries(tokens.strings)) {
    if (typeof value !== 'string') throw new Error(`tokens.json strings.${key} must be a string.`);
  }
  if (!isRecord(locales.strings)) {
    throw new Error('locale_strings.json must contain a strings object.');
  }
  for (const [locale, dictionary] of Object.entries(locales.strings)) {
    if (!isRecord(dictionary)) throw new Error(`locale dictionary ${locale} must be an object.`);
    for (const [key, value] of Object.entries(dictionary)) {
      if (typeof value !== 'string') throw new Error(`locale string ${locale}.${key} must be a string.`);
    }
  }
}
