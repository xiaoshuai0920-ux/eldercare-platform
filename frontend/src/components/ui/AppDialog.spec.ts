import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import AppDialog from './AppDialog.vue'

const flushFrame = () => new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))

describe('AppDialog', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('以 dialog 语义渲染标题与内容', async () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, title: '确认完成', subtitle: '确认后进入结算' },
      slots: { default: '<p>正文</p>' },
      attachTo: document.body,
    })
    await nextTick()

    const dialog = document.body.querySelector('[role="dialog"]')
    expect(dialog).not.toBeNull()
    expect(dialog?.getAttribute('aria-modal')).toBe('true')
    expect(document.body.textContent).toContain('确认完成')
    expect(document.body.textContent).toContain('正文')
    wrapper.unmount()
  })

  it('按 Esc 请求关闭', async () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, title: '标题' },
      attachTo: document.body,
    })
    await nextTick()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    wrapper.unmount()
  })

  it('打开时接管焦点，关闭后归还触发元素', async () => {
    const trigger = document.createElement('button')
    trigger.textContent = '打开'
    document.body.appendChild(trigger)
    trigger.focus()

    const wrapper = mount(AppDialog, {
      props: { modelValue: true, title: '标题' },
      attachTo: document.body,
    })
    await nextTick()
    await flushFrame()
    const panel = document.body.querySelector('.dialog')
    await vi.waitFor(() => expect(document.activeElement).toBe(panel))

    await wrapper.setProps({ modelValue: false })
    await nextTick()
    expect(document.activeElement).toBe(trigger)
    wrapper.unmount()
  })

  it('关闭状态对辅助技术隐藏', async () => {
    const wrapper = mount(AppDialog, { props: { modelValue: false, title: '标题' }, attachTo: document.body })
    await nextTick()
    const dialog = document.body.querySelector('[role="dialog"]')
    expect(dialog?.getAttribute('aria-hidden')).toBe('true')
    expect(dialog?.classList.contains('is-open')).toBe(false)
    wrapper.unmount()
  })
})
