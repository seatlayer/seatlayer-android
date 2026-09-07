#!/usr/bin/env node
// Emits the generated Kotlin token objects from `Design/tokens.json`.
//
//   node tool/generate-picker-tokens.mjs            # write
//   node tool/generate-picker-tokens.mjs --check    # fail if stale
//
// One `internal object` per token group, so a reader looking for a colour is
// never scrolling past a hundred sizes, and no generated file approaches the
// project's source-length limit. The generator hand-picks no keys: it emits
// whatever the group holds, which is how a new token arrives without an edit
// here.

import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { loadDesign, sha256 } from './picker-design-core.mjs';
import {
  argbLong,
  generatedHeader,
  isRecord,
  kotlinFloat,
  kotlinInt,
  kotlinString,
  screamingSnake,
  sourceDigests,
  validateDesign,
} from './kotlin-emit.mjs';

const OUT_DIR = 'seatlayer-compose/src/main/kotlin/io/seatlayer/android/compose';
const PROSE_KEYS = new Set(['note', 'reducedMotion']);

/** @param {string} name @param {Array<[string, string]>} entries @param {string} type */
function object(name, entries, type) {
  const seen = new Set();
  const lines = [`internal object ${name} {`];
  for (const [key, literal] of entries) {
    if (seen.has(key)) throw new Error(`${name} would declare ${key} twice.`);
    seen.add(key);
    lines.push(`    internal const val ${key}: ${type} = ${literal}`);
  }
  lines.push('}');
  return lines.join('\n');
}

/** @param {Record<string, unknown>} group @param {(value: any) => string} render */
function entries(group, render) {
  return Object.entries(group)
    .filter(([key, value]) => !PROSE_KEYS.has(key) && typeof value !== 'object')
    .sort(([left], [right]) => (left < right ? -1 : 1))
    .map(([key, value]) => [screamingSnake(key), render(value)]);
}

/** @param {Record<string, unknown>} type */
function typeEntries(type) {
  /** @type {Array<[string, string]>} */
  const rows = [];
  for (const [role, value] of Object.entries(type).sort(([l], [r]) => (l < r ? -1 : 1))) {
    if (role === 'scaleClamp' || !isRecord(value)) continue;
    const prefix = screamingSnake(role);
    if (typeof value.size === 'number') rows.push([`${prefix}_SIZE`, kotlinFloat(value.size)]);
    if (typeof value.weight === 'number') rows.push([`${prefix}_WEIGHT`, kotlinInt(value.weight)]);
    if (typeof value.lineHeight === 'number') {
      rows.push([`${prefix}_LINE_HEIGHT`, kotlinFloat(value.lineHeight)]);
    }
  }
  return rows;
}

/** @param {Record<string, unknown>} curve */
function curveEntries(curve) {
  /** @type {Array<[string, string]>} */
  const rows = [];
  for (const [name, value] of Object.entries(curve).sort(([l], [r]) => (l < r ? -1 : 1))) {
    if (!isRecord(value) || !Array.isArray(value.cubicBezier)) continue;
    const bezier = value.cubicBezier;
    if (bezier.length !== 4) throw new Error(`motion.curve.${name} must carry four control values.`);
    const prefix = screamingSnake(name);
    // `name` is deliberately not emitted: every curve calls itself "cubic" and
    // carries its meaning only in these four numbers.
    for (const [index, axis] of ['X1', 'Y1', 'X2', 'Y2'].entries()) {
      rows.push([`${prefix}_${axis}`, kotlinFloat(bezier[index])]);
    }
  }
  return rows;
}

/** @param {ReturnType<typeof loadDesign>} design */
function render(design) {
  const { tokens } = design;
  const digests = sourceDigests(design, sha256);
  const header = generatedHeader({
    generator: 'generate-picker-tokens.mjs',
    sha256: digests,
  });
  const type = /** @type {Record<string, any>} */ (tokens.type);
  const motion = /** @type {Record<string, any>} */ (tokens.motion);

  const typeRows = typeEntries(type);
  for (const [key, value] of entries(type.scaleClamp ?? {}, kotlinFloat)) {
    typeRows.push([`SCALE_CLAMP_${key}`, value]);
  }

  const motionRows = [
    ['BUDGET_MS', kotlinInt(motion.budgetMs)],
    ...entries(motion.duration, kotlinInt),
    ...entries(motion.durationOutsideBudget, kotlinInt),
  ];

  const tokensFile = [
    header,
    '/** Every generated constant carries the digest of the design file above. */',
    object('SeatLayerDesignSource', [
      ['TOKENS_SHA256', kotlinString(digests.tokens)],
      ['LOCALE_STRINGS_SHA256', kotlinString(digests.localeStrings)],
      ['COMPONENTS_SHA256', kotlinString(digests.components)],
      ['PICKER_SPEC_SHA256', kotlinString(digests.spec)],
    ], 'String'),
    '',
    object('SeatLayerLightTokens', entries(tokens.color.light, argbLong), 'Long'),
    '',
    object('SeatLayerDarkTokens', entries(tokens.color.dark, argbLong), 'Long'),
    '',
    object('SeatLayerRadiusTokens', entries(tokens.radius, kotlinFloat), 'Float'),
    '',
    object('SeatLayerElevationTokens', entries(tokens.elevation, kotlinFloat), 'Float'),
    '',
    object('SeatLayerOpacityTokens', entries(tokens.opacity, kotlinFloat), 'Float'),
    '',
    ['internal object SeatLayerTypeTokens {',
      ...typeRows.map(([key, literal]) =>
        `    internal const val ${key}: ${key.endsWith('_WEIGHT') ? 'Int' : 'Float'} = ${literal}`),
      '}'].join('\n'),
    '',
    object('SeatLayerMotionTokens', motionRows, 'Int'),
    '',
    object('SeatLayerPhysicsTokens', entries(motion.physics, kotlinFloat), 'Float'),
    '',
    object('SeatLayerCurveTokens', curveEntries(motion.curve), 'Float'),
    '',
    object('SeatLayerHapticTokens', entries(tokens.haptics, kotlinString), 'String'),
    '',
  ].join('\n');

  const sizeFile = [
    header,
    object('SeatLayerSizeTokens', entries(tokens.size, kotlinFloat), 'Float'),
    '',
  ].join('\n');

  const stringFile = [
    header,
    '/** The English default for every buyer-facing chrome string. */',
    object('SeatLayerStringTokens', entries(tokens.strings, kotlinString), 'String'),
    '',
  ].join('\n');

  return {
    'SeatLayerPickerTokens.g.kt': tokensFile,
    'SeatLayerPickerSizeTokens.g.kt': sizeFile,
    'SeatLayerPickerStringTokens.g.kt': stringFile,
  };
}

function main() {
  const check = process.argv.includes('--check');
  const design = loadDesign('.');
  validateDesign(design.tokens, design.locales);
  const files = render(design);
  const stale = [];
  for (const [name, contents] of Object.entries(files)) {
    const path = join(OUT_DIR, name);
    const current = (() => {
      try {
        return readFileSync(path, 'utf8');
      } catch {
        return null;
      }
    })();
    const lines = contents.split('\n').length;
    if (lines > 800) throw new Error(`${name} would be ${lines} lines, over the 800-line limit.`);
    if (current === contents) continue;
    stale.push(name);
    if (!check) writeFileSync(path, contents);
  }
  if (check && stale.length > 0) {
    process.stderr.write(
      `stale generated token sources: ${stale.join(', ')}\n` +
        'Run `node tool/generate-picker-tokens.mjs`.\n',
    );
    process.exitCode = 1;
    return;
  }
  process.stdout.write(
    check
      ? `${Object.keys(files).length} generated token sources are up to date\n`
      : `wrote ${Object.keys(files).length} files (${stale.length} changed)\n`,
  );
}

main();
