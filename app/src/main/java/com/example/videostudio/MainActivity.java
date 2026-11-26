package com.example.videostudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.IOException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int PICK_MEDIA = 41;
    private final ArrayList<Uri> videos = new ArrayList<Uri>();
    private LinearLayout timeline;
    private TextView countLabel;
    private Spinner durationSpinner;
    private final ArrayList<Uri> photos = new ArrayList<Uri>();
    private LinearLayout root;
    private ImageView imagePreview;
    private VideoView videoPreview;
    private TextView status;
    private TextView previewHint;
    private EditText prompt;
    private Spinner ratioSpinner;
    private int photoIndex = 0;
    private File lastVideo;

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        t.setPadding(dp(5), dp(7), dp(5), dp(7));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button actionButton(String value) {
        Button b = new Button(this);
        b.setText(value); b.setTextColor(Color.WHITE); b.setTextSize(15);
        b.setAllCaps(false); b.setBackground(shape(Color.rgb(48, 91, 190), 14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(50));
        p.setMargins(0, dp(5), 0, dp(5)); b.setLayoutParams(p);
        return b;
    }

    private LinearLayout section(String heading) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(12));
        card.setBackground(shape(Color.rgb(25,34,54), 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(8), 0, dp(8)); root.addView(card, p);
        TextView h = label(heading, 19, Color.rgb(235,242,255), true);
        card.addView(h);
        return card;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        root.setBackgroundColor(Color.rgb(12,18,32));
        scroll.addView(root); setContentView(scroll);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(18),dp(22),dp(18),dp(22));
        hero.setBackground(shape(Color.rgb(29,43,75),22)); root.addView(hero);
        TextView title=label("STUDIO  •  סטודיו וידאו",26,Color.WHITE,true); title.setGravity(Gravity.CENTER); hero.addView(title);
        TextView sub=label("הסרט שלך. התמונות שלך. היצירה שלך.",14,Color.rgb(190,208,240),false); sub.setGravity(Gravity.CENTER); hero.addView(sub);

        LinearLayout editor=section("🎬  סביבת העריכה");
        editor.setBackground(shape(Color.rgb(25,34,54),20));
        FrameLayout preview=new FrameLayout(this); preview.setBackground(shape(Color.rgb(5,9,17),16));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(210)); pp.setMargins(0,dp(8),0,dp(10)); editor.addView(preview,pp);
        imagePreview=new ImageView(this); imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER); preview.addView(imagePreview,new FrameLayout.LayoutParams(-1,-1));
        videoPreview=new VideoView(this); preview.addView(videoPreview,new FrameLayout.LayoutParams(-1,-1));
        previewHint=label("הוסף תמונות או סרטונים כדי להתחיל",15,Color.WHITE,false); previewHint.setGravity(Gravity.CENTER); preview.addView(previewHint,new FrameLayout.LayoutParams(-1,-1));

        Button addPhoto=actionButton("＋  הוספת תמונות"); addPhoto.setOnClickListener(v->pick("image/*")); editor.addView(addPhoto);
        Button addVideo=actionButton("▶  הוספת סרטונים"); addVideo.setOnClickListener(v->pick("video/*")); editor.addView(addVideo);
        countLabel=label("עדיין לא נוספו פריטים",13,Color.rgb(190,205,230),true); editor.addView(countLabel);
        timeline=new LinearLayout(this); timeline.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView strip=new HorizontalScrollView(this); strip.setHorizontalScrollBarEnabled(false);
        strip.addView(timeline); editor.addView(strip,new LinearLayout.LayoutParams(-1,dp(112)));
        Button next=actionButton("הבא בתצוגה"); next.setOnClickListener(v->showNextPhoto()); editor.addView(next);

        LinearLayout export=section("✨  יצירת סרטון MP4");
        export.setBackground(shape(Color.rgb(25,34,54),20));
        TextView help=label("בחר כמה שניות להציג כל תמונה, ואז צור סרטון ושמור אותו בתיקיית Movies.",14,Color.rgb(220,228,244),false); export.addView(help);
        durationSpinner=new Spinner(this);
        String[] durations={"3 שניות לכל תמונה","5 שניות לכל תמונה","7 שניות לכל תמונה"};
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,durations);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); durationSpinner.setAdapter(adapter);
        export.addView(durationSpinner,new LinearLayout.LayoutParams(-1,dp(48)));
        Button make=actionButton("▶  יצירת סרטון MP4"); make.setBackground(shape(Color.rgb(0,160,125),15));
        make.setOnClickListener(v->exportSlideshow()); export.addView(make);
        status=label("מוכן לעריכה",14,Color.rgb(120,230,190),true); status.setGravity(Gravity.CENTER);
        status.setBackground(shape(Color.rgb(25,66,62),12)); export.addView(status);
        TextView foot=label("היצירה מתבצעת במכשיר ללא Gemini וללא חיבור לאינטרנט. בשלב זה הייצוא כולל תמונות; סרטונים שמוסיפים ניתנים לתצוגה אך עדיין אינם משולבים בקובץ הסופי.",12,Color.rgb(180,194,218),false); export.addView(foot);
    }
    private void pick(String type) {
        Intent i=new Intent(Intent.ACTION_GET_CONTENT); i.setType(type);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i,"בחירת קבצים"),PICK_MEDIA); }
        catch(Exception e){setStatus("לא ניתן לפתוח את בורר הקבצים");}
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK_MEDIA || result!=RESULT_OK || data==null) return;
        if(data.getClipData()!=null) {
            for(int i=0;i<data.getClipData().getItemCount();i++) addMedia(data.getClipData().getItemAt(i).getUri());
        } else if(data.getData()!=null) addMedia(data.getData());
        refreshTimeline();
    }

    private void addMedia(Uri uri) {
        String type=getContentResolver().getType(uri);
        if(type!=null && type.startsWith("video/")) videos.add(uri);
        else { photos.add(uri); photoIndex=photos.size()-1; showPhoto(photoIndex); }
    }

    private void refreshTimeline() {
        timeline.removeAllViews();
        for(int i=0;i<photos.size();i++) {
            final int index=i;
            LinearLayout tile=new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(5),dp(5),dp(5),dp(5)); tile.setBackground(shape(Color.rgb(43,57,82),12));
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(dp(92),dp(100)); tp.setMargins(dp(3),dp(4),dp(3),dp(4)); timeline.addView(tile,tp);
            ImageView thumb=new ImageView(this); thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try{thumb.setImageURI(photos.get(i));}catch(Exception ignored){}
            tile.addView(thumb,new LinearLayout.LayoutParams(-1,dp(62)));
            TextView num=label("תמונה "+(i+1),11,Color.WHITE,true); num.setGravity(Gravity.CENTER); tile.addView(num);
            tile.setOnClickListener(v->{photoIndex=index;showPhoto(index);});
        }
        for(int i=0;i<videos.size();i++) {
            final Uri uri=videos.get(i);
            LinearLayout tile=new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(5),dp(5),dp(5),dp(5)); tile.setBackground(shape(Color.rgb(65,48,78),12));
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(dp(92),dp(100)); tp.setMargins(dp(3),dp(4),dp(3),dp(4)); timeline.addView(tile,tp);
            TextView icon=label("▶",30,Color.WHITE,true); icon.setGravity(Gravity.CENTER); tile.addView(icon,new LinearLayout.LayoutParams(-1,dp(62)));
            TextView num=label("סרטון "+(i+1),11,Color.WHITE,true); num.setGravity(Gravity.CENTER); tile.addView(num);
            tile.setOnClickListener(v->{imagePreview.setVisibility(View.GONE);previewHint.setVisibility(View.GONE);videoPreview.setVisibility(View.VISIBLE);videoPreview.setVideoURI(uri);videoPreview.start();});
        }
        countLabel.setText("ברצועה: "+photos.size()+" תמונות  •  "+videos.size()+" סרטונים");
        setStatus("המדיה נוספה לרצועת השקופיות");
    }

    private void exportSlideshow() {
        if(photos.size()==0){setStatus("כדי ליצור MP4 הוסף לפחות תמונה אחת");return;}
        final int seconds=durationSpinner.getSelectedItemPosition()==0?3:durationSpinner.getSelectedItemPosition()==1?5:7;
        setStatus("מכין סרטון MP4... נא להמתין");
        SlideshowExporter.export(this,new ArrayList<Uri>(photos),seconds,new SlideshowExporter.Callback(){
            public void progress(final int p){setStatus("יוצר סרטון... "+p+"%");}
            public void done(final File file){setStatus("הסרטון נשמר ב-Movies: "+file.getName());runOnUiThread(()->{imagePreview.setVisibility(View.GONE);previewHint.setVisibility(View.GONE);videoPreview.setVisibility(View.VISIBLE);videoPreview.setVideoURI(Uri.fromFile(file));videoPreview.setOnPreparedListener(mp->mp.start());});}
            public void error(final String message){setStatus("לא ניתן ליצור MP4: "+message);}
        });
    }

    private void showNextPhoto() {
        if(photos.size()==0){setStatus("תחילה הוסף תמונות");return;}
        photoIndex=(photoIndex+1)%photos.size();
        showPhoto(photoIndex);
    }

    private void showPhoto(int index) {
        try {
            videoPreview.setVisibility(View.GONE);
            previewHint.setVisibility(View.GONE);
            imagePreview.setVisibility(View.VISIBLE);
            imagePreview.setImageURI(photos.get(index));
            setStatus("תמונה "+(index+1)+" מתוך "+photos.size());
        } catch(Exception e) { setStatus("לא ניתן להציג את התמונה"); }
    }

    private void setStatus(final String message) {
        runOnUiThread(new Runnable(){ public void run(){ if(status!=null) status.setText(message); }});
    }
}
