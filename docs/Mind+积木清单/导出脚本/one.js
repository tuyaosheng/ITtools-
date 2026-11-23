async (it) => {
 const sleep=ms=>new Promise(r=>setTimeout(r,ms)); const ws=Blockly.getMainWorkspace(); const L=ws.listeners_;
 const esc=s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
 const vars=ws.getAllVariables();
 let n=0; const nid=()=>'T'+(++n)+'_'+Math.random().toString(36).slice(2,7);
 const ids=[];
 const fieldXml=f=>{ if(f.name==null) return ''; const v=vars.find(v=>v.getId()===f.value);
   if(v) return `<field name="${f.name}" id="${v.getId()}" variabletype="${v.type}">${esc(v.name)}</field>`;
   return `<field name="${f.name}">${esc(f.value)}</field>`; };
 const blockXml=(t)=>{ const id=nid(); ids.push(id); let x=`<block type="${t.type}" id="${id}">`;
   for(const inp of t.inputs){ for(const f of inp.fields) x+=fieldXml(f);
     if(inp.shadow){ const sid=nid(); ids.push(sid); x+=`<value name="${inp.name}"><shadow type="${inp.shadow.type}" id="${sid}">${inp.shadow.fields.map(fieldXml).join('')}</shadow></value>`; } }
   return {id, x:x+'</block>'}; };
 // 清场（Blockly 和 VM 两边）
 const start=ws.getTopBlocks().find(b=>/_start$/.test(b.type));
 const kill=b=>{ const bid=b.id; b.dispose(false); try{ L[0]({type:'delete', blockId:bid, ids:[bid], workspaceId:ws.id}); }catch(e){} };
 ws.getTopBlocks().forEach(b=>{ if(b!==start) kill(b); }); start.getChildren().forEach(c=>{ if(c.previousConnection) kill(c); });
 await sleep(300);
 const target=blockXml(it);
 const isStack=it.prev, isOut=it.output, isBool=it.output && it.outputShape===1;
 let topId=target.id, xml=target.x, how='hat';
 if(isOut){
   const sv=vars.find(v=>v.type==='string');
   if(isBool){ const w=nid(); ids.unshift(w); topId=w; xml=`<block type="control_if" id="${w}"><value name="CONDITION">${target.x}</value></block>`; how='in-if'; }
   else { const w=nid(); ids.unshift(w); topId=w; xml=`<block type="data_setStringto" id="${w}"><field name="STRING" id="${sv.getId()}" variabletype="string">${esc(sv.name)}</field><value name="VALUE">${target.x}</value></block>`; how='in-set-string'; }
 } else if(isStack) how='stack';
 const full='<xml>'+xml.replace(/^<block /,'<block x="200" y="200" ')+'</xml>';
 Blockly.Xml.domToWorkspace(Blockly.Xml.textToDom(full), ws);
 const topDom=Blockly.Xml.textToDom(full).children[0];
 L[0]({type:'create', blockId:topId, xml:topDom, ids:ids.slice(), workspaceId:ws.id, group:''});
 if(how!=='hat'){ const tb=ws.getBlockById(topId); start.nextConnection.connect(tb.previousConnection);
   L[0]({type:'move', blockId:topId, newParentId:start.id, oldCoordinate:{x:200,y:200}, workspaceId:ws.id}); }
 try{ L[1]({type:'endDrag', blockId:topId}); }catch(e){}
 await sleep(900);
 try{ Blockly.getMainWorkspace().getAllBlocks? null:null; document.activeElement&&document.activeElement.blur(); }catch(e){}
 const tb=ws.getBlockById(target.id); if(tb&&tb.unselect) tb.unselect(); const r=tb?tb.getSvgRoot().getBoundingClientRect():{x:0,y:0,width:0,height:0};
 return {how, xml:full, code:monaco.editor.getModels()[0].getValue(), rect:{x:r.x,y:r.y,w:r.width,h:r.height}};
}
