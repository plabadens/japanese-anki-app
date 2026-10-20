// Fixture test for the corrected reference template's pitch parser.
// Runs the functions exactly as shipped in Recognition/front.html by
// extracting the marked blocks, so the test cannot drift from the template.
//
//   nix shell nixpkgs#nodejs -c node docs/v0.3-reference-template/parser-test.mjs

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import assert from "node:assert/strict";

const here = dirname(fileURLToPath(import.meta.url));
const html = readFileSync(join(here, "Recognition/front.html"), "utf8");

function extract(name) {
  const start = html.indexOf(`/* @test-begin ${name} */`);
  const end = html.indexOf(`/* @test-end ${name} */`);
  assert.ok(start >= 0 && end > start, `missing ${name} block`);
  return html.slice(start, end);
}

const source = `${extract("tokenizer")}\n${extract("pattern")}\nexport { tokenizeMoraeHtml, pitchPattern };`;
const moduleUrl = `data:text/javascript;base64,${Buffer.from(source).toString("base64")}`;
const { tokenizeMoraeHtml, pitchPattern } = await import(moduleUrl);

const tests = [];
const test = (name, fn) => tests.push([name, fn]);

// Real AJT records (pinned revision), including the nested nasal handakuten case.
test("悪逆 keeps its nasal handakuten as an annotation, not a mora", () => {
  const morae = tokenizeMoraeHtml(
    '<span class="low_rise">ア</span><span class="high">ク<span class="nasal">キ<span class="handakuten">&#176;</span></span>ャク</span>',
  );
  assert.deepEqual(morae.map((m) => m.text), ["ア", "ク", "キャ", "ク"]);
  assert.deepEqual(morae.map((m) => m.level), ["low", "high", "high", "high"]);
  assert.deepEqual(morae.map((m) => m.mark), ["", "", "nasal", ""]);
});

test("devoiced mark is inherited from the wrapping level span", () => {
  const morae = tokenizeMoraeHtml(
    '<span class="low_rise"><span class="devoiced">シ</span></span><span class="high">コー</span>',
  );
  // A long-vowel mark is its own mora, as in the upstream notation.
  assert.deepEqual(morae.map((m) => m.text), ["シ", "コ", "ー"]);
  assert.deepEqual(morae.map((m) => m.mark), ["devoiced", "", ""]);
});

test("contracted kana join one mora and the pitch_number stop mark is ignored", () => {
  const morae = tokenizeMoraeHtml(
    '<span class="high_drop">キュ</span><span class="low">ウセイ</span> <span class="pitch_number">10</span>',
  );
  assert.deepEqual(morae.map((m) => m.text), ["キュ", "ウ", "セ", "イ"]);
});

test("accent numbers of ten or more are read whole", () => {
  assert.equal(
    pitchPattern({ pitch: "<span class=\"pitch_number\">10</span>", kana: "きゅうせいあるこーるちゅうどく" }),
    "nakadaka",
  );
  assert.equal(pitchPattern({ pitch: "<span class=\"pitch_number\">0</span>", kana: "あくぎゃく" }), "heiban");
  assert.equal(pitchPattern({ pitch: "", kana: "あくぎゃく" }), "unknown");
});

test("odaka is distinguished from nakadaka at the final mora", () => {
  // おとこ (3 morae, accent 3) is odaka; あくぎゃく (4 morae, accent 2) is nakadaka.
  assert.equal(pitchPattern({ pitch: "3", kana: "おとこ" }), "odaka");
  assert.equal(pitchPattern({ pitch: "2", kana: "あくぎゃく" }), "nakadaka");
  // あたらしい has five morae, so accent 4 is nakadaka, not odaka.
  assert.equal(pitchPattern({ pitch: "4", kana: "あたらしい" }), "nakadaka");
});

let failures = 0;
for (const [name, fn] of tests) {
  try {
    fn();
    console.log(`ok   ${name}`);
  } catch (error) {
    failures++;
    console.log(`FAIL ${name}\n     ${error.message.split("\n").join("\n     ")}`);
  }
}
console.log(`\n${tests.length - failures}/${tests.length} parser fixtures passed`);
process.exit(failures === 0 ? 0 : 1);
