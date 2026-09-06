// Shared walker for the SeatLayer Android picker design generators.
//
// The canonical design source is `design/`, copied verbatim from the reference
// SDK. Nothing here decides what a token means: it loads the three locked
// files, proves they are the ones the lock names, and hands the generators a
// flat, sorted view of the tree. A generator that hand-picks keys drifts the
// moment the source grows a group, so both of them emit whatever they are
// given.

import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/** @param {unknown} value */
export function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

/** @param {string} path */
function readWithDigest(path) {
  const source = readFileSync(path);
  return {
    text: source.toString('utf8'),
    sha256: createHash('sha256').update(source).digest('hex'),
  };
}

/**
 * Loads `design/` and fails unless every file matches `source-lock.json`.
 *
 * The lock is the contract with the reference SDK: a design file that changed
 * without the lock changing is an unreviewed edit, and a lock that changed
 * without the file is a stale copy. Both are refused here rather than in a
 * reviewer's head.
 *
 * @param {string} directory
 */
export function loadDesign(directory) {
  const tokens = readWithDigest(join(directory, 'tokens.json'));
  const localeStrings = readWithDigest(join(directory, 'locale_strings.json'));
  const components = readWithDigest(join(directory, 'components.md'));
  const lock = JSON.parse(readFileSync(join(directory, 'source-lock.json'), 'utf8'));

  const expected = [
    ['tokens.json', 'tokensSha256', tokens.sha256],
    ['locale_strings.json', 'localeStringsSha256', localeStrings.sha256],
    ['components.md', 'componentsSha256', components.sha256],
  ];
  for (const [file, field, digest] of expected) {
    if (lock[field] !== digest) {
      throw new Error(
        `design/${file} does not match design/source-lock.json.${field}: ` +
          `expected ${lock[field]}, read ${digest}.`,
      );
    }
  }

  const tokenTree = JSON.parse(tokens.text);
  const localeTree = JSON.parse(localeStrings.text);
  validate(tokenTree, localeTree);

  return {
    tokens: tokenTree,
    locales: localeTree.strings,
    sha256: {
      tokens: tokens.sha256,
      localeStrings: localeStrings.sha256,
      components: components.sha256,
    },
  };
}

/** @param {Record<string, unknown>} tokens @param {Record<string, unknown>} localeStrings */
function validate(tokens, localeStrings) {
  for (const group of ['color', 'size', 'radius', 'elevation', 'opacity', 'type', 'motion', 'haptics', 'strings']) {
    if (!isRecord(tokens[group])) throw new Error(`tokens.json is missing the ${group} group.`);
  }
  for (const [key, value] of Object.entries(tokens.strings)) {
    if (typeof value !== 'string') throw new Error(`tokens.json strings.${key} must be a string.`);
  }
  if (!isRecord(localeStrings.strings)) {
    throw new Error('locale_strings.json must contain a strings object.');
  }
  for (const [locale, dictionary] of Object.entries(localeStrings.strings)) {
    if (!isRecord(dictionary)) throw new Error(`locale dictionary ${locale} must be an object.`);
    for (const [key, value] of Object.entries(dictionary)) {
      if (typeof value !== 'string') throw new Error(`locale string ${locale}.${key} must be a string.`);
    }
  }
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
 * The `xOne`/`xOther` pairs in `keys`, and the base key each one shadows.
 *
 * A plural is two entries in the source and one function on the surface, so
 * both generators need the same view of which keys are halves of a pair.
 *
 * @param {readonly string[]} keys
 */
export function pluralPairs(keys) {
  const present = new Set(keys);
  /** @type {Map<string, {one: string, other: string, base: string | null}>} */
  const pairs = new Map();
  for (const key of keys) {
    if (!key.endsWith('One')) continue;
    const base = key.slice(0, -'One'.length);
    const other = `${base}Other`;
    if (!present.has(other)) continue;
    pairs.set(base, { one: key, other, base: present.has(base) ? base : null });
  }
  return pairs;
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
 * @param {{ generator: string, sha256: {tokens: string, localeStrings: string, components: string} }} options
 */
export function generatedHeader({ generator, sha256 }) {
  return [
    '// GENERATED — do not edit.',
    '//',
    '// Source: design/tokens.json, design/locale_strings.json, design/components.md',
    `// Regenerate: node tool/${generator}`,
    '//',
    `// tokens.json         SHA-256: ${sha256.tokens}`,
    `// locale_strings.json SHA-256: ${sha256.localeStrings}`,
    `// components.md       SHA-256: ${sha256.components}`,
    '',
    'package io.seatlayer.android.compose',
    '',
  ].join('\n');
}
