import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PaginationBar from './PaginationBar.vue'

const base = { total: 45, size: 10, disabled: false }

describe('PaginationBar', () => {
  it('显示总条数与总页数', () => {
    const wrapper = mount(PaginationBar, { props: { ...base, page: 1 } })
    expect(wrapper.text()).toContain('共 45 条')
    expect(wrapper.text()).toContain('第 1 / 5 页')
  })

  it('第一页禁用上一页，最后一页禁用下一页', async () => {
    const first = mount(PaginationBar, { props: { ...base, page: 1 } })
    expect(first.findAll('button')[0].attributes('disabled')).toBeDefined()

    const last = mount(PaginationBar, { props: { ...base, page: 5 } })
    const buttons = last.findAll('button')
    expect(buttons[buttons.length - 1].attributes('disabled')).toBeDefined()
  })

  it('点击下一页抛出新页码', async () => {
    const wrapper = mount(PaginationBar, { props: { ...base, page: 2 } })
    const next = wrapper.findAll('button').at(-1)!
    await next.trigger('click')
    expect(wrapper.emitted('change')?.[0]).toEqual([3])
  })

  it('当前页带 aria-current 且不会重复触发', async () => {
    const wrapper = mount(PaginationBar, { props: { ...base, page: 3 } })
    const current = wrapper.findAll('button').find((item) => item.attributes('aria-current') === 'page')
    expect(current?.text()).toBe('3')

    await current!.trigger('click')
    expect(wrapper.emitted('change')).toBeUndefined()
  })

  it('页数较多时折叠中间页码', () => {
    const wrapper = mount(PaginationBar, { props: { page: 25, total: 500, size: 10 } })
    const gaps = wrapper.findAll('.pager__gap')
    expect(gaps.length).toBe(2)
    expect(wrapper.text()).toContain('1')
    expect(wrapper.text()).toContain('50')
  })

  it('没有数据时不渲染分页条', () => {
    const wrapper = mount(PaginationBar, { props: { page: 1, total: 0, size: 10 } })
    expect(wrapper.find('nav').exists()).toBe(false)
  })

  it('禁用时点击无效', async () => {
    const wrapper = mount(PaginationBar, { props: { page: 2, total: 45, size: 10, disabled: true } })
    await wrapper.findAll('button').at(-1)!.trigger('click')
    expect(wrapper.emitted('change')).toBeUndefined()
  })
})
