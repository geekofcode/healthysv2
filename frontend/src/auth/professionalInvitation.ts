const storageKey='healthys:professional-invitation';
/** Capture only invitation fragments; leave OAuth callback fragments intact. */
export function captureProfessionalInvitation(){if(window.location.pathname!=='/professional-invitation')return;const fragment=new URLSearchParams(window.location.hash.slice(1));const token=fragment.get('token');if(!token)return;sessionStorage.setItem(storageKey,token);window.history.replaceState(window.history.state,'',window.location.pathname+window.location.search);}
export function storedProfessionalInvitation(){return sessionStorage.getItem(storageKey)??'';}
export function clearProfessionalInvitation(){sessionStorage.removeItem(storageKey);}
