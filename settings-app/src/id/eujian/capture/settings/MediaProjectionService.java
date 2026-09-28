package id.eujian.capture.settings;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import java.io.*;
import java.nio.ByteBuffer;

public final class MediaProjectionService extends Service {
    public static final String ACTION_START="id.eujian.capture.settings.START";
    public static final String ACTION_STOP="id.eujian.capture.settings.STOP";
    public static final int TX_STATUS=1, TX_FRAME=2, TX_STOP=3;
    private MediaProjection projection; private VirtualDisplay display; private ImageReader reader; private HandlerThread thread; private Handler handler;
    private volatile boolean ready; private volatile boolean stopping; private volatile long sequence; private volatile long lastFrame; private volatile byte[] latestJpeg; private volatile int latestW,latestH;
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && ACTION_STOP.equals(intent.getAction())){ stopCapture("USER_STOP"); return START_NOT_STICKY; }
        if(intent==null || !intent.hasExtra("resultData")){ return START_NOT_STICKY; }
        try { startForegroundNow(); MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE); projection=m.getMediaProjection(intent.getIntExtra("resultCode",0),(Intent)intent.getParcelableExtra("resultData"));
            if(projection==null) throw new IllegalStateException("projection_null");
            thread=new HandlerThread("eujian-projection"); thread.start(); handler=new Handler(thread.getLooper());
            projection.registerCallback(new MediaProjection.Callback(){ @Override public void onStop(){ stopCapture("PROJECTION_STOP"); } },handler);
            int w=getResources().getDisplayMetrics().widthPixels,h=getResources().getDisplayMetrics().heightPixels,d=getResources().getDisplayMetrics().densityDpi;
            reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2); reader.setOnImageAvailableListener(r->{ Image im=r.acquireLatestImage(); if(im!=null){ try { Bitmap b=imageBitmap(im); ByteArrayOutputStream out=new ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.JPEG,85,out); latestJpeg=out.toByteArray(); latestW=b.getWidth(); latestH=b.getHeight(); b.recycle(); lastFrame=System.currentTimeMillis(); sequence++; } finally { im.close(); } } },handler);
            display=projection.createVirtualDisplay("E-Ujian",w,h,d,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler); ready=true; getSharedPreferences("projection",0).edit().putString("status","READY").apply();
            return START_NOT_STICKY;
        } catch(Exception ex){ ready=false; stopCapture("INIT_FAILED"); return START_NOT_STICKY; }
    }
    private void startForegroundNow(){ NotificationChannel c=new NotificationChannel("eujian_projection","E-Ujian screen capture",NotificationManager.IMPORTANCE_LOW); c.setSound(null,null); c.enableVibration(false); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c); Notification n=new Notification.Builder(this,"eujian_projection").setContentTitle("E-Ujian capture aktif").setContentText("Screen capture session aktif").setSmallIcon(android.R.drawable.ic_menu_camera).setOngoing(true).build(); if(Build.VERSION.SDK_INT>=29) startForeground(91,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION); else startForeground(91,n); }
    private synchronized void stopCapture(String reason){ if(stopping)return; stopping=true; ready=false; latestJpeg=null; if(display!=null){display.release();display=null;} if(reader!=null){reader.close();reader=null;} if(projection!=null){projection.stop();projection=null;} if(thread!=null){thread.quitSafely();thread=null;} getSharedPreferences("projection",0).edit().putString("status",reason).apply(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    @Override public IBinder onBind(Intent intent){ return new Binder(){ @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{ if(code==TX_STATUS){reply.writeInt(ready?1:0);reply.writeLong(sequence);reply.writeLong(lastFrame);return true;} if(code==TX_STOP){stopCapture("REMOTE_STOP");return true;} if(code==TX_FRAME){ if(!ready||latestJpeg==null)return false; try { byte[] jpeg=latestJpeg; ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe(); new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){out.write(jpeg);}catch(Exception ignored){}}).start(); reply.writeParcelable(pipe[0],0); reply.writeInt(latestW);reply.writeInt(latestH);reply.writeLong(sequence); return true; } catch(IOException ex){ return false; } } return super.onTransact(code,data,reply,flags);} }; }
    private Bitmap imageBitmap(Image im){ Image.Plane p=im.getPlanes()[0]; ByteBuffer buf=p.getBuffer(); int w=im.getWidth(),h=im.getHeight(),row=p.getRowStride(),pixel=p.getPixelStride(); byte[] bytes=new byte[buf.remaining()];buf.get(bytes); Bitmap raw=Bitmap.createBitmap(row/pixel,h,Bitmap.Config.ARGB_8888);raw.copyPixelsFromBuffer(ByteBuffer.wrap(bytes)); return Bitmap.createBitmap(raw,0,0,w,h); }
    @Override public void onDestroy(){stopCapture("DESTROYED");super.onDestroy();}
}
