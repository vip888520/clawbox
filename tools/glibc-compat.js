/**
 * glibc-compat.js — Android compatibility shim for glibc Node.js (ClawBox).
 *
 * Loaded via: node --require <bundle>/patches/glibc-compat.js
 *
 * Fixes Android/kernel-level restrictions (not libc):
 *  - os.cpus(): SELinux blocks /proc/stat on Android 8+
 *  - os.networkInterfaces(): EACCES on some Android builds
 *  - process.execPath: points at ld.so when spawned via wrapper
 *  - /bin/sh fallback for Android 7-8 (Android 9+ ships /system/bin/sh)
 */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');

// ─── process.execPath fix ──────────────────────────────────────
// When node runs via `ld.so node`, process.execPath points at ld.so,
// which breaks child spawns that re-exec node. Point it back.
const wrapper = process.env.CLAWBOX_NODE_WRAPPER;
try {
  if (wrapper && fs.existsSync(wrapper)) {
    Object.defineProperty(process, 'execPath', {
      value: wrapper,
      writable: true,
      configurable: true,
    });
  }
} catch (e) { /* ignore */ }

// ─── os.cpus() fallback ────────────────────────────────────────
// /proc/stat is blocked by SELinux on many Android builds.
const _origCpus = os.cpus;
try {
  _origCpus();
} catch (e) {
  os.cpus = function cpusFallback() {
    try {
      const cores = Math.max(1, parseInt(process.env.CLAWBOX_CPU_CORES || '4', 10) || 4);
      const arr = [];
      for (let i = 0; i < cores; i++) {
        arr.push({ model: 'Android ARM64', speed: 1800, times: { user: 0, nice: 0, sys: 0, idle: 1e9, irq: 0 } });
      }
      return arr;
    } catch (e2) {
      return [];
    }
  };
}

// ─── os.networkInterfaces() safety ─────────────────────────────
// Can throw EACCES on some Android configurations.
const _origNet = os.networkInterfaces;
os.networkInterfaces = function netFallback() {
  try {
    return _origNet();
  } catch (e) {
    try {
      // minimal fallback using ifconfig-less approach: loopback only
      return { lo: [{ address: '127.0.0.1', netmask: '255.0.0.0', family: 'IPv4', mac: '00:00:00:00:00:00', internal: true }] };
    } catch (e2) {
      return {};
    }
  }
};

// ─── /bin/sh shim for older Android ────────────────────────────
// Android 9+ has /system/bin/sh but not /bin/sh. Create a best-effort
// in-memory shim that resolves /bin/sh → /system/bin/sh at spawn time.
// openclaw spawns `sh -c` in several places; it usually resolves via
// PATH which we set to include /system/bin, so this is only a last resort.
const childProcess = require('child_process');
const _origSpawn = childProcess.spawn;
childProcess.spawn = function spawnShim(cmd, args, options) {
  if (cmd === '/bin/sh' && !fs.existsSync('/bin/sh') && fs.existsSync('/system/bin/sh')) {
    return _origSpawn.call(this, '/system/bin/sh', args, options);
  }
  return _origSpawn.call(this, cmd, args, options);
};
const _origExec = childProcess.exec;
childProcess.exec = function execShim(cmd, options, cb) {
  if (typeof options === 'function') { cb = options; options = {}; }
  if (typeof cmd === 'string' && cmd.startsWith('/bin/sh ')) {
    cmd = '/system/bin/sh ' + cmd.slice('/bin/sh '.length);
  }
  return _origExec.call(this, cmd, options, cb);
};

// ─── os.hostname fallback ──────────────────────────────────────
try { os.hostname(); } catch (e) {
  os.hostname = function () { return process.env.CLAWBOX_HOSTNAME || 'clawbox-phone'; };
}

module.exports = {};
