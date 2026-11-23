// 把 Mind+ 设备/扩展 main.js 里的积木定义和 Arduino 代码生成器跑一遍，导出 JSON（只读，不改 Mind+ 文件）
const fs=require('fs');
const [,,p,out]=process.argv;
const anyFn=new Proxy(function(){},{get:(t,k)=>k==='then'?undefined:anyFn,apply:()=>[]});
const FMAP={DIGITAL_READ:'DIGITAL_IN',DIGITAL_WRITE:'DIGITAL_OUT',ANALOG_READ:'ADC',ANALOG_WRITE:'PWM'};
const base={getBoardPinsByType:(type,dev)=>{ const pins=(dev&&dev.getPins)?dev.getPins():[]; const f=FMAP[type]||type; return pins.filter(x=>x.features.includes(f)).map(x=>({text:x.text,value:x.value})); }, assetPath:{getArduinoPath:()=>''}, isWin:()=>true,isMac:()=>false,isLinux:()=>false};
const runtime=new Proxy(base, {get:(t,k)=>{ if(k in t) return t[k]; return function(){ return []; }; }});
const m=require(p); const C=m.default||m;
const inst=new C(runtime);
try{ inst.setLocale && inst.setLocale('zh-cn'); }catch(e){ console.error('setLocale',e.message); }
const info=inst.getInfo();
const prims=(inst.getCodePrimitives&&inst.getCodePrimitives())||{};
function menuItems(name){ const mm=info.menus&&info.menus[name]; if(!mm) return null; let it=mm.items||mm; if(typeof it==='function'){try{it=it.call(inst)}catch(e){return 'fn'}} if(typeof it==='string'){ try{ it=inst[it](); }catch(e){ return it; } } return it; }
const res={name:info.name,color:[info.color1,info.color2,info.color3],blocks:[],menus:{}};
for(const b of info.blocks){
  if(typeof b!=='object'){ res.blocks.push({sep:String(b)}); continue; }
  const r={opcode:b.opcode,id:b.id,blockType:b.blockType,text:b.text,submenu:b.submenuId,arguments:b.arguments,outputTypes:b.outputTypes,hidden:b.hideFromPalette};
  if(b.opcode && prims[b.opcode]){
    const g={inc:[],setup:[],decl:[],other:[]};
    const gen=new Proxy({}, {get:(t,k)=>{
      if(k==='addInclude') return (...a)=>g.inc.push(a.join(' | '));
      if(k==='addSetup') return (...a)=>g.setup.push(a.slice(0,2).join(' => '));
      if(k==='formatCodeInBraces') return (x)=>x;
      return (...a)=>{ g.other.push(String(k)+'('+a.map(x=>typeof x==='string'?x:JSON.stringify(x)).join(', ').slice(0,300)+')'); return ''; };
    }});
    const args={};
    for(const [k,v] of Object.entries(b.arguments||{})){ let d=v.defaultValue; if(d===undefined && v.menu){ const it=menuItems(v.menu); if(Array.isArray(it)&&it.length) d=(it[0].value!==undefined?it[0].value:it[0]); } args[k]={code:String(d===undefined?'':d), value:d}; }
    args.SUBSTACK={code:'/*SUBSTACK*/'}; args.SUBSTACK2={code:'/*SUBSTACK2*/'};
    try{ const c=prims[b.opcode].call(inst,gen,{opcode:b.opcode},args); r.code=c; }catch(e){ r.codeErr=e.message; }
    r.gen=g;
  }
  res.blocks.push(r);
}
for(const k of Object.keys(info.menus||{})) res.menus[k]=menuItems(k);
res.extraPrims=Object.keys(prims).filter(k=>!info.blocks.some(b=>b.opcode===k));
fs.writeFileSync(out,JSON.stringify(res,null,1));
console.log(res.name, res.blocks.filter(b=>b.opcode).length,'blocks', res.blocks.filter(b=>b.codeErr).length,'errs', 'extra',res.extraPrims.length);
