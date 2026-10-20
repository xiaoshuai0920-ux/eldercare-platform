import { ref } from 'vue'

const STORAGE_KEY = 'eldercare-senior-mode'
const senior = ref(false)

function apply(enabled: boolean) {
  senior.value = enabled
  document.documentElement.dataset.senior = enabled ? 'on' : 'off'
  localStorage.setItem(STORAGE_KEY, enabled ? 'on' : 'off')
}

/** 在 App 挂载前调用，保证刷新后与登录页也能保持适老模式。 */
export function initSeniorMode() {
  apply(localStorage.getItem(STORAGE_KEY) === 'on')
}

export function useSeniorMode() {
  return {
    senior,
    toggle: () => apply(!senior.value),
    setSenior: (enabled: boolean) => apply(enabled),
  }
}
