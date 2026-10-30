/** 内联描边图标库：24 网格 / stroke 1.75 / 跟随 currentColor */
export const ICONS = {
  home: '<path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z"/><path d="M9.5 21v-6h5v6"/>',
  list: '<path d="M4 7h16M4 12h16M4 17h10"/><circle cx="18" cy="17" r="2.5"/>',
  folder:
    '<path d="M4 6.5A2.5 2.5 0 0 1 6.5 4h4l2 2.5h5A2.5 2.5 0 0 1 20 9v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"/>',
  users:
    '<circle cx="9" cy="8.5" r="3.5"/><path d="M3.5 20c0-3 2.5-5 5.5-5s5.5 2 5.5 5"/><path d="M16 5.5a3.5 3.5 0 0 1 0 6.6M17.5 20c0-2.4-.8-4-2.4-5.2"/>',
  message: '<path d="M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v7A2.5 2.5 0 0 1 17.5 16H9l-5 4z"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  calendar:
    '<rect x="3.5" y="5" width="17" height="15.5" rx="2.5"/><path d="M8 3v4M16 3v4M3.5 10h17"/><path d="M9 14h2M13 14h2"/>',
  meal: '<path d="M6 3v7a2.5 2.5 0 0 0 5 0V3"/><path d="M8.5 12v9"/><path d="M16.5 3c-1.5 1.5-2 3-2 5s.7 3 2 3.5V21"/>',
  broom: '<path d="M17 3.5 20.5 7l-3 3-3.5-3.5z"/><path d="M14.5 8.5 6 17l1 3 3 1 8.5-8.5"/>',
  bag: '<rect x="3.5" y="7.5" width="17" height="12.5" rx="2.5"/><path d="M9 7.5V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5v2"/><path d="M12 11v5M9.5 13.5h5"/>',
  pill: '<path d="M9 4h6v3a4 4 0 0 1 4 4v3a5 5 0 0 1-5 5h-4a5 5 0 0 1-5-5v-3a4 4 0 0 1 4-4z"/><path d="M9 20h6"/>',
  card: '<rect x="3" y="6" width="18" height="13" rx="2.5"/><path d="M3 10h18M7 15h3"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7"/>',
  shield: '<path d="M12 3.5 19.5 6v6c0 4.5-3 7.5-7.5 9-4.5-1.5-7.5-4.5-7.5-9V6z"/><path d="m9 12 2 2 4-4"/>',
  user: '<circle cx="12" cy="8.5" r="4"/><path d="M4.5 20.5c0-3.8 3.4-6 7.5-6s7.5 2.2 7.5 6"/>',
  logout:
    '<path d="M15 4.5H6.5A1.5 1.5 0 0 0 5 6v12a1.5 1.5 0 0 0 1.5 1.5H15"/><path d="M18.5 12H10M15.5 8.5 19 12l-3.5 3.5"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  chevronDown: '<path d="m6 9 6 6 6-6"/>',
  chevronRight: '<path d="m9 5 7 7-7 7"/>',
  alert: '<path d="M12 4.5 21 20H3z"/><path d="M12 10v4.5M12 17.5h.01"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.8h.01"/>',
  refresh: '<path d="M20 12a8 8 0 1 1-2.6-5.9"/><path d="M20 4v4.5h-4.5"/>',
  star: '<path d="m12 4 2.4 5 5.6.8-4 3.9 1 5.5-5-2.6-5 2.6 1-5.5-4-3.9 5.6-.8z"/>',
  inbox: '<path d="M4 13.5h4l1.5 3h5l1.5-3h4"/><path d="M5.5 5h13l1.5 8.5V19H4v-5.5z"/>',
  pin: '<path d="M12 21s6.5-6 6.5-11a6.5 6.5 0 0 0-13 0C5.5 15 12 21 12 21Z"/><circle cx="12" cy="10" r="2.5"/>',
} as const

export type IconName = keyof typeof ICONS
