/* Native UI composition study. Device services are deliberately not connected. */
(() => {
  'use strict';
  const M = window.FlowModel;
  const SAMPLE = '図書館で本を読みました。';
  const iconPaths = {
    back:'<path d="m14 6-6 6 6 6M8 12h13"/>',close:'<path d="m6 6 12 12M6 18 18 6"/>',
    arrow:'<path d="M3 12h17m-6-6 6 6-6 6"/>',
    settings:'<path d="m9 3-1 3-3 1-2 3 2 2-1 3 2 3 3-1 2 2 3-1 1-3 3-1 2-3-2-2 1-3-2-3-3 1-2-2Z"/><circle cx="11.5" cy="10.5" r="3"/>',
    camera:'<path d="M3 7h4l2-3h6l2 3h4v13H3Z"/><circle cx="12" cy="13" r="4"/>',
    type:'<path d="M4 5h16M12 5v15M8 20h8"/>',
    image:'<rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8" cy="8" r="1.5"/><path d="m3 17 6-6 4 4 3-3 5 5"/>',
    flash:'<path d="m13 2-8 12h6l-1 8 9-13h-7Z"/>',check:'<path d="m5 12 4 4L19 6"/>',
    edit:'<path d="m15 4 5 5M4 20l5-1L21 7l-5-5L4 14Z"/>',
    play:'<path d="m8 4 12 8-12 8Z"/>',plus:'<path d="M12 5v14M5 12h14"/>',
    more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    expand:'<path d="M4 9V4h5m6 0h5v5M4 15v5h5m6 0h5v-5"/>',
    chevron:'<path d="m9 5 7 7-7 7"/>',down:'<path d="m6 9 6 6 6-6"/>',
    moon:'<path d="M20 14A8.5 8.5 0 0 1 10 4a8.5 8.5 0 1 0 10 10Z"/>',
    sun:'<circle cx="12" cy="12" r="4"/><path d="M12 1v2m0 18v2M1 12h2m18 0h2M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2"/>',
    warning:'<path d="m12 3 10 18H2Z"/><path d="M12 9v5m0 3v.2"/>',
    info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.2"/>',
    error:'<circle cx="12" cy="12" r="9"/><path d="m8 8 8 8m-8 0 8-8"/>',
    refresh:'<path d="M20 6v5h-5M20 11a8 8 0 1 0-1 7"/>',
    wifi:'<path d="M2 8c6-5 14-5 20 0M5 12c4-3 10-3 14 0M9 16c2-1 4-1 6 0M12 20h.01"/>',
    battery:'<rect x="2" y="6" width="18" height="12" rx="2"/><path d="M22 10v4M5 9h12v6H5Z"/>',
    signal:'<path d="M4 18v2M9 13v7M14 8v12M19 3v17"/>'
  };
  const icon = name => `<svg class="icon-svg" viewBox="0 0 24 24" aria-hidden="true">${iconPaths[name] || iconPaths.more}</svg>`;
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const phones = {};
  const local = {};
  let view = 'overview', theme = 'light', scale = 1, scenario = 'type-empty';
  let photoVariant = 'portrait';
  const photoSrc = () => photoVariant === 'landscape' ? STUDY_LANDSCAPE_PHOTO : STUDY_PHOTO;
  let pillDrag = null, suppressPillClick = false;
  const scenarios = [
    ['type-empty','Empty Type'],['type','Type → Review'],['regions','OCR excerpts'],
    ['entries','Dictionary entries'],['generating','Preparing'],['failed','Network failure'],
    ['missing','Pitch unavailable'],['counter','Counter entry'],['span','Span selection'],
    ['readings','Reading & pitch'],['uncertain','Send status unknown']
  ];

  function ui(id) {
    if (!local[id]) local[id] = {overlay:null, editing:false, toast:'', error:'', returnFocus:null, buffer:{}};
    return local[id];
  }
  function selected(s) { return M.selectedEntry(s); }
  function chooseBook(s) {
    const start = s.sentence.indexOf('本');
    s = M.reduce(s, {type:'SELECT_SPAN', start, end:start + 1});
    const entry = M.entriesFor(s).find(e => /book/.test(e.senses.join(' ')));
    if (entry) s = M.reduce(s, {type:'CHOOSE_ENTRY', id:entry.id});
    return s;
  }
  function overviewStates() {
    phones.input = M.create('input', {sentence:'', mode:'photo'});
    phones.target = chooseBook(M.create('target', {sentence:SAMPLE,stage:'target',hasPhoto:true}));
    phones.review = M.reduce(phones.target, {type:'CONTINUE'});
    phones.review = M.reduce(phones.review, {type:'GENERATION_COMPLETE',generation:phones.review.generation});
    phones.review = {...phones.review,id:'review'};
    ['input','target','review'].forEach(id => {local[id] = {overlay:null,editing:false,toast:'',error:'',returnFocus:null,buffer:{}};});
  }
  function shell(id) { return `<article class="phone" data-phone="${id}" data-theme="${theme}" aria-label="${id === 'live' ? 'Interactive mining flow' : id + ' view'}"></article>`; }
  function stageTitle(s) {
    if (s.submission === 'uncertain' || s.status === 'uncertain') return 'Send status unknown';
    if (s.stage === 'preparing') return s.status === 'failed' ? 'Couldn’t finish' : 'Preparing';
    return {input:'Input',target:'Target',review:'Review'}[s.stage] || 'Target';
  }
  function toolbar(s) {
    const step=s.stage==='review'||s.submission==='uncertain'?3:s.stage==='input'?1:2;
    const progress=`<div class="flow-progress" role="progressbar" aria-label="Mining flow" aria-valuemin="1" aria-valuemax="3" aria-valuenow="${step}" aria-valuetext="${stageTitle(s)}, step ${step} of 3">${[1,2,3].map(n=>`<span aria-hidden="true" data-state="${n<step?'completed':n===step?'current':'upcoming'}"></span>`).join('')}</div>`;
    return `<div class="statusbar" aria-hidden="true"><span>9:41</span><span class="status-icons">${icon('signal')}${icon('wifi')}${icon('battery')}</span></div>
      <div class="toolbar"><button class="icon-button" data-action="back" aria-label="${s.stage === 'input' ? 'Close input' : 'Back'}">${icon(s.stage === 'input' ? 'close' : 'back')}</button>
      <h2>${stageTitle(s)}</h2>${s.stage === 'input' ? `<button class="icon-button" data-action="settings" aria-label="Settings">${icon('settings')}</button>` : ''}</div>${progress}`;
  }
  function input(s) {
    if (s.mode === 'type') {
      return `<div class="main type-main"><textarea class="writing" lang="ja" data-input="sentence" aria-label="Japanese sentence" placeholder="日本語…">${escape(s.sentence)}</textarea>
        <div class="type-actions"><button class="text-button" data-action="photo-mode">${icon('camera')}Photo</button>
        <button class="text-button" data-action="attach">${icon('image')}${s.hasPhoto ? 'Photo attached' : 'Add photo'}</button></div></div>
        <div class="footer-actions"><button class="primary" data-action="prepare" ${s.sentence.trim() ? '' : 'disabled'}>Continue ${icon('arrow')}</button></div>`;
    }
    return `<div class="main camera-main"><div class="camera-stage"><div class="viewfinder"><img src="${photoSrc()}" alt="Japanese textbook page on a wooden table">
      ${s.captured ? '' : `<div class="focus-frame"></div><button class="icon-button flash" data-action="flash" aria-label="Flash off">${icon('flash')}</button>`}</div></div>
      ${s.captured ? `<div class="photo-confirm"><button class="text-button" data-action="retake">Retake</button><button class="primary" data-action="use-photo">Use photo ${icon('arrow')}</button></div>` :
      `<div class="shutter-row"><div class="camera-side"><button class="icon-button" data-action="gallery" aria-label="Choose a photo">${icon('image')}</button></div>
      <button class="shutter" data-action="capture" aria-label="Take photo"><span></span></button>
      <div class="camera-side"><button class="icon-button" data-action="type-mode" aria-label="Type a sentence">${icon('type')}</button><small>Type</small></div></div>`}</div>`;
  }
  function sentenceMarkup(s, range = s.selection) {
    const result = [];
    const boundaries = new Set([0,s.sentence.length]);
    for (const token of s.tokens) {boundaries.add(token.start);boundaries.add(token.end);}
    if (range) {boundaries.add(range.start);boundaries.add(range.end);}
    const sorted = [...boundaries].sort((a,b) => a-b);
    for (let i=0;i<sorted.length-1;i++) {
      const start=sorted[i],end=sorted[i+1];
      if (range && start === range.start) result.push('<mark data-selected-occurrence>');
      const token=s.tokens.find(t => start>=t.start && start<t.end);
      const pressed=Boolean(range&&start>=range.start&&end<=range.end);
      result.push(`<button class="word-pill" type="button" aria-pressed="${pressed}" data-token-start="${token?.start ?? start}" data-token-end="${token?.end ?? end}" lang="ja">${escape(s.sentence.slice(start,end))}</button>`);
      if (range && end === range.end) result.push('</mark>');
    }
    return result.join('');
  }
  function sentenceChooser(s) {
    return `<div class="sentence-box" data-selection-box><div class="sentence-run" lang="ja" tabindex="0" role="group" aria-label="Japanese word pills. Tap a word to select. Drag across words or use Shift plus arrows to extend a span." data-sentence-run>${sentenceMarkup(s)}</div></div>`;
  }
  function target(s) {
    const entry=selected(s), entries=M.entriesFor(s);
    const dataMiss=s.status === 'missing';
    let body=sentenceChooser(s);
    body += `<div class="sentence-controls"><button class="text-button" data-action="edit-sentence">${icon('edit')}Edit sentence</button></div>`;
    if (entry) {
      body += `<div class="entry-line"><p class="entry-name" lang="ja">${escape(entry.word)}<small>${escape(entry.reading)}</small></p>
        ${entries.length>1 ? `<button class="text-button" data-action="entries">${entries.length} entries ${icon('down')}</button>` : ''}</div><p class="section-label">Meaning</p>
        <div role="group" aria-label="Meanings">${entry.senses.map((meaning,index) => `<button class="sense-choice" role="checkbox" aria-checked="${s.senseIds.includes(index)}" data-sense="${index}"><span class="check-square">${s.senseIds.includes(index)?icon('check'):''}</span><span>${escape(meaning)}</span></button>`).join('')}</div>`;
    } else if (s.selection && entries.length > 1) {
      body += `<button class="text-button" data-action="entries">Choose a dictionary entry ${icon('chevron')}</button>`;
    } else if (s.selection) {
      body += `<p class="inline-message" data-no-entry>This sample has no entry for <span lang="ja">「${escape(M.selectedText(s))}」</span>. Tap another word.</p>`;
    }
    if (dataMiss) body += `<div class="data-message" role="status"><strong>${icon('info')}Pronunciation data unavailable</strong>${escape(entry?.reading || M.selectedText(s))} has no matching ${entry?.pitchAvailable === false ? 'pitch pattern' : 'word recording'} in this sample.
      <div><button class="text-button" data-action="reselect">Choose another word</button>${entries.length>1?'<button class="text-button" data-action="entries">Change entry</button>':''}</div></div>`;
    const reason = !s.selection ? 'Tap a word to continue.' : !entry ? 'Choose a supported word and dictionary entry.' : !s.senseIds.length ? 'Choose a meaning to continue.' : dataMiss ? 'Choose another word or entry.' : '';
    return `<div class="main"><div class="padded target-content">${body}</div></div><div class="footer-actions">${!M.canContinue(s) ? `<p class="reason" id="${s.id}-continue-reason">${reason}</p>` : ''}
      <button class="primary" data-action="continue" ${M.canContinue(s)?'':'disabled'} ${M.canContinue(s)?'':`aria-describedby="${s.id}-continue-reason"`}>Continue ${icon('arrow')}</button></div>`;
  }
  const readingDefaults = {'図書館':'としょかん','本':'ほん','読':'よ','窓':'まど','席':'せき','座':'すわ','丈夫':'じょうぶ'};
  function readingSpans(s) {
    return [...s.sentence.matchAll(/図書館|丈夫|本|読|窓|席|座/gu)].map(match => ({start:match.index,end:match.index+match[0].length,text:match[0],reading:s.readings[`${match.index}:${match.index+match[0].length}`] || readingDefaults[match[0]]}));
  }
  function rubyPart(s,start,end) {
    let cursor=start,html='';
    for (const span of readingSpans(s).filter(r=>r.start>=start && r.end<=end)) {
      html += escape(s.sentence.slice(cursor,span.start));
      html += `<ruby>${escape(span.text)}<rt>${escape(span.reading)}</rt></ruby>`;
      cursor=span.end;
    }
    return html+escape(s.sentence.slice(cursor,end));
  }
  function reviewSentence(s) {
    if (!s.selection) return rubyPart(s,0,s.sentence.length);
    const {start,end}=s.selection;
    return rubyPart(s,0,start)+`<mark data-selected-occurrence>${rubyPart(s,start,end)}</mark>`+rubyPart(s,end,s.sentence.length);
  }
  function katakana(text) {return [...text].map(c=>{const n=c.charCodeAt(0);return n>=0x3041&&n<=0x3096?String.fromCharCode(n+0x60):c;}).join('');}
  function moras(text) {
    const result=[];
    for (const c of katakana(text)) {
      if (/[ャュョァィゥェォヮ]/u.test(c) && result.length) result[result.length-1]+=c;
      else result.push(c);
    }
    return result;
  }
  function pitchHtml(entry,index=0) {
    if (!entry) return '';
    const kana=moras(entry.katakana||entry.reading), accent=entry.pitches[index]??entry.pitches[0];
    const labels=[...kana,'（ヲ）'], n=labels.length, width=16+n*56, height=96;
    const xs=labels.map((_,i)=>32+i*56);
    const ys=labels.map((_,i)=>accent===1?(i===0?20:48):i===0?48:(accent===0||i<accent?20:48));
    let path=`M${xs[0]} ${ys[0]}`;
    for(let i=1;i<xs.length;i++)path+=`L${xs[i]} ${ys[i]}`;
    const aria=`${entry.word}, ${katakana(entry.reading)}. ${accent===0?'No pitch drop.':`Pitch drops after mora ${accent}.`} Following particle shown in parentheses.`;
    return `<div class="pitch-scroll" lang="ja" role="img" aria-label="${escape(aria)}"><svg class="pitch-svg" data-pitch-katakana viewBox="0 0 ${width} ${height}" style="width:${width*scale}px;height:${height*scale}px" aria-hidden="true">
      <path d="${path}" fill="none" stroke="currentColor" stroke-width="2"/>
      ${xs.map((x,i)=>`<circle cx="${x}" cy="${ys[i]}" r="4" fill="${i===n-1?'var(--surface-container-low)':'currentColor'}" stroke="currentColor" stroke-width="2"/><text x="${x}" y="88" font-size="22" text-anchor="middle" class="${i===n-1?'particle':''}">${labels[i]}</text>`).join('')}</svg></div>`;
  }
  function editAttrs(kind,enabled) {return enabled ? `data-edit="${kind}" tabindex="0" role="button" aria-label="Edit ${kind}"` : '';}
  function reviewContent(s,readonly=false) {
    const u=ui(s.id),editing=u.editing&&!readonly,entry=selected(s);
    return `<div class="review-content ${editing?'editing':''}">${editing?'<p class="edit-callout">Tap the content you want to correct.</p>':''}
      ${s.hasPhoto ? `<button class="context-strip" data-action="${editing?'photo-edit':'view-photo'}" aria-label="${editing?'Change photo':'View full photo'}"><img src="${photoSrc()}" alt="Japanese textbook page on a wooden table"><span class="photo-indicator">${icon('expand')}</span></button>` : ''}
      <div class="sentence-audio"><div class="read-sentence" lang="ja" ${editAttrs('sentence',editing)}>${reviewSentence(s)}</div>
        <button class="icon-button audio-play" data-action="sentence-audio" aria-label="Play sentence">${icon('play')}</button></div>
      <p class="translation" ${editAttrs('translation',editing)}>${escape(s.translation)}</p>
      <section class="word-hero" aria-label="Vocabulary"><div class="word-header"><strong class="word-title" lang="ja" ${editAttrs('pronunciation',editing)}>${escape(entry?.word||M.selectedText(s))}</strong>
        <button class="icon-button audio-play" data-action="word-audio" aria-label="Play word">${icon('play')}</button></div>
        <div ${editAttrs('pitch',editing)}>${pitchHtml(entry,s.pitchIndex)}</div><p class="definition" ${editAttrs('definition',editing)}>${escape(s.definition)}</p></section>
      ${s.notes?`<p class="notes-preview">${escape(s.notes)}</p>`:''}${s.tags?`<div class="tags-preview">${s.tags.split(/\s+/).filter(Boolean).map(t=>`<span>${escape(t)}</span>`).join('')}</div>`:''}</div>`;
  }
  function review(s) {
    const locked=s.submission==='uncertain'||s.status==='uncertain';
    return `<div class="main">${locked ? `<div class="padded" style="padding-bottom:0"><div class="uncertain-banner" role="status"><strong>${icon('warning')}The note may already exist.</strong>Check AnkiDroid before starting again. This submitted note stays read-only.</div></div>` : ''}${reviewContent(s,locked)}</div>
      <div class="footer-actions">${locked ? `<p class="section-label">Deck · ${escape(s.deck||'Japanese')}</p><button class="primary" data-action="recheck">Recheck status ${icon('refresh')}</button><div class="failure-secondary"><button class="text-button" data-action="open-anki">Open AnkiDroid</button><button class="text-button" data-action="discard">Discard local draft</button></div>` :
      `<div class="review-options"><button class="text-button" data-action="edit">${ui(s.id).editing?'Done editing':'Edit'}</button><button class="icon-button" data-action="extras" aria-label="Add notes and tags">${icon('plus')}</button><button class="text-button deck-button" data-action="deck">Deck · ${escape(s.deck||'Japanese')}${icon('down')}</button></div>
      <button class="primary" data-action="send" ${M.canSend(s)?'':'disabled'}>${s.status==='sending'?'Sending…':'Send to AnkiDroid'} ${icon('arrow')}</button>`}</div>`;
  }
  function preparing(s) {
    const e=selected(s),failed=s.status==='failed',fixtureMiss=ui(s.id).failedKind==='fixture-translation';
    return `<div class="main"><div class="transition-content">${failed?`<span class="outcome-icon ${fixtureMiss?'data':'network'}">${icon(fixtureMiss?'info':'error')}</span><h3>${fixtureMiss?'Sample translation unavailable.':'Sentence audio didn’t finish.'}</h3><p>${fixtureMiss?'Edit the sentence to return to the supported examples. Your word selection is retained.':s.translation?'Your translation and word selection are retained.':'Your photo, sentence and word selection are retained.'}</p>` :
      `<div class="loading-track" role="progressbar" aria-label="Preparing note" aria-valuemin="0" aria-valuemax="100" aria-valuetext="Creating audio and translation"><span></span></div><h3>Preparing your note.</h3><p role="status">Creating audio and translation…</p>`}
      <div class="transition-summary"><strong lang="ja">${escape(e?.word||M.selectedText(s))} · ${escape(e?.reading||'')}</strong><div>${escape(s.definition)}</div><small>Deck · ${escape(s.deck||'Japanese')}</small></div>
      ${failed ? `${fixtureMiss?'<button class="primary" data-action="edit-source">Edit sentence</button>':`<button class="primary" data-action="retry">Retry ${icon('refresh')}</button>`}<div class="failure-secondary">${fixtureMiss?'':'<button class="text-button" data-action="edit-source">Edit</button>'}<button class="text-button" data-action="discard">Discard</button></div>` : ''}</div></div>
      ${failed ? '' : '<div class="footer-actions"><button class="text-button" style="width:100%" data-action="cancel-generation">Cancel</button></div>'}`;
  }
  function overlay(s) {
    const u=ui(s.id),kind=u.overlay;
    if(!kind)return '';
    if(kind==='photo')return `<div class="full-photo"><img src="${photoSrc()}" alt="Full Japanese textbook photograph specimen"><button class="icon-button" data-action="close-sheet" aria-label="Close">${icon('close')}</button></div>`;
    let title='',body='';
    const editor=(name,label,value,language='en') => `<label for="${s.id}-${name}">${label}</label><textarea id="${s.id}-${name}" data-editor="${name}" lang="${language}">${escape(value)}</textarea>`;
    if(kind==='sentence') {title='Sentence';body=editor('sentence','Japanese sentence',u.buffer.sentence??s.sentence,'ja')+'<button class="text-button" data-action="sentence-readings">Correct a reading</button><button class="primary" data-action="save-editor">Done</button>';}
    else if(kind==='definition'||kind==='translation') {title=kind==='definition'?'Definition':'Translation';body=editor(kind,title,u.buffer[kind]??s[kind])+'<button class="primary" data-action="save-editor">Done</button>';}
    else if(kind==='extras'){title='Notes & tags';body=editor('notes','Note',u.buffer.notes??s.notes)+`<label for="${s.id}-tags">Tags</label><input id="${s.id}-tags" data-editor="tags" value="${escape(u.buffer.tags??s.tags)}" placeholder="Add a tag"><button class="text-button" data-action="tag-suggestion">JLPT::N5</button><button class="primary" data-action="save-editor">Done</button>`;}
    else if(kind==='entries') {
      title='Dictionary entry';
      body=`<div role="radiogroup" aria-label="Dictionary entry">${M.entriesFor(s).map(e=>`<button class="entry-choice" role="radio" aria-checked="${s.entryId===e.id}" data-entry="${escape(e.id)}"><span class="check-square">${s.entryId===e.id?icon('check'):''}</span><span class="entry-copy"><span lang="ja">${escape(e.word)} · ${escape(e.reading)}</span><small>${escape(e.kind)} · ${escape(e.senses.join('; '))}</small></span></button>`).join('')}</div>`;
    } else if(kind==='regions') {
      title='Choose a sentence';body=`<div role="radiogroup" aria-label="Recognized excerpts">${[SAMPLE,'窓の席に座りました。'].map((t,i)=>`<button class="entry-choice" data-region="${i}" role="radio" aria-checked="false" lang="ja">${escape(t)}</button>`).join('')}</div>`;
    } else if(kind==='pitch'||kind==='pronunciation') {
      const entry=selected(s);title='Pronunciation';
      body=`<p class="choice-current" lang="ja">${escape(entry?.word||'')}</p><label for="${s.id}-word-reading">Dictionary reading</label><input id="${s.id}-word-reading" data-editor="word-reading" lang="ja" value="${escape(u.buffer['word-reading']??entry?.reading??'')}">
        <p class="section-label">Listed pronunciation</p><div role="radiogroup" aria-label="Pitch patterns">${(entry?.pitches||[]).map((p,i)=>`<button class="entry-choice radio-line" role="radio" aria-checked="${s.pitchIndex===i}" data-pitch="${i}"><span class="check-square">${s.pitchIndex===i?icon('check'):''}</span>${pitchHtml(entry,i)}</button>`).join('')}</div><button class="primary" data-action="save-pronunciation">Done</button>`;
    } else if(kind==='sentence-readings') {
      title='Sentence readings';body=readingSpans(s).map(r=>`<label for="${s.id}-reading-${r.start}" lang="ja">${escape(r.text)}</label><input id="${s.id}-reading-${r.start}" data-reading-start="${r.start}" data-reading-end="${r.end}" lang="ja" value="${escape(u.buffer[`reading-${r.start}`]??r.reading)}">`).join('')+'<button class="primary" data-action="save-readings">Done</button>';
    } else if(kind==='deck') {
      title='Deck';body=`<div role="radiogroup" aria-label="Destination deck">${['Japanese','Textbook'].map(d=>`<button class="entry-choice" role="radio" aria-checked="${s.deck===d}" data-deck="${d}"><span class="check-square">${s.deck===d?icon('check'):''}</span>${d}</button>`).join('')}</div>`;
    } else if(kind==='photo-edit') {
      title='Photo';body=`<button class="context-strip" data-action="view-photo" aria-label="View full photo"><img src="${photoSrc()}" alt="Japanese textbook page"></button><div class="editor-actions"><button class="primary" data-action="replace-photo">Choose photo</button>${s.origin==='type'?'<button class="text-button" data-action="remove-photo">Remove photo</button>':''}</div>`;
    } else if(kind==='discard') {
      title='Discard local draft?';body=`<p>${s.submission==='uncertain'?'This removes the saved draft only. A note may still exist in AnkiDroid.':'The photo, sentence and generated content in this draft will be removed.'}</p><div class="editor-actions"><button class="primary" data-action="close-sheet">Keep working</button><button class="destructive" data-action="confirm-discard">Discard local draft</button></div>`;
    } else if(kind==='settings') {
      title='Settings';body='<p>Destination and Google Cloud setup are outside the mining steps. This study does not connect to device settings.</p><button class="primary" data-action="close-sheet">Done</button>';
    }
    return `<div class="overlay" data-backdrop><section class="sheet" role="dialog" aria-modal="true" aria-label="${escape(title)}"><div class="sheet-drag" aria-hidden="true"></div><div class="sheet-title"><h3>${title}</h3><button class="icon-button" data-action="close-sheet" aria-label="Close">${icon('close')}</button></div>${u.error?`<p class="field-error" role="alert">${escape(u.error)}</p>`:''}${body}</section></div>`;
  }
  function positionHandles(id,selection=null) {
    const p=document.querySelector(`[data-phone="${id}"]`),box=p?.querySelector('[data-selection-box]');
    if(!box)return;
    const mark=box.querySelector('mark'),rects=mark?[...mark.getClientRects()].filter(r=>r.width>0):[];
    if(!rects.length)return;
    const b=box.getBoundingClientRect();
    for(const [name,r,x] of [['start',rects[0],rects[0].left],['end',rects.at(-1),rects.at(-1).right]]) {
      const h=box.querySelector(`[data-handle="${name}"]`);
      if(h){h.style.left=`${Math.max(16,Math.min(b.width-16,x-b.left))}px`;h.style.top=`${r.bottom-b.top}px`;}
    }
  }
  function render(id) {
    const s=phones[id],u=ui(id),p=document.querySelector(`[data-phone="${id}"]`);
    if(!p||!s)return;
    const active=document.activeElement,had=p.contains(active);
    const previousOverlay=p.dataset.overlay,previousStage=p.dataset.stage,scroll=p.querySelector('.main')?.scrollTop||0;
    const activeAction=had?active.dataset.action:null,activeHandle=had?active.dataset.handle:null;
    p.dataset.theme=theme;p.dataset.stage=s.stage;p.dataset.status=s.status;p.dataset.overlay=u.overlay||'';
    const body=s.submission==='uncertain'||s.status==='uncertain'?review(s):s.stage==='preparing'?preparing(s):s.stage==='review'?review(s):s.stage==='target'?target(s):input(s);
    p.innerHTML=toolbar(s)+body+'<div class="gesture-inset" aria-hidden="true"></div>'+overlay(s)+(u.toast?`<div class="toast" role="status">${escape(u.toast)}</div>`:'');
    p.querySelector('.main')?.scrollTo(0,previousStage===s.stage?scroll:0);
    positionHandles(id);
    if(u.overlay) {
      p.querySelectorAll(':scope>.main,:scope>.toolbar,:scope>.footer-actions').forEach(e=>e.inert=true);
      if(previousOverlay!==u.overlay||had)p.querySelector('.sheet-title button,.full-photo button')?.focus({preventScroll:true});
    } else if(previousOverlay && u.returnFocus) {
      p.querySelector(u.returnFocus)?.focus({preventScroll:true});
    } else if(had) {
      const replacement=activeHandle?p.querySelector(`[data-handle="${activeHandle}"]`):activeAction?p.querySelector(`[data-action="${activeAction}"]`):p.querySelector('[data-sentence-run]');
      replacement?.focus({preventScroll:true});
    }
  }
  function dispatch(id,event) {
    const prior=phones[id];phones[id]=M.reduce(prior,event);
    const s=phones[id];
    if(event.type==='GENERATION_COMPLETE'&&s.status==='failed'&&!s.translation)ui(id).failedKind='fixture-translation';
    if(event.type==='GENERATION_FAIL')ui(id).failedKind='network';
    render(id);
    if(s.status==='generating'&&(prior.status!=='generating'||prior.generation!==s.generation)) {
      const generation=s.generation,fail=Boolean(ui(id).failNext);ui(id).failNext=false;
      if(ui(id).holdGeneration)return;
      setTimeout(()=>dispatch(id,{type:fail?'GENERATION_FAIL':'GENERATION_COMPLETE',generation}),700);
    }
    return s;
  }
  function notify(id,text) {
    const u=ui(id);u.toast=text;render(id);
    // Expiry must not reconstruct an active editor or affect a replacement draft.
    setTimeout(()=>{if(local[id]===u&&u.toast===text){u.toast='';document.querySelector(`[data-phone="${id}"] .toast`)?.remove();}},2200);
  }
  function open(id,name,invoker=null) {
    const u=ui(id);u.overlay=name;u.error='';u.buffer={};
    const action=invoker?.dataset.action,edit=invoker?.dataset.edit;
    u.returnFocus=action?`[data-action="${action}"]`:edit?`[data-edit="${edit}"]`:u.returnFocus;
    render(id);
  }
  function close(id,save=true) {
    const u=ui(id);
    if(save&&['sentence','definition','translation','extras'].includes(u.overlay))return saveEditor(id);
    u.overlay=null;u.error='';render(id);
  }
  function editorError(id,text){ui(id).error=text;render(id);return false;}
  function saveEditor(id) {
    let s=phones[id];const u=ui(id),kind=u.overlay;
    const fields=kind==='extras'?['notes','tags']:[kind];
    for(const field of fields) {
      const value=u.buffer[field]??s[field]??'';
      if(['sentence','definition','translation'].includes(field)&&!value.trim())return editorError(id,'This content is required.');
      const types={sentence:'SET_SENTENCE',definition:'SET_DEFINITION',translation:'SET_TRANSLATION',notes:'SET_NOTES',tags:'SET_TAGS'};
      s=M.reduce(s,{type:types[field],text:value});
    }
    phones[id]=s;u.overlay=null;u.error='';u.editing=false;render(id);return true;
  }
  function selectSpan(id,start,end,showEntries=true) {
    dispatch(id,{type:'SELECT_SPAN',start,end});
    const s=phones[id];
    if(showEntries&&M.entriesFor(s).length>1&&!s.entryId)open(id,'entries');
  }
  function action(id,name,button=null) {
    const s=phones[id],u=ui(id);
    if(s.status==='sending')return;
    const locked=s.submission==='uncertain';
    if(locked && !['back','view-photo','close-sheet','recheck','open-anki','discard','confirm-discard','sentence-audio','word-audio'].includes(name)) {
      notify(id,'Resolve the send status or discard this local draft first.');return;
    }
    if(name==='back') {
      if(u.overlay){close(id);return;}
      if(locked){dispatch(id,{type:'BACK'});notify(id,'Send status is unresolved. The submitted note is retained.');return;}
      if(s.stage==='input'){open(id,'discard',button);return;}
      u.editing=false;dispatch(id,{type:'BACK'});
    } else if(name==='close-sheet')close(id);
    else if(name==='capture')dispatch(id,{type:'CAPTURE'});
    else if(name==='retake')dispatch(id,{type:'RETAKE'});
    else if(name==='use-photo') {
      let next=s;
      if(!next.sentence)next=M.reduce(next,{type:'SET_SENTENCE',text:SAMPLE});
      phones[id]=next;dispatch(id,{type:'USE_PHOTO'});
    } else if(name==='prepare')dispatch(id,{type:'PREPARE'});
    else if(name==='type-mode')dispatch(id,{type:'TYPE_MODE'});
    else if(name==='photo-mode')dispatch(id,{type:'PHOTO_MODE'});
    else if(name==='gallery'){dispatch(id,{type:'CAPTURE'});notify(id,'Sample photo selected.');}
    else if(name==='attach'){dispatch(id,{type:s.hasPhoto?'REMOVE_PHOTO':'ATTACH_PHOTO'});}
    else if(name==='continue')dispatch(id,{type:'CONTINUE'});
    else if(name==='retry')dispatch(id,{type:'RETRY'});
    else if(name==='cancel-generation'||name==='edit-source'){dispatch(id,{type:'CANCEL_GENERATION'});}
    else if(name==='reselect'){u.overlay=null;dispatch(id,{type:'RESELECT'});}
    else if(name==='edit') {u.editing=!u.editing;render(id);}
    else if(name==='edit-sentence')open(id,'sentence',button);
    else if(['extras','entries','deck','discard','photo-edit','settings'].includes(name))open(id,name,button);
    else if(name==='view-photo')open(id,'photo',button);
    else if(name==='sentence-readings'){if(ui(id).buffer.sentence!==undefined&&!saveEditor(id))return;open(id,'sentence-readings',button);}
    else if(name==='save-editor')saveEditor(id);
    else if(name==='save-pronunciation') {
      const entry=selected(s),reading=u.buffer['word-reading']??entry?.reading??'';
      if(katakana(reading)!==katakana(entry?.reading||''))return editorError(id,'Choose a listed dictionary reading; this sample has no matching pronunciation for that correction.');
      // An unchanged listed reading is confirmation, not a source correction.
      // Preserve the current senses, manual definition, pitch and Review state.
      u.overlay=null;render(id);
    } else if(name==='save-readings') {
      const fields=[...document.querySelectorAll(`[data-phone="${id}"] [data-reading-start]`)];
      if(fields.some(f=>!f.value.trim()))return editorError(id,'Each reading needs kana.');
      let next=s;
      for(const field of fields) next=M.reduce(next,{type:'SET_READING',start:Number(field.dataset.readingStart),end:Number(field.dataset.readingEnd),reading:field.value,scope:'sentence'});
      phones[id]=next;u.overlay=null;u.editing=false;render(id);
    } else if(name==='tag-suggestion') {
      const field=document.querySelector(`[data-phone="${id}"] [data-editor="tags"]`);
      field.value=(field.value+' JLPT::N5').trim();u.buffer.tags=field.value;
    } else if(name==='replace-photo'){u.overlay=null;dispatch(id,{type:'ATTACH_PHOTO'});notify(id,'Sample photo selected.');}
    else if(name==='remove-photo'){u.overlay=null;dispatch(id,{type:'REMOVE_PHOTO'});}
    else if(name==='confirm-discard'){phones[id]=M.reduce(s,{type:'DISCARD'});local[id]=undefined;render(id);}
    else if(name==='send') {
      const next=dispatch(id,{type:'SEND'});
      if(next.status==='sending')setTimeout(()=>{if(phones[id].status==='sending'){dispatch(id,{type:'SEND_COMPLETE'});ui(id).editing=false;notify(id,'Sent · simulation only. No Anki note was created.');}},600);
    } else if(name==='recheck'){dispatch(id,{type:'RECHECK'});notify(id,'Simulation: status remains unknown. No second send was attempted.');}
    else if(name==='open-anki')notify(id,'AnkiDroid navigation is represented only.');
    else if(name==='sentence-audio'||name==='word-audio')notify(id,'Playback control only · no audio service connected.');
    else if(name==='flash')notify(id,'Camera controls are represented only.');
  }
  function systemContent() {
    return `<article class="rationale"><h2>Vermilion, paper, and a clear sequence.</h2><p>The photograph belongs to Input. The sentence and intended meaning belong to Target. Review is the finished learning content, with corrections revealed on demand.</p>
      <h3>Material 3 Expressive</h3><p>Color, type, shape and containment establish hierarchy.[1] The selected word and pronunciation are the focus; other controls remain familiar and quiet. Expressive emphasis is concentrated rather than applied to every element.[2]</p>
      <h3>Native component mapping</h3><ul><li>Small top app bar with a compact three-step progress indicator.</li><li>Expressive large primary button, shared icon-button treatment for both playback controls.</li><li>Tappable word pills, with drag and keyboard range selection.</li><li>Centered portrait camera preview. Landscape photographs are supported as source images; the app composition stays portrait. The captured image remains visible in Review.</li><li>Modal bottom sheets and targeted text correction; no field-form default.</li><li>Material color, typography, shape, state-layer and motion roles. CSS previews the composition; Compose supplies the native behavior.[3]</li></ul>
      <h3>Japanese content</h3><p>Target uses word pills. Review returns to continuous Japanese with ruby at half the base size. One highlight covers the selected occurrence, including inflections. Pitch uses katakana and straight connecting lines, with no axes or accent-number label.</p>
      <div class="scope"><p>This is an offline composition and interaction mockup, not a WebView frontend or native accessibility certification. Input images and dictionary entries are illustrative fixtures. Camera, OCR, network generation, audio and Anki submission are simulated. The 200% switch doubles mock-phone text; native font scaling, IME, TalkBack and rotation require Compose validation.</p></div>
      <h3>Sources</h3><div class="sources"><p><a href="https://design.google/library/expressive-material-design-google-research">[1] Google Design · Expressive UX research</a><a href="https://m3.material.io/blog/building-with-m3-expressive">[2] Material Design · Building with M3 Expressive</a><a href="https://developer.android.com/develop/ui/compose/designsystems/material3">[3] Android Developers · Material 3 in Compose</a></p></div></article>`;
  }
  function mount() {
    const root=document.getElementById('study-content');
    document.querySelectorAll('[data-view]').forEach(b=>{if(b.dataset.view===view)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');});
    if(view==='overview') {
      if(!phones.input)overviewStates();
      root.innerHTML=`<div class="board">${[['input','Input','The photo takes the screen. Type sits beside the shutter.'],['target','Target','One sentence. One word and meaning.'],['review','Review','A complete note, ready to inspect and send.']].map(([id,title,caption])=>`<section><h2 class="board-label">${title}</h2>${shell(id)}<p class="board-caption">${caption}</p></section>`).join('')}</div>`;
    } else if(view==='flow') {
      if(!phones.live)phones.live=M.create('live',{sentence:'',mode:'photo'});
      root.innerHTML=`<div class="scenario-layout"><section class="scenario-picker"><h2>Try the flow</h2><button class="scenario-button" data-demo="photo">Start with Photo</button><button class="scenario-button" data-demo="type">Start with Type</button><button class="scenario-button" data-demo="fail">Fail next generation</button></section>${shell('live')}<aside class="scenario-copy"><p>Capture the sample page, tap the <span lang="ja">本</span> pill, choose its meaning, then Continue.</p><p>Drag across pills to select a span. Keyboard: arrows, Shift + arrows, Enter.</p><p>All service outcomes are simulated.</p></aside></div>`;
    } else if(view==='states') {
      if(!phones.edge)seedScenario(scenario);
      root.innerHTML=`<div class="scenario-layout"><section class="scenario-picker"><h2>States</h2>${scenarios.map(([name,label])=>`<button class="scenario-button" data-scenario="${name}" ${name===scenario?'aria-current="true"':''}>${label}</button>`).join('')}</section>${shell('edge')}<aside class="scenario-copy"><p>${scenarioDescription()}</p><p>These controls select illustrative fixtures, not app navigation.</p></aside></div>`;
    } else root.innerHTML=systemContent();
    Object.keys(phones).forEach(render);
    document.getElementById('theme-toggle').innerHTML=icon(theme==='light'?'moon':'sun');
  }
  function seedScenario(name,id='edge') {
    const modelName=name==='type-empty'?'type-empty':name;
    phones[id]=M.seed(modelName,id);local[id]=undefined;
    if(name==='regions')ui(id).overlay='regions';
    if(name==='entries')ui(id).overlay='entries';
    if(name==='readings'){ui(id).editing=true;ui(id).overlay='pitch';}
    if(name==='generating')ui(id).holdGeneration=true;
    scenario=name;
  }
  function scenarioDescription(){return ({'type-empty':'An empty writing canvas, with no image placeholder.','type':'Typed input keeps the photograph optional.','regions':'Choose readable excerpts only when OCR has several plausible regions.','entries':'The noun and counter remain separate dictionary identities.','generating':'Preparing is a transition, not an unfinished Review.','failed':'Retry generation without discarding the source or selection.','missing':'A dataset miss is not a network error.','counter':'The chosen counter meaning is retained, even when pronunciation is unavailable.','span':'Word pills preserve the exact contiguous span, including when no entry matches.','readings':'Current readings and listed pronunciations are directly correctable.','uncertain':'The submitted snapshot is read-only. Back cannot reopen Send.'})[scenario]||'';}
  function offsetAtPoint(id,x,y) {
    const run=document.querySelector(`[data-phone="${id}"] [data-sentence-run]`);
    if(!run)return null;
    const caret=document.caretPositionFromPoint?.(x,y);
    const range=caret?null:document.caretRangeFromPoint?.(x,y);
    const node=caret?.offsetNode||range?.startContainer,offset=caret?.offset??range?.startOffset;
    if(!node||!run.contains(node))return null;
    const textRange=document.createRange();textRange.setStart(run,0);textRange.setEnd(node,offset);
    return Math.min(phones[id].sentence.length,textRange.toString().length);
  }
  function stepBoundary(s,which,delta) {
    if(!s.selection)return null;
    const boundaries=[0,...s.tokens.map(t=>t.end)];
    const current=s.selection[which],position=boundaries.indexOf(current),next=boundaries[Math.max(0,Math.min(boundaries.length-1,position+delta))];
    const range={...s.selection,[which]:next};
    return range.start<range.end?range:null;
  }
  document.addEventListener('click',e=>{
    const tab=e.target.closest('[data-view]');if(tab){view=tab.dataset.view;mount();return;}
    const sc=e.target.closest('[data-scenario]');if(sc){seedScenario(sc.dataset.scenario);view='states';mount();return;}
    const demo=e.target.closest('[data-demo]');
    if(demo){
      if(demo.dataset.demo==='fail'){ui('live').failNext=true;notify('live','Next generation will show a network failure.');return;}
      if(phones.live?.submission==='uncertain'){open('live','discard');return;}
      phones.live=M.create('live',{sentence:'',mode:demo.dataset.demo==='type'?'type':'photo'});local.live=undefined;mount();return;
    }
    const p=e.target.closest('[data-phone]');if(!p)return;const id=p.dataset.phone,s=phones[id];
    const command=e.target.closest('[data-action]');if(command){action(id,command.dataset.action,command);return;}
    if(s.status==='sending'||s.submission==='uncertain')return;
    const token=e.target.closest('[data-token-start]');if(token){if(suppressPillClick)return;const start=Number(token.dataset.tokenStart),end=Number(token.dataset.tokenEnd);selectSpan(id,e.shiftKey&&s.selection?Math.min(start,s.selection.start):start,e.shiftKey&&s.selection?Math.max(end,s.selection.end):end);return;}
    const entry=e.target.closest('[data-entry]');if(entry){ui(id).overlay=null;dispatch(id,{type:'CHOOSE_ENTRY',id:entry.dataset.entry});return;}
    const sense=e.target.closest('[data-sense]');if(sense){dispatch(id,{type:'TOGGLE_SENSE',index:Number(sense.dataset.sense)});return;}
    const pitch=e.target.closest('[data-pitch]');if(pitch){dispatch(id,{type:'SET_PITCH',index:Number(pitch.dataset.pitch)});return;}
    const deck=e.target.closest('[data-deck]');if(deck){ui(id).overlay=null;dispatch(id,{type:'SET_DECK',name:deck.dataset.deck});return;}
    const region=e.target.closest('[data-region]');if(region){ui(id).overlay=null;phones[id]=M.reduce(s,{type:'SET_SENTENCE',text:Number(region.dataset.region)===0?SAMPLE:'窓の席に座りました。'});dispatch(id,{type:'USE_PHOTO'});return;}
    const edit=e.target.closest('[data-edit]');if(edit){open(id,edit.dataset.edit,edit);return;}
    if(e.target.matches('[data-backdrop]'))close(id);
  });
  document.addEventListener('input',e=>{
    const p=e.target.closest('[data-phone]');if(!p)return;const id=p.dataset.phone,u=ui(id);
    if(e.target.dataset.input==='sentence') {
      phones[id]=M.reduce(phones[id],{type:'SET_SENTENCE',text:e.target.value});
      const button=p.querySelector('[data-action=prepare]');if(button)button.disabled=!e.target.value.trim();
    }
    if(e.target.dataset.editor)u.buffer[e.target.dataset.editor]=e.target.value;
    if(e.target.dataset.readingStart)u.buffer[`reading-${e.target.dataset.readingStart}`]=e.target.value;
  });
  document.addEventListener('pointerdown',e=>{
    const pill=e.target.closest('.word-pill');if(!pill)return;
    const id=pill.closest('[data-phone]').dataset.phone;if(phones[id].submission||phones[id].status==='sending')return;
    pillDrag={id,x:e.clientX,y:e.clientY,start:Number(pill.dataset.tokenStart),end:Number(pill.dataset.tokenEnd),range:null};
  });
  document.addEventListener('pointermove',e=>{
    if(!pillDrag||Math.hypot(e.clientX-pillDrag.x,e.clientY-pillDrag.y)<8)return;
    const pill=document.elementFromPoint(e.clientX,e.clientY)?.closest('.word-pill');if(!pill||pill.closest('[data-phone]').dataset.phone!==pillDrag.id)return;
    e.preventDefault();const start=Math.min(pillDrag.start,Number(pill.dataset.tokenStart)),end=Math.max(pillDrag.end,Number(pill.dataset.tokenEnd));pillDrag.range={start,end};
    document.querySelectorAll(`[data-phone="${pillDrag.id}"] .word-pill`).forEach(b=>b.setAttribute('aria-pressed',Number(b.dataset.tokenStart)>=start&&Number(b.dataset.tokenEnd)<=end));
  },{passive:false});
  document.addEventListener('pointerup',()=>{if(pillDrag){const d=pillDrag;pillDrag=null;if(d.range){suppressPillClick=true;selectSpan(d.id,d.range.start,d.range.end,false);setTimeout(()=>{suppressPillClick=false;},0);}}});
  document.addEventListener('pointercancel',()=>{if(pillDrag){const id=pillDrag.id;pillDrag=null;render(id);}});
  document.addEventListener('keydown',e=>{
    const p=e.target.closest('[data-phone]');
    if(e.key==='Escape') {
      const id=p?.dataset.phone||Object.keys(local).find(k=>local[k]?.overlay);
      if(id&&ui(id).overlay){e.preventDefault();close(id);return;}
      if(id&&phones[id].submission==='uncertain'){e.preventDefault();action(id,'back');return;}
    }
    if(!p)return;const id=p.dataset.phone,s=phones[id],u=ui(id);
    const panel=p.querySelector('.sheet,.full-photo');
    if(e.key==='Tab'&&panel) {
      const controls=[...panel.querySelectorAll('button:not(:disabled),input,textarea,[tabindex="0"]')];
      const first=controls[0],last=controls.at(-1);
      if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}
      else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}
    }
    const handle=e.target.closest('[data-handle]');
    if(handle&&['ArrowLeft','ArrowRight'].includes(e.key)) {
      e.preventDefault();const range=stepBoundary(s,handle.dataset.handle,e.key==='ArrowLeft'?-1:1);
      if(range)selectSpan(id,range.start,range.end,false);return;
    }
    if(e.target.matches('[data-sentence-run]')&&['ArrowLeft','ArrowRight','Enter',' '].includes(e.key)) {
      e.preventDefault();
      if(e.shiftKey&&s.selection){const range=stepBoundary(s,'end',e.key==='ArrowLeft'?-1:1);if(range)selectSpan(id,range.start,range.end,false);return;}
      const old=s.tokens.findIndex(t=>s.selection&&t.start===s.selection.start),delta=e.key==='ArrowLeft'?-1:e.key==='ArrowRight'?1:0;
      const index=Math.max(0,Math.min(s.tokens.length-1,(old<0?0:old)+delta)),token=s.tokens[index];
      if(token)selectSpan(id,token.start,token.end,e.key==='Enter'||e.key===' ');return;
    }
    if(e.target.matches('[data-edit]')&&['Enter',' '].includes(e.key)){e.preventDefault();open(id,e.target.dataset.edit,e.target);}
    if(e.target.matches('[role="radio"]')&&['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(e.key)) {
      e.preventDefault();const group=e.target.closest('[role="radiogroup"]'),radios=[...group.querySelectorAll('[role="radio"]')],delta=['ArrowLeft','ArrowUp'].includes(e.key)?-1:1;
      radios[(radios.indexOf(e.target)+delta+radios.length)%radios.length].focus();
    }
  });
  function setPhotoVariant(name) {
    photoVariant = name === 'landscape' ? 'landscape' : 'portrait';
    const toggle = document.getElementById('photo-toggle');
    if (toggle) {
      toggle.setAttribute('aria-pressed', String(photoVariant === 'landscape'));
      toggle.textContent = photoVariant === 'landscape' ? 'Portrait photo' : 'Landscape photo';
    }
    Object.keys(phones).forEach(render);
  }
  document.getElementById('theme-toggle').addEventListener('click',()=>{
    theme=theme==='light'?'dark':'light';document.body.classList.toggle('dark-board',theme==='dark');
    const toggle=document.getElementById('theme-toggle');toggle.innerHTML=icon(theme==='light'?'moon':'sun');toggle.setAttribute('aria-label',`Use ${theme==='light'?'dark':'light'} theme`);
    Object.keys(phones).forEach(render);
  });
  document.getElementById('size-toggle').addEventListener('click',()=>{
    scale=scale===1?2:1;document.body.classList.toggle('large-type',scale===2);document.getElementById('size-toggle').setAttribute('aria-pressed',scale===2);Object.keys(phones).forEach(render);
  });
  document.getElementById('photo-toggle').addEventListener('click',()=>setPhotoVariant(photoVariant === 'landscape' ? 'portrait' : 'landscape'));
  addEventListener('resize',()=>Object.keys(phones).forEach(id=>positionHandles(id)));
  addEventListener('popstate',()=>{if(phones.live)action('live','back');});
  window.designStudy={apiVersion:2,model:M,get phones(){return phones;},get local(){return local;},get theme(){return theme;},get textScale(){return scale;},get photoVariant(){return photoVariant;},
    setView(name){view=name;mount();},setScenario(name){seedScenario(name);view='states';mount();},
    seedLive(name){seedScenario(name,'live');view='flow';mount();},dispatch,render,selectSpan,setPhotoVariant,
    setTheme(name){if(theme!==name)document.getElementById('theme-toggle').click();},
    setTextScale(value){if(scale!==value)document.getElementById('size-toggle').click();}};
  mount();
})();
