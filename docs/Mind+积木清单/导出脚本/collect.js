() => {
 const ws=Blockly.getMainWorkspace(); const fl=ws.getFlyout(); const fws=fl.getWorkspace();
 const pos=(fl.categoryScrollPositions||[]).map(p=>({name:p.categoryName, y:p.position}));
 const fieldInfo=f=>{ const o={name:f.name, text:(f.getText?f.getText():''), value:(f.getValue?f.getValue():''), cls:(f.constructor&&f.constructor.name)||''};
   try{ if(f.getOptions){ const op=f.getOptions(); o.options=op.map(x=>[typeof x[0]==='string'?x[0]:(x[0]&&x[0].alt)||'img', x[1]]); } }catch(e){}
   return o; };
 const info=b=>({type:b.type, text:b.toString(), colour:b.getColour&&b.getColour(),
   prev:!!b.previousConnection, next:!!b.nextConnection, output:!!b.outputConnection,
   outputShape:b.getOutputShape?b.getOutputShape():null, hat:(b.startHat_||(!b.previousConnection&&!b.outputConnection)),
   inputs:b.inputList.map(i=>({name:i.name, type:i.type, fields:i.fieldRow.map(fieldInfo),
     shadow: i.connection&&i.connection.targetBlock()? {type:i.connection.targetBlock().type, text:i.connection.targetBlock().toString(), fields:i.connection.targetBlock().inputList.flatMap(j=>j.fieldRow.map(fieldInfo))}:null }))});
 const items=fws.getTopBlocks(true).map(b=>{ const y=b.getRelativeToSurfaceXY().y; let cat=''; for(const p of pos){ if(y>=p.y-1) cat=p.name; } return Object.assign({cat,y},info(b)); });
 const labels=(fl.buttons_||[]).map(x=>({text:x.text_, y:x.position_? x.position_.y : null}));
 return {pos, labels, items};
}
