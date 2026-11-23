package com.alda.translator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import java.util.concurrent.CountDownLatch

class MainActivity:AppCompatActivity(),TextToSpeech.OnInitListener {
    private val engine=TranslationEngine()
    private lateinit var store:HistoryStore
    private lateinit var root:LinearLayout
    private lateinit var body:LinearLayout
    private lateinit var source:EditText
    private lateinit var result:TextView
    private lateinit var from:Spinner
    private lateinit var to:Spinner
    private lateinit var info:TextView
    private var translated=""
    private lateinit var tts:TextToSpeech
    private val languages=listOf("Китайский","Русский","Английский","Турецкий","Туркменский")

    private val gallery=registerForActivityResult(ActivityResultContracts.GetContent()){u->if(u!=null) Ocr.uri(this,u){r->runOnUiThread{r.onSuccess{source.setText(it);doTranslate()}.onFailure{message(it.message?: "OCR error")}}}}
    private val camera=registerForActivityResult(ActivityResultContracts.TakePicturePreview()){b->if(b!=null) Ocr.bitmap(b){r->runOnUiThread{r.onSuccess{source.setText(it);doTranslate()}.onFailure{message(it.message?: "OCR error")}}}}
    private val docs=registerForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null) openDoc(u)}
    private val voice=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
        if(r.resultCode==Activity.RESULT_OK){val words=r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull();if(!words.isNullOrBlank()){source.setText(words);doTranslate()}}
    }
    private val screen=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
        if(r.resultCode==Activity.RESULT_OK&&r.data!=null){
            val i=Intent(this,ScreenCaptureService::class.java).putExtra("code",r.resultCode).putExtra("data",r.data)
            ContextCompat.startForegroundService(this,i);message("Плавающая кнопка включена. Нажмите её для OCR.")
        } else message("Захват экрана отменён")
    }

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);store=HistoryStore(this);tts=TextToSpeech(this,this);shell();home()
        intent.getStringExtra("recognized_text")?.let{source.setText(it)}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.RECORD_AUDIO),10)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("recognized_text")?.let { recognized ->
            if (::source.isInitialized) {
                source.setText(recognized)
                message("Текст с экрана распознан. Нажмите «Перевести».")
            }
        }
    }

    private fun shell(){
        root=LinearLayout(this).apply{orientation=1;setBackgroundColor(0xFF07130F.toInt())}
        val head=LinearLayout(this).apply{orientation=1;setPadding(d(22),d(25),d(22),d(15))}
        head.addView(txt("ALDA  TRANSLATOR",26,0xFF45E6B1.toInt(),true))
        head.addView(txt("Переводи мир. Понимай больше.",14,0xFF9EB6AA.toInt(),false))
        root.addView(head)
        val scroll=ScrollView(this);body=LinearLayout(this).apply{orientation=1;setPadding(d(16),0,d(16),d(20))}
        scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply{gravity=Gravity.CENTER;setPadding(d(5),d(7),d(5),d(7));setBackgroundColor(0xFF0B1C15.toInt())}
        listOf("Главная","История","Избранное","Настройки").forEachIndexed{i,s->nav.addView(txt(s,11,0xFFB8CEC3.toInt(),false).apply{gravity=Gravity.CENTER;setPadding(d(5),d(10),d(5),d(10));setOnClickListener{when(i){0->home();1->history(false);2->history(true);else->settings()}}},LinearLayout.LayoutParams(0,-2,1f))}
        root.addView(nav);setContentView(root)
    }
    private fun home(){
        body.removeAllViews()
        card().also{c->val b=LinearLayout(this).apply{orientation=1;setPadding(d(18),d(18),d(18),d(18));setBackgroundColor(0xFF10271D.toInt())}
            b.addView(txt("ПРЕМИАЛЬНЫЙ ПЕРЕВОДЧИК",11,0xFF45E6B1.toInt(),true))
            b.addView(txt("Что будем переводить?",23,0xFFFFFFFF.toInt(),true));b.addView(txt("Текст • фото • камера • экран • голос • документы",13,0xFFB6C9BF.toInt(),false));c.addView(b);body.addView(c)}
        body.addView(section("ТЕКСТОВЫЙ ПЕРЕВОД"))
        val row=LinearLayout(this).apply{orientation=0}
        from=spinner("Китайский");to=spinner("Русский")
        row.addView(from,LinearLayout.LayoutParams(0,d(50),1f));row.addView(to,LinearLayout.LayoutParams(0,d(50),1f));body.addView(row)
        source=EditText(this).apply{hint="Введите или вставьте текст…";setTextColor(-1);setHintTextColor(0xFF789187.toInt());minLines=4;gravity=Gravity.TOP;textSize=16f;setPadding(d(13),d(12),d(13),d(12));setBackgroundColor(0xFF102019.toInt())}
        body.addView(source,LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(10)})
        body.addView(button("ПЕРЕВЕСТИ",true){doTranslate()})
        val c=card();result=txt("Здесь появится перевод",16,0xFFDAE9E1.toInt(),false).apply{setPadding(d(14),d(15),d(14),d(15))}
        c.addView(result);body.addView(c)
        val actions=LinearLayout(this)
        actions.addView(button("Копировать",false){copy(translated)},LinearLayout.LayoutParams(0,d(46),1f))
        actions.addView(button("Озвучить",false){if(translated.isNotBlank())tts.speak(translated,TextToSpeech.QUEUE_FLUSH,null,"alda")},LinearLayout.LayoutParams(0,d(46),1f))
        actions.addView(button("★",false){favorite()},LinearLayout.LayoutParams(0,d(46),1f));body.addView(actions)
        body.addView(section("ИНСТРУМЕНТЫ"))
        tool("▧","Перевод фото","Выбрать изображение"){gallery.launch("image/*")}
        tool("◉","Камера","Сфотографировать текст"){camera.launch(null)}
        tool("▤","Документ","TXT или PDF (OCR сканов)"){docs.launch(arrayOf("text/plain","application/pdf"))}
        tool("A","Перевод экрана","Плавающая кнопка поверх приложений"){startCapture()}
        tool("♫","Голосовой перевод","Системное распознавание речи"){startVoice()}
        info=txt("Готово • ML Kit загружает языковые модели при первом использовании",12,0xFF45E6B1.toInt(),false);body.addView(info)
    }
    private fun doTranslate(){
        val text=source.text.toString();message("Переводим…")
        engine.translate(text,from.selectedItem.toString(),to.selectedItem.toString()){r->runOnUiThread{
            r.onSuccess{translated=it;result.text=it;store.add(Entry(text,it,from.selectedItem.toString(),to.selectedItem.toString(),System.currentTimeMillis()));message("Перевод готов")}
                .onFailure{message("Ошибка: ${it.message}")}}}
    }
    private fun openDoc(uri:Uri){
        val mime=contentResolver.getType(uri)?: ""
        if(mime=="text/plain"||uri.toString().endsWith(".txt",true)){
            try{source.setText(contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?.take(10000)?: "");doTranslate()}
            catch(e:Exception){message("Не удалось прочитать TXT: ${e.message}")}
        }else if(mime=="application/pdf"||uri.toString().endsWith(".pdf",true)){
            message("OCR PDF: обрабатываются первые 5 страниц")
            Thread{
                try{
                    val pfd=contentResolver.openFileDescriptor(uri,"r")?:throw Exception("Не удалось открыть PDF")
                    val pdf=PdfRenderer(pfd);val all=StringBuilder()
                    for(i in 0 until minOf(pdf.pageCount,5)){
                        val page=pdf.openPage(i);val bmp=Bitmap.createBitmap(page.width*2,page.height*2,Bitmap.Config.ARGB_8888)
                        page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);page.close()
                        val latch=CountDownLatch(1)
                        Ocr.bitmap(bmp){r->r.onSuccess{all.append(it).append("\n")};latch.countDown()}
                        latch.await();bmp.recycle()
                    }
                    pdf.close();pfd.close()
                    runOnUiThread{if(all.isNotBlank()){source.setText(all.toString());doTranslate()}else message("Текст PDF не распознан")}
                }catch(e:Exception){runOnUiThread{message("PDF: ${e.message}")}}
            }.start()
        }else message("Поддерживаются TXT и PDF")
    }
    private fun startVoice(){
        if(!android.speech.SpeechRecognizer.isRecognitionAvailable(this)){message("Системное распознавание речи недоступно");return}
        val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN")
        try{voice.launch(i)}catch(e:Exception){message("Голосовой ввод недоступен: ${e.message}")}
    }
    private fun startCapture(){
        if(!android.provider.Settings.canDrawOverlays(this)){startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")));message("Разрешите отображение поверх других приложений и нажмите снова");return}
        val m=getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        screen.launch(m.createScreenCaptureIntent())
    }
    private fun history(fav:Boolean){
        body.removeAllViews();body.addView(section(if(fav)"ИЗБРАННОЕ" else "ИСТОРИЯ"))
        val items=store.all().filter{!fav||it.favorite}
        if(items.isEmpty())body.addView(txt("Пока пусто",15,0xFFB7C9C0.toInt(),false))
        items.forEach{e->val c=card();val b=LinearLayout(this).apply{orientation=1;setPadding(d(13),d(12),d(13),d(12))}
            b.addView(txt("${e.from} → ${e.to}",12,0xFF45E6B1.toInt(),true));b.addView(txt(e.source.take(160),13,0xFFB7C9C0.toInt(),false));b.addView(txt(e.translated.take(300),15,-1,false))
            b.addView(button(if(e.favorite)"★ Убрать из избранного" else "☆ В избранное",false){store.toggle(e);history(fav)})
            c.addView(b);body.addView(c)}
        if(!fav&&items.isNotEmpty())body.addView(button("Очистить историю",false){store.clear();history(false)})
    }
    private fun settings(){
        body.removeAllViews();body.addView(section("НАСТРОЙКИ И ПРИВАТНОСТЬ"))
        body.addView(txt("Офлайн-модели ML Kit: китайский, русский, английский, турецкий. Языковые модели загружаются при первом использовании.",15,-1,false))
        body.addView(txt("Туркменский в текущей версии показан в списке, но встроенная офлайн-модель ML Kit для него недоступна.",14,0xFFFFD18B.toInt(),false))
        body.addView(txt("Захват экрана начинается только после системного подтверждения Android. Плавающая кнопка распознаёт экран по нажатию. Защищённые окна могут быть недоступны.",14,0xFFB7C9C0.toInt(),false))
        body.addView(button("Остановить захват экрана",false){stopService(Intent(this,ScreenCaptureService::class.java));message("Служба остановлена")})
        body.addView(button("Очистить историю",false){store.clear();message("История очищена")})
        body.addView(txt("ALDA Translator 2.0.0 • Android 8.0+",12,0xFF789187.toInt(),false))
    }
    private fun favorite(){if(translated.isBlank()){message("Сначала выполните перевод");return};store.add(Entry(source.text.toString(),translated,from.selectedItem.toString(),to.selectedItem.toString(),System.currentTimeMillis(),true));message("Добавлено в избранное")}
    private fun startCapturePlaceholder(){}
    private fun spinner(selected:String)=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,languages);setSelection(languages.indexOf(selected))}
    private fun tool(icon:String,title:String,desc:String,click:()->Unit){val c=card();val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(d(14),d(13),d(14),d(13))}
        row.addView(txt(icon,24,0xFF45E6B1.toInt(),true),LinearLayout.LayoutParams(d(42),-2))
        val col=LinearLayout(this).apply{orientation=1};col.addView(txt(title,15,-1,true));col.addView(txt(desc,11,0xFF91A99D.toInt(),false));row.addView(col);row.setOnClickListener{click()};c.addView(row);body.addView(c)}
    private fun section(s:String)=txt(s,11,0xFF45E6B1.toInt(),true).apply{setPadding(d(4),d(20),d(4),d(9));letterSpacing=.1f}
    private fun txt(s:String,size:Int,color:Int,bold:Boolean)=TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(color);if(bold)typeface=android.graphics.Typeface.DEFAULT_BOLD}
    private fun card()=MaterialCardView(this).apply{radius=d(17).toFloat();cardElevation=d(2).toFloat();setCardBackgroundColor(0xFF102019.toInt());strokeWidth=d(1);setStrokeColor(0xFF244A38.toInt());layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(9)}}
    private fun button(s:String,primary:Boolean,lp:LinearLayout.LayoutParams?=null,click:()->Unit)=MaterialButton(this).apply{text=s;textSize=12f;setTextColor(if(primary)0xFF062116.toInt() else 0xFF45E6B1.toInt());if(primary)backgroundTintList=android.content.res.ColorStateList.valueOf(0xFF45E6B1.toInt()) else strokeColor=android.content.res.ColorStateList.valueOf(0xFF2D7757.toInt());setOnClickListener{click()};layoutParams=lp?:LinearLayout.LayoutParams(-1,d(48)).apply{topMargin=d(8)}}
    private fun d(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun message(s:String){if(::info.isInitialized)info.text=s;Toast.makeText(this,s,Toast.LENGTH_SHORT).show()}
    private fun copy(s:String){if(s.isBlank()){message("Нет перевода для копирования");return};(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("ALDA",s));message("Скопировано")}
    override fun onInit(status:Int){if(status==TextToSpeech.SUCCESS)tts.language=Locale("ru")}
    override fun onDestroy(){if(::tts.isInitialized)tts.shutdown();super.onDestroy()}
}
