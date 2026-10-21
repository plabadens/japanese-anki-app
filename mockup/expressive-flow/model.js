/* Controlled design-study fixtures only; no provider, audio, or Anki calls. */
(function (root, factory) {
  'use strict';
  const model = factory();
  if (typeof module === 'object' && module.exports) module.exports = model;
  if (root) root.FlowModel = model;
})(typeof globalThis === 'object' ? globalThis : this, function () {
  'use strict';
  const SAMPLE = '図書館で本を読みました。';
  const translations = { [SAMPLE]: 'I read a book at the library.', '図書館で本を読んだ。': 'I read a book at the library.',
    '窓の席に座りました。': 'I sat in the seat by the window.', 'この本は丈夫です。': 'This book is sturdy.' };
  function deepFreeze(value) {
    if (value && typeof value === 'object' && !Object.isFrozen(value)) {
      Object.values(value).forEach(deepFreeze);
      Object.freeze(value);
    }
    return value;
  }
  const BOOK = deepFreeze({ id: 'book', word: '本', reading: 'ほん', katakana: 'ホン', kind: 'noun',
    senses: ['book; volume', 'script; scenario'], pitches: [1], pitchAvailable: true, recordingAvailable: true });
  const READ = deepFreeze({ id: 'read', word: '読む', reading: 'よむ', katakana: 'ヨム', kind: 'verb',
    senses: ['to read'], pitches: [1], pitchAvailable: true, recordingAvailable: true });
  const COUNTER = deepFreeze({ id: 'counter', word: '本', reading: 'ほん', katakana: 'ホン', kind: 'counter',
    senses: ['counter for long, cylindrical things'], pitches: [], pitchAvailable: false, recordingAvailable: true });
  const LIBRARY = deepFreeze({ id: 'library', word: '図書館', reading: 'としょかん', katakana: 'トショカン', kind: 'noun',
    senses: ['library'], pitches: [2], pitchAvailable: true, recordingAvailable: true });
  // Patterns and availability are illustrative fixture data, not source claims.
  const STURDY = deepFreeze({ id: 'sturdy', word: '丈夫', reading: 'じょうぶ', katakana: 'ジョウブ', kind: 'na-adjective',
    senses: ['sturdy; durable'], pitches: [0, 1], pitchAvailable: true, recordingAvailable: true });
  const WINDOW = deepFreeze({ id: 'window', word: '窓', reading: 'まど', katakana: 'マド', kind: 'noun',
    senses: ['window'], pitches: [1], pitchAvailable: true, recordingAvailable: true });
  const SEAT = deepFreeze({ id: 'seat', word: '席', reading: 'せき', katakana: 'セキ', kind: 'noun',
    senses: ['seat'], pitches: [1], pitchAvailable: true, recordingAvailable: true });
  const SIT = deepFreeze({ id: 'sit', word: '座る', reading: 'すわる', katakana: 'スワル', kind: 'verb',
    senses: ['to sit'], pitches: [0], pitchAvailable: true, recordingAvailable: true });
  const surfaces = ['図書館', '読みました', '読んだ', '読む', '座りました', '座った', '座る', '丈夫', '窓', '席', '本', 'で', 'を', 'の', 'に', 'は', '。'];
  const tokenReadings = { '図書館': 'としょかん', '本': 'ほん', '読む': 'よむ', '読みました': 'よみました', '読んだ': 'よんだ',
    '窓': 'まど', '席': 'せき', '座る': 'すわる', '座りました': 'すわりました', '座った': 'すわった', '丈夫': 'じょうぶ' };
  const graphemes = typeof Intl === 'object' && Intl.Segmenter ? new Intl.Segmenter('ja', { granularity: 'grapheme' }) : null;
  function tokenize(sentence) {
    const text = String(sentence);
    const fallback = new Map(graphemes ? [...graphemes.segment(text)].map(p => [p.index, p.segment]) : []);
    const tokens = [];
    let start = 0;
    while (start < text.length) {
      const word = surfaces.find(word => text.startsWith(word, start)) || fallback.get(start) || String.fromCodePoint(text.codePointAt(start));
      const end = start + word.length;
      const token = { start, end, text: word };
      if (Object.hasOwn(tokenReadings, word)) token.reading = tokenReadings[word];
      tokens.push(token); start = end;
    }
    return tokens;
  }
  function create(id = 'live', options = {}) {
    const sentence = options.sentence || '';
    return { id, stage: options.stage || 'input', status: 'ready', mode: options.mode || 'photo',
      sentence, tokens: tokenize(sentence), selection: null, entryId: null, senseIds: [], definition: '',
      translation: '', notes: '', tags: '', deck: 'Japanese', hasPhoto: !!options.hasPhoto,
      origin: options.mode === 'type' ? 'type' : 'photo', captured: false, blockedEntries: [],
      generation: 0, submission: null, snapshot: null, readings: {}, sentenceAudioStale: true, pitchIndex: 0 };
  }
  function copy(s) {
    return { ...s, tokens: s.tokens.map(t => ({ ...t })), selection: s.selection && { ...s.selection },
      senseIds: [...s.senseIds], blockedEntries: [...s.blockedEntries], readings: { ...s.readings } };
  }
  function validSpan(text, start, end) {
    if (!Number.isInteger(start) || !Number.isInteger(end) || start < 0 || end > text.length || start >= end) return false;
    const boundaries = new Set([0, text.length]);
    if (graphemes) for (const p of graphemes.segment(text)) boundaries.add(p.index);
    else { let i = 0; for (const char of text) { boundaries.add(i); i += char.length; } }
    return boundaries.has(start) && boundaries.has(end);
  }
  function selectedText(s) { return s.selection && validSpan(s.sentence, s.selection.start, s.selection.end) ? s.sentence.slice(s.selection.start, s.selection.end) : ''; }
  function entriesFor(s) {
    const text = selectedText(s);
    if (text === '本') return [BOOK, COUNTER];
    if (text === '図書館') return [LIBRARY];
    if (text === '丈夫') return [STURDY];
    if (text === '窓') return [WINDOW];
    if (text === '席') return [SEAT];
    if (['座る', '座りました', '座った'].includes(text)) return [SIT];
    return ['読む', '読みました', '読んだ'].includes(text) ? [READ] : [];
  }
  function selectedEntry(s) { return entriesFor(s).find(entry => entry.id === s.entryId) || null; }
  function available(s, entry = selectedEntry(s)) {
    return !!entry && entry.pitchAvailable && entry.recordingAvailable && entry.pitches.length > 0 && !s.blockedEntries.includes(entry.id);
  }
  function choose(s, entry) {
    s.entryId = entry ? entry.id : null; s.senseIds = entry ? [0] : [];
    s.definition = entry ? entry.senses[0] : ''; s.pitchIndex = 0; s.stage = 'target';
    if (entry && !available(s, entry)) {
      if (!s.blockedEntries.includes(entry.id)) s.blockedEntries.push(entry.id);
      s.status = 'missing';
    } else s.status = entry ? 'ready' : 'unknown';
  }
  function targetStatus(s) { return s.selection ? (selectedEntry(s) ? (available(s) ? 'ready' : 'missing') : 'unknown') : 'ready'; }
  function startGeneration(s) { s.stage = 'preparing'; s.status = 'generating'; s.generation++; s.sentenceAudioStale = true; }
  function reset(s, confirmed = false) {
    const blank = create(s.id, { mode: s.mode });
    blank.deck = s.deck; blank.generation = s.generation + 1;
    if (confirmed) blank.tags = s.snapshot.tags;
    return blank;
  }
  function prepared(s) {
    const entry = selectedEntry(s);
    return available(s, entry) && s.senseIds.length > 0 && s.senseIds.every(index => Number.isInteger(index) && index >= 0 && index < entry.senses.length) &&
      Number.isInteger(s.pitchIndex) && s.pitchIndex >= 0 && s.pitchIndex < entry.pitches.length && !!s.definition.trim();
  }
  function canContinue(s) {
    if (s.submission || !s.sentence.trim() || (s.origin === 'photo' && !s.hasPhoto)) return false;
    if (s.stage === 'input') return !!s.sentence.trim() && (s.origin === 'type' || s.hasPhoto);
    return s.stage === 'target' && s.status === 'ready' && prepared(s);
  }
  function canSend(s) {
    return s.stage === 'review' && s.status === 'ready' && !s.submission && (s.origin === 'type' || s.hasPhoto) && prepared(s) && !!s.deck.trim() && !!s.translation.trim() && !s.sentenceAudioStale;
  }
  function seed(name, id = 'edge') {
    if (!['type-empty', 'type', 'regions', 'entries', 'generating', 'failed', 'missing', 'counter', 'uncertain', 'readings', 'span'].includes(name)) throw new RangeError(`Unknown controlled flow fixture: ${name}`);
    if (name === 'type-empty' || name === 'type') return create(id, { mode: 'type', sentence: name === 'type' ? SAMPLE : '' });
    if (name === 'regions') { const s = create(id, { sentence: SAMPLE, hasPhoto: true }); s.captured = true; return s; }
    let s = create(id, { sentence: SAMPLE, stage: 'target', hasPhoto: true });
    s = reduce(s, { type: 'SELECT_SPAN', start: 4, end: 5 });
    if (name === 'missing') { s.blockedEntries.push(s.entryId); s.status = 'missing'; }
    if (name === 'counter') s = reduce(s, { type: 'CHOOSE_ENTRY', id: 'counter' });
    if (name === 'span') s = reduce(s, { type: 'SELECT_SPAN', start: 0, end: 5 });
    if (name === 'readings') {
      s = create(id, { mode: 'type', sentence: 'この本は丈夫です。', stage: 'target' });
      s = reduce(s, { type: 'SELECT_SPAN', start: 4, end: 6 });
      s.translation = 'This book is sturdy.';
    }
    if (['generating', 'failed', 'uncertain', 'readings'].includes(name)) s = reduce(s, { type: 'CONTINUE' });
    if (name === 'failed') s = reduce(s, { type: 'GENERATION_FAIL', generation: s.generation });
    if (name === 'uncertain' || name === 'readings') s = reduce(s, { type: 'GENERATION_COMPLETE', generation: s.generation });
    if (name === 'uncertain') { s = reduce(s, { type: 'SEND' }); s = reduce(s, { type: 'SEND_UNCERTAIN' }); }
    return s;
  }
  function reduce(state, event) {
    const s = copy(state);
    // Submission identity is independent of presentation stage/status. Only a
    // confirmed external completion or explicit local discard releases a draft.
    if (event.type === 'DISCARD') return reset(s);
    if (s.submission) {
      if (event.type === 'SEND_COMPLETE') return reset(s, true);
      if (event.type === 'SEND_UNCERTAIN' && s.submission === 'sending') {
        s.submission = 'uncertain'; s.status = 'uncertain';
      }
      return s;
    }
    switch (event.type) {
      case 'TYPE_MODE':
        s.stage = 'input'; s.mode = 'type'; s.status = 'ready';
        if (!s.captured && !s.sentence) s.origin = 'type';
        break;
      case 'PHOTO_MODE': s.stage = 'input'; s.mode = 'photo'; s.status = 'ready'; break;
      case 'PREPARE':
        if (s.stage === 'input' && canContinue(s)) { s.stage = 'target'; s.status = targetStatus(s); }
        break;
      case 'ATTACH_PHOTO': s.hasPhoto = true; break;
      case 'REMOVE_PHOTO': if (s.origin === 'type') s.hasPhoto = false; break;
      case 'SET_DECK': if (typeof event.name === 'string' && event.name.trim()) s.deck = event.name; break;
      case 'SET_DEFINITION': if (typeof event.text === 'string' && selectedEntry(s)) s.definition = event.text; break;
      case 'SET_TRANSLATION': if (typeof event.text === 'string') s.translation = event.text; break;
      case 'SET_NOTES': if (typeof event.text === 'string') s.notes = event.text; break;
      case 'SET_TAGS': if (typeof event.text === 'string') s.tags = event.text; break;
      case 'SET_READING': {
        if (!validSpan(s.sentence, event.start, event.end) || typeof event.reading !== 'string') break;
        const reading = event.reading.trim();
        if (reading && !/^[\p{Script_Extensions=Hiragana}\p{Script_Extensions=Katakana}ー]+$/u.test(reading)) break;
        if (event.scope === 'sentence') {
          const key = `${event.start}:${event.end}`;
          if (reading) s.readings[key] = reading; else delete s.readings[key];
        } else if (event.scope === 'word') {
          if (!s.selection || s.selection.start !== event.start || s.selection.end !== event.end) break;
          const current = selectedEntry(s);
          const matching = entriesFor(s).filter(entry => entry.reading === reading);
          // A homograph still requires an explicit entry choice; never pick a
          // different meaning merely because it shares the supplied reading.
          const entry = current && current.reading === reading ? current : matching.length === 1 ? matching[0] : null;
          choose(s, entry);
        } else break;
        s.sentenceAudioStale = true; s.stage = 'target'; s.status = targetStatus(s); s.generation++;
        break;
      }
      case 'SET_PITCH': {
        const entry = selectedEntry(s);
        if (available(s, entry) && Number.isInteger(event.index) && event.index >= 0 && event.index < entry.pitches.length) {
          s.pitchIndex = event.index;
          if (s.stage === 'preparing') { s.stage = 'target'; s.status = targetStatus(s); s.generation++; }
        }
        break;
      }
      case 'TOGGLE_SENSE': {
        const entry = selectedEntry(s);
        if (entry && Number.isInteger(event.index) && event.index >= 0 && event.index < entry.senses.length) {
          s.senseIds = s.senseIds.includes(event.index) ? s.senseIds.filter(index => index !== event.index) : [...s.senseIds, event.index].sort((a, b) => a - b);
          s.definition = s.senseIds.map(index => entry.senses[index]).join('; ');
        }
        break;
      }
      case 'SET_SENTENCE':
        if (typeof event.text === 'string' && event.text !== s.sentence) {
          s.sentence = event.text; s.tokens = tokenize(s.sentence); s.selection = null;
          s.entryId = null; s.senseIds = []; s.definition = ''; s.translation = ''; s.readings = {};
          s.sentenceAudioStale = true; s.pitchIndex = 0; s.status = 'ready';
          if (s.stage !== 'input') s.stage = 'target';
        }
        break;
      case 'CAPTURE': s.captured = true; s.hasPhoto = true; break;
      case 'RETAKE': s.captured = false; s.hasPhoto = false; s.stage = 'input'; s.mode = 'photo'; s.status = 'ready'; break;
      case 'USE_PHOTO':
        if (s.captured && s.hasPhoto) {
          if (s.origin === 'photo' && !s.sentence) { s.sentence = SAMPLE; s.tokens = tokenize(s.sentence); }
          if (s.sentence.trim()) { s.stage = 'target'; s.status = targetStatus(s); }
        }
        break;
      case 'SELECT_SPAN': {
        if (!validSpan(s.sentence, event.start, event.end)) break;
        const sameSpan = s.selection && s.selection.start === event.start && s.selection.end === event.end;
        const previousId = sameSpan ? s.entryId : null;
        s.selection = { start: event.start, end: event.end };
        const entries = entriesFor(s);
        // Re-tapping a failed occurrence must not silently choose a valid homograph.
        const entry = entries.find(e => e.id === previousId) || entries.find(e => s.blockedEntries.includes(e.id)) || entries[0];
        choose(s, entry); break;
      }
      case 'CHOOSE_ENTRY': {
        const entry = entriesFor(s).find(e => e.id === event.id);
        if (entry) choose(s, entry);
        break;
      }
      case 'RESELECT':
        s.selection = null; s.entryId = null; s.senseIds = []; s.definition = '';
        s.stage = 'target'; s.status = 'ready'; break;
      case 'CONTINUE':
        if (canContinue(s)) {
          if (s.stage === 'input') s.stage = 'target';
          else startGeneration(s);
        }
        break;
      case 'RETRY':
        if (s.stage === 'preparing' && s.status === 'failed' && canContinue({ ...s, stage: 'target', status: 'ready' })) startGeneration(s);
        break;
      case 'GENERATION_FAIL':
        if (s.stage === 'preparing' && s.status === 'generating' && event.generation === s.generation) s.status = 'failed';
        break;
      case 'CANCEL_GENERATION':
        if (s.stage === 'preparing') { s.stage = 'target'; s.status = targetStatus(s); s.generation++; }
        break;
      case 'BACK':
        if (s.stage === 'preparing' || s.stage === 'review') { s.stage = 'target'; s.status = targetStatus(s); s.generation++; }
        else if (s.stage === 'target') { s.stage = 'input'; s.status = 'ready'; }
        break;
      case 'GENERATION_COMPLETE':
        if (s.stage === 'preparing' && s.status === 'generating' && event.generation === s.generation) {
          if (!s.translation.trim()) s.translation = Object.hasOwn(translations, s.sentence) ? translations[s.sentence] : '';
          if (prepared(s) && s.translation.trim()) { s.stage = 'review'; s.status = 'ready'; s.sentenceAudioStale = false; }
          else s.status = 'failed';
        }
        break;
      case 'SEND':
        if (canSend(s)) {
          const entry = selectedEntry(s);
          s.snapshot = deepFreeze({ ...copy(s), snapshot: null, word: entry.word, reading: entry.reading });
          s.submission = 'sending'; s.status = 'sending';
        }
        break;

    }
    return s;
  }
  return Object.freeze({ create, seed, tokenize, reduce, selectedEntry, selectedText, canContinue, canSend, entriesFor });
});
