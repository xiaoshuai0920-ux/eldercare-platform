<script setup lang="ts">
  import { computed, onMounted } from 'vue'
  import { useRouter } from 'vue-router'
  import PageHeader from '@/components/ui/PageHeader.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import AppTag from '@/components/ui/AppTag.vue'
  import AppTabs from '@/components/ui/AppTabs.vue'
  import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
  import ErrorBanner from '@/components/ui/ErrorBanner.vue'
  import EmptyState from '@/components/ui/EmptyState.vue'
  import PaginationBar from '@/components/ui/PaginationBar.vue'
  import { useMessageStore, type MessageFilter } from '@/stores/messages'
  import { useUiStore } from '@/stores/ui'
  import { formatFullDate } from '@/utils/format'

  const messages = useMessageStore()
  const ui = useUiStore()
  const router = useRouter()

  // 分类计数由后端按同一口径返回，仅「全部」显示总数
  const tabs = computed(() => [
    { id: 'all', label: '全部', count: messages.total },
    { id: 'order', label: '订单' },
    { id: 'system', label: '系统' },
  ])

  /** 图标按目标路径归类，与后端 kind 口径一致 */
  function isSystem(target: string): boolean {
    return target.includes('/reminders') || target.includes('/family')
  }

  const active = computed({
    get: () => messages.filter,
    set: (value: string) => messages.setFilter(value as MessageFilter),
  })

  const PRIORITY: Record<string, { label: string; tone: 'danger' | 'action' | 'neutral' }> = {
    HIGH: { label: '高优先级', tone: 'danger' },
    ACTION: { label: '待处理', tone: 'action' },
    NORMAL: { label: '普通', tone: 'neutral' },
  }

  onMounted(() => messages.load())

  async function open(rowId: number, target: string) {
    await messages.markRead(rowId)
    if (target) await router.push(target)
  }

  async function markAll() {
    const count = await messages.markAllRead()
    ui.notify(count ? `已标记 ${count} 条为已读` : '没有未读消息', count ? 'success' : 'default')
  }
</script>

<template>
  <PageHeader
    eyebrow="消息"
    title="派单、服务与结算的变动都会在这里。"
    description="消息来自通知接口；点击后标记已读并跳转对应页面。"
  >
    <template #actions>
      <AppButton variant="secondary" @click="markAll">本页全部标为已读</AppButton>
    </template>
  </PageHeader>

  <div style="margin-top: var(--sp-6)">
    <AppTabs v-model="active" :tabs="tabs" aria-label="消息分类" />

    <ErrorBanner
      v-if="messages.error"
      :title="messages.error"
      description="消息加载失败，请稍后重试。"
      style="margin-top: var(--sp-4)"
    />

    <div class="card" style="margin-top: var(--sp-4); overflow: hidden">
      <div v-if="messages.loading" style="padding: var(--sp-6)"><SkeletonBlock :rows="4" /></div>
      <div v-else-if="!messages.list.length" style="padding: var(--sp-6)">
        <EmptyState title="这个分类下暂时没有消息" description="有新的派单或确认提醒时会出现在这里。" />
      </div>
      <div v-else class="list">
        <article
          v-for="item in messages.list"
          :key="item.id"
          class="list__item"
          :class="{ 'list__item--unread': !item.read_at }"
          style="cursor: pointer"
          @click="open(item.id, item.target_path)"
        >
          <span class="list__icon" aria-hidden="true"
            ><AppIcon :name="isSystem(item.target_path) ? 'shield' : 'message'"
          /></span>
          <div class="list__body">
            <p class="list__title">
              <span v-if="!item.read_at" class="dot-unread" aria-hidden="true" />
              {{ item.title }}
            </p>
            <p class="list__desc">{{ item.body }}</p>
            <p class="list__desc num">{{ formatFullDate(item.created_at) }} · {{ item.target_path }}</p>
          </div>
          <div class="list__end">
            <AppTag v-if="PRIORITY[item.priority]" :tone="PRIORITY[item.priority].tone">{{
              PRIORITY[item.priority].label
            }}</AppTag>
            <span class="linkbtn">{{ item.read_at ? '查看' : '去处理' }}</span>
          </div>
        </article>
      </div>
      <PaginationBar
        :page="messages.paged.page"
        :total="messages.paged.total"
        :size="messages.paged.size"
        :disabled="messages.loading"
        @change="messages.load"
      />
    </div>
  </div>

  <div class="banner banner--info" style="margin-top: var(--sp-7)" role="note">
    <AppIcon name="info" />
    <div class="banner__body">
      <p class="banner__title">消息与业务记录相互印证</p>
      <p class="banner__desc">通知只做提醒，订单状态以订单接口返回为准；两者不一致时以订单为准。</p>
    </div>
  </div>
</template>
