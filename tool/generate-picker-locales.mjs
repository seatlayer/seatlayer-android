#!/usr/bin/env node
// Emits the typed picker string surface and the 37-locale catalogue.
//
//   node tool/generate-picker-locales.mjs            # write
//   node tool/generate-picker-locales.mjs --check    # fail if stale
//
// `SeatLayerPickerStrings` is generated rather than hand-written because the
// reference SDK already ships a reviewed default and a reviewed translation for
// every one of these sentences. Writing them again on this side is the same
// words drifting in two places.
//
// Two tables below are the only judgement in this file: which fields take
// arguments (a placeholder is not enough — "Row {row}" is a plain label), and
// which strings this platform's chrome needs that the shared design has no
// token for. Everything else follows from `design/tokens.json`.

import { readFileSync, writeFileSync, readdirSync, unlinkSync } from 'node:fs';
import { join } from 'node:path';
import {
  flatten,
  generatedHeader,
  kotlinString,
  loadDesign,
  pluralPairs,
  screamingSnake,
} from './picker-design-core.mjs';

const OUT_DIR = 'seatlayer-compose/src/main/kotlin/io/seatlayer/android/compose';
const LOCALE_FILE_PREFIX = 'SeatLayerPickerLocaleData';
const LOCALE_GROUPS = 3;
const LINE_LIMIT = 800;

/** Fields the chrome calls with arguments, and the token placeholder each fills. */
const SIGNATURES = Object.freeze({
  sightline: [['metres', 'String', '{m}']],
  accessFreeCount: [['count', 'Int', '{count}']],
  accessibleStep: [['index', 'Int', '{index}'], ['total', 'Int', '{total}']],
  accessibleSections: [['count', 'Int', '{count}']],
  accessNeedWithCount: [['need', 'String', '{need}'], ['count', 'Int', '{count}']],
  holdLapsedBody: [['minutes', 'Int', '{n}']],
  seatsNotRecovered: [['count', 'Int', '{n}']],
  chooseMore: [['count', 'Int', '{count}']],
  seatsLeft: [['count', 'Int', '{count}']],
  onlyLeft: [['count', 'Int', '{count}']],
  fromPrice: [['money', 'String', '{price}']],
  continueWithTotal: [['money', 'String', '{money}']],
  moreCount: [['count', 'Int', '{count}']],
  secureMoreAndCheckout: [['count', 'Int', '{count}']],
  peekSecured: [['count', 'Int', '{count}'], ['total', 'String', '{total}']],
  heldFor: [['clock', 'String', '{clock}']],
  addMinutes: [['minutes', 'Int', '{count}']],
  seatsHeldForNeedMoreTime: [['time', 'String', '{time}']],
  seatJustTakenByAnother: [['label', 'String', '{label}']],
  chooseMinMaxGuests: [['minimum', 'Int', '{min}'], ['maximum', 'Int', '{max}']],
  placesAvailable: [['count', 'Int', '{count}']],
  venueMap: [['venue', 'String', '{venue}']],
  seatIdentity: [['parts', 'List<String>', '{parts}']],
});

/** The wire keys of the twelve accommodations, and the token naming each one. */
const ACCESS_NEEDS = Object.freeze({
  wheelchair: 'accessWheelchair',
  companion: 'accessCompanion',
  'semi-ambulatory': 'accessSemiAmbulatory',
  'designated-aisle': 'accessDesignatedAisle',
  'step-free': 'accessStepFree',
  hearing: 'accessHearing',
  cart: 'accessCart',
  'sign-language': 'accessSignLanguage',
  'low-vision': 'accessLowVision',
  'sensory-friendly': 'accessSensoryFriendly',
  'plus-size': 'accessPlusSize',
  'lift-armrest': 'accessLiftArmrest',
});

/** Chrome this platform draws that the shared design has no token for. */
const PLATFORM_EXTRAS = Object.freeze({
  actionFailed: 'That action could not be completed.',
  confirm: 'Confirm',
  dismiss: 'Dismiss',
  floors: 'Floors',
  from: 'From',
  generatedSeatView: 'Generated preview',
  preview: 'Preview',
  prices: 'Prices',
  quantity: 'Quantity',
  realSeatView: 'Real view',
  seatView: 'Seat view',
  sections: 'Sections',
  tickets: 'tickets',
  timeRemaining: 'Time remaining',
  total: 'Total',
});

/** @param {Record<string, string>} strings */
function plan(strings) {
  const keys = Object.keys(strings);
  const pairs = pluralPairs(keys);
  const shadowedBases = new Set(
    [...pairs.values()].filter((pair) => pair.base !== null).map((pair) => pair.base),
  );
  const pluralHalves = new Set([...pairs.values()].flatMap((pair) => [pair.one, pair.other]));
  const accommodations = new Set(Object.values(ACCESS_NEEDS));

  /** @type {Array<{name: string, token: string | null, literal: string}>} */
  const fields = [];
  for (const key of keys.sort()) {
    if (shadowedBases.has(key) || accommodations.has(key)) continue;
    fields.push({ name: key, token: screamingSnake(key), literal: '' });
  }
  for (const [name, value] of Object.entries(PLATFORM_EXTRAS)) {
    if (Object.hasOwn(strings, name)) {
      throw new Error(`${name} is a design token; remove it from PLATFORM_EXTRAS.`);
    }
    fields.push({ name, token: null, literal: kotlinString(value) });
  }
  fields.sort((left, right) => (left.name < right.name ? -1 : left.name > right.name ? 1 : 0));

  const names = new Set(fields.map((field) => field.name));
  for (const [name, parameters] of Object.entries(SIGNATURES)) {
    if (!names.has(name)) throw new Error(`SIGNATURES names an unknown field ${name}.`);
    for (const [, , placeholder] of parameters) {
      if (!strings[name].includes(placeholder)) {
        throw new Error(`strings.${name} does not carry ${placeholder}.`);
      }
    }
  }
  for (const [base, pair] of pairs) {
    if (!names.has(pair.one) || !names.has(pair.other)) {
      throw new Error(`plural ${base} lost one of its halves.`);
    }
  }
  for (const [wire, token] of Object.entries(ACCESS_NEEDS)) {
    if (!Object.hasOwn(strings, token)) throw new Error(`tokens.json has no strings.${token}.`);
    if (wire.length === 0) throw new Error('an accommodation wire key must not be empty.');
  }
  return { fields, pairs };
}

/** @param {string} name @param {Array<[string, string, string]>} parameters @param {boolean} plural */
function functionSource(name, parameters, plural) {
  const signature = parameters.map(([parameter, type]) => `${parameter}: ${type}`).join(', ');
  const pattern = plural ? `(if (count == 1) ${name}One else ${name}Other)` : name;
  const replacements = parameters
    .map(([parameter, type, placeholder]) => {
      const value = type === 'List<String>'
        ? `${parameter}.joinToString(separator = " \\u00b7 ")`
        : type === 'String'
          ? parameter
          : `${parameter}.toString()`;
      return `\n        .replace(${kotlinString(placeholder)}, ${value})`;
    })
    .join('');
  return `    public fun ${name}(${signature}): String = ${pattern}${replacements}`;
}

/** @param {ReturnType<typeof loadDesign>} design */
function renderStrings(design, planned) {
  const { sha256 } = design;
  const { fields, pairs } = planned;
  const lines = [];
  lines.push(generatedHeader({ generator: 'generate-picker-locales.mjs', sha256 }));
  lines.push('import androidx.compose.runtime.Immutable');
  lines.push('import java.util.Locale');
  lines.push('');
  lines.push('/**');
  lines.push(' * Every buyer-facing string the native picker chrome draws.');
  lines.push(' *');
  lines.push(' * The map itself is localized by the runtime; this covers the native chrome');
  lines.push(' * around it. Every entry has an English default, so a host that overrides');
  lines.push(' * nothing still gets a complete interface, and [localized] starts from the');
  lines.push(" * runtime's own wording so the drawn map and the chrome agree.");
  lines.push(' */');
  lines.push('@Immutable');
  lines.push('@Suppress("LargeClass")');
  lines.push('public data class SeatLayerPickerStrings(');
  lines.push('    val languageTag: String = "en",');
  for (const field of fields) {
    const value = field.token === null
      ? field.literal
      : `SeatLayerStringTokens.${field.token}`;
    lines.push(`    val ${field.name}: String = ${value},`);
  }
  lines.push('    val accessNeeds: Map<String, String> = defaultAccessNeeds,');
  lines.push(') {');
  for (const [name, parameters] of Object.entries(SIGNATURES).sort()) {
    lines.push(functionSource(name, parameters, false));
    lines.push('');
  }
  for (const base of [...pairs.keys()].sort()) {
    lines.push(functionSource(base, [['count', 'Int', '{count}']], true));
    lines.push('');
  }
  lines.push('    /** The translated name of one accommodation, or its wire key. */');
  lines.push('    public fun accessNeed(key: String): String = accessNeeds[key] ?: key');
  lines.push('');
  lines.push('    public companion object {');
  lines.push('        /** The English name of every accommodation the runtime reports. */');
  lines.push('        @JvmStatic');
  lines.push('        public val defaultAccessNeeds: Map<String, String> = mapOf(');
  for (const [wire, token] of Object.entries(ACCESS_NEEDS)) {
    lines.push(`            ${kotlinString(wire)} to SeatLayerStringTokens.${screamingSnake(token)},`);
  }
  lines.push('        )');
  lines.push('');
  lines.push('        @JvmStatic');
  lines.push('        public val supportedLanguageTags: Set<String>');
  lines.push('            get() = SeatLayerPickerLocaleData.dictionaries.keys');
  lines.push('');
  lines.push('        /** Resolves an exact tag, then its base language, then English. */');
  lines.push('        @JvmStatic');
  lines.push('        @JvmOverloads');
  lines.push('        public fun localized(');
  lines.push('            languageTag: String = Locale.getDefault().toLanguageTag(),');
  lines.push('            overrides: Map<String, String> = emptyMap(),');
  lines.push('        ): SeatLayerPickerStrings {');
  lines.push('            val tag = resolveLanguageTag(languageTag)');
  lines.push('            val dictionary = SeatLayerPickerLocaleData.dictionaries.getValue(tag) +');
  lines.push('                overrides.filterValues(String::isNotEmpty)');
  lines.push('            val defaults = SeatLayerPickerStrings(languageTag = tag)');
  lines.push('            fun text(key: String, fallback: String): String = dictionary[key] ?: fallback');
  lines.push('            return defaults.copy(');
  const localeKeys = new Set(Object.keys(design.locales.en));
  const names = new Set(fields.map((field) => field.name));
  const localized = [];
  for (const key of [...localeKeys].sort()) {
    if (key.startsWith('accessNeeds.')) continue;
    const field = key.endsWith('.one')
      ? `${key.slice(0, -'.one'.length)}One`
      : key.endsWith('.other')
        ? `${key.slice(0, -'.other'.length)}Other`
        : key;
    if (!names.has(field)) continue;
    localized.push(field);
    lines.push(`                ${field} = text(${kotlinString(key)}, defaults.${field}),`);
  }
  if (names.has('continueWithTotal')) {
    lines.push('                continueWithTotal = text("continueWord", defaults.continueWord)');
    lines.push('                    .let { word -> "$word \\u00b7 {money}" },');
  }
  lines.push('                accessNeeds = defaults.accessNeeds.mapValues { (key, name) ->');
  lines.push('                    text("accessNeeds.$key", name)');
  lines.push('                },');
  lines.push('            )');
  lines.push('        }');
  lines.push('');
  lines.push('        private fun resolveLanguageTag(requested: String): String {');
  lines.push('            val normalized = requested.trim().replace(\'_\', \'-\')');
  lines.push('            SeatLayerPickerLocaleData.dictionaries.keys.firstOrNull {');
  lines.push('                it.equals(normalized, ignoreCase = true)');
  lines.push('            }?.let { return it }');
  lines.push('            val locale = Locale.forLanguageTag(normalized)');
  lines.push('            if (locale.language == "zh") {');
  lines.push('                val traditional = locale.script.equals("Hant", ignoreCase = true) ||');
  lines.push('                    locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")');
  lines.push('                return if (traditional) "zh-Hant" else "zh-Hans"');
  lines.push('            }');
  lines.push('            return locale.language.takeIf(');
  lines.push('                SeatLayerPickerLocaleData.dictionaries::containsKey,');
  lines.push('            ) ?: "en"');
  lines.push('        }');
  lines.push('    }');
  lines.push('}');
  lines.push('');
  return { source: lines.join('\n'), localized };
}

/** @param {ReturnType<typeof loadDesign>} design */
function renderLocales(design) {
  const { locales, sha256 } = design;
  const header = generatedHeader({ generator: 'generate-picker-locales.mjs', sha256 });
  const tags = Object.keys(locales).sort();
  const size = Math.ceil(tags.length / LOCALE_GROUPS);
  /** @type {Record<string, string>} */
  const files = {};
  const objects = [];
  for (let group = 0; group * size < tags.length; group += 1) {
    const suffix = String.fromCharCode('A'.charCodeAt(0) + group);
    const name = `${LOCALE_FILE_PREFIX}${suffix}`;
    objects.push(name);
    const lines = [header, `internal object ${name} {`];
    lines.push('    internal val entries: Map<String, Map<String, String>> = mapOf(');
    for (const tag of tags.slice(group * size, (group + 1) * size)) {
      lines.push(`        ${kotlinString(tag)} to mapOf(`);
      const dictionary = Object.entries(locales[tag]).sort(([l], [r]) => (l < r ? -1 : 1));
      for (let index = 0; index < dictionary.length; index += 2) {
        lines.push(
          `            ${dictionary
            .slice(index, index + 2)
            .map(([key, value]) => `${kotlinString(key)} to ${kotlinString(value)},`)
            .join(' ')}`,
        );
      }
      lines.push('        ),');
    }
    lines.push('    )');
    lines.push('}');
    lines.push('');
    files[`${name}.g.kt`] = lines.join('\n');
  }
  files[`${LOCALE_FILE_PREFIX}.g.kt`] = [
    header,
    '/** Every locale the runtime translates, keyed by its own dictionary name. */',
    `internal object ${LOCALE_FILE_PREFIX} {`,
    '    internal val dictionaries: Map<String, Map<String, String>> =',
    `        ${objects.map((name) => `${name}.entries`).join(' +\n            ')}`,
    '}',
    '',
  ].join('\n');
  return files;
}

function main() {
  const check = process.argv.includes('--check');
  const design = loadDesign('design');
  const planned = plan(design.tokens.strings);
  const strings = renderStrings(design, planned);
  const files = {
    'SeatLayerPickerStrings.g.kt': strings.source,
    ...renderLocales(design),
  };
  const stale = [];
  for (const [name, contents] of Object.entries(files)) {
    const path = join(OUT_DIR, name);
    const lines = contents.split('\n').length;
    if (lines > LINE_LIMIT) {
      throw new Error(`${name} would be ${lines} lines, over the ${LINE_LIMIT}-line limit.`);
    }
    const current = (() => {
      try {
        return readFileSync(path, 'utf8');
      } catch {
        return null;
      }
    })();
    if (current === contents) continue;
    stale.push(name);
    if (!check) writeFileSync(path, contents);
  }
  // A locale group that shrank away leaves a file nothing reads and every
  // reader still believes.
  for (const orphan of readdirSync(OUT_DIR)) {
    if (!/^SeatLayerPickerLocaleData[A-Z]?\.g\.kt$/.test(orphan)) continue;
    if (Object.hasOwn(files, orphan)) continue;
    stale.push(orphan);
    if (!check) unlinkSync(join(OUT_DIR, orphan));
  }
  if (check && stale.length > 0) {
    process.stderr.write(
      `stale generated string sources: ${stale.join(', ')}\n` +
        'Run `node tool/generate-picker-locales.mjs`.\n',
    );
    process.exitCode = 1;
    return;
  }
  process.stdout.write(
    check
      ? `${Object.keys(files).length} generated string sources are up to date\n`
      : `wrote ${Object.keys(files).length} files (${stale.length} changed), ` +
        `${planned.fields.length + 2} fields, ${strings.localized.length} localized\n`,
  );
}

main();
