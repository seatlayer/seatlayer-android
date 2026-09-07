// Shared reader for the canonical cross-platform picker design sources.
//
// This module is deliberately platform-neutral: it loads `Design/tokens.json`,
// `Design/locale_strings.json`, `Design/components.md` and
// `Design/picker-spec.md`, verifies them against `Design/source-lock.json`,
// and hands back a flattened, grouped view of every token plus the plural
// structure of the string table. It emits no source code of its own.
//
// Another SeatLayer SDK can copy this file byte-for-byte and write only its
// own emitter on top of `loadDesign`, `flattenTokens`, `groupTokens` and
// `stringTokens`. Nothing here may grow a Swift-, Kotlin- or Dart-shaped
// assumption.
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

/// Keys that are counts of things rather than measurements. A count that
/// arrives as `4.0` cannot index a list or compare against a length, so every
/// emitter is told which size tokens are integral.
export const COUNT_SIZE_KEYS = new Set([
  "denseVisibleLines",
  "denseCollapseFrom",
  "confirmSectionShortMax",
]);

/// Sub-keys that document the group they sit in rather than carrying a value.
export const PROSE_KEYS = new Set(["note"]);

export const sha256 = (bytes) =>
  crypto.createHash("sha256").update(bytes).digest("hex");

/// Loads the four design documents and proves they are the ones the lock
/// names. A caller that skips the check cannot claim the numbers it emits are
/// the canonical ones, so verification is not optional here.
export function loadDesign(root) {
  const read = (name) => fs.readFileSync(path.join(root, "Design", name));
  const lock = JSON.parse(read("source-lock.json"));
  const raw = {
    tokens: read("tokens.json"),
    locales: read("locale_strings.json"),
    components: read("components.md"),
    spec: read("picker-spec.md"),
  };
  const expected = [
    ["tokens", lock.tokensSha256, "Design/tokens.json"],
    ["locales", lock.localeStringsSha256, "Design/locale_strings.json"],
    ["components", lock.componentsSha256, "Design/components.md"],
    ["spec", lock.pickerSpecSha256, "Design/picker-spec.md"],
  ];
  const mismatched = expected
    .filter(([name, hash]) => sha256(raw[name]) !== hash)
    .map(([, , file]) => file);
  if (mismatched.length) {
    throw new Error(
      `${mismatched.join(", ")} do not match Design/source-lock.json`,
    );
  }
  return {
    lock,
    raw,
    tokens: JSON.parse(raw.tokens.toString()),
    locales: JSON.parse(raw.locales.toString()),
  };
}

const isPlainObject = (value) =>
  value !== null && typeof value === "object" && !Array.isArray(value);

/// Every leaf of the token document as `{ path, group, key, value, kind }`,
/// where `path` is the dotted key ("color.light.background") and `group` is
/// its top-level section ("color").
///
/// `kind` is one of `color`, `count`, `number`, `string`, `numberList` or
/// `prose`, decided from the value and from where in the tree it sits — the
/// one place any emitter needs to look to know what type to write.
export function flattenTokens(tokens) {
  const entries = [];
  const walk = (node, trail) => {
    for (const key of Object.keys(node)) {
      const value = node[key];
      const next = [...trail, key];
      if (isPlainObject(value)) {
        walk(value, next);
        continue;
      }
      const group = next[0];
      entries.push({
        path: next.join("."),
        trail: next,
        group,
        key,
        value,
        kind: classify(group, key, value),
      });
    }
  };
  for (const group of Object.keys(tokens)) {
    if (group.startsWith("$") || !isPlainObject(tokens[group])) continue;
    walk(tokens[group], [group]);
  }
  return entries;
}

function classify(group, key, value) {
  if (PROSE_KEYS.has(key)) return "prose";
  if (Array.isArray(value)) return "numberList";
  if (typeof value === "string") {
    return group === "color" && /^#[0-9a-fA-F]{6,8}$/.test(value)
      ? "color"
      : "string";
  }
  if (group === "size" && COUNT_SIZE_KEYS.has(key)) return "count";
  if (typeof value === "number") {
    return Number.isInteger(value) &&
      (group === "motion" || group === "elevation")
      ? "count"
      : "number";
  }
  return "string";
}

/// The flattened tokens indexed by top-level group, each group keeping the
/// document's own key order.
export function groupTokens(flat) {
  const grouped = new Map();
  for (const entry of flat) {
    if (!grouped.has(entry.group)) grouped.set(entry.group, []);
    grouped.get(entry.group).push(entry);
  }
  return grouped;
}

/// The leaves directly under `tokens.<group>.<sub>`, in document order.
export function section(tokens, ...trail) {
  let node = tokens;
  for (const step of trail) node = node?.[step];
  if (!isPlainObject(node)) {
    throw new Error(`tokens.${trail.join(".")} is not an object`);
  }
  return Object.entries(node).filter(([key]) => !PROSE_KEYS.has(key));
}

const PLURAL_SUFFIXES = [
  ["One", "one"],
  ["Other", "other"],
  ["Zero", "zero"],
  ["Two", "two"],
  ["Few", "few"],
  ["Many", "many"],
];

/// The string table as `{ key, value, base, plural, localeKey }`.
///
/// The token document names plural forms `<base>One` / `<base>Other` because a
/// token key has to be a legal identifier in every target language. The locale
/// dictionaries the runtime publishes name the same strings `<base>.one` /
/// `<base>.other`. `localeKey` is the bridge between the two, so a resolver
/// can look a typed key up in a translated dictionary without a hand-written
/// table on each platform.
export function stringTokens(tokens) {
  const strings = tokens.strings;
  if (!isPlainObject(strings)) {
    throw new Error("tokens.strings is not an object");
  }
  const keys = Object.keys(strings);
  return keys.map((key) => {
    const match = PLURAL_SUFFIXES.map(([suffix, category]) => {
      if (!key.endsWith(suffix)) return null;
      const base = key.slice(0, -suffix.length);
      return base && keys.includes(`${base}${suffix}`) ? { base, category } : null;
    }).find(Boolean);
    // A lone `…Other` with no sibling form is a word that happens to end that
    // way, not a plural. Require at least the `other` category to exist.
    const plural =
      match && keys.includes(`${match.base}Other`) ? match : null;
    return {
      key,
      value: strings[key],
      base: plural ? plural.base : key,
      plural: plural ? plural.category : null,
      localeKey: plural ? `${plural.base}.${plural.category}` : key,
    };
  });
}

/// The plural families in the string table, `{ base, forms: { one, other } }`.
export function pluralFamilies(tokens) {
  const families = new Map();
  for (const entry of stringTokens(tokens)) {
    if (!entry.plural) continue;
    if (!families.has(entry.base)) {
      families.set(entry.base, { base: entry.base, forms: {} });
    }
    families.get(entry.base).forms[entry.plural] = entry.key;
  }
  return [...families.values()];
}

/// The locale codes the dictionaries carry, sorted.
export function localeCodes(locales) {
  return Object.keys(locales.strings ?? {}).sort();
}
