package my.noveldokusha.webview

/**
 * JS-обвязка перевода страницы WebView.
 *
 * IIFE устанавливает `window.__novelaPageTranslator` с методами:
 *  - start(targetLang) — перевести текущее состояние страницы и наблюдать
 *    за динамически подгружаемым контентом (комментарии) через MutationObserver;
 *  - restore() — вернуть страницу в исходное состояние;
 *  - applyResult(batchId, jsonObj) — применить перевод батча (вызывается
 *    Kotlin-мостом NovelaTranslateBridge).
 *
 * Ключевые решения контракта:
 *  - накопление батчей: min(8000 символов, 100 узлов) — в пределах лимита
 *    провайдера (maxChunkChars = 8000 в TranslationManagerGoogleFree/PA),
 *    узел длиннее 8000 символов не отправляется вовсе (крайний случай, задокументирован);
 *  - последовательный конвейер: батч N+1 отправляется только после applyResult(N)
 *    либо таймаута ~15с — страница не должна висеть полупереведённой из-за упавшего батча;
 *  - гард поколений `gen`: start()/restore() инкрементируют счётчик, батч штампуется
 *    поколением в момент сбора; поздние applyResult и таймаут-отправки старого
 *    поколения — no-op (гонка с кнопкой «Original» не оставляет страницу полупереведённой);
 *  - оригинал хранится в атрибуте `data-original` самого span (запись setAttribute,
 *    чтение dataset.original), восстановление по селектору — НЕ WeakMap: на
 *    SPA-ререндере (React/Vue заменяют DOM-узлы) WeakMap-ссылки протухают;
 *  - вставка переведённого текста строго через textContent/createTextNode,
 *    никогда innerHTML (в тексте могут быть `&`, `<`, `>`);
 *  - MutationObserver: фильтр мутаций внутри переведённых span + отключение
 *    observer на время DOM-записей applyResult + гейт `active && gen` перед enqueue.
 *
 * Безопасность raw-строки Kotlin: внутри JS запрещены JS-шаблонные литералы
 * (backtick + `${...}`), последовательность `"""` и `$identifier`; единственный
 * `$` — якорь regex в SIGN_ONLY_RE (за ним следует `'`, интерполяция Kotlin
 * не срабатывает). SIGN_ONLY_RE строится через new RegExp в try/catch
 * (WebView < 64 не парсит `\p{...}` + `u` — фильтр отключается, IIFE живёт).
 * Строки — только в одинарных кавычках, конкатенацией.
 */
val PAGE_TRANSLATOR_JS: String = """(function () {
  'use strict';

  // Идемпотентность: повторная инъекция обвязки ничего не делает.
  if (window.__novelaPageTranslator) { return; }

  var MAX_BATCH_CHARS = 8000;   // батч: min(8000 символов, 100 узлов) — в пределах лимита провайдера
  var MAX_BATCH_NODES = 100;
  var MAX_NODE_CHARS = 8000;    // текст длиннее 8000 символов не отправляется вовсе (провайдер режет по 8000)
  var BATCH_TIMEOUT_MS = 15000; // без applyResult за ~15с принудительно идём к N+1
  var DEBOUNCE_MS = 300;        // debounce MutationObserver

  // Теги, чей текст не переводим.
  var SKIP_TAGS = { SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, CODE: 1, PRE: 1, TEXTAREA: 1, OPTION: 1, TITLE: 1, SVG: 1, HEAD: 1 };

  // Текст только из цифр/пробелов/пунктуации/символов не переводим.
  // \p{...} + флаг u парсится только на WebView >= 64: на старых конструктор
  // бросает SyntaxError — try/catch глушит, фильтр отключается безвредно.
  var SIGN_ONLY_RE = null;
  try { SIGN_ONLY_RE = new RegExp('^[\\d\\s\\p{P}\\p{S}]+$', 'u'); } catch (e) { SIGN_ONLY_RE = null; }

  var active = false;      // идёт ли перевод
  var gen = 0;             // поколение: start()/restore() инкрементируют
  var targetLang = '';     // целевой язык текущего поколения
  var currentBatch = null; // накапливаемый батч
  var nextBatchId = 1;
  var queue = [];          // готовые к отправке батчи
  var sentBatches = {};    // отправленные батчи: id -> батч
  var inflight = null;     // отправленный батч, ждущий applyResult или таймаут
  var timeoutId = null;    // таймер текущего батча
  var debounceId = null;   // таймер debounce observer
  var pendingNodes = [];   // узлы из мутаций, ждущие debounce
  var pendingGen = 0;      // поколение на момент записи мутаций
  var observer = null;

  function newBatch() {
    return {
      id: nextBatchId++,
      gen: gen,            // штампуется в момент СБОРА, не отправки
      targetLang: targetLang,
      nodes: [],
      texts: [],
      chars: 0,
      sent: false
    };
  }

  // Узел внутри уже переведённого span? Селектор всегда находит актуальные
  // span-ы (в отличие от WeakMap, который протухает на SPA-ререндере).
  function insideTranslatedSpan(el) {
    if (!el || !el.closest) { return false; }
    return el.closest('span[data-novela-tr]') !== null;
  }

  function shouldSkip(node) {
    var parent = node.parentElement;
    if (!parent) { return true; }
    if (SKIP_TAGS[parent.tagName]) { return true; }
    if (insideTranslatedSpan(parent)) { return true; }
    var text = node.nodeValue;
    if (text === null) { return true; }
    var trimmed = text.trim();
    if (trimmed.length === 0) { return true; }
    if (trimmed.length < 4) { return true; }
    if (SIGN_ONLY_RE && SIGN_ONLY_RE.test(text)) { return true; }
    return false;
  }

  // Правило накопления: узел попадает в батч, только если сумма символов не
  // превысит 8000; иначе батч закрывается и узел начинает новый. Одиночный узел
  // до 8000 символов уходит собственным батчем (провайдер режет чанки по 8000).
  // Узел длиннее 8000 символов не отправляется вовсе — батч без него
  // уходит в лимитах, сам узел остаётся непереведённым (крайний случай).
  function addNode(node) {
    var len = node.nodeValue.length;
    if (len > MAX_NODE_CHARS) { return; }
    if (currentBatch !== null && currentBatch.chars > 0 && currentBatch.chars + len > MAX_BATCH_CHARS) {
      queue.push(currentBatch);
      currentBatch = null;
      pump();
    }
    if (currentBatch === null) { currentBatch = newBatch(); }
    currentBatch.nodes.push(node);
    currentBatch.texts.push(node.nodeValue);
    currentBatch.chars += len;
    if (currentBatch.nodes.length >= MAX_BATCH_NODES) {
      queue.push(currentBatch);
      currentBatch = null;
      pump();
    }
  }

  // Конвейер: следующий батч отправляется только после applyResult предыдущего
  // (или истечения его таймаута). Пропускаем устаревшие по поколению и уже
  // отправленные батчи.
  function pump() {
    if (!active || inflight !== null) { return; }
    while (queue.length > 0) {
      var batch = queue.shift();
      if (batch.gen !== gen) { continue; }
      if (batch.sent) { continue; }
      batch.sent = true;
      sentBatches[batch.id] = batch;
      inflight = batch;
      window.NovelaTranslate.translateAsync(batch.id, batch.targetLang, JSON.stringify(batch.texts));
      scheduleTimeout();
      return;
    }
  }

  function scheduleTimeout() {
    if (timeoutId !== null) { clearTimeout(timeoutId); }
    timeoutId = setTimeout(onBatchTimeout, BATCH_TIMEOUT_MS);
  }

  // Таймаут ~15с на батч: applyResult не пришёл — принудительно переходим к N+1,
  // страница не должна висеть полупереведённой из-за упавшего батча. Просроченный
  // батч остаётся в sentBatches: поздний applyResult всё равно применится.
  function onBatchTimeout() {
    timeoutId = null;
    inflight = null;
    pump();
  }

  // Закрыть текущий батч и поставить его в очередь.
  function flushBatch() {
    if (currentBatch === null) { return; }
    var batch = currentBatch;
    currentBatch = null;
    if (batch.nodes.length === 0) { return; }
    queue.push(batch);
    pump();
  }

  // Первичный сбор текстовых узлов страницы через TreeWalker.
  function collect(root) {
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    var node;
    while ((node = walker.nextNode()) !== null) {
      if (!active) { break; }
      if (shouldSkip(node)) { continue; }
      addNode(node);
    }
    flushBatch();
  }

  // Применить результат перевода батча (вызывается Kotlin-мостом).
  // jsonObj приходит ОБЪЕКТОМ (мост интерполирует JSON-литерал); JSON.parse
  // вызываем только defensively для строки.
  function applyResult(batchId, jsonObj) {
    var batch = sentBatches[batchId];
    if (!batch) { return; }
    if (batch.gen !== gen) { return; } // результат старого поколения — no-op
    var result = jsonObj;
    if (typeof result === 'string') { result = JSON.parse(result); }
    if (!result) { return; }

    // Гасим observer на время своих DOM-записей: фильтр не ловит childList-мутацию
    // обёртки (её target — родитель span), без этого самосрабатывание неизбежно.
    disconnectObserver();
    for (var i = 0; i < batch.nodes.length; i++) {
      var node = batch.nodes[i];
      var parent = node.parentNode;
      if (!parent) { continue; }
      var original = node.nodeValue;
      if (original === null) { continue; }
      var translated = result[original];
      if (typeof translated !== 'string' || translated.length === 0) { continue; }
      var span = document.createElement('span');
      span.setAttribute('data-novela-tr', String(batch.id)); // маркер — только на span, без обёрток
      span.setAttribute('data-original', original);          // браузер сам экранирует атрибут
      span.textContent = translated;                         // только textContent, никогда innerHTML
      parent.replaceChild(span, node);
    }
    observe(); // re-observe после записи

    if (inflight === batch) {
      if (timeoutId !== null) { clearTimeout(timeoutId); timeoutId = null; }
      inflight = null;
    }
    pump(); // следующий батч — только если он ещё не отправлен
  }

  // Вернуть страницу в исходное состояние: span-ы заменяем исходными text-узлами
  // из data-original (обход по селектору; dataset декодирует HTML-сущности обратно).
  function restore() {
    gen++; // гард поколений: поздние applyResult/таймеры становятся no-op
    active = false;
    if (timeoutId !== null) { clearTimeout(timeoutId); timeoutId = null; }
    if (debounceId !== null) { clearTimeout(debounceId); debounceId = null; }
    disconnectObserver();
    currentBatch = null;
    queue = [];
    sentBatches = {};
    inflight = null;
    pendingNodes = [];

    var spans = document.querySelectorAll('span[data-novela-tr]');
    for (var i = spans.length - 1; i >= 0; i--) {
      var span = spans[i];
      var parent = span.parentNode;
      if (!parent) { continue; }
      var original = span.dataset.original;
      if (typeof original !== 'string' || original.length === 0) { continue; }
      parent.replaceChild(document.createTextNode(original), span);
    }
  }

  // Начать перевод страницы. Повторный вызов при активном переводе — no-op.
  function start(lang) {
    if (active) { return; }
    if (!document.body) { return; }
    gen++; // новое поколение
    active = true;
    targetLang = lang;
    currentBatch = null;
    queue = [];
    sentBatches = {};
    inflight = null;
    pendingNodes = [];
    if (timeoutId !== null) { clearTimeout(timeoutId); timeoutId = null; }
    if (debounceId !== null) { clearTimeout(debounceId); debounceId = null; }

    collect(document.body);
    observe(); // наблюдаем за динамически подгружаемым контентом (комментарии)
  }

  function observe() {
    if (observer === null) { observer = new MutationObserver(onMutations); }
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }

  function disconnectObserver() {
    if (observer !== null) { observer.disconnect(); }
  }

  // Мутации: новые текстовые узлы (комментарии, подгруженный контент) гоним через
  // тот же конвейер с debounce ~300ms.
  function onMutations(mutations) {
    var found = [];
    for (var i = 0; i < mutations.length; i++) {
      var m = mutations[i];
      // ОБЯЗАТЕЛЬНЫЙ ФИЛЬТР: мутации внутри уже переведённых span игнорируем —
      // иначе бесконечный цикл перевод -> мутация -> перевод.
      if (insideTranslatedSpan(m.target)) { continue; }
      var added = m.addedNodes;
      for (var j = 0; j < added.length; j++) {
        var n = added[j];
        if (n.nodeType === Node.TEXT_NODE) {
          if (!insideTranslatedSpan(n.parentElement)) { found.push(n); }
        } else if (n.nodeType === Node.ELEMENT_NODE) {
          collectTexts(n, found);
        }
      }
    }
    if (found.length === 0) { return; }
    pendingNodes = pendingNodes.concat(found);
    pendingGen = gen; // поколение на момент записи мутаций
    if (debounceId !== null) { clearTimeout(debounceId); }
    debounceId = setTimeout(flushPending, DEBOUNCE_MS);
  }

  function collectTexts(root, out) {
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    var node;
    while ((node = walker.nextNode()) !== null) {
      if (!insideTranslatedSpan(node.parentElement)) { out.push(node); }
    }
  }

  function flushPending() {
    debounceId = null;
    // ГЕЙТ: мутации, записанные до disconnect/re-observe, прилетают асинхронно,
    // а debounce-колбэк может сработать уже после restore() — отсекаем enqueue.
    if (!active || pendingGen !== gen) { pendingNodes = []; return; }
    var nodes = pendingNodes;
    pendingNodes = [];
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      if (!node.parentNode) { continue; } // узел уже удалён из DOM
      if (shouldSkip(node)) { continue; }
      addNode(node);
    }
    flushBatch();
  }

  window.__novelaPageTranslator = {
    start: start,
    restore: restore,
    applyResult: applyResult
  };
})();"""
