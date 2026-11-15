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
    lib: { entry: resolve(__dirname, '../src/utils/apiPath.ts'), formats: ['cjs'] }
  }
})).then(output => (Array.isArray(output) ? output[0] : output).output[0].code)

async function loadModule() {
  const module = { exports: {} }
  runInNewContext(await compiled, { exports: module.exports, module })
  return module.exports
}

test('uses the test API prefix for admin binary requests', async () => {
  const { resolveApiPath } = await loadModule()
  assert.equal(resolveApiPath('/api/admin/activities/11/share-qr.png', '/test-api/api'), '/test-api/api/admin/activities/11/share-qr.png')
  assert.equal(resolveApiPath('/api/admin/exports/users?phone=138', '/test-api/api/'), '/test-api/api/admin/exports/users?phone=138')
})

test('keeps production paths and absolute URLs valid', async () => {
  const { resolveApiPath } = await loadModule()
  assert.equal(resolveApiPath('/api/admin/activities/11/share-qr.png', '/api'), '/api/admin/activities/11/share-qr.png')
  assert.equal(resolveApiPath('/admin/activities/11/share-qr.png', '/api'), '/api/admin/activities/11/share-qr.png')
  assert.equal(resolveApiPath('https://example.com/file.png', '/test-api/api'), 'https://example.com/file.png')
})
