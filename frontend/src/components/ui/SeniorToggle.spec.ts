import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import SeniorToggle from './SeniorToggle.vue'

describe('SeniorToggle', () => {
  beforeEach(() => setActivePinia(createPinia()))
  afterEach(() => {
    document.documentElement.removeAttribute('data-senior')
    window.localStorage.clear()
  })

  it('初始为关闭，点击后开启并写入 html 属性', async () => {
    const wrapper = mount(SeniorToggle, { global: { plugins: [createPinia()] } })
    const button = wrapper.get('button')
    expect(button.attributes('aria-pressed')).toBe('false')

    await button.trigger('click')
    expect(wrapper.get('button').attributes('aria-pressed')).toBe('true')
    expect(document.documentElement.getAttribute('data-senior')).toBe('on')
    expect(wrapper.text()).toContain('已开')
  })

  it('再次点击可关闭并移除属性', async () => {
    const wrapper = mount(SeniorToggle, { global: { plugins: [createPinia()] } })
    await wrapper.get('button').trigger('click')
    await wrapper.get('button').trigger('click')

    expect(wrapper.get('button').attributes('aria-pressed')).toBe('false')
    expect(document.documentElement.hasAttribute('data-senior')).toBe(false)
  })
})
