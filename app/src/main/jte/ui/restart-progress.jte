@param String previous
<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Restarting · Zalava</title>
<style>body{margin:0;background:#f4f6fa;color:#202938;font:1rem system-ui,sans-serif;display:grid;place-items:center;min-height:100vh}main{background:white;border:1px solid #dce1eb;border-radius:1rem;padding:2rem;width:min(28rem,calc(100vw - 6rem));box-shadow:0 8px 24px #14243c12}h1{font-size:1.6rem;margin-top:0}p{line-height:1.6}progress{width:100%;height:1rem;accent-color:#255da6}a{color:#255da6}button{font:inherit;padding:.65rem 1rem;border:1px solid #255da6;border-radius:.5rem;background:white;color:#255da6}a:focus-visible,button:focus-visible{outline:3px solid #255da6;outline-offset:3px}</style></head>
<body><main data-restart-previous="${previous}"><h1>Restarting Zalava</h1><progress aria-label="Restart in progress"></progress><p role="status" id="restart-message">Zalava is restarting. You’ll be taken to sign in when it is ready.</p><div id="restart-recovery" hidden><p>Your workspace data is retained. You can check again or return to sign in.</p><button type="button" id="retry">Check again</button> <a href="/login">Sign in</a></div></main>
<script>
(() => {
  const previous=document.querySelector('[data-restart-previous]').dataset.restartPrevious;
  const message=document.getElementById('restart-message'); const recovery=document.getElementById('restart-recovery');
  let deadline=Date.now()+180000; let stopped=false;
  function failed(text){stopped=true; message.textContent=text; recovery.hidden=false; document.querySelector('progress').hidden=true;}
  async function poll(){
    if(stopped)return;
    try { const response=await fetch('/restart-status?previous='+encodeURIComponent(previous),{cache:'no-store',credentials:'same-origin'});
      if(response.ok){const state=await response.json(); if(state.failed){failed('Zalava could not complete the restart. Check the application logs before retrying.'); return;}
        if(state.ready){location.replace('/login');return;}}
    }catch(error){ /* Temporary disconnect is expected during restart. */ }
    if(Date.now()>deadline){failed('Zalava is taking longer than expected to restart. Check again or inspect the application logs.');return;}
    setTimeout(poll,1000);
  }
  document.getElementById('retry').addEventListener('click',()=>{stopped=false;deadline=Date.now()+180000;recovery.hidden=true;document.querySelector('progress').hidden=false;message.textContent='Waiting for Zalava to be ready…';poll();});
  poll();
})();
</script></body></html>
