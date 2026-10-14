import {useEffect,useRef,useState,type ChangeEvent} from 'react';
export function SignatureUpload({fr,onChange}:{fr:boolean;onChange:(value:string|undefined)=>void}) {
 const [preview,setPreview]=useState<string>();const [error,setError]=useState<string>();const revision=useRef(0);useEffect(()=>()=>{revision.current++;},[]);
 async function upload(event:ChangeEvent<HTMLInputElement>) {
  const current=++revision.current,file=event.target.files?.[0];setPreview(undefined);setError(undefined);onChange(undefined);if(!file)return;
  if(!['image/png','image/jpeg'].includes(file.type)||file.size>1_000_000){setError(fr?'Choisissez une image PNG ou JPEG de 1 Mo maximum.':'Choose a PNG or JPEG image up to 1 MB.');return;}
  const url=URL.createObjectURL(file);
  try {
   const image=new Image();image.src=url;await image.decode();
   if(image.width<40||image.height<20||image.width>2048||image.height>1024||image.width*image.height>1_500_000)throw new Error('dimensions');
   const canvas=document.createElement('canvas');canvas.width=image.width;canvas.height=image.height;const context=canvas.getContext('2d');if(!context)throw new Error('canvas');
   context.fillStyle='#fff';context.fillRect(0,0,canvas.width,canvas.height);context.drawImage(image,0,0);const png=canvas.toDataURL('image/png');if(png.length>1_333_358)throw new Error('size');
   if(current!==revision.current)return;setPreview(png);onChange(png);
  }catch{if(current===revision.current)setError(fr?'Image invalide : dimensions de 40 × 20 à 2048 × 1024 pixels, 1,5 million de pixels maximum.':'Invalid image: dimensions from 40 × 20 to 2048 × 1024 pixels, up to 1.5 million pixels.');}finally{URL.revokeObjectURL(url);}
 }
 return <fieldset><legend>{fr?'Importer votre signature':'Upload your signature'}</legend><label>{fr?'Image de votre propre signature (PNG/JPEG, 1 Mo maximum)':'Image of your own signature (PNG/JPEG, up to 1 MB)'}<input type="file" accept="image/png,image/jpeg" onChange={e=>void upload(e)}/></label>{error&&<p role="alert">{error}</p>}{preview&&<img src={preview} alt={fr?'Aperçu de votre signature':'Your signature preview'} style={{maxWidth:'100%',maxHeight:200}}/>}</fieldset>;
}
