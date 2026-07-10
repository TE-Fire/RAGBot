<!--
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-10 10:06:57
 * @Description: 将父组件传递的 MD 格式文本实时渲染为 HTML
-->
<template>
  <div class="markdown-container">
    <div v-html="renderedContent">
    </div>    
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'

// 定义一个 content 字段，用于父组件传入 markdown 文本
const props = defineProps({
  content: {
    type: String,
    default: ''
  }
})

// 解析后的 HTML
const renderedContent = ref('')

// 初始化 MarkdownIt
const md = new MarkdownIt({
  html: true,        // 允许解析 HTML 标签
  xhtmlOut: true,    // 输出符合 XHTML 规范的标签（如 `<br />` 而不是 `<br>`）。默认 false。
  linkify: true,     // 自动将文本中的 URL 转换为可点击的链接
  typographer: true, // 启用排版优化
  breaks: true,       // 将单个换行符 (\n) 转换为 <br>
  langPrefix: 'language-', // 代码块的语言类名前缀（默认 'language-'）。例如 ```js 会生成 <pre><code class="language-js">
})

// 监听 content 字段，流式更新处理
watch(() => props.content, (newVal) => {
  if (newVal) {
    // 渲染为 HTML
    const html = md.render(newVal)
    renderedContent.value = html
  }
}, { immediate: true })
</script>

<style scoped>
.markdown-container {
  width: 100%;
  line-height: 24px;
  color: rgb(64 64 64);
}

/* 第一个 p 标签的上边距设置为0 */
:deep(.markdown-container > p:first-child),
:deep(p:first-child) {
  margin-top: 0;
}

/* Markdown 转换为 HTML 的样式 */

/* 修复标题选择器 - 使用逗号分隔多个选择器 */
:deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
  font-weight: 600;
  margin: calc(1.143 * 16px) 0 calc(1.143 * 12px) 0;
}

:deep(h1) {
  font-size: 1.5em;
  margin-top: 1.2em;
  margin-bottom: 0.7em;
  line-height: 1.5;
}

:deep(h2) {
  font-size: 1.3em;
  margin-top: 1.1em;
  margin-bottom: 0.6em;
  line-height: 1.5;
}

:deep(h3) {
  font-size: calc(1.143 * 16px);
  line-height: 1.5;
}

:deep(p) {
  line-height: 1.7;
  margin: calc(1.143 * 12px) 0;
  font-size: calc(1.143* 14px);
}

:deep(ul) {
  list-style: disc; /* 实心圆点 */
  margin-top: 0.6em;
  margin-bottom: 0.9em;
  padding-left: 2em;
}

:deep(ol) {
  list-style: decimal;
  margin-top: 0.6em;
  margin-bottom: 0.9em;
  padding-left: 2em;
}

/* 列表项样式 */
:deep(li) {
  margin-bottom: 0.5em;
  line-height: 1.7;
}

/* 修复列表标记样式 */
:deep(ol li::marker) {
  line-height: calc(1.143 * 25px);
  color: rgb(139 139 139);
}

:deep(ul li::marker) {
  color: rgb(139 139 139);
}

/* 嵌套列表样式 */
:deep(ul ul) {
  list-style: circle;
  margin-top: 0.3em;
  margin-bottom: 0.3em;
}

:deep(ul ul ul) {
  list-style: square; /* 三级列表使用方块 */
}

:deep(pre) {
  background-color: #f5f5f5;
  padding: 1em;
  border-radius: 5px;
  overflow-x: auto;
  margin: 1em 0;
  max-width: 100%; /* 确保不超过容器宽度 */
  white-space: pre; /* 保持原始格式 */
  word-wrap: normal; /* 不在单词内部换行 */
}

/* 单独的 code 标签样式 - 不在 pre 内的code */
:deep(:not(pre) > code) {
  font-size: .875em;
  font-weight: 600;
  background-color: #ececec;
  border-radius: 4px;
  padding: .15rem .3rem;
  margin: 0 .2rem;
}

/* pre 内的 code 标签样式 */
:deep(pre > code) {
  font-size: .875em;
  background-color: transparent;
  padding: 0;
  border-radius: 0;
  font-family: monospace;
  font-weight: normal;
  color: #333;
  display: block;
  width: 100%;
}

:deep(a) {
  color: #4d6bfe;
  text-decoration: none;
}

:deep(a:hover) {
  text-decoration: underline;
}

:deep(blockquote) {
  border-left: 4px solid #e5e5e5;
  padding-left: 1em;
  margin: 1em 0;
  color: #666;
}

:deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
  font-size: 0.95em;
}

:deep(th), :deep(td) {
  border: 1px solid #e5e5e5;
  padding: 0.6em;
  text-align: left;
}

:deep(th) {
  background-color: #f5f5f5;
}

:deep(hr) {
  background-color: rgb(229 229 229);
  margin: 1.5em 0;
  height: 1px;
  border: none;
}

/* 确保相邻元素之间的间距一致且适当 */
:deep(h1 + p),
:deep(h2 + p),
:deep(h3 + p) {
  margin-top: 0.5em;
}

:deep(p + ul),
:deep(p + ol) {
  margin-top: 0.5em;
}

:deep(ul + p),
:deep(ol + p) {
  margin-top: 0.7em;
}

</style>
