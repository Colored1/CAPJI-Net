package com.capji.netpulse.ui.screens

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.capji.netpulse.AppGraph
import com.capji.netpulse.R
import com.capji.netpulse.data.net.Diag
import com.capji.netpulse.domain.model.UiText
import org.json.JSONObject

data class AssistRequest(val host: String, val user: String, val pass: String, val mac: String?, val name: String?)

private const val JS_LIB = """
function __docs(w){var r=[w.document];try{for(var i=0;i<w.frames.length;i++){try{r=r.concat(__docs(w.frames[i]));}catch(e){}}}catch(e){}return r;}
function __set(el,v){try{var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(el,v);}catch(e){el.value=v;}
 ['input','change','keyup','blur'].forEach(function(t){el.dispatchEvent(new Event(t,{bubbles:true}));});}
"""

private fun jsLogin(user: String, pass: String) = """(function(){$JS_LIB
var n=0;__docs(window).forEach(function(d){var pw=d.querySelector('input[type=password]');if(!pw)return;
 var ins=[].slice.call(d.querySelectorAll('input')).filter(function(i){return ['text','email',''].indexOf((i.type||'').toLowerCase())>=0&&i.offsetParent!==null;});
 var u=ins.filter(function(i){return /user|name|account|login|admin/i.test(i.name+i.id+i.placeholder);})[0]||ins[0];
 if(u)__set(u,${JSONObject.quote(user)});__set(pw,${JSONObject.quote(pass)});n++;
 var b=[].slice.call(d.querySelectorAll('button,input[type=submit],input[type=button],a')).filter(function(x){return /log|sign|ok|submit|enter|apply|دخول/i.test((x.innerText||'')+(x.value||'')+x.id+x.className);})[0];
 setTimeout(function(){if(b)b.click();else if(pw.form)pw.form.submit();},300);});
return n;})()"""

private val JS_FIND = """(function(){$JS_LIB
var rx=/mac\s*filter|mac\s*filtering|access\s*control|parental|black\s*list|blacklist|device\s*filter|wlan\s*filter|wireless\s*access/i;var best=null,len=1e9;
__docs(window).forEach(function(d){[].slice.call(d.querySelectorAll('a,li,span,div,td,button,p')).forEach(function(e){var t=(e.innerText||'').trim();
 if(t&&t.length<40&&rx.test(t)&&t.length<len){best=e;len=t.length;}});});
if(best){best.click();return 1;}return 0;})()"""

private fun jsFill(mac: String) = """(function(){$JS_LIB
var mac=${JSONObject.quote(mac)};var dash=mac.replace(/:/g,'-');var n=0;
__docs(window).forEach(function(d){var ins=[].slice.call(d.querySelectorAll('input')).filter(function(i){return i.offsetParent!==null&&['text',''].indexOf((i.type||'').toLowerCase())>=0;});
 var parts=ins.filter(function(i){return /mac\d|mac_?[a-f1-6]\b/i.test(i.name+i.id)&&i.maxLength==2;});
 if(parts.length>=6){mac.split(':').forEach(function(p,k){__set(parts[k],p);});n++;return;}
 var one=ins.filter(function(i){return /mac/i.test(i.name+i.id+i.placeholder+i.title);})[0];
 if(one){__set(one,/-/.test(one.placeholder||'')?dash:mac);n++;}});
return n;})()"""

/** Fallback for any router: shows the router's own web UI with one-tap auto-login, "find MAC filter" and "fill MAC". */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RouterAssistScreen(req: AssistRequest, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val web = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    // self-signed router certificates: accept only for the router's own LAN address
                    if (android.net.Uri.parse(error.url).host == req.host) handler.proceed() else handler.cancel()
                }
                override fun shouldOverrideUrlLoading(view: WebView, r: WebResourceRequest) = false
            }
            loadUrl("http://${req.host}/")
        }
    }
    fun run(js: String, okRes: Int, noRes: Int) {
        web.evaluateJavascript(js) { r ->
            val n = r?.trim('"')?.toIntOrNull() ?: 0
            Diag.add("assist js -> $n")
            AppGraph.toast(UiText(if (n > 0) okRes else noRes))
        }
    }
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.assist_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.cd_back)) }
        }
        Text(stringResource(R.string.assist_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { run(jsLogin(req.user, req.pass), R.string.assist_done, R.string.assist_none) }, label = { Text(stringResource(R.string.assist_login)) })
            AssistChip(onClick = { run(JS_FIND, R.string.assist_done, R.string.assist_none) }, label = { Text(stringResource(R.string.assist_find)) })
            if (req.mac != null) AssistChip(onClick = { run(jsFill(req.mac), R.string.assist_done, R.string.assist_none) }, label = { Text(stringResource(R.string.assist_fill, req.mac)) })
        }
        AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
