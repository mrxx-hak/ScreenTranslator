package com.alda.translator

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

class ScreenCaptureService:Service(){
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private var thread:HandlerThread?=null
    private var handler:Handler?=null
    private lateinit var wm:WindowManager
    private var bubble:LinearLayout?=null
    private var busy=false
    override fun onBind(i:Intent?):IBinder?=null
    override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
        val ch="alda_capture"
        if(Build.VERSION.SDK_INT>=26)(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(ch,"ALDA screen capture",NotificationManager.IMPORTANCE_LOW))
        val n=Notification.Builder(this,ch).setContentTitle("ALDA Translator").setContentText("Захват экрана активен — нажмите плавающую кнопку").setSmallIcon(android.R.drawable.ic_menu_search).build()
        if(Build.VERSION.SDK_INT>=29)startForeground(52,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(52,n)
        val code=i?.getIntExtra("code",0)?:0
        @Suppress("DEPRECATION")
        val data:Intent?=if(Build.VERSION.SDK_INT>=33)i?.getParcelableExtra("data",Intent::class.java) else i?.getParcelableExtra("data")
        if(data==null){stopSelf();return START_NOT_STICKY}
        try{
            wm=getSystemService(WINDOW_SERVICE) as WindowManager
            thread=HandlerThread("alda-screen").also{it.start()};handler=Handler(thread!!.looper)
            val dm=resources.displayMetrics
            reader=ImageReader.newInstance(dm.widthPixels,dm.heightPixels,PixelFormat.RGBA_8888,2)
            projection=(getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code,data)
            projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopSelf()}},handler)
            display=projection?.createVirtualDisplay("ALDA",dm.widthPixels,dm.heightPixels,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,handler)
            showBubble()
        }catch(e:Exception){Toast.makeText(this,"Ошибка захвата: ${e.message}",Toast.LENGTH_LONG).show();stopSelf()}
        return START_NOT_STICKY
    }
    private fun showBubble(){
        val b=Button(this).apply{text="A\n译";textSize=12f;setTextColor(0xFF062116.toInt());backgroundTintList=android.content.res.ColorStateList.valueOf(0xFF45E6B1.toInt());setOnClickListener{capture()}}
        val v=LinearLayout(this).apply{addView(b)}
        bubble=v
        wm.addView(v,WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.END or Gravity.CENTER_VERTICAL;x=8})
    }
    private fun capture(){
        if(busy)return
        busy=true
        handler?.postDelayed({
            val img=try{reader?.acquireLatestImage()}catch(_:Exception){null}
            if(img==null){busy=false;showText("Не удалось получить кадр. Нажмите снова.");return@postDelayed}
            val bmp=toBitmap(img);img.close()
            if(bmp==null){busy=false;showText("Не удалось обработать кадр.");return@postDelayed}
            val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            recognizer.process(InputImage.fromBitmap(bmp,0)).addOnSuccessListener{r->
                bmp.recycle();recognizer.close();busy=false
                val text=r.text
                if(text.isBlank())showText("Китайский текст не найден.")
                else{
                    val launch=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("recognized_text",text)
                    startActivity(launch);showText("Текст распознан и передан в ALDA Translator. Нажмите «Перевести» в приложении.")
                }
            }.addOnFailureListener{e->bmp.recycle();recognizer.close();busy=false;showText("Ошибка OCR: ${e.localizedMessage}")}
        },400)
    }
    private fun toBitmap(image:Image):android.graphics.Bitmap?{
        val p=image.planes[0];val padding=p.rowStride-p.pixelStride*image.width
        val wide=android.graphics.Bitmap.createBitmap(image.width+padding/p.pixelStride,image.height,android.graphics.Bitmap.Config.ARGB_8888)
        wide.copyPixelsFromBuffer(p.buffer)
        val cropped=android.graphics.Bitmap.createBitmap(wide,0,0,image.width,image.height);wide.recycle();return cropped
    }
    private fun showText(s:String){ Handler(Looper.getMainLooper()).post{ showTextUi(s) } }
    private fun showTextUi(s:String){
        try{
            val box=LinearLayout(this).apply{orientation=1;setPadding(25,20,25,20);setBackgroundColor(0xF0102119.toInt())}
            box.addView(TextView(this).apply{text="ALDA • SCREEN OCR\n\n$s";textSize=15f;setTextColor(-1);setTextIsSelectable(true)})
            box.addView(Button(this).apply{text="Закрыть";setOnClickListener{try{wm.removeView(box)}catch(_:Exception){}}})
            wm.addView(box,WindowManager.LayoutParams((resources.displayMetrics.widthPixels*.85f).toInt(),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.CENTER})
        }catch(_:Exception){}
    }
    override fun onDestroy(){try{bubble?.let{wm.removeView(it)}}catch(_:Exception){};display?.release();reader?.close();projection?.stop();thread?.quitSafely();super.onDestroy()}
}
