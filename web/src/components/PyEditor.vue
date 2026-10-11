<template>
  <div ref="host" class="py-editor" :style="{ height }" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { EditorView, basicSetup } from 'codemirror'
import { EditorState, Compartment } from '@codemirror/state'
import { keymap } from '@codemirror/view'
import { indentWithTab } from '@codemirror/commands'
import { indentUnit } from '@codemirror/language'
import { python } from '@codemirror/lang-python'

const props = withDefaults(defineProps<{ modelValue?: string | null; readonly?: boolean; height?: string }>(), {
  modelValue: '',
  readonly: false,
  height: '320px'
})
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()
const host = ref<HTMLDivElement>()
const editable = new Compartment()
let view: EditorView | null = null

onMounted(() => {
  view = new EditorView({
    parent: host.value!,
    state: EditorState.create({
      doc: props.modelValue || '',
      extensions: [
        basicSetup,
        python(),
        indentUnit.of('    '),
        keymap.of([indentWithTab]),
        editable.of([EditorState.readOnly.of(props.readonly), EditorView.editable.of(!props.readonly)]),
        EditorView.updateListener.of(u => {
          if (u.docChanged) emit('update:modelValue', u.state.doc.toString())
        }),
        EditorView.theme({ '&': { height: '100%', fontSize: '14px' }, '.cm-scroller': { fontFamily: 'Consolas, "Courier New", monospace' } })
      ]
    })
  })
})

watch(() => props.modelValue, v => {
  if (view && (v || '') !== view.state.doc.toString()) {
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: v || '' } })
  }
})

watch(() => props.readonly, ro => {
  view?.dispatch({ effects: editable.reconfigure([EditorState.readOnly.of(ro), EditorView.editable.of(!ro)]) })
})

onBeforeUnmount(() => view?.destroy())
</script>

<style scoped>
.py-editor { border: 1px solid #dcdfe6; border-radius: 4px; overflow: hidden; }
</style>
