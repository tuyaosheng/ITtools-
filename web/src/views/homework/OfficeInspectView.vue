<template>
  <div class="inspect">
    <template v-if="data.kind === 'docx'">
      <div class="page">
        <span>纸张：{{ data.page.orient }} {{ data.page.size }}</span>
        <span>页边距：{{ data.page.margins }}</span>
        <span>分栏：{{ data.page.columns }}</span>
        <span>页眉：{{ data.page.header || '无' }}</span>
        <span>页脚：{{ data.page.footer || '无' }}{{ data.page.pageNumber ? '（有页码）' : '' }}</span>
        <span>图片：{{ data.page.images }}</span>
        <span>表格：{{ data.page.tables.join('；') || '无' }}</span>
      </div>
      <el-table :data="data.paragraphs" size="small" border max-height="460">
        <el-table-column prop="index" label="段" width="44" />
        <el-table-column prop="text" label="内容" min-width="180" show-overflow-tooltip />
        <el-table-column prop="font" label="字体" width="130" />
        <el-table-column prop="size" label="字号" width="110" />
        <el-table-column label="加粗/倾斜/下划线" width="120">
          <template #default="{ row }: { row: any }">{{ yn(row.bold) }} / {{ yn(row.italic) }} / {{ yn(row.underline) }}</template>
        </el-table-column>
        <el-table-column prop="color" label="颜色" width="80" />
        <el-table-column prop="align" label="对齐" width="80" />
        <el-table-column prop="indent" label="首行缩进" width="80" />
        <el-table-column prop="lineSpacing" label="行距" width="90" />
        <el-table-column label="段前/段后" width="100"><template #default="{ row }: { row: any }">{{ row.spaceBefore }} / {{ row.spaceAfter }}</template></el-table-column>
      </el-table>
    </template>

    <template v-else-if="data.kind === 'xlsx'">
      <el-tabs>
        <el-tab-pane v-for="s in data.sheets" :key="s.name" :label="s.name">
          <div class="page">
            <span>合并：{{ s.merged.join('、') || '无' }}</span>
            <span>图表：{{ s.charts.join('、') || '无' }}</span>
            <span>自动筛选：{{ yn(s.autoFilter) }}</span>
            <span>条件格式：{{ s.conditionalFormats }} 个</span>
          </div>
          <el-table :data="s.cells" size="small" border max-height="420">
            <el-table-column prop="ref" label="单元格" width="70" />
            <el-table-column prop="value" label="值" min-width="120" show-overflow-tooltip />
            <el-table-column prop="formula" label="公式" min-width="120" show-overflow-tooltip />
            <el-table-column prop="font" label="字体" width="150" />
            <el-table-column prop="color" label="颜色" width="80" />
            <el-table-column prop="fill" label="底纹" width="80" />
            <el-table-column prop="align" label="对齐" width="90" />
            <el-table-column prop="format" label="数字格式" width="100" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>

    <template v-else-if="data.kind === 'pptx'">
      <el-collapse>
        <el-collapse-item v-for="s in data.slides" :key="s.index" :name="s.index">
          <template #title>
            <b>第 {{ s.index }} 张</b>
            <span class="meta">版式：{{ s.layout }}</span>
            <span class="meta">切换：{{ s.transition || '无' }}</span>
            <span class="meta">动画：{{ yn(s.animation) }}</span>
            <span class="meta">背景：{{ yn(s.background) }}</span>
            <span class="meta">图片：{{ s.images }}</span>
          </template>
          <div v-if="s.links.length" class="page"><span>超链接：{{ s.links.join('、') }}</span></div>
          <el-table :data="s.texts" size="small" border>
            <el-table-column prop="text" label="文字" min-width="180" />
            <el-table-column prop="font" label="字体" width="140" />
            <el-table-column prop="size" label="字号" width="80" />
            <el-table-column label="加粗" width="60"><template #default="{ row }: { row: any }">{{ yn(row.bold) }}</template></el-table-column>
            <el-table-column prop="color" label="颜色" width="90" />
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </template>
    <p class="tip">段落编号 = 评分点中的“第几段”（只数非空正文段落）；颜色为十六进制，评分点里也可以写“红色”“蓝色”等颜色名。</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{ data: any }>()
const yn = (b: boolean) => (b ? '是' : '否')
</script>

<style scoped>
.page { display: flex; flex-wrap: wrap; gap: 6px 16px; font-size: 12px; color: var(--el-text-color-regular); margin-bottom: 8px; }
.meta { margin-left: 12px; font-size: 12px; color: var(--el-text-color-secondary); }
.tip { font-size: 12px; color: var(--el-text-color-secondary); margin: 8px 0 0; }
</style>
