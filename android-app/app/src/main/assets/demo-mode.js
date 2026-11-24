(function () {
  'use strict';

  const now = Date.now();
  const timers = {
    brake: 2, enable: 1, direction: 1, speed0a: 1, speed1a: 1,
    speed2: 2, speed1b: 1, speed0b: 1, dirOff: 1, enOff: 1,
    buzzerInterval: 10, readyDelay: 30, runModeHours: 1,
  };
  const state = {
    apSsid: 'Repair Center', apIp: '10.10.10.10', apActive: false,
    apClients: 0, staConfigured: false, staConnected: false, staIp: '',
    canBitrate: 500000, uartBaud: 115200, canRx: 128, uartRx: 42,
    canErrors: 0, uartDropped: 0, analyzerDropped: 0, canLastRxMs: 8400,
    uartLastRxMs: 12600, canBusState: 1, modbusFrames: 12, modbusErrors: 0,
    uartMode: 'modbus', canDriverReady: true, canListenOnly: false,
    sequenceDirection: '', sequenceRunning: false, runModeActive: false,
    readyRelayOn: false, physicalResetPressCount: 0, resetButtonPressed: false,
    buzzerAfterRunMode: false, buzzerRelayOn: false, sequenceStepDurationMs: 0,
    sequenceStepElapsedMs: 0, sequenceStepRemainingMs: 0,
    safetyState: 'DEMO', sequenceStatus: 'Demo preview · idle',
    freeHeap: 172400, minFreeHeap: 154800, controlHeartbeatAgeMs: 8,
    controlTaskHealthy: true, controlStackHighWaterWords: 2300,
    commsStackHighWaterWords: 2100, ledStackHighWaterWords: 1400,
    analyzerCount: 4, analyzerCapacity: 80,
    outputs: { Brake: false, Enable: false, Up: false, Down: false,
      'Speed 0': false, 'Speed 1': false, 'Speed 2': false },
  };
  let analyzerId = 4;
  const events = [
    { id: 1, ms: 4200, source: 'can', text: 'RX ID: 0x123 DLC: 4 DATA: 11 22 33 44' },
    { id: 2, ms: 6100, source: 'can', text: 'RX ID: 0x18FF50E5 DLC: 8 DATA: 01 02 03 04 05 06 07 08' },
    { id: 3, ms: 8400, source: 'serial', text: 'RX HEX: 01 03 00 00 00 04 44 09' },
    { id: 4, ms: 12600, source: 'serial', text: 'RX ASCII: READY' },
  ];
  const realFetch = window.fetch.bind(window);

  function json(data, ok) {
    return Promise.resolve(new Response(JSON.stringify(data), {
      status: ok === false ? 400 : 200,
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
    }));
  }

  function addEvent(source, text) {
    analyzerId += 1;
    events.push({ id: analyzerId, ms: Date.now() - now, source, text });
    if (events.length > 80) events.shift();
  }

  function setOutput(name, enabled) {
    if (!(name in state.outputs)) return;
    if (enabled && (name === 'Up' || name === 'Down')) {
      state.outputs.Up = name === 'Up';
      state.outputs.Down = name === 'Down';
    } else if (enabled && name.startsWith('Speed ')) {
      for (const key of ['Speed 0', 'Speed 1', 'Speed 2']) state.outputs[key] = key === name;
    } else state.outputs[name] = enabled;
  }

  function setSequence(command) {
    if (command === 'stop') {
      state.sequenceRunning = false;
      state.runModeActive = false;
      state.sequenceDirection = '';
      state.sequenceStatus = 'Demo preview · stopped';
      state.sequenceStepDurationMs = 0;
      state.sequenceStepElapsedMs = 0;
      state.sequenceStepRemainingMs = 0;
      Object.keys(state.outputs).forEach((name) => { state.outputs[name] = false; });
      return { ok: true, message: 'Demo stop simulated' };
    }
    state.sequenceRunning = true;
    state.runModeActive = command === 'run';
    state.sequenceDirection = command === 'down' ? 'DOWN' : 'UP';
    state.sequenceStatus = `Demo preview · ${command === 'run' ? 'Run Mode' : `Auto ${command.toUpperCase()}`}`;
    state.sequenceStepDurationMs = 2000;
    state.sequenceStepElapsedMs = 400;
    state.sequenceStepRemainingMs = 1600;
    return { ok: true, message: `Demo ${command} simulated` };
  }

  function mockFetch(input, init) {
    const requestUrl = typeof input === 'string' ? input : input.url;
    const url = new URL(requestUrl, location.href);
    if (!url.pathname.startsWith('/api/')) return realFetch(input, init);
    const method = (init && init.method) || (typeof input === 'object' && input.method) || 'GET';
    const body = init && init.body;
    const form = body instanceof URLSearchParams ? body : new URLSearchParams(typeof body === 'string' ? body : '');

    if (url.pathname === '/api/status') return json(state);
    if (url.pathname === '/api/timers' && method === 'GET') return json(timers);
    if (url.pathname === '/api/timers' && method === 'POST') {
      for (const key of Object.keys(timers)) if (form.has(key)) timers[key] = Number(form.get(key)) || timers[key];
      return json({ ok: true, message: 'Demo timer settings saved on this screen only.' });
    }
    if (url.pathname === '/api/output' && method === 'POST') {
      const name = url.searchParams.get('name');
      const enabled = url.searchParams.get('state') === '1';
      setOutput(name, enabled);
      return json({ ok: true });
    }
    if (url.pathname === '/api/sequence' && method === 'POST') {
      return json(setSequence(url.searchParams.get('cmd') || 'stop'));
    }
    if (url.pathname === '/api/analyzer') {
      const source = url.searchParams.get('source') || 'can';
      const since = Number(url.searchParams.get('since')) || 0;
      return json({
        canRx: state.canRx, uartRx: state.uartRx, modbusFrames: state.modbusFrames,
        modbusErrors: state.modbusErrors, canErrors: state.canErrors,
        droppedLogs: state.analyzerDropped, canLastRxMs: state.canLastRxMs,
        uartLastRxMs: state.uartLastRxMs, uptimeMs: Date.now() - now,
        canStateText: 'RUNNING · demo', events: events.filter((event) => event.source === source && event.id > since),
      });
    }
    if (url.pathname === '/api/analyzer/config' && method === 'POST') {
      const kind = form.get('kind');
      const value = form.get('value');
      if (kind === 'can') state.canBitrate = Number(value) || state.canBitrate;
      if (kind === 'canmode') state.canListenOnly = value === '1';
      if (kind === 'serial') state.uartBaud = Number(value) || state.uartBaud;
      return json({ ok: true, message: `Demo setting applied locally (${kind}: ${value}).` });
    }
    if (url.pathname === '/api/analyzer/mode' && method === 'POST') {
      state.uartMode = form.get('mode') || state.uartMode;
      return json({ ok: true, message: `Demo UART mode: ${state.uartMode}.` });
    }
    if (url.pathname === '/api/analyzer/tx' && method === 'POST') {
      addEvent('serial', `TX ASCII: ${form.get('data') || ''}`);
      return json({ ok: true, message: 'Demo UART transmit simulated; no hardware was contacted.' });
    }
    if (url.pathname === '/api/analyzer/can-tx' && method === 'POST') {
      addEvent('can', `TX ID: 0x${form.get('id') || '123'} DATA: ${form.get('data') || '(empty)'}`);
      return json({ ok: true, message: 'Demo CAN transmit simulated; no hardware was contacted.' });
    }
    return json({ ok: true, message: 'Demo action recorded locally.' });
  }

  window.__iraDemoMode = true;
  window.fetch = mockFetch;
  document.addEventListener('submit', (event) => {
    const form = event.target;
    if (form && form.id !== 'timerForm') {
      event.preventDefault();
      if (typeof window.showToast === 'function') window.showToast('Demo mode · Wi-Fi settings were not sent to hardware.');
    }
  }, true);
  const statusText = new MutationObserver(() => {
    const status = document.getElementById('connectionState');
    const demoStatus = 'Demo preview · no hardware commands are sent';
    if (status && status.textContent !== demoStatus) status.textContent = demoStatus;
    const connection = document.getElementById('connectionLine');
    if (connection && !connection.classList.contains('connecting')) {
      connection.classList.remove('live', 'offline');
      connection.classList.add('connecting');
    }
    const timerMessage = document.getElementById('timerMessage');
    if (timerMessage && !timerMessage.textContent.includes('Demo mode')) {
      timerMessage.textContent += ' · Demo mode: not saved to hardware.';
    }
  });
  statusText.observe(document.documentElement, { childList: true, subtree: true, characterData: true });
})();
