const prefix = import.meta.env.BASE_URL === '/'
  ? ''
  : `admin:${import.meta.env.BASE_URL.replace(/^\/+|\/+$/g, '')}:`

export function adminStorageKey(name: string) {
  return `${prefix}${name}`
}

export function getAdminStorage(name: string) {
  return localStorage.getItem(adminStorageKey(name))
}

export function setAdminStorage(name: string, value: string) {
  localStorage.setItem(adminStorageKey(name), value)
}

export function clearAdminSession() {
  for (const name of ['adminToken', 'adminName', 'adminRole']) {
    localStorage.removeItem(adminStorageKey(name))
  }
}
