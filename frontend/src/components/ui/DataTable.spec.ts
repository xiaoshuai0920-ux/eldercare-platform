import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DataTable from './DataTable.vue'

const columns = [
  { key: 'id', label: '订单号' },
  { key: 'elder_name', label: '老人' },
  { key: 'amount', label: '金额', align: 'right' as const },
]

const rows = [
  { id: 61, elder_name: '张桂芳', amount: 45 },
  { id: 60, elder_name: '陈志强', amount: 180 },
]

describe('DataTable', () => {
  it('渲染表头与数据行', () => {
    const wrapper = mount(DataTable, { props: { columns, rows, caption: '订单列表' } })
    const headers = wrapper.findAll('th').map((item) => item.text())
    expect(headers).toEqual(['订单号', '老人', '金额'])
    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
    expect(wrapper.text()).toContain('张桂芳')
  })

  it('每个单元格带 data-label，便于窄屏降级为卡片', () => {
    const wrapper = mount(DataTable, { props: { columns, rows } })
    const firstRowCells = wrapper.findAll('tbody tr')[0].findAll('td')
    expect(firstRowCells.map((cell) => cell.attributes('data-label'))).toEqual(['订单号', '老人', '金额'])
  })

  it('数字列带对齐与等宽数字样式类', () => {
    const wrapper = mount(DataTable, { props: { columns, rows } })
    const amountCell = wrapper.findAll('tbody tr')[0].findAll('td')[2]
    expect(amountCell.classes()).toContain('cell-num')
    expect(amountCell.classes()).toContain('num')
  })

  it('空数据展示空态而不是空表', () => {
    const wrapper = mount(DataTable, {
      props: { columns, rows: [], emptyTitle: '没有符合条件的订单', emptyDesc: '换个筛选条件' },
    })
    expect(wrapper.text()).toContain('没有符合条件的订单')
    expect(wrapper.find('tbody').exists()).toBe(false)
  })

  it('加载态只显示骨架，不渲染表格', () => {
    const wrapper = mount(DataTable, { props: { columns, rows, loading: true } })
    expect(wrapper.find('table').exists()).toBe(false)
  })
})
