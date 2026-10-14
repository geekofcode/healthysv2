const allowed = new Set(['P','H1','H2','H3','STRONG','B','EM','I','U','UL','OL','LI','TABLE','THEAD','TBODY','TR','TD','TH','BLOCKQUOTE','BR','A']);
const discarded = new Set(['SCRIPT','STYLE','IFRAME','OBJECT','EMBED','IMG','SVG','MATH','LINK','META','BASE','FORM','INPUT','BUTTON','TEXTAREA','SELECT','VIDEO','AUDIO','SOURCE','TEMPLATE']);
export type SanitizedContractHtml={html:string;changed:boolean};
/** Parse in an inert template, then rebuild a fresh fragment without copying arbitrary attributes. */
export function sanitizeContractHtml(input:string):SanitizedContractHtml {
 const template=document.createElement('template');template.innerHTML=input;
 const output=document.createElement('div');let removed=false;
 let processed=0;
 const append=(node:Node,parent:Node,depth=0)=>{
  if(depth>64||++processed>10000){removed=true;return;}
  if(node.nodeType===Node.TEXT_NODE){parent.appendChild(document.createTextNode(node.textContent??''));return;}
  if(node.nodeType!==Node.ELEMENT_NODE){removed=true;return;}
  const source=node as Element;
  if(discarded.has(source.tagName)||source.namespaceURI!=='http://www.w3.org/1999/xhtml'){removed=true;return;}
  if(!allowed.has(source.tagName)){removed=true;if(source.tagName==='DIV'){const paragraph=document.createElement('p');for(const child of source.childNodes)append(child,paragraph,depth+1);parent.appendChild(paragraph);}else for(const child of source.childNodes)append(child,parent,depth+1);return;}
  const element=document.createElement(source.tagName.toLowerCase());
  for(const attr of source.attributes){
   if(source.tagName==='A'&&attr.name==='href'){
    try{const url=new URL(attr.value);if(url.protocol==='https:'){element.setAttribute('href',url.href);element.setAttribute('rel','noopener noreferrer');continue;}}catch{/* Strip unsafe or invalid links. */}
   }
   if(source.tagName==='A'&&attr.name==='rel')continue;
   removed=true;
  }
  for(const child of source.childNodes)append(child,element,depth+1);
  parent.appendChild(element);
 };
 for(const child of template.content.childNodes)append(child,output);
 return {html:output.innerHTML,changed:removed};
}
export function escapeContractText(value:string){const element=document.createElement('div');element.textContent=value;return element.innerHTML;}
export function textContractAsHtml(value:string){return value.split(/\r?\n/).map(line=>`<p>${escapeContractText(line)||'<br>'}</p>`).join('');}
export function contractHtmlAsText(value:string){const clean=sanitizeContractHtml(value).html;const template=document.createElement('template');template.innerHTML=clean;for(const element of template.content.querySelectorAll('br'))element.replaceWith('\n');for(const element of template.content.querySelectorAll('td,th'))element.append('\t');for(const element of template.content.querySelectorAll('p,div,h1,h2,h3,h4,h5,h6,li,tr,blockquote'))element.append('\n');return template.content.textContent?.trim()??'';}
