const assert = require('node:assert/strict')
const { resolve } = require('node:path')
const { test } = require('node:test')
const { runInNewContext } = require('node:vm')
const compiled = import('vite').then(({ build }) => build({
  configFile: false,
  logLevel: 'silent',
  build: {
    write: false,
    minify: false,
    lib: { entry: resolve(__dirname, '../src/utils/imageUpload.ts'), formats: ['cjs'] },
    rolldownOptions: { external: ['vue', 'element-plus'] }
  }
})).then(output => (Array.isArray(output) ? output[0] : output).output[0].code)

async function harness(fetch) {
  const state = { opened: 0, closed: 0, timers: new Map(), listeners: new Map() }
  const module = { exports: {} }
  runInNewContext(await compiled, {
    exports: module.exports,
    module,
    require(name) {
      if (name === 'vue') return { ref: value => ({ value }), readonly: value => value }
      if (name === 'element-plus') return { ElLoading: { service(options) {
        assert.equal(options.fullscreen, true)
        assert.equal(options.lock, true)
        state.opened++
        return { close: () => state.closed++ }
      } } }
      throw new Error(`Unexpected import: ${name}`)
    },
    AbortController,
    FormData: class { append() {} },
    localStorage: { getItem: () => 'test-token' },
    document: {
      addEventListener: (event, callback) => state.listeners.set(event, callback),
      removeEventListener: event => state.listeners.delete(event)
    },
    window: {
      setTimeout: (callback, milliseconds) => {
        assert.equal(milliseconds, 30_000)
        state.timers.set(1, callback)
        return 1
      },
      clearTimeout: id => state.timers.delete(id)
    },
    fetch
  })
  return { ...module.exports, state }
}

function assertUnlocked(upload) {
  assert.equal(upload.imageUploading.value, false)
  assert.equal(upload.state.opened, upload.state.closed)
  assert.equal(upload.state.timers.size, 0)
  assert.equal(upload.state.listeners.size, 0)
}

test('successful upload locks the UI and releases it after returning the image URL', async () => {
  const result = { success: true, data: { url: '/uploads/test.jpg' } }
  const upload = await harness(async (url, options) => {
    assert.equal(url, '/api/admin/uploads')
    assert.equal(options.headers.Authorization, 'Bearer test-token')
    assert.equal(options.method, 'POST')
    return { ok: true, json: async () => result }
  })
  const request = upload.uploadAdminImage({ name: 'test.jpg' })
  assert.equal(upload.imageUploading.value, true)
  assert.equal(upload.state.listeners.has('keydown'), true)
  let prevented = false
  upload.state.listeners.get('keydown')({
    preventDefault: () => { prevented = true }, stopImmediatePropagation() {}
  })
  assert.equal(prevented, true)
  assert.deepEqual(await request, result)
  assertUnlocked(upload)
})

test('server errors and network failures restore all controls', async () => {
  for (const fetch of [
    async () => ({ ok: false, json: async () => ({ message: '图片上传失败' }) }),
    async () => { throw new Error('Network error') },
    async () => ({ ok: true, json: async () => ({ success: true, data: {} }) })
  ]) {
    const upload = await harness(fetch)
    await assert.rejects(upload.uploadAdminImage({}), /图片上传失败|Network error/)
    assertUnlocked(upload)
  }
})

test('timeout aborts the request and restores controls with the timeout message', async () => {
  let signal
  const upload = await harness((url, options) => {
    signal = options.signal
    return new Promise((resolve, reject) => {
      signal.addEventListener('abort', () => reject(new Error('Aborted')))
    })
  })
  const request = upload.uploadAdminImage({})
  upload.state.timers.get(1)()
  await assert.rejects(request, /图片上传超时/)
  assert.equal(signal.aborted, true)
  assertUnlocked(upload)
  // A subsequent upload is allowed after the timed-out upload has settled.
  const retry = upload.uploadAdminImage({})
  upload.state.timers.get(1)()
  await assert.rejects(retry, /图片上传超时/)
  assertUnlocked(upload)
})

test('a concurrent upload cannot release the first upload lock', async () => {
  let complete
  const upload = await harness(() => new Promise(resolve => { complete = resolve }))
  const first = upload.uploadAdminImage({})
  await assert.rejects(upload.uploadAdminImage({}), /已有图片正在上传/)
  assert.equal(upload.imageUploading.value, true)
  assert.equal(upload.state.closed, 0)
  complete({ ok: true, json: async () => ({ success: true, data: { url: '/uploads/test.jpg' } }) })
  await first
  assertUnlocked(upload)
})
