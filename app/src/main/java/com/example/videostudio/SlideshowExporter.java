package com.example.videostudio;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Environment;
import android.net.Uri;
import android.content.Context;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.tracks.H264TrackImpl;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.coremedia.iso.IsoFile;

public class SlideshowExporter {
    public interface Callback { void done(File file); void error(String message); void progress(int percent); }
    private static final int W=640, H=360, FPS=20;
    public static void export(final Context context, final List<Uri> photos, final int seconds, final Callback callback) {
        new Thread(new Runnable(){ public void run(){
            MediaCodec codec=null;
            File raw=null;
            try {
                if (photos==null || photos.isEmpty()) throw new Exception("הוסף לפחות תמונה אחת");
                raw=new File(context.getCacheDir(),"slideshow_"+System.currentTimeMillis()+".h264");
                FileOutputStream out=new FileOutputStream(raw);
                MediaFormat fmt=MediaFormat.createVideoFormat("video/avc",W,H);
                fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfoCompat.COLOR_FORMAT_YUV420_PLANAR);
                fmt.setInteger(MediaFormat.KEY_BIT_RATE,1200000);
                fmt.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);
                fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
                codec=MediaCodec.createEncoderByType("video/avc");
                codec.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
                codec.start();
                MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                long pts=0; boolean eos=false;
                int total=photos.size()*seconds*FPS;
                for(int p=0;p<photos.size();p++){
                    Bitmap source=BitmapFactory.decodeStream(context.getContentResolver().openInputStream(photos.get(p)));
                    if(source==null) throw new Exception("לא ניתן לקרוא תמונה מספר "+(p+1));
                    Bitmap frame=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
                    Canvas canvas=new Canvas(frame); canvas.drawColor(Color.BLACK);
                    float scale=Math.min((float)W/source.getWidth(),(float)H/source.getHeight());
                    int dw=(int)(source.getWidth()*scale), dh=(int)(source.getHeight()*scale);
                    Rect dst=new Rect((W-dw)/2,(H-dh)/2,(W+dw)/2,(H+dh)/2);
                    Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG); canvas.drawBitmap(source,null,dst,paint);
                    byte[] yuv=toYuv(frame);
                    int count=seconds*FPS;
                    for(int f=0;f<count;f++){
                        int index;
                        while((index=codec.dequeueInputBuffer(10000))<0){}
                        ByteBuffer input=codec.getInputBuffer(index); input.clear(); input.put(yuv);
                        codec.queueInputBuffer(index,0,yuv.length,pts,0); pts+=1000000/FPS;
                        drain(codec,out,info,false);
                        final int progress=(p*count+f+1)*90/total;
                        if(callback!=null) callback.progress(progress);
                    }
                    source.recycle(); frame.recycle();
                }
                int idx;
                while((idx=codec.dequeueInputBuffer(10000))<0){}
                codec.queueInputBuffer(idx,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                drain(codec,out,info,true);
                out.flush(); out.close();
                codec.stop(); codec.release(); codec=null;
                Movie movie=new Movie();
                Track track=new H264TrackImpl(new FileDataSourceImpl(raw),"eng",1,FPS);
                movie.addTrack(track);
                File dir=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                if(!dir.exists()) dir.mkdirs();
                File mp4=new File(dir,"MySlideshow_"+System.currentTimeMillis()+".mp4");
                IsoFile iso=new DefaultMp4Builder().build(movie);
                FileOutputStream fos=new FileOutputStream(mp4);
                iso.getBox(java.nio.channels.Channels.newChannel(fos)); fos.close();
                raw.delete();
                if(callback!=null) callback.done(mp4);
            } catch(final Exception e){ if(callback!=null) callback.error(e.getMessage()==null?"שגיאה ביצירת הסרטון":e.getMessage()); }
            finally { if(codec!=null){ try{codec.stop();}catch(Exception ignored){} codec.release(); } if(raw!=null)raw.delete(); }
        }}).start();
    }
    private static void drain(MediaCodec codec,FileOutputStream out,MediaCodec.BufferInfo info,boolean end) throws Exception {
        while(true){
            int status=codec.dequeueOutputBuffer(info,10000);
            if(status==MediaCodec.INFO_TRY_AGAIN_LATER){ if(!end) return; else continue; }
            if(status==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue;
            if(status>=0){
                ByteBuffer b=codec.getOutputBuffer(status);
                if(b!=null && info.size>0){ b.position(info.offset); b.limit(info.offset+info.size); byte[] bytes=new byte[info.size]; b.get(bytes); out.write(bytes); }
                boolean done=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                codec.releaseOutputBuffer(status,false);
                if(done)return;
            }
        }
    }
    private static byte[] toYuv(Bitmap bitmap){
        int w=bitmap.getWidth(),h=bitmap.getHeight(); int[] pixels=new int[w*h]; bitmap.getPixels(pixels,0,w,0,0,w,h);
        byte[] out=new byte[w*h*3/2]; int y=0,u=w*h,v=u+w*h/4, index=0;
        for(int j=0;j<h;j++) for(int i=0;i<w;i++){
            int c=pixels[index++], r=(c>>16)&255,g=(c>>8)&255,b=c&255;
            int yy=((66*r+129*g+25*b+128)>>8)+16;
            int uu=((-38*r-74*g+112*b+128)>>8)+128;
            int vv=((112*r-94*g-18*b+128)>>8)+128;
            out[y++]=(byte)(yy<0?0:yy>255?255:yy);
            if((j&1)==0 && (i&1)==0){ out[u++]=(byte)(uu<0?0:uu>255?255:uu); out[v++]=(byte)(vv<0?0:vv>255?255:vv); }
        }
        return out;
    }
    private static class MediaCodecInfoCompat { static final int COLOR_FORMAT_YUV420_PLANAR=19; }
}
