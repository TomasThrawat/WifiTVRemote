package com.tomasthrawat.wifitvremote
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
class MainActivity:ComponentActivity(){
    override fun onCreate(b:Bundle?){super.onCreate(b);setContent{Screen()}}
    @Composable fun Screen(){
        val discovery=remember{NsdDiscovery(this)};val devices=remember{mutableStateListOf<TvDevice>()};var connected by remember{mutableStateOf(false)};var pairing by remember{mutableStateOf<TvPairing?>(null)};var remote by remember{mutableStateOf<TvRemote?>(null)};var code by remember{mutableStateOf("")};var status by remember{mutableStateOf("ابحث عن تلفزيون على نفس شبكة Wi-Fi")};val scope=rememberCoroutineScope()
        fun scan(){devices.clear();scope.launch{discovery.scan().collect{d->if(d.host.isNotBlank()&&devices.none{it.host==d.host})devices.add(d)}}}
        fun connect(d:TvDevice){status="جاري الاقتران...";lateinit var p:TvPairing;p=TvPairing(this,d.host,{runOnUiThread{pairing=p;status="أدخل رمز التلفزيون"}},{val id=CertificateStore.loadOrCreate(this,d.host);val r=TvRemote(d.host,id,{runOnUiThread{connected=true;pairing=null;status="متصل"}},{e->runOnUiThread{status="فشل الاتصال: "+(e.message?:"خطأ")}});remote=r;r.start()},{e->runOnUiThread{status="فشل الاقتران: "+(e.message?:"خطأ")}});pairing=p;p.start()}
        MaterialTheme{Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Wi-Fi TV Remote",style=MaterialTheme.typography.headlineSmall);Text(status)
            if(!connected){Button(onClick={scan()}){Text("بحث عن التلفزيون")};LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(devices,key={it.host}){d->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(d.name);Text(d.host);Button(onClick={connect(d)}){Text("اتصال")}}}}};pairing?.let{p->OutlinedTextField(value=code,onValueChange={code=it},label={Text("رمز التلفزيون")},singleLine=true);Button(onClick={if(p.submitCode(code))status="تم إرسال الرمز"}){Text("تأكيد")}}}
            else{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){Button(onClick={remote?.power()}){Text("Power")};Button(onClick={remote?.home()}){Text("Home")};Button(onClick={remote?.back()}){Text("Back")}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){Button(onClick={remote?.volumeDown()}){Text("Vol −")};Button(onClick={remote?.mute()}){Text("Mute")};Button(onClick={remote?.volumeUp()}){Text("Vol +")}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){Button(onClick={remote?.up()}){Text("↑")}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){Button(onClick={remote?.left()}){Text("←")};Button(onClick={remote?.ok()}){Text("OK")};Button(onClick={remote?.right()}){Text("→")}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){Button(onClick={remote?.down()}){Text("↓")}};Button(onClick={remote?.playPause}){Text("Play/Pause")};TextButton(onClick={remote?.stop}){Text("قطع الاتصال")}}
        }}
    }
}
