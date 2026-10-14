// @vitest-environment jsdom
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,describe,expect,it,vi} from 'vitest';
import {SignatureUpload} from './SignatureUpload';
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.unstubAllGlobals();});
describe('SignatureUpload',()=>{
 it('refuses unsupported images before decoding',()=>{
  const onChange=vi.fn();render(<SignatureUpload fr onChange={onChange}/>);
  fireEvent.change(screen.getByLabelText(/Image de votre propre/),{target:{files:[new File(['<svg/>'],'signature.svg',{type:'image/svg+xml'})]}});
  expect(screen.getByRole('alert').textContent).toContain('PNG ou JPEG');expect(onChange).toHaveBeenCalledWith(undefined);
 });
 it('refuses files above the server size limit',()=>{
  render(<SignatureUpload fr onChange={vi.fn()}/>);
  fireEvent.change(screen.getByLabelText(/Image de votre propre/),{target:{files:[new File([new Uint8Array(1_000_001)],'signature.png',{type:'image/png'})]}});
  expect(screen.getByRole('alert').textContent).toContain('1 Mo');
 });
 it('clears the previous signature when another file is selected',async()=>{
  vi.stubGlobal('Image',class {width=700;height=200;src='';decode(){return Promise.reject(new Error('invalid'));}});
  vi.stubGlobal('URL',Object.assign(URL,{createObjectURL:vi.fn(()=> 'blob:test'),revokeObjectURL:vi.fn()}));
  const onChange=vi.fn();render(<SignatureUpload fr onChange={onChange}/>);
  fireEvent.change(screen.getByLabelText(/Image de votre propre/),{target:{files:[new File(['invalid'],'signature.png',{type:'image/png'})]}});
  await waitFor(()=>expect(screen.getByRole('alert').textContent).toContain('Image invalide'));
  expect(onChange).toHaveBeenCalledWith(undefined);vi.unstubAllGlobals();
 });
});
