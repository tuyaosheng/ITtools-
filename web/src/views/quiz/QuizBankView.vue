<template>
  <el-card shadow="never">
    <el-tabs v-model="tab" @tab-change="onTab">
      <!-- ===== 题库 ===== -->
      <el-tab-pane label="题库" name="bank">
        <div class="filters">
          <el-radio-group v-model="filter.status" size="small" @change="loadQuestions">
            <el-radio-button label="active">题库</el-radio-button>
            <el-radio-button label="draft">待设置答案<span v-if="stats.drafts">（{{ stats.drafts }}）</span></el-radio-button>
            <el-radio-button label="trash">回收站</el-radio-button>
          </el-radio-group>
          <el-select v-model="filter.qtype" clearable placeholder="全部题型" class="w120" @change="loadQuestions">
            <el-option v-for="(l, v) in typeLabel" :key="v" :label="l" :value="v" />
          </el-select>
          <el-select v-model="filter.category" clearable filterable placeholder="全部分类" class="w140" @change="loadQuestions">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="filter.tag" clearable filterable placeholder="全部标签" class="w140" @change="loadQuestions">
            <el-option v-for="t in tags" :key="t.tag" :label="`${t.tag}（${t.count}）`" :value="t.tag" />
          </el-select>
          <el-select v-model="filter.minStars" clearable placeholder="星级" class="w100" @change="loadQuestions">
            <el-option v-for="n in 5" :key="n" :label="`≥ ${n} 星`" :value="n" />
          </el-select>
          <el-input v-model="filter.keyword" placeholder="搜索题干" clearable class="w160" @change="loadQuestions" />
          <div class="spacer" />
          <el-dropdown @command="onImport">
            <el-button>导入 ▾</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="images">批量传图入库（zip）</el-dropdown-item>
                <el-dropdown-item command="pack">导入题库包</el-dropdown-item>
                <el-dropdown-item command="excel" divided>Excel 导入</el-dropdown-item>
                <el-dropdown-item command="template">下载 Excel 模板</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="primary" @click="openQuestion()">新增题目</el-button>
        </div>

        <div v-if="selected.length" class="batch">
          <span>已选 {{ selected.length }} 题：</span>
          <template v-if="filter.status !== 'trash'">
            <el-dropdown @command="(n: number) => batch({ stars: n })">
              <el-button size="small">设星级 ▾</el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="n in 6" :key="n" :command="n - 1">{{ n - 1 }} 星</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
            <el-button size="small" @click="promptTags('add')">加标签</el-button>
            <el-button size="small" @click="promptTags('remove')">去标签</el-button>
            <el-button size="small" @click="promptCategory">设分类</el-button>
            <el-button size="small" @click="batch({ shared: true })">共享</el-button>
            <el-button size="small" @click="batch({ shared: false })">取消共享</el-button>
            <el-button size="small" @click="exportPack">打包导出</el-button>
            <el-popconfirm title="移入回收站？" @confirm="batch({ trash: true })">
              <template #reference><el-button size="small" type="danger" plain>删除</el-button></template>
            </el-popconfirm>
          </template>
          <template v-else>
            <el-button size="small" @click="restore(selectedIds)">恢复</el-button>
            <el-popconfirm title="彻底删除？已被试卷或作答记录引用的题会保留在回收站" @confirm="purge">
              <template #reference><el-button size="small" type="danger">彻底删除</el-button></template>
            </el-popconfirm>
          </template>
        </div>

        <el-table ref="table" :data="questionList" stripe size="small" row-key="id" @selection-change="(s: any[]) => (selected = s)">
          <el-table-column type="selection" width="36" :selectable="(r: any) => r.teacherId === me" />
          <el-table-column label="题型" width="70"><template #default="{ row }: { row: any }">{{ typeLabel[row.qtype] }}</template></el-table-column>
          <el-table-column label="题干" min-width="260">
            <template #default="{ row }: { row: any }">
              <div class="stem-cell">
                <el-image v-if="row.hasImage" :src="imgUrl(row.id)" :preview-src-list="[imgUrl(row.id)]" fit="contain" class="thumb"
                  preview-teleported :hide-on-click-modal="true" lazy />
                <div class="stem" v-html="safeHtml(row.stem)" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="答案" width="110">
            <template #default="{ row }: { row: any }">
              <el-tag v-if="row.needsAnswer" type="warning" size="small">待设置</el-tag>
              <span v-else>{{ answerText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="星级" width="96">
            <template #default="{ row }: { row: any }"><span class="stars">{{ '★'.repeat(row.stars) }}<span class="off">{{ '★'.repeat(5 - row.stars) }}</span></span></template>
          </el-table-column>
          <el-table-column label="标签 / 分类" min-width="140">
            <template #default="{ row }: { row: any }">
              <el-tag v-for="t in row.tags" :key="t" size="small" effect="plain" class="mr4">{{ t }}</el-tag>
              <span v-if="row.category" class="muted">{{ row.category }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="分值" width="55" />
          <el-table-column label="来源" width="90">
            <template #default="{ row }: { row: any }">
              <el-tag v-if="row.teacherId !== me" size="small" type="info">{{ row.teacherName }}</el-tag>
              <el-tag v-else-if="row.shared" size="small">共享</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }: { row: any }">
              <template v-if="row.teacherId === me">
                <template v-if="filter.status === 'trash'">
                  <el-button link type="primary" @click="restore([row.id])">恢复</el-button>
                </template>
                <template v-else>
                  <el-button link type="primary" @click="openQuestion(row)">{{ row.needsAnswer ? '设置答案' : '编辑' }}</el-button>
                  <el-popconfirm title="移入回收站？" @confirm="delQuestion(row)">
                    <template #reference><el-button link type="danger">删除</el-button></template>
                  </el-popconfirm>
                </template>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <div class="muted foot">共 {{ questionList.length }} 题{{ questionList.length >= 1000 ? '（最多显示 1000 题，请用筛选缩小范围）' : '' }}</div>
      </el-tab-pane>

      <!-- ===== 试卷 ===== -->
      <el-tab-pane label="试卷" name="papers">
        <div class="filters">
          <div class="spacer" />
          <el-button type="primary" @click="openPaper()">新建试卷</el-button>
        </div>
        <el-table :data="paperList" stripe>
          <el-table-column prop="name" label="试卷名称" min-width="200" />
          <el-table-column prop="questionCount" label="题数" width="70" />
          <el-table-column prop="totalScore" label="总分" width="70" />
          <el-table-column label="作者" width="120">
            <template #default="{ row }: { row: any }">{{ row.teacherName }}<el-tag v-if="row.shared" size="small" class="ml">共享</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="230">
            <template #default="{ row }: { row: any }">
              <el-button link type="primary" @click="openPublish(row)">发布测验</el-button>
              <template v-if="row.teacherId === me">
                <el-button link type="primary" @click="openPaper(row)">编辑</el-button>
                <el-popconfirm title="删除该试卷？" @confirm="delPaper(row)">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ===== 刷题与分析 ===== -->
      <el-tab-pane label="刷题与分析" name="practice" lazy>
        <PracticeAdmin />
      </el-tab-pane>

      <!-- ===== 统计与标签 ===== -->
      <el-tab-pane label="统计与标签" name="stats">
        <div class="stat-cards">
          <div class="stat"><b>{{ stats.total ?? 0 }}</b><span>题目总数</span></div>
          <div class="stat"><b>{{ stats.withImage ?? 0 }}</b><span>图片题</span></div>
          <div class="stat warn"><b>{{ stats.drafts ?? 0 }}</b><span>待设置答案</span></div>
          <div class="stat"><b>{{ stats.shared ?? 0 }}</b><span>已共享</span></div>
          <div class="stat muted-card"><b>{{ stats.trash ?? 0 }}</b><span>回收站</span></div>
        </div>
        <div class="stat-row">
          <div>
            <div class="sub-title">按题型</div>
            <div v-for="t in stats.byType || []" :key="t.qtype" class="kv"><span>{{ typeLabel[t.qtype] }}</span><b>{{ t.count }}</b></div>
          </div>
          <div>
            <div class="sub-title">按星级</div>
            <div v-for="t in stats.byStars || []" :key="t.stars" class="kv"><span class="stars">{{ t.stars ? '★'.repeat(t.stars) : '未评星' }}</span><b>{{ t.count }}</b></div>
          </div>
          <div class="tag-admin">
            <div class="sub-title">标签维护 <span class="muted">（改名与已有标签同名时自动合并）</span></div>
            <el-empty v-if="!tags.length" description="还没有标签" :image-size="40" />
            <div v-for="t in tags" :key="t.tag" class="kv">
              <span>{{ t.tag }} <span class="muted">{{ t.count }} 题</span></span>
              <span>
                <el-button link type="primary" size="small" @click="renameTag(t.tag)">改名</el-button>
                <el-popconfirm :title="`从所有题目上移除标签「${t.tag}」？题目本身不会删除`" @confirm="deleteTag(t.tag)">
                  <template #reference><el-button link type="danger" size="small">删除</el-button></template>
                </el-popconfirm>
              </span>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 题目编辑 -->
    <el-dialog v-model="qVisible" :title="qForm.id ? (qForm.needsAnswer ? '设置答案（入库处理）' : '编辑题目') : '新增题目'" width="720px" top="4vh" append-to-body>
      <el-form :model="qForm" label-width="70px">
        <el-form-item label="题型">
          <el-radio-group v-model="qForm.qtype">
            <el-radio-button v-for="(l, v) in typeLabel" :key="v" :value="v">{{ l }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题干图片">
          <div class="img-edit">
            <el-image v-if="qForm.hasImage && !pendingImageUrl" :src="imgUrl(qForm.id) + '?v=' + imgVersion" :preview-src-list="[imgUrl(qForm.id)]"
              fit="contain" class="big-img" preview-teleported />
            <img v-else-if="pendingImageUrl" :src="pendingImageUrl" class="big-img" />
            <div class="img-ops">
              <label class="el-button el-button--small file-btn">
                {{ qForm.hasImage || pendingImageUrl ? '更换图片' : '上传图片' }}
                <input type="file" accept="image/*" hidden @change="pickImage" />
              </label>
              <el-button v-if="qForm.hasImage && qForm.id" size="small" @click="removeImage">移除图片</el-button>
              <span class="muted">图片可代替题干文字，选项内容可以都在图片里</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="题干"><el-input v-model="qForm.stem" type="textarea" :rows="2" :placeholder="qForm.hasImage || pendingImageUrl ? '有图片时可不填' : ''" /></el-form-item>
        <template v-if="qForm.qtype === 'SINGLE' || qForm.qtype === 'MULTI'">
          <el-form-item v-for="(o, i) in qForm.options" :key="i" :label="'选项' + letter(i)">
            <div class="opt-row">
              <el-input v-model="qForm.options[i]" :placeholder="qForm.hasImage || pendingImageUrl ? '选项在图片中时可不填' : ''" />
              <el-radio v-if="qForm.qtype === 'SINGLE'" v-model="qForm.answer" :value="letter(i)">正确</el-radio>
              <el-checkbox v-else :model-value="(qForm.answer || '').includes(letter(i))" @change="(v: any) => toggleMulti(letter(i), !!v)">正确</el-checkbox>
              <el-button link type="danger" :disabled="qForm.options.length <= 2" @click="removeOption(i)">删</el-button>
            </div>
          </el-form-item>
          <el-form-item><el-button size="small" :disabled="qForm.options.length >= 8" @click="qForm.options.push('')">+ 添加选项</el-button></el-form-item>
        </template>
        <el-form-item v-if="qForm.qtype === 'JUDGE'" label="答案">
          <el-radio-group v-model="qForm.answer"><el-radio value="T">对</el-radio><el-radio value="F">错</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item v-if="qForm.qtype === 'FILL'" label="答案">
          <div class="fill-answers">
            <div v-for="(b, i) in qForm.blanks" :key="i" class="opt-row">
              <el-input v-model="qForm.blanks[i]" :placeholder="`第 ${i + 1} 空答案，多个可接受答案用 | 分隔`" />
              <el-button link type="danger" :disabled="qForm.blanks.length <= 1" @click="qForm.blanks.splice(i, 1)">删</el-button>
            </div>
            <el-button size="small" @click="qForm.blanks.push('')">+ 添加一空</el-button>
          </div>
        </el-form-item>
        <el-form-item label="解析"><el-input v-model="qForm.analysis" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="星级">
          <el-rate v-model="qForm.stars" clearable />
          <span class="ml muted">按难度或重要程度自行约定</span>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="qForm.tags" multiple filterable allow-create default-first-option placeholder="输入后回车可新建标签" class="full">
            <el-option v-for="t in tags" :key="t.tag" :label="t.tag" :value="t.tag" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="qForm.category" filterable allow-create clearable placeholder="知识点分类" class="w200">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <span class="ml">分值</span>
          <el-input-number v-model="qForm.score" :min="0" :max="100" class="ml" />
          <el-checkbox v-model="qForm.shared" class="ml">共享（可用于学生刷题）</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qVisible = false">取消</el-button>
        <el-button v-if="qForm.needsAnswer && nextDraftId" @click="saveQuestion(true)">保存并处理下一题</el-button>
        <el-button type="primary" @click="saveQuestion(false)">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量传图入库 -->
    <el-dialog v-model="imgImportVisible" title="批量传图入库" width="560px" append-to-body>
      <el-alert type="info" :closable="false" class="mb">
        把题目截图（jpg/png 等）直接打成 <b>zip</b> 压缩包上传，不要带文件夹。按文件名顺序入库（2.jpg 排在 10.jpg 之前）；
        和已有题目（含回收站）完全相同的图片不会重复入库，只合并标签。入库后在「待设置答案」中逐题设置答案。
      </el-alert>
      <el-form label-width="80px">
        <el-form-item label="压缩包" required><input ref="zipInput" type="file" accept=".zip" @change="(e: Event) => (imgZip = pick(e))" /></el-form-item>
        <el-form-item label="题型">
          <el-radio-group v-model="imgForm.qtype">
            <el-radio-button v-for="(l, v) in typeLabel" :key="v" :value="v">{{ l }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="imgForm.qtype === 'SINGLE' || imgForm.qtype === 'MULTI'" label="选项数">
          <el-input-number v-model="imgForm.optionCount" :min="2" :max="8" />
          <span class="ml muted">如单选题有 A~D 四个选项填 4</span>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="imgForm.category" filterable allow-create clearable class="w200">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <span class="ml">分值</span>
          <el-input-number v-model="imgForm.score" :min="0" :max="100" class="ml" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="imgForm.tags" multiple filterable allow-create default-first-option class="full">
            <el-option v-for="t in tags" :key="t.tag" :label="t.tag" :value="t.tag" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="imgImportVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="importImages">上传入库</el-button>
      </template>
    </el-dialog>

    <!-- 导入题库包 -->
    <el-dialog v-model="packVisible" title="导入题库包" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="题库包" required><input ref="packInput" type="file" accept=".zip" @change="(e: Event) => (packFile = pick(e))" /></el-form-item>
        <el-form-item label="已有题目">
          <el-radio-group v-model="packOverwrite" class="col">
            <el-radio value="NONE">不覆盖（只合并标签）</el-radio>
            <el-radio value="ANSWER">覆盖答案</el-radio>
            <el-radio value="ANALYSIS">覆盖解析</el-radio>
            <el-radio value="ALL">答案和解析都覆盖</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div class="muted">题库包由「打包导出」生成。与你已有的题目重复的（图片相同，或题型+题干相同）不会重复添加。</div>
      <template #footer>
        <el-button @click="packVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="importPack">导入</el-button>
      </template>
    </el-dialog>

    <input ref="excelInput" type="file" accept=".xlsx,.xls,.csv" hidden @change="importExcel" />

    <!-- 试卷编辑 -->
    <el-dialog v-model="pVisible" :title="pForm.id ? '编辑试卷' : '新建试卷'" width="900px" top="5vh" append-to-body>
      <el-form label-width="70px">
        <el-form-item label="名称"><el-input v-model="pForm.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="pForm.description" /></el-form-item>
        <el-form-item label="共享"><el-switch v-model="pForm.shared" /></el-form-item>
      </el-form>
      <div class="paper-edit">
        <div>
          <div class="sub-title">试卷题目（{{ pForm.items.length }} 题，共 {{ paperTotal }} 分）</div>
          <div v-for="(it, i) in pForm.items" :key="it.questionId" class="p-item">
            <span class="no">{{ i + 1 }}.</span>
            <span class="p-stem">[{{ typeLabel[it.qtype] }}] {{ brief(it) }}</span>
            <el-input-number v-model="it.score" :min="0" :max="100" size="small" controls-position="right" class="w90" />
            <el-button link size="small" :disabled="i === 0" @click="swap(i, -1)">↑</el-button>
            <el-button link size="small" type="danger" @click="pForm.items.splice(i, 1)">移除</el-button>
          </div>
          <div class="random">
            随机抽题：
            <span v-for="(l, v) in typeLabel" :key="v">{{ l }}<el-input-number v-model="randomCounts[v]" :min="0" :max="50" size="small" controls-position="right" class="w80" /></span>
          </div>
          <div class="random">
            范围：
            <el-select v-model="randomFilter.category" clearable filterable placeholder="全部分类" size="small" class="w120">
              <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
            </el-select>
            <el-select v-model="randomFilter.tag" clearable filterable placeholder="全部标签" size="small" class="w120">
              <el-option v-for="t in tags" :key="t.tag" :label="t.tag" :value="t.tag" />
            </el-select>
            <el-select v-model="randomFilter.minStars" clearable placeholder="星级不限" size="small" class="w100">
              <el-option v-for="n in 5" :key="n" :label="`≥ ${n} 星`" :value="n" />
            </el-select>
            <el-button size="small" :disabled="!pForm.id" @click="randomFill">抽取</el-button>
            <span v-if="!pForm.id" class="muted">（先保存试卷）</span>
          </div>
        </div>
        <div>
          <div class="sub-title">从题库添加</div>
          <el-input v-model="pickKeyword" size="small" placeholder="搜索" class="mb" />
          <div class="pick-list">
            <div v-for="q in pickable" :key="q.id" class="pick" @click="addToPaper(q)">
              <span>[{{ typeLabel[q.qtype] }}] {{ brief(q) }}</span><span class="muted">+{{ q.score }}</span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="pVisible = false">取消</el-button>
        <el-button type="primary" @click="savePaper">保存试卷</el-button>
      </template>
    </el-dialog>

    <PublishTestDialog v-model="publishVisible" :paper="publishPaper" />
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { http } from '../../api/http'
import { safeHtml } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import PublishTestDialog from './PublishTestDialog.vue'
import PracticeAdmin from './PracticeAdmin.vue'

const auth = useAuthStore()
const me = computed(() => auth.user?.userId)
const typeLabel: Record<string, string> = { SINGLE: '单选题', MULTI: '多选题', JUDGE: '判断题', FILL: '填空题' }
const letter = (i: number) => String.fromCharCode(65 + i)
const plain = (h: string) => (h || '').replace(/<[^>]+>/g, '').slice(0, 60)
const brief = (q: any) => plain(q.stem) || (q.hasImage ? `（图片题 #${q.questionId ?? q.id}）` : '')
const imgUrl = (id: number) => `/api/quiz/questions/${id}/image`

const tab = ref('bank')
const questionList = ref<any[]>([])
const categories = ref<string[]>([])
const tags = ref<any[]>([])
const stats = ref<any>({})
const paperList = ref<any[]>([])
const filter = reactive<any>({ status: 'active', qtype: '', category: '', tag: '', minStars: undefined, keyword: '' })
const selected = ref<any[]>([])
const selectedIds = computed(() => selected.value.map(q => q.id))

const qVisible = ref(false)
const qForm = reactive<any>({})
const pendingImage = ref<File | null>(null)
const pendingImageUrl = ref('')
const imgVersion = ref(0)
const pVisible = ref(false)
const pForm = reactive<any>({ items: [] })
const pickKeyword = ref('')
const randomCounts = reactive<Record<string, number>>({ SINGLE: 0, MULTI: 0, JUDGE: 0, FILL: 0 })
const randomFilter = reactive<any>({ category: '', tag: '', minStars: undefined })
const publishVisible = ref(false)
const publishPaper = ref<any>(null)

const imgImportVisible = ref(false)
const imgForm = reactive<any>({ qtype: 'SINGLE', optionCount: 4, category: '', score: 5, tags: [] })
const imgZip = ref<File | null>(null)
const zipInput = ref<HTMLInputElement>()
const packVisible = ref(false)
const packFile = ref<File | null>(null)
const packInput = ref<HTMLInputElement>()
const packOverwrite = ref('NONE')
const excelInput = ref<HTMLInputElement>()
const importing = ref(false)

const paperTotal = computed(() => pForm.items.reduce((s: number, i: any) => s + (i.score || 0), 0))
const pickable = computed(() => questionList.value.filter(q => !q.needsAnswer &&
  !pForm.items.some((i: any) => i.questionId === q.id) && (!pickKeyword.value || q.stem.includes(pickKeyword.value))))
const nextDraftId = computed(() => questionList.value.find(q => q.needsAnswer && q.id !== qForm.id)?.id)

function pick(e: Event): File | null { return (e.target as HTMLInputElement).files?.[0] || null }

async function loadQuestions() {
  const params: any = { ...filter }
  if (filter.status !== 'active') params.includeShared = false
  questionList.value = await api.get('/teacher/quiz/questions', params)
  selected.value = []
}
async function loadMeta() {
  ;[categories.value, tags.value, stats.value] = await Promise.all([
    api.get('/teacher/quiz/categories'), api.get('/teacher/quiz/tags'), api.get('/teacher/quiz/stats')])
}
async function reload() { await Promise.all([loadQuestions(), loadMeta()]) }
async function loadPapers() { paperList.value = await api.get('/teacher/quiz/papers') }
function onTab(name: any) { if (name === 'stats') loadMeta() }

function answerText(q: any) {
  if (q.qtype === 'JUDGE') return q.answer === 'T' ? '对' : '错'
  if (q.qtype === 'FILL') return (q.answer || []).join(' / ')
  return q.answer
}

function resetImage() {
  pendingImage.value = null
  if (pendingImageUrl.value) URL.revokeObjectURL(pendingImageUrl.value)
  pendingImageUrl.value = ''
}

function openQuestion(q?: any) {
  resetImage()
  Object.keys(qForm).forEach(k => delete qForm[k])
  if (q) {
    const choice = q.qtype === 'SINGLE' || q.qtype === 'MULTI'
    Object.assign(qForm, {
      ...q, tags: [...(q.tags || [])],
      options: choice && q.options.length ? [...q.options] : ['', '', '', ''],
      blanks: q.qtype === 'FILL' && Array.isArray(q.answer) && q.answer.length ? [...q.answer] : [''],
      answer: q.qtype === 'FILL' ? '' : q.answer
    })
  } else {
    Object.assign(qForm, { qtype: 'SINGLE', stem: '', options: ['', '', '', ''], answer: '', blanks: [''], analysis: '',
      category: filter.category || '', score: 5, shared: false, stars: 0, tags: filter.tag ? [filter.tag] : [], hasImage: false })
  }
  qVisible.value = true
}

function pickImage(e: Event) {
  const f = pick(e)
  if (!f) return
  resetImage()
  pendingImage.value = f
  pendingImageUrl.value = URL.createObjectURL(f)
  ;(e.target as HTMLInputElement).value = ''
}

async function uploadImage(id: number) {
  const fd = new FormData()
  fd.append('file', pendingImage.value as File)
  await http.post(`/teacher/quiz/questions/${id}/image`, fd)
}

async function removeImage() {
  try {
    await api.del(`/teacher/quiz/questions/${qForm.id}/image`)
    qForm.hasImage = false
    await loadQuestions()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

function toggleMulti(l: string, on: boolean) {
  const set = new Set((qForm.answer || '').split(''))
  if (on) set.add(l); else set.delete(l)
  qForm.answer = [...set].filter(Boolean).sort().join('')
}

function removeOption(i: number) {
  qForm.options.splice(i, 1)
  qForm.answer = ''
}

async function saveQuestion(next: boolean) {
  const body = { ...qForm, answer: qForm.qtype === 'FILL' ? qForm.blanks : qForm.answer }
  const needImageFirst = !qForm.id && pendingImage.value && !String(qForm.stem || '').trim()
  try {
    let saved
    if (qForm.id) {
      if (pendingImage.value) await uploadImage(qForm.id)
      saved = await api.put(`/teacher/quiz/questions/${qForm.id}`, body)
    } else {
      // 只有图片没有文字的新题：先用占位题干建题，传图后再清空
      saved = await api.post('/teacher/quiz/questions', needImageFirst ? { ...body, stem: '（图片题）' } : body)
      if (pendingImage.value) {
        await uploadImage(saved.id)
        if (needImageFirst) saved = await api.put(`/teacher/quiz/questions/${saved.id}`, { ...body, stem: '' })
      }
    }
    imgVersion.value++
    const nextId = next ? nextDraftId.value : undefined
    ElMessage.success('已保存')
    await reload()
    if (nextId) {
      const nq = questionList.value.find(q => q.id === nextId)
      if (nq) { openQuestion(nq); return }
    }
    qVisible.value = false
    void saved
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function delQuestion(q: any) {
  try { await api.del(`/teacher/quiz/questions/${q.id}`); await reload() } catch (err) { ElMessage.error(errMsg(err)) }
}

async function batch(body: any) {
  try {
    const n = await api.post('/teacher/quiz/questions/batch', { ids: selectedIds.value, ...body })
    ElMessage.success(`已处理 ${n} 题`)
    await reload()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function promptTags(mode: 'add' | 'remove') {
  try {
    const { value } = await ElMessageBox.prompt(mode === 'add' ? '要添加的标签，多个用逗号分隔' : '要移除的标签，多个用逗号分隔', mode === 'add' ? '批量加标签' : '批量去标签')
    const list = String(value || '').split(/[,，;；\s]+/).filter(Boolean)
    if (list.length) await batch(mode === 'add' ? { addTags: list } : { removeTags: list })
  } catch { /* 取消 */ }
}

async function promptCategory() {
  try {
    const { value } = await ElMessageBox.prompt('分类名称（留空表示清除分类）', '批量设分类')
    await batch({ category: value || '' })
  } catch { /* 取消 */ }
}

async function restore(ids: number[]) {
  try {
    await api.post('/teacher/quiz/questions/restore', { ids })
    ElMessage.success('已恢复')
    await reload()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function purge() {
  try {
    const r = await api.post('/teacher/quiz/questions/purge', { ids: selectedIds.value })
    ElMessage.success(`彻底删除 ${r.removed} 题${r.kept ? `，${r.kept} 题已被试卷或作答记录引用，保留在回收站` : ''}`)
    await reload()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function exportPack() {
  try {
    const resp = await http.post('/teacher/quiz/questions/export', { ids: selectedIds.value }, { responseType: 'blob' })
    const url = URL.createObjectURL(resp.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `题库包_${new Date().toISOString().slice(0, 10)}.zip`
    a.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    ElMessage.error(errMsg(err, '导出失败'))
  }
}

function onImport(cmd: string) {
  if (cmd === 'images') {
    imgZip.value = null
    if (zipInput.value) zipInput.value.value = ''
    Object.assign(imgForm, { category: filter.category || '', tags: filter.tag ? [filter.tag] : [] })
    imgImportVisible.value = true
  } else if (cmd === 'pack') {
    packFile.value = null
    if (packInput.value) packInput.value.value = ''
    packVisible.value = true
  } else if (cmd === 'excel') {
    excelInput.value?.click()
  } else if (cmd === 'template') {
    api.download('/teacher/quiz/questions/template')
  }
}

async function importImages() {
  if (!imgZip.value) { ElMessage.warning('请选择 zip 压缩包'); return }
  importing.value = true
  try {
    const r = await api.upload('/teacher/quiz/questions/import-images', imgZip.value, {
      qtype: imgForm.qtype, optionCount: imgForm.optionCount, category: imgForm.category || '', score: imgForm.score, tags: imgForm.tags.join(',')
    })
    ElMessage.success(`共 ${r.images} 张图片：新入库 ${r.imported} 题，重复 ${r.duplicates} 题`)
    imgImportVisible.value = false
    filter.status = 'draft'
    await reload()
  } catch (err) {
    ElMessage.error(errMsg(err, '上传失败'))
  } finally {
    importing.value = false
  }
}

async function importPack() {
  if (!packFile.value) { ElMessage.warning('请选择题库包'); return }
  importing.value = true
  try {
    const r = await api.upload('/teacher/quiz/questions/import-pack', packFile.value, { overwrite: packOverwrite.value })
    ElMessage({ type: r.failed ? 'warning' : 'success', message: `新增 ${r.added} 题，合并重复 ${r.merged} 题${r.failed ? `，失败 ${r.failed} 题：${r.errors.join('；')}` : ''}`, duration: 6000 })
    packVisible.value = false
    await reload()
  } catch (err) {
    ElMessage.error(errMsg(err, '导入失败'))
  } finally {
    importing.value = false
  }
}

async function importExcel(e: Event) {
  const f = pick(e)
  ;(e.target as HTMLInputElement).value = ''
  if (!f) return
  try {
    const r = await api.upload('/teacher/quiz/questions/import', f)
    ElMessage.success(`导入 ${r.imported}/${r.total} 题${r.failed ? '，失败 ' + r.failed + ' 题：' + r.errors.map((x: any) => `第${x.row}行 ${x.message}`).join('；') : ''}`)
    await reload()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function renameTag(from: string) {
  try {
    const { value } = await ElMessageBox.prompt(`把「${from}」改名为：`, '标签改名', { inputValue: from })
    if (!value || value === from) return
    await api.put('/teacher/quiz/tags/rename', { from, to: value })
    await reload()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(errMsg(err))
  }
}

async function deleteTag(tag: string) {
  await http.delete('/teacher/quiz/tags', { params: { tag } })
  await reload()
}

async function openPaper(p?: any) {
  if (!questionList.value.length) await loadQuestions()
  if (p) {
    const full = await api.get(`/teacher/quiz/papers/${p.id}`)
    Object.assign(pForm, { id: full.id, name: full.name, description: full.description, shared: full.shared,
      items: full.questions.map(toItem) })
  } else {
    Object.assign(pForm, { id: null, name: '', description: '', shared: false, items: [] })
  }
  pVisible.value = true
}

function toItem(q: any) { return { questionId: q.id, qtype: q.qtype, stem: q.stem, hasImage: q.hasImage, score: q.score } }
function addToPaper(q: any) { pForm.items.push(toItem(q)) }
function swap(i: number, d: number) { const a = pForm.items; [a[i], a[i + d]] = [a[i + d], a[i]] }

async function savePaper() {
  try {
    const body = { name: pForm.name, description: pForm.description, shared: pForm.shared, items: pForm.items }
    const saved = pForm.id ? await api.put(`/teacher/quiz/papers/${pForm.id}`, body) : await api.post('/teacher/quiz/papers', body)
    pForm.id = saved.id
    ElMessage.success('试卷已保存')
    pVisible.value = false
    await loadPapers()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function randomFill() {
  try {
    await api.put(`/teacher/quiz/papers/${pForm.id}`, { name: pForm.name, description: pForm.description, shared: pForm.shared, items: pForm.items })
    const full = await api.post(`/teacher/quiz/papers/${pForm.id}/random`, { ...randomCounts, ...randomFilter })
    pForm.items = full.questions.map(toItem)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function delPaper(p: any) {
  try { await api.del(`/teacher/quiz/papers/${p.id}`); await loadPapers() } catch (err) { ElMessage.error(errMsg(err)) }
}

function openPublish(p: any) { publishPaper.value = p; publishVisible.value = true }

onMounted(async () => { await reload(); await loadPapers() })
</script>

<style scoped>
.filters { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.spacer { flex: 1; }
.w80 { width: 80px; margin: 0 8px 0 4px; } .w90 { width: 90px; } .w100 { width: 100px; } .w120 { width: 120px; }
.w140 { width: 140px; } .w160 { width: 160px; } .w200 { width: 200px; }
.full { width: 100%; }
.ml { margin-left: 10px; }
.mb { margin-bottom: 8px; }
.mr4 { margin-right: 4px; }
.muted { color: #909399; font-size: 12px; }
.foot { margin-top: 6px; }
.batch { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; padding: 6px 10px; margin-bottom: 8px; background: #ecf5ff; border-radius: 6px; font-size: 13px; }
.batch .el-button + .el-button { margin-left: 0; }
.stem-cell { display: flex; gap: 8px; align-items: flex-start; }
.thumb { width: 120px; height: 60px; flex-shrink: 0; border: 1px solid #ebeef5; border-radius: 4px; background: #fafafa; }
.stem :deep(p) { margin: 0; }
.stars { color: #e6a23c; letter-spacing: -1px; }
.stars .off { color: #dcdfe6; }
.img-edit { width: 100%; }
.big-img { display: block; max-width: 100%; max-height: 260px; border: 1px solid #ebeef5; border-radius: 6px; margin-bottom: 6px; }
.img-ops { display: flex; align-items: center; gap: 8px; }
.file-btn { cursor: pointer; }
.opt-row { display: flex; gap: 8px; align-items: center; width: 100%; }
.fill-answers { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.col { display: flex; flex-direction: column; align-items: flex-start; }
.paper-edit { display: grid; grid-template-columns: 1.4fr 1fr; gap: 16px; }
.sub-title { font-weight: 600; margin-bottom: 8px; }
.p-item { display: flex; align-items: center; gap: 6px; padding: 4px 0; border-bottom: 1px dashed #ebeef5; font-size: 13px; }
.p-stem { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.no { width: 22px; }
.random { margin-top: 10px; font-size: 13px; display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
.pick-list { max-height: 360px; overflow-y: auto; border: 1px solid #ebeef5; border-radius: 6px; }
.pick { display: flex; justify-content: space-between; padding: 6px 10px; cursor: pointer; font-size: 13px; border-bottom: 1px solid #f5f5f5; }
.pick:hover { background: var(--el-color-primary-light-9); }
.stat-cards { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.stat { display: flex; flex-direction: column; align-items: center; padding: 12px 24px; border: 1px solid #ebeef5; border-radius: 8px; min-width: 110px; }
.stat b { font-size: 24px; color: #409eff; }
.stat.warn b { color: #e6a23c; }
.stat.muted-card b { color: #909399; }
.stat span { font-size: 12px; color: #606266; }
.stat-row { display: grid; grid-template-columns: 1fr 1fr 2fr; gap: 24px; }
.kv { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; border-bottom: 1px dashed #ebeef5; font-size: 13px; }
</style>
