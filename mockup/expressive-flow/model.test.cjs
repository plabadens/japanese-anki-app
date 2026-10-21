'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const modelPath = path.join(__dirname, 'model.js');
const M = fs.existsSync(modelPath) ? require(modelPath) : {};
const SAMPLE = '図書館で本を読みました。';
const ALT = '図書館で本を読んだ。';
const span = (text, word, from = 0) => ({ start: text.indexOf(word, from), end: text.indexOf(word, from) + word.length });
const act = (s, type, fields = {}) => M.reduce(s, { type, ...fields });
function selected(s, word = '本') { return act(s, 'SELECT_SPAN', span(s.sentence, word)); }
function target(text = SAMPLE) {
  return selected(act(M.create('test', { mode: 'type', sentence: text }), 'CONTINUE'));
}
function review(text = SAMPLE) {
  const s = act(target(text), 'CONTINUE');
  return act(s, 'GENERATION_COMPLETE', { generation: s.generation });
}
function freezeDeep(v) {
  if (v && typeof v === 'object') { Object.values(v).forEach(freezeDeep); Object.freeze(v); }
  return v;
}

test('photo happy path is pure and exports the browser/CommonJS API', () => {
  assert.equal(typeof M.create, 'function', 'model.js must export create');
  const api = ['create', 'seed', 'tokenize', 'reduce', 'selectedEntry', 'selectedText', 'canContinue', 'canSend', 'entriesFor'];
  for (const name of api) assert.equal(typeof M[name], 'function', name);
  const sandbox = { Intl }; vm.createContext(sandbox);
  vm.runInContext(fs.readFileSync(modelPath, 'utf8'), sandbox);
  for (const name of api) assert.equal(typeof sandbox.FlowModel[name], 'function', `browser ${name}`);
  const initial = freezeDeep(M.create());
  assert.equal(initial.id, 'live'); assert.equal(initial.stage, 'input');
  assert.equal(initial.sentence, ''); assert.equal(M.canContinue(initial), false);
  let s = act(initial, 'CAPTURE');
  assert.notEqual(s, initial); assert.equal(initial.captured, false);
  assert.equal(s.captured, true);
  s = act(freezeDeep(s), 'USE_PHOTO');
  assert.equal(s.stage, 'target'); assert.equal(s.sentence, SAMPLE);
  assert.equal(s.selection, null); assert.equal(M.canContinue(s), false);
  s = selected(s);
  assert.equal(M.selectedText(s), '本'); assert.equal(M.selectedEntry(s).word, '本');
  assert.equal(s.definition, 'book; volume'); assert.equal(M.canContinue(s), true);
  s = act(s, 'CONTINUE');
  assert.equal(s.stage, 'preparing'); assert.equal(s.status, 'generating');
  assert.equal(s.generation, 1); assert.equal(M.canSend(s), false);
  s = act(s, 'GENERATION_COMPLETE', { generation: s.generation });
  assert.equal(s.stage, 'review'); assert.equal(s.status, 'ready');
  assert.equal(s.translation, 'I read a book at the library.'); assert.equal(M.canSend(s), true);
  s = act(s, 'SEND');
  assert.equal(s.status, 'sending'); assert.equal(s.submission, 'sending');
  assert.equal(s.snapshot.word, '本'); assert.equal(s.snapshot.definition, 'book; volume');
  assert.equal(s.snapshot.hasPhoto, true); assert.equal(M.canSend(s), false);
  assert.equal(Object.isFrozen(s.snapshot), true); assert.equal(Object.isFrozen(s.snapshot.selection), true);
  s = act(s, 'SEND_COMPLETE');
  assert.equal(s.stage, 'input'); assert.equal(s.sentence, ''); assert.equal(s.snapshot, null);
  assert.equal(s.hasPhoto, false); assert.equal(s.submission, null);
});

test('word-pill selection retains keyboard boundaries without obsolete handle plumbing', () => {
  const study = fs.readFileSync(path.join(__dirname, 'study.js'), 'utf8');
  const css = fs.readFileSync(path.join(__dirname, 'study.css'), 'utf8');
  assert.match(study, /function stepBoundary\(/, 'Shift+arrow selection keeps token boundaries');
  assert.match(study, /e\.shiftKey&&s\.selection/, 'the sentence group still extends a selected span');
  for (const deadCode of [/function positionHandles\(/, /function offsetAtPoint\(/,
    /caretPositionFromPoint|caretRangeFromPoint/, /data-handle/, /data-selection-box/,
    /activeHandle/, /addEventListener\(['"]resize['"]/]) {
    assert.doesNotMatch(study, deadCode);
  }
  assert.doesNotMatch(css, /selection-handle|selection-help|data-handle/);
});

function legacy() {
  const archived = path.join(__dirname, 'history', 'previous-study', 'flow.template.html');
  const source = fs.readFileSync(fs.existsSync(archived) ? archived : path.join(__dirname, 'flow.template.html'), 'utf8');
  assert.ok(source.includes('function handle(s,a){'), 'legacy fixture must contain the original handler');
  const extract = (start, end) => source.slice(source.indexOf(start), source.indexOf(end, source.indexOf(start)));
  const sandbox = { renderPhone() {}, notify() {}, setTimeout() {}, document: { querySelector() { return null; } } };
  vm.createContext(sandbox);
  vm.runInContext(`const sample=${JSON.stringify(SAMPLE)}, PHOTO='fixture'; let revision=0,failNext=false;
    ${extract('const fresh=', 'function toolbar(')}
    ${extract('function generate(', 'function handle(')}
    ${extract('function handle(', "document.addEventListener('click'")}
    globalThis.legacy = {fresh,handle};`, sandbox);
  return { ...sandbox.legacy, sandbox };
}

test('P0-1 regression assertion rejects the unchanged legacy Back escape', () => {
  const old = legacy(); const s = old.fresh('legacy', 'review');
  s.status = 'uncertain'; s.submission = 'uncertain';
  old.handle(s, 'back'); old.handle(s, 'generate'); old.handle(s, 'send');
  assert.throws(() => assert.notEqual(s.status, 'sending'), assert.AssertionError,
    'the safety assertion must fail on the unmodified legacy handler');
});

test('P0-1 uncertain submission blocks every mutation independently of stage/status', () => {
  let s = act(review(), 'SEND');
  const snapshot = s.snapshot;
  s = act(s, 'SEND_UNCERTAIN');
  assert.equal(s.submission, 'uncertain'); assert.equal(s.status, 'uncertain');
  assert.equal(s.snapshot, snapshot);
  const events = [
    'CAPTURE', 'RETAKE', 'USE_PHOTO', 'PREPARE', 'TYPE_MODE', 'PHOTO_MODE',
    { type: 'SET_SENTENCE', text: ALT }, { type: 'SELECT_SPAN', start: 0, end: 3 },
    { type: 'CHOOSE_ENTRY', id: 'counter' }, { type: 'TOGGLE_SENSE', index: 0 },
    { type: 'SET_DEFINITION', text: 'changed' }, { type: 'SET_TRANSLATION', text: 'changed' },
    { type: 'SET_NOTES', text: 'changed' }, { type: 'SET_TAGS', text: 'changed' },
    { type: 'SET_DECK', name: 'Other' },
    'CONTINUE', { type: 'GENERATION_COMPLETE', generation: s.generation },
    { type: 'GENERATION_FAIL', generation: s.generation }, 'CANCEL_GENERATION', 'RETRY',
    'BACK', 'RESELECT', 'SEND', 'SEND_UNCERTAIN', 'RECHECK',
    { type: 'SET_READING', start: 4, end: 5, reading: 'ほん', scope: 'sentence' },
    { type: 'SET_PITCH', index: 0 }, 'ATTACH_PHOTO', 'REMOVE_PHOTO',
    'ESCAPE', 'CLOSE_SHEET', 'BACKDROP', 'SETTINGS'
  ];
  for (const stage of ['input', 'target', 'preparing', 'review']) {
    for (const status of ['ready', 'generating', 'failed', 'missing', 'unknown', 'uncertain', 'sending']) {
      const tampered = freezeDeep({ ...s, stage, status });
      assert.equal(M.canContinue(tampered), false, `${stage}/${status} continue`);
      assert.equal(M.canSend(tampered), false, `${stage}/${status} send`);
      for (const event of events) {
        const next = M.reduce(tampered, typeof event === 'string' ? { type: event } : event);
        assert.notEqual(next, tampered);
        assert.deepEqual(next, tampered, `${stage}/${status}/${typeof event === 'string' ? event : event.type}`);
        assert.equal(next.snapshot, snapshot, 'the submitted snapshot must be retained by identity');
      }
    }
  }
  assert.equal(act(s, 'RECHECK').submission, 'uncertain');
  for (const event of ['DISCARD', 'SEND_COMPLETE']) {
    const reset = act(s, event);
    assert.equal(reset.sentence, ''); assert.equal(reset.stage, 'input');
    assert.equal(reset.snapshot, null); assert.equal(reset.submission, null);
  }
});

test('sending is also immutable and cannot snapshot twice or accept unsolicited completion', () => {
  const draft = review(); assert.deepEqual(act(draft, 'SEND_COMPLETE'), draft);
  assert.deepEqual(act(draft, 'SEND_UNCERTAIN'), draft);
  const sent = act(draft, 'SEND');
  for (const event of ['SEND', 'BACK', 'RESELECT', 'RETRY', 'CAPTURE']) {
    assert.deepEqual(act(sent, event), sent, event);
    assert.equal(act(sent, event).snapshot, sent.snapshot);
  }
});

test('destination is editable before send and frozen with the submitted note', () => {
  assert.equal(M.create().deck, 'Japanese');
  let s = act(review(), 'SET_DECK', { name: 'Mining::Books' });
  assert.equal(s.deck, 'Mining::Books');
  assert.equal(act(s, 'SET_DECK', { name: '   ' }).deck, 'Mining::Books');
  s = act(s, 'SEND');
  assert.equal(s.snapshot.deck, 'Mining::Books');
  assert.equal(act(s, 'SET_DECK', { name: 'Other' }).deck, 'Mining::Books');
});

test('P0-2 regression assertion rejects the unchanged legacy edited-sentence failure', () => {
  const old = legacy(); const s = old.fresh('legacy', 'target');
  old.sandbox.document.querySelector = () => ({ dataset: { editor: 'sentence' }, value: ALT });
  old.handle(s, 'save-editor');
  assert.throws(() => assert.equal(s.status, 'ready'), assert.AssertionError);
});

test('P0-2 editing retokenizes and resolves an inflected occurrence without replacing the sentence', () => {
  const original = freezeDeep(review());
  let s = act(original, 'SET_SENTENCE', { text: ALT });
  assert.equal(s.sentence, ALT); assert.equal(s.stage, 'target'); assert.equal(s.status, 'ready');
  assert.equal(s.selection, null); assert.equal(s.entryId, null); assert.equal(s.definition, '');
  assert.equal(s.sentenceAudioStale, true); assert.equal(s.translation, '');
  assert.deepEqual(s.tokens.map(t => t.text), ['図書館', 'で', '本', 'を', '読んだ', '。']);
  s = selected(s, '読んだ');
  assert.equal(M.selectedText(s), '読んだ'); assert.equal(M.selectedEntry(s).word, '読む');
  assert.equal(M.selectedEntry(s).reading, 'よむ'); assert.equal(M.canContinue(s), true);
  assert.equal(s.sentence, ALT); assert.equal(original.sentence, SAMPLE);
  const polite = selected(target(), '読みました');
  assert.equal(M.selectedText(polite), '読みました'); assert.equal(M.selectedEntry(polite).word, '読む');
});

test('tokenization covers unknown text, punctuation, and UTF-16 offsets without a dead end', () => {
  for (const text of [SAMPLE, ALT, '不思議な猫が歩いた。', '📚本と本。', '𠮷の本。', '', 'かな\u3099と漢字']) {
    const tokens = M.tokenize(text);
    assert.equal(tokens.map(t => t.text).join(''), text);
    let end = 0;
    for (const token of tokens) {
      assert.equal(token.start, end); assert.ok(token.end > token.start);
      assert.equal(text.slice(token.start, token.end), token.text); end = token.end;
      assert.equal(/^[\uDC00-\uDFFF]/u.test(token.text), false);
      assert.equal(/[\uD800-\uDBFF]$/u.test(token.text), false);
    }
    assert.equal(end, text.length);
    if (text.length > 4) assert.ok(tokens.length > 1);
  }
  let s = target(); s = act(s, 'SET_SENTENCE', { text: '猫が歩いた。' });
  s = act(s, 'SELECT_SPAN', { start: 0, end: 1 });
  assert.equal(s.stage, 'target'); assert.equal(s.status, 'unknown');
  assert.equal(M.selectedText(s), '猫'); assert.equal(M.selectedEntry(s), null);
  assert.equal(M.canContinue(s), false);
  s = act(s, 'SET_SENTENCE', { text: ALT }); s = selected(s);
  assert.equal(M.canContinue(s), true);
});

test('P0-3 regression assertion rejects unchanged legacy span collapse', () => {
  const old = legacy(); const s = old.fresh('legacy', 'target'); s.target = 0;
  old.sandbox.document.querySelector = () => ({ querySelector(selector) { return { value: selector.includes('start') ? '0' : '2' }; } });
  old.handle(s, 'save-boundaries');
  assert.throws(() => assert.deepEqual(s.selection, { start: 0, end: 5 }), assert.AssertionError);
  assert.equal(s.target, 0); assert.equal(s.status, 'missing');
});

test('P0-3 exact multi-token spans remain unknown and can be trimmed or explicitly cleared', () => {
  let s = act(target(), 'SELECT_SPAN', { start: 0, end: 5 });
  assert.deepEqual(s.selection, { start: 0, end: 5 }); assert.equal(M.selectedText(s), '図書館で本');
  assert.equal(s.status, 'unknown'); assert.equal(s.stage, 'target');
  assert.equal(s.entryId, null); assert.equal(M.selectedEntry(s), null);
  assert.deepEqual(M.entriesFor(s), []); assert.equal(M.canContinue(s), false);
  assert.deepEqual(act(s, 'CONTINUE').selection, s.selection);
  const trimmed = act(s, 'SELECT_SPAN', { start: 4, end: 5 });
  assert.equal(M.selectedEntry(trimmed).word, '本'); assert.equal(M.canContinue(trimmed), true);
  s = act(s, 'RESELECT');
  assert.equal(s.selection, null); assert.equal(s.entryId, null); assert.equal(s.definition, '');
  assert.equal(s.status, 'ready'); assert.equal(M.canContinue(s), false);
});

test('selection validates UTF-16 and grapheme boundaries and never coerces bad ranges', () => {
  const s = target('📚本と本。');
  for (const [start, end] of [[-1, 3], [0, 20], [4, 3], [2, 2], [1, 3], [0, 1], [NaN, 3], [2, Infinity], [2.5, 3], ['2', 3]]) {
    assert.deepEqual(act(s, 'SELECT_SPAN', { start, end }), s, `${start}:${end}`);
  }
  const second = act(s, 'SELECT_SPAN', { start: 4, end: 5 });
  assert.deepEqual(second.selection, { start: 4, end: 5 }); assert.equal(M.selectedEntry(second).word, '本');
  assert.equal(second.sentence, '📚本と本。');
  const combining = M.create('combining', { stage: 'target', mode: 'type', sentence: 'か\u3099本' });
  assert.deepEqual(act(combining, 'SELECT_SPAN', { start: 0, end: 1 }), combining);
});

test('P0-4 regression assertions reject unchanged legacy missing/counter recovery', () => {
  const old = legacy(); const missing = old.fresh('legacy', 'target'); missing.status = 'missing';
  old.handle(missing, 'reselect');
  assert.throws(() => assert.equal(missing.target, null), assert.AssertionError);
  const counter = old.fresh('legacy', 'target'); old.handle(counter, 'counter-entry');
  assert.throws(() => assert.equal(counter.entryId, 'counter'), assert.AssertionError);
  old.handle(counter, 'reselect');
  assert.equal(counter.target, 2); assert.equal(counter.status, 'ready');
});

test('P0-4 a selected counter never silently changes to the book meaning', () => {
  let s = target();
  assert.deepEqual(M.entriesFor(s).map(e => e.id), ['book', 'counter']);
  const before = JSON.stringify(s); M.entriesFor(s); assert.equal(JSON.stringify(s), before);
  s = act(s, 'CHOOSE_ENTRY', { id: 'counter' });
  assert.equal(s.entryId, 'counter'); assert.equal(M.selectedEntry(s).kind, 'counter');
  assert.match(s.definition, /cylindrical/); assert.equal(s.status, 'missing');
  assert.equal(M.selectedEntry(s).pitchAvailable, false);
  assert.deepEqual(M.selectedEntry(s).pitches, []); assert.ok(s.blockedEntries.includes('counter'));
  assert.equal(M.canContinue(s), false); assert.equal(M.canContinue({ ...s, status: 'ready' }), false);
  assert.equal(act(s, 'CONTINUE').entryId, 'counter');
  s = selected(s); assert.equal(s.entryId, 'counter'); assert.equal(M.canContinue(s), false);
  s = act(s, 'RESELECT');
  assert.equal(s.entryId, null); assert.equal(s.selection, null); assert.equal(s.definition, '');
  s = selected(s); assert.equal(s.entryId, 'counter'); assert.equal(M.canContinue(s), false);
  s = act(s, 'CHOOSE_ENTRY', { id: 'book' });
  assert.equal(s.definition, 'book; volume'); assert.equal(M.canContinue(s), true);
  assert.deepEqual(act(s, 'CHOOSE_ENTRY', { id: 'not-an-entry' }), s);
});

test('P0-4 missing fixture survives recovery, same-target reselection, and status tampering', () => {
  let s = M.seed('missing');
  assert.equal(s.status, 'missing'); assert.equal(s.stage, 'target');
  assert.equal(M.selectedEntry(s).word, '本'); assert.equal(M.selectedEntry(s).reading, 'ほん');
  assert.equal(s.definition, 'book; volume'); assert.ok(s.blockedEntries.includes(s.entryId));
  assert.equal(M.canContinue(s), false);
  const entryId = s.entryId;
  s = act(s, 'RESELECT'); assert.equal(s.selection, null); assert.equal(M.canContinue(s), false);
  s = selected(s); assert.equal(s.entryId, entryId); assert.equal(s.status, 'missing');
  assert.equal(M.canContinue({ ...s, status: 'ready' }), false);
  s = selected(s, '図書館'); assert.equal(s.entryId, 'library'); assert.equal(M.canContinue(s), true);
  assert.ok(s.blockedEntries.includes(entryId));
  s = selected(s, '本'); assert.equal(s.status, 'missing'); assert.equal(M.canContinue(s), false);
});

test('generation fail/retry/cancel retains context and rejects out-of-order completions', () => {
  let s = act(target(), 'CONTINUE'); const first = s.generation;
  assert.deepEqual(act(s, 'GENERATION_FAIL', { generation: first - 1 }), s);
  s = act(s, 'GENERATION_FAIL', { generation: first });
  assert.equal(s.stage, 'preparing'); assert.equal(s.status, 'failed');
  assert.equal(s.sentence, SAMPLE); assert.equal(M.selectedEntry(s).id, 'book');
  s = act(s, 'RETRY'); assert.equal(s.status, 'generating'); assert.ok(s.generation > first);
  assert.deepEqual(act(s, 'GENERATION_COMPLETE', { generation: first }), s);
  const second = s.generation;
  s = act(s, 'CANCEL_GENERATION');
  assert.equal(s.stage, 'target'); assert.equal(s.status, 'ready'); assert.equal(M.canContinue(s), true);
  assert.deepEqual(act(s, 'GENERATION_COMPLETE', { generation: second }), s);
  s = act(s, 'CONTINUE');
  assert.ok(s.generation > second);
  assert.deepEqual(act(s, 'GENERATION_COMPLETE', { generation: second }), s);
  s = act(s, 'GENERATION_COMPLETE', { generation: s.generation });
  assert.equal(s.stage, 'review'); assert.equal(M.canSend(s), true);
  assert.deepEqual(act(s, 'GENERATION_FAIL', { generation: s.generation }), s);
  s = act(s, 'BACK'); assert.equal(s.stage, 'target'); assert.equal(s.status, 'ready');
  assert.equal(s.sentence, SAMPLE); assert.equal(s.translation, 'I read a book at the library.');
  s = act(s, 'BACK'); assert.equal(s.stage, 'input'); assert.equal(s.sentence, SAMPLE);
});

test('editing/reselection/Back invalidate outstanding work and discard cannot reuse a generation ticket', () => {
  const generating = act(target(), 'CONTINUE');
  const events = [
    { type: 'SET_SENTENCE', text: ALT }, { type: 'SELECT_SPAN', start: 0, end: 3 },
    { type: 'CHOOSE_ENTRY', id: 'counter' }, { type: 'RESELECT' }, { type: 'BACK' }
  ];
  for (const event of events) {
    const edited = M.reduce(freezeDeep(generating), event);
    assert.notEqual(edited.status, 'generating', event.type);
    assert.deepEqual(act(edited, 'GENERATION_COMPLETE', { generation: generating.generation }), edited, event.type);
  }
  let reset = act(generating, 'DISCARD');
  reset = act(reset, 'TYPE_MODE'); reset = act(reset, 'SET_SENTENCE', { text: SAMPLE });
  reset = act(reset, 'CONTINUE'); reset = selected(reset); reset = act(reset, 'CONTINUE');
  assert.equal(reset.status, 'generating');
  assert.notEqual(reset.generation, generating.generation);
  assert.deepEqual(act(reset, 'GENERATION_COMPLETE', { generation: generating.generation }), reset);
  assert.deepEqual(act(target(), 'RETRY'), target());
  assert.deepEqual(act(M.seed('missing'), 'RETRY'), M.seed('missing'));
});

test('Type input PREPARE preserves optional images and mode changes preserve text', () => {
  let s = act(M.create(), 'TYPE_MODE');
  assert.equal(s.mode, 'type'); assert.equal(s.origin, 'type'); assert.equal(s.stage, 'input');
  assert.equal(M.canContinue(s), false); assert.deepEqual(act(s, 'PREPARE'), s);
  s = act(s, 'SET_SENTENCE', { text: SAMPLE });
  assert.equal(s.stage, 'input'); assert.equal(s.origin, 'type'); assert.equal(s.hasPhoto, false);
  s = act(s, 'PHOTO_MODE'); assert.equal(s.sentence, SAMPLE); assert.equal(s.origin, 'type');
  assert.equal(s.hasPhoto, false, 'switching mode does not fabricate an attachment');
  s = act(s, 'TYPE_MODE'); s = act(s, 'ATTACH_PHOTO');
  assert.equal(s.hasPhoto, true); assert.equal(s.origin, 'type');
  s = act(s, 'REMOVE_PHOTO'); assert.equal(s.hasPhoto, false);
  s = act(s, 'PREPARE');
  assert.equal(s.stage, 'target'); assert.equal(s.selection, null); assert.equal(s.generation, 0);
  s = selected(s); s = act(s, 'CONTINUE'); s = act(s, 'GENERATION_COMPLETE', { generation: s.generation });
  assert.equal(M.canSend(s), true); assert.equal(s.hasPhoto, false);
  s = act(s, 'ATTACH_PHOTO'); assert.equal(s.hasPhoto, true); assert.equal(s.stage, 'review');
  assert.equal(M.canSend(s), true);
  s = act(s, 'REMOVE_PHOTO'); assert.equal(s.hasPhoto, false); assert.equal(M.canSend(s), true);
});

test('photo origin cannot bypass its required image by mode switching, retake, or direct status edits', () => {
  let s = act(act(M.create(), 'CAPTURE'), 'USE_PHOTO');
  assert.equal(s.origin, 'photo'); assert.equal(s.hasPhoto, true);
  s = act(s, 'TYPE_MODE'); assert.equal(s.origin, 'photo'); assert.equal(s.sentence, SAMPLE);
  assert.deepEqual(act(s, 'REMOVE_PHOTO'), s);
  s = act(s, 'RETAKE'); assert.equal(s.captured, false); assert.equal(s.hasPhoto, false);
  assert.equal(s.stage, 'input'); assert.equal(s.sentence, SAMPLE);
  s = act(s, 'TYPE_MODE'); assert.equal(s.origin, 'photo'); assert.equal(M.canContinue(s), false);
  assert.equal(act(s, 'PREPARE').stage, 'input');
  const selectedNoPhoto = selected({ ...s, stage: 'target', status: 'ready' });
  assert.equal(M.canContinue(selectedNoPhoto), false);
  assert.equal(M.canSend({ ...review(), origin: 'photo', hasPhoto: false }), false);
  assert.deepEqual(act(M.create(), 'USE_PHOTO'), M.create());
  s = act(act(s, 'CAPTURE'), 'USE_PHOTO'); assert.equal(s.stage, 'target');
  assert.equal(s.sentence, SAMPLE); assert.equal(s.hasPhoto, true);
});

test('taking a contextual photo for Type never OCR-replaces its text or changes its origin', () => {
  let s = M.create('typed', { mode: 'type', sentence: ALT });
  s = act(s, 'PHOTO_MODE'); s = act(s, 'CAPTURE'); s = act(s, 'USE_PHOTO');
  assert.equal(s.sentence, ALT); assert.equal(s.origin, 'type'); assert.equal(s.hasPhoto, true);
  s = act(s, 'REMOVE_PHOTO'); assert.equal(s.hasPhoto, false); assert.equal(s.sentence, ALT);
});

test('sense choices and direct definition/translation/notes/tags edits remain pure and independent', () => {
  let s = freezeDeep(target());
  assert.ok(M.selectedEntry(s).senses.length > 1, 'book fixture offers multiple senses');
  s = act(s, 'TOGGLE_SENSE', { index: 1 }); assert.deepEqual(s.senseIds, [0, 1]);
  assert.equal(s.definition, M.selectedEntry(s).senses.join('; '));
  s = act(s, 'TOGGLE_SENSE', { index: 0 }); assert.deepEqual(s.senseIds, [1]);
  assert.equal(s.definition, M.selectedEntry(s).senses[1]);
  s = act(s, 'TOGGLE_SENSE', { index: 1 }); assert.equal(s.definition, ''); assert.equal(M.canContinue(s), false);
  assert.deepEqual(act(s, 'TOGGLE_SENSE', { index: 99 }), s);
  assert.deepEqual(act(s, 'TOGGLE_SENSE', { index: -1 }), s);
  s = act(s, 'TOGGLE_SENSE', { index: 0 });
  s = act(s, 'SET_DEFINITION', { text: 'my selected book meaning' });
  assert.equal(s.definition, 'my selected book meaning'); assert.equal(M.canContinue(s), true);
  s = act(s, 'CONTINUE'); s = act(s, 'GENERATION_COMPLETE', { generation: s.generation });
  const generation = s.generation;
  s = act(s, 'SET_TRANSLATION', { text: 'My corrected translation.' });
  s = act(s, 'SET_NOTES', { text: 'Line one\n<literal note>' });
  s = act(s, 'SET_TAGS', { text: 'JLPT::N5 books' });
  assert.equal(s.stage, 'review'); assert.equal(s.definition, 'my selected book meaning');
  assert.equal(s.translation, 'My corrected translation.'); assert.equal(s.notes, 'Line one\n<literal note>');
  assert.equal(s.tags, 'JLPT::N5 books'); assert.equal(s.generation, generation);
  assert.equal(M.canSend(s), true);
  assert.equal(M.canSend(act(s, 'SET_TRANSLATION', { text: ' ' })), false);
  assert.equal(M.canSend(act(s, 'SET_DEFINITION', { text: '' })), false);
  const changedTarget = selected(s, '図書館');
  assert.equal(changedTarget.translation, 'My corrected translation.');
  assert.equal(changedTarget.definition, 'library'); assert.equal(changedTarget.stage, 'target');
  const snapshot = act(s, 'SEND').snapshot;
  assert.equal(snapshot.tags, s.tags); assert.equal(snapshot.notes, s.notes);
  assert.equal(snapshot.translation, s.translation); assert.equal(snapshot.definition, s.definition);
});

test('sentence reading correction is occurrence-specific and requires deliberate audio regeneration', () => {
  const original = freezeDeep(review());
  let s = act(original, 'SET_READING', { ...span(SAMPLE, '図書館'), reading: 'としょかん', scope: 'sentence' });
  assert.equal(s.readings['0:3'], 'としょかん'); assert.equal(s.sentence, SAMPLE);
  assert.equal(s.sentenceAudioStale, true); assert.equal(s.stage, 'target');
  assert.equal(s.translation, original.translation); assert.equal(M.canSend(s), false);
  assert.equal(M.canContinue(s), true); assert.equal(original.readings['0:3'], undefined);
  s = act(s, 'CONTINUE'); const current = s.generation;
  assert.equal(s.status, 'generating');
  assert.deepEqual(act(s, 'GENERATION_COMPLETE', { generation: original.generation }), s);
  s = act(s, 'GENERATION_COMPLETE', { generation: current });
  assert.equal(s.sentenceAudioStale, false); assert.equal(s.readings['0:3'], 'としょかん');
  assert.equal(M.canSend(s), true);
  const repeated = review('本と本。');
  const changed = act(repeated, 'SET_READING', { start: 2, end: 3, reading: 'もと', scope: 'sentence' });
  assert.deepEqual(changed.readings, { '2:3': 'もと' }); assert.equal(changed.sentence, '本と本。');
  assert.deepEqual(act(s, 'SET_READING', { start: 0, end: 3, reading: '<ruby>bad</ruby>', scope: 'sentence' }), s);
  assert.deepEqual(act(s, 'SET_READING', { start: -1, end: 3, reading: 'ほん', scope: 'sentence' }), s);
  const cleared = act(s, 'SET_READING', { start: 0, end: 3, reading: '', scope: 'sentence' });
  assert.equal(cleared.readings['0:3'], undefined); assert.equal(cleared.sentenceAudioStale, true);
});

test('word reading corrections re-resolve identity or clear it, never invent a pronunciation', () => {
  let s = review();
  s = act(s, 'SET_READING', { ...s.selection, reading: 'もと', scope: 'word' });
  assert.equal(s.entryId, null); assert.equal(M.selectedEntry(s), null);
  assert.deepEqual(s.selection, span(SAMPLE, '本')); assert.equal(s.definition, '');
  assert.equal(s.stage, 'target'); assert.equal(s.status, 'unknown'); assert.equal(M.canContinue(s), false);
  assert.equal(s.translation, 'I read a book at the library.');
  s = act(s, 'CHOOSE_ENTRY', { id: 'book' });
  s = act(s, 'SET_READING', { ...s.selection, reading: 'ほん', scope: 'word' });
  assert.equal(s.entryId, 'book'); assert.equal(M.selectedEntry(s).reading, 'ほん');
  assert.equal(M.canContinue(s), true);
  let counter = act(target(), 'CHOOSE_ENTRY', { id: 'counter' });
  counter = act(counter, 'SET_READING', { ...counter.selection, reading: 'ほん', scope: 'word' });
  assert.equal(counter.entryId, 'counter'); assert.equal(counter.status, 'missing');
  const wrongOccurrence = act(s, 'SET_READING', { start: 0, end: 3, reading: 'ほん', scope: 'word' });
  assert.deepEqual(wrongOccurrence, s);
});

test('pitch alternatives are listed numeric fixture values for one reading, not arbitrary accents', () => {
  let s = M.create('pitch', { mode: 'type', stage: 'target', sentence: 'この本は丈夫です。' });
  s = selected(s, '丈夫'); const entry = M.selectedEntry(s);
  assert.equal(entry.word, '丈夫'); assert.equal(entry.reading, 'じょうぶ');
  assert.deepEqual(entry.pitches, [0, 1]);
  s = act(s, 'SET_TRANSLATION', { text: 'This book is sturdy.' });
  s = act(s, 'CONTINUE'); s = act(s, 'GENERATION_COMPLETE', { generation: s.generation });
  const original = freezeDeep(s);
  s = act(s, 'SET_PITCH', { index: 1 });
  assert.equal(s.pitchIndex, 1); assert.equal(s.stage, 'review');
  assert.equal(s.entryId, original.entryId); assert.equal(s.translation, original.translation);
  assert.equal(s.sentenceAudioStale, false); assert.equal(M.canSend(s), true);
  for (const index of [-1, 2, 20, 0.5, '1']) assert.deepEqual(act(s, 'SET_PITCH', { index }), s);
  s = act(s, 'SET_PITCH', { index: 0 });
  assert.equal(s.pitchIndex, 0); assert.equal(M.selectedEntry(s).pitches[s.pitchIndex], 0);
  assert.equal(M.canSend(s), true, 'zero is a valid heiban fixture');
  assert.equal(original.pitchIndex, 0);
});

test('controlled seeds cover declared UI states without simulating real service acceptance', () => {
  const names = ['type-empty', 'type', 'regions', 'entries', 'generating', 'failed', 'missing', 'counter', 'uncertain', 'readings', 'span'];
  for (const name of names) {
    const a = M.seed(name); const b = M.seed(name, 'other');
    assert.equal(a.id, 'edge'); assert.equal(b.id, 'other');
    assert.deepEqual(a, { ...b, id: 'edge', snapshot: b.snapshot && { ...b.snapshot, id: 'edge' } }, name);
    const isolated = act(a, 'SET_NOTES', { text: 'independent' });
    assert.notEqual(isolated, a); assert.notEqual(M.seed(name).notes, 'independent');
  }
  assert.equal(M.seed('type-empty').sentence, ''); assert.equal(M.seed('type-empty').stage, 'input');
  assert.equal(M.seed('type-empty').mode, 'type'); assert.equal(M.seed('type-empty').hasPhoto, false);
  assert.equal(M.seed('type').sentence, SAMPLE); assert.equal(M.seed('type').origin, 'type');
  assert.equal(M.seed('regions').captured, true); assert.equal(M.seed('regions').stage, 'input');
  assert.equal(M.seed('entries').entryId, 'book'); assert.equal(M.seed('entries').stage, 'target');
  assert.equal(M.seed('generating').status, 'generating'); assert.equal(M.seed('generating').stage, 'preparing');
  assert.equal(M.seed('failed').status, 'failed'); assert.equal(M.seed('failed').stage, 'preparing');
  assert.equal(M.seed('counter').entryId, 'counter'); assert.equal(M.seed('counter').status, 'missing');
  assert.equal(M.seed('uncertain').submission, 'uncertain'); assert.ok(Object.isFrozen(M.seed('uncertain').snapshot));
  assert.equal(M.selectedEntry(M.seed('readings')).word, '丈夫'); assert.equal(M.seed('readings').stage, 'review');
  assert.equal(M.seed('span').status, 'unknown'); assert.equal(M.selectedText(M.seed('span')), '図書館で本');
  assert.throws(() => M.seed('made-up'), RangeError);
});

test('the second OCR region has genuine selectable fixture entries and contiguous inflection', () => {
  const text = '窓の席に座りました。';
  let s = M.create('region', { mode: 'type', sentence: text, stage: 'target' });
  assert.deepEqual(s.tokens.map(t => t.text), ['窓', 'の', '席', 'に', '座りました', '。']);
  for (const [surface, lemma] of [['窓', '窓'], ['席', '席'], ['座りました', '座る']]) {
    s = selected(s, surface);
    const entry = M.selectedEntry(s);
    assert.equal(entry.word, lemma); assert.match(entry.reading, /^[ぁ-ゖー]+$/u);
    assert.match(entry.katakana, /^[ァ-ヺー]+$/u); assert.equal(M.canContinue(s), true);
    assert.equal(s.sentence, text);
  }
  assert.equal(M.tokenize(SAMPLE).find(t => t.text === '読みました').reading, 'よみました');
  assert.equal(M.tokenize(ALT).find(t => t.text === '読んだ').reading, 'よんだ');
});

test('public state has exactly the agreed serializable fields; entries cannot corrupt other drafts', () => {
  const expected = ['id','stage','status','mode','sentence','tokens','selection','entryId','senseIds','definition','translation','notes','tags','deck','hasPhoto','origin','captured','blockedEntries','generation','submission','snapshot','readings','sentenceAudioStale','pitchIndex'].sort();
  assert.deepEqual(Object.keys(M.create()).sort(), expected);
  for (const s of [M.create(), M.seed('uncertain'), M.seed('missing'), M.seed('readings')]) {
    assert.deepEqual(Object.keys(s).sort(), expected);
    assert.deepEqual(JSON.parse(JSON.stringify(s)), s);
  }
  const entry = M.selectedEntry(target());
  assert.deepEqual(Object.keys(entry).sort(), ['id','word','reading','katakana','kind','senses','pitches','pitchAvailable','recordingAvailable'].sort());
  assert.throws(() => entry.pitches.push(99), TypeError);
  assert.throws(() => { entry.reading = 'invented'; }, TypeError);
  assert.deepEqual(M.selectedEntry(target()).pitches, [1]);
  const input = freezeDeep(M.create('in', { mode: 'type', sentence: SAMPLE }));
  const event = freezeDeep({ type: 'PREPARE' });
  const next = M.reduce(input, event);
  assert.notEqual(next.tokens, input.tokens); assert.equal(input.stage, 'input'); assert.equal(next.stage, 'target');
});

test('confirmed completion preserves destination, input mode, and last-sent tags, never source content', () => {
  let s = act(review(), 'SET_DECK', { name: 'Mining::Books' });
  s = act(s, 'SET_TAGS', { text: 'books JLPT::N5' });
  s = act(s, 'SET_NOTES', { text: 'do not carry this' });
  s = act(act(s, 'SEND'), 'SEND_COMPLETE');
  assert.equal(s.mode, 'type'); assert.equal(s.origin, 'type'); assert.equal(s.deck, 'Mining::Books');
  assert.equal(s.tags, 'books JLPT::N5');
  assert.equal(s.sentence, ''); assert.equal(s.selection, null); assert.equal(s.hasPhoto, false);
  assert.equal(s.notes, ''); assert.equal(s.translation, ''); assert.equal(s.definition, '');
  const discarded = act(act(s, 'SET_TAGS', { text: 'unsent' }), 'DISCARD');
  assert.equal(discarded.deck, 'Mining::Books'); assert.equal(discarded.mode, 'type');
  assert.equal(discarded.tags, '');
});

test('completion never invents a translation for text outside controlled fixtures', () => {
  for (const [sentence, expected] of [[ALT, 'I read a book at the library.'], ['窓の席に座りました。', 'I sat in the seat by the window.']]) {
    const selectedWord = sentence === ALT ? '本' : '窓';
    let s = M.create('fixture', { mode: 'type', sentence, stage: 'target' });
    s = selected(s, selectedWord); s = act(s, 'CONTINUE');
    s = act(s, 'GENERATION_COMPLETE', { generation: s.generation });
    assert.equal(s.translation, expected); assert.equal(M.canSend(s), true);
  }
  let s = act(target('猫と本。'), 'CONTINUE');
  s = act(s, 'GENERATION_COMPLETE', { generation: s.generation });
  assert.equal(s.translation, ''); assert.equal(M.canSend(s), false);
  assert.notEqual(s.status, 'ready', 'outside-fixture content cannot appear complete');
});

test('send validates selected senses and pitch bounds independently of display status', () => {
  const s = review();
  assert.equal(M.canSend({ ...s, senseIds: [] }), false);
  assert.equal(M.canSend({ ...s, pitchIndex: 99 }), false);
  assert.equal(M.canSend({ ...s, senseIds: [99] }), false);
  assert.equal(M.canSend({ ...s, deck: '' }), false);
  assert.equal(M.canContinue({ ...target(), pitchIndex: 99 }), false);
});

test('recording availability is independently enforced before any generation request', () => {
  // Exercise the same pure model with one controlled source-data flag changed
  // in memory; neither the model file nor the legacy source is modified.
  const source = fs.readFileSync(modelPath, 'utf8');
  const sandbox = { Intl }; vm.createContext(sandbox);
  vm.runInContext(source.replace('recordingAvailable: true', 'recordingAvailable: false'), sandbox);
  const fixture = sandbox.FlowModel;
  let s = fixture.create('missing-recording', { mode: 'type', sentence: SAMPLE, stage: 'target' });
  s = fixture.reduce(s, { type: 'SELECT_SPAN', ...span(SAMPLE, '本') });
  assert.equal(fixture.selectedEntry(s).pitchAvailable, true);
  assert.equal(fixture.selectedEntry(s).recordingAvailable, false);
  assert.equal(s.status, 'missing'); assert.ok(s.blockedEntries.includes('book'));
  assert.equal(fixture.canContinue(s), false);
  assert.equal(fixture.reduce(s, { type: 'CONTINUE' }).generation, 0);
  assert.equal(fixture.canSend({ ...s, stage: 'review', status: 'ready', translation: 'fixture', sentenceAudioStale: false }), false);
});

test('no-Intl fallback retains every code point and valid UTF-16 selection offsets', () => {
  const sandbox = { Intl: undefined }; vm.createContext(sandbox);
  vm.runInContext(fs.readFileSync(modelPath, 'utf8'), sandbox);
  const fixture = sandbox.FlowModel; const text = '📚未知\n本。';
  const tokens = fixture.tokenize(text);
  assert.equal(tokens.map(t => t.text).join(''), text);
  assert.equal(tokens[0].end, 2);
  let s = fixture.create('fallback', { mode: 'type', stage: 'target', sentence: text });
  s = fixture.reduce(s, { type: 'SELECT_SPAN', ...span(text, '本') });
  assert.equal(fixture.selectedEntry(s).word, '本');
  assert.equal(fixture.selectedText(s), '本');
});

test('retry cannot bypass required-image or preparation completeness guards', () => {
  const failed = M.seed('failed');
  for (const change of [{ hasPhoto: false }, { senseIds: [] }, { pitchIndex: 9 }, { definition: '' }]) {
    const incomplete = { ...failed, ...change };
    assert.deepEqual(act(incomplete, 'RETRY'), incomplete, JSON.stringify(change));
  }
});
