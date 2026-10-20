<template><WorkspaceLayout :role="role"><p class="eyebrow">站内消息</p><h1 class="page-title">需要关注的动态。</h1><p class="muted">消息只提示事件；具体操作仍以当前权限和业务状态为准。</p><p v-if="error" class="error" role="alert">{{ error }}</p><div class="list"><article v-for="message in messages" :key="message.id" class="surface message"><div><span class="eyebrow">{{ priorityName(message.priority) }} · {{ message.read_at?'已读':'未读' }}</span><h2>{{ message.title }}</h2><p>{{ message.body }}</p></div><button class="text-button" @click="open(message)">查看相关页面 →</button></article><div v-if="!messages.length&&!error" class="empty">暂无站内消息。</div></div></WorkspaceLayout></template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import WorkspaceLayout from '../layouts/WorkspaceLayout.vue'
import { useSessionStore } from '../stores/session'
import { api } from '../api/client'
type Message={id:number;title:string;body:string;target_path:string;priority:string;read_at:string|null}
const session=useSessionStore(),router=useRouter(),messages=ref<Message[]>([]),error=ref('')
const role=computed(()=>(session.user?.role||'USER') as 'USER'|'WORKER'|'ADMIN')
function priorityName(value:string){return ({NORMAL:'普通消息',ACTION:'待处理',HIGH:'高优先级'} as Record<string,string>)[value]||value}
async function open(message:Message){try{await api(`/notifications/${message.id}/read`,{method:'POST'});await router.push(message.target_path)}catch(reason){ElMessage.error(reason instanceof Error?reason.message:'消息已失效或无权查看');await load()}}
async function load(){try{messages.value=await api<Message[]>('/notifications');error.value=''}catch(reason){error.value=reason instanceof Error?reason.message:'消息加载失败'}}
onMounted(load)
</script>
<style scoped>.list{display:grid;gap:15px;margin-top:34px}.message{display:flex;justify-content:space-between;align-items:center;gap:20px;padding:25px}.message h2{font-size:22px;margin:9px 0}.message p{margin:0;color:#62786d}.error{color:#b34939}@media(max-width:650px){.message{align-items:flex-start;flex-direction:column}}</style>
