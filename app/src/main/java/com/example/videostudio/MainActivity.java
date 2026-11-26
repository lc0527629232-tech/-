package com.example.videostudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int PICK_MEDIA = 41;
    private final ArrayList<Uri> photos = new ArrayList<Uri>();
    private LinearLayout root;
    private FrameLayout previewFrame;
    private ImageView imagePreview;
    private VideoView videoPreview;
    private TextView status;
    private EditText prompt;
    private EditText apiKey;
    private int photoIndex = 0;
    private Handler handler = new Handler();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER); t.setPadding(dp(8), dp(8), dp(8), dp(8)); return t;
    }
    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(48, 91, 180)); b.setAllCaps(false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.setMargins(0, dp(5), 0, dp(5)); b.setLayoutParams(p); return b;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(245,247,251));
        scroll.addView(root); setContentView(scroll);

        TextView title = text("🎬 סטודיו וידאו", 26, Color.rgb(30,45,75));
        root.addView(title);
        root.addView(text("עורך סרטונים ויצירה בעזרת AI", 15, Color.DKGRAY));

        previewFrame = new FrameLayout(this);
        previewFrame.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, dp(210));
        fp.setMargins(0, dp(10), 0, dp(10)); root.addView(previewFrame, fp);
        imagePreview = new ImageView(this); imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewFrame.addView(imagePreview, new FrameLayout.LayoutParams(-1,-1));
        videoPreview = new VideoView(this);
        previewFrame.addView(videoPreview, new FrameLayout.LayoutParams(-1,-1));
        TextView empty = text("התצוגה המקדימה תופיע כאן", 16, Color.WHITE);
        previewFrame.addView(empty);

        root.addView(text("עריכת וידאו", 20, Color.rgb(30,45,75)));
        Button addPhoto = button("הוספת תמונות");
        addPhoto.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pick("image/*"); }});
        root.addView(addPhoto);
        Button addVideo = button("פתיחת סרטון מהמכשיר");
        addVideo.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pick("video/*"); }});
        root.addView(addVideo);
        Button nextPhoto = button("הצגת התמונה הבאה");
        nextPhoto.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showNextPhoto(); }});
        root.addView(nextPhoto);
        Button stop = button("עצירת ניגון");
        stop.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ videoPreview.stopPlayback(); }});
        root.addView(stop);
        root.addView(text("אפשר לבחור תמונות ולדפדף ביניהן, או לנגן סרטון קיים. ייצוא וידאו מורכב יתווסף בשלב הבא לאחר בדיקת תאימות למכשיר.", 13, Color.DKGRAY));

        View line = new View(this); line.setBackgroundColor(Color.LTGRAY);
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        root.addView(text("יצירת סרטון באמצעות AI", 20, Color.rgb(30,45,75)));
        prompt = new EditText(this); prompt.setMinLines(3); prompt.setGravity(Gravity.TOP|Gravity.RIGHT);
        prompt.setHint("תאר את הסרטון שתרצה ליצור..."); root.addView(prompt, new LinearLayout.LayoutParams(-1, dp(100)));
        Button settings = button("הגדרות חיבור AI");
        settings.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showSettings(); }});
        root.addView(settings);
        Button generate = button("שליחת בקשת יצירה");
        generate.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ sendAiRequest(); }});
        root.addView(generate);
        status = text("מוכן לעבודה", 14, Color.rgb(55,90,70)); root.addView(status);
        root.addView(text("הערה: יצירת וידאו ב-AI מחייבת שירות חיצוני שתומך ביצירת וידאו דרך API. יש להגדיר כתובת API ומפתח מתאימים.", 12, Color.DKGRAY));
    }

    private void pick(String type) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT); i.setType(type); i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, "בחירת קובץ"), PICK_MEDIA); }
        catch(Exception e) { status.setText("לא ניתן לפתוח את בורר הקבצים במכשיר הזה"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request == PICK_MEDIA && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData(); String type = getContentResolver().getType(uri);
            if(type != null && type.startsWith("video/")) {
                imagePreview.setImageDrawable(null); videoPreview.setVideoURI(uri); videoPreview.start();
                status.setText("הסרטון נטען ומנגן");
            } else {
                photos.add(uri); photoIndex = photos.size()-1; videoPreview.stopPlayback();
                showPhoto(photoIndex); status.setText("נוספה תמונה. מספר תמונות: " + photos.size());
            }
        }
    }
    private void showNextPhoto() {
        if(photos.size()==0){ status.setText("תחילה הוסף תמונות"); return; }
        photoIndex=(photoIndex+1)%photos.size(); showPhoto(photoIndex);
    }
    private void showPhoto(int index) {
        try { imagePreview.setImageURI(photos.get(index)); status.setText("תמונה " + (index+1) + " מתוך " + photos.size()); }
        catch(Exception e){ status.setText("לא ניתן להציג את התמונה"); }
    }
    private void showSettings() {
        LinearLayout box = new LinearLayout(this); box.setPadding(dp(12),dp(8),dp(12),dp(8)); box.setOrientation(LinearLayout.VERTICAL);
        apiKey = new EditText(this); apiKey.setSingleLine(true); apiKey.setHint("מפתח API"); apiKey.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(apiKey);
        new AlertDialog.Builder(this).setTitle("הגדרות Gemini").setMessage("הזן מפתח Gemini API מ-Google AI Studio. המפתח נשמר במכשיר זה.").setView(box)
          .setPositiveButton("שמירה", (d,w)->{
              getPreferences(0).edit().putString("gemini_key",apiKey.getText().toString().trim()).apply();
              status.setText("ההגדרות נשמרו במכשיר");
          }).setNegativeButton("ביטול",null).show();
    }
    private void sendAiRequest() {
        final String promptText = prompt.getText().toString().trim();
        final String key = getPreferences(0).getString("gemini_key","");
        if(promptText.length()==0){ status.setText("כתוב תיאור לסרטון"); return; }
        if(key.length()==0){ status.setText("תחילה הזן מפתח Gemini API בהגדרות"); return; }
        status.setText("שולח בקשה ל-Gemini...");
        new Thread(new Runnable(){ public void run(){
            HttpURLConnection c=null;
            try {
                URL u=new URL("https://generativelanguage.googleapis.com/v1beta/interactions");
                c=(HttpURLConnection)u.openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(30000);
                c.setReadTimeout(180000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                c.setRequestProperty("x-goog-api-key",key);
                String safe=promptText.replace("\\\\","\\\\\\\\").replace("\\\"","\\\\\\\"").replace("\\n"," ");
                String json="{\\\"model\\\":\\\"gemini-omni-1.1-flash\\\",\\\"input\\\":[{\\\"type\\\":\\\"text\\\",\\\"text\\\":\\\""+safe+"\\\"}],\\\"response_format\\\":[{\\\"type\\\":\\\"video\\\",\\\"delivery\\\":\\\"inline\\\"}],\\\"generation_config\\\":{\\\"video_config\\\":{\\\"task\\\":\\\"text_to_video\\\"}}}";
                byte[] body=json.getBytes("UTF-8");
                OutputStream out=c.getOutputStream(); out.write(body); out.close();
                int code=c.getResponseCode();
                final String result="Gemini החזיר קוד "+code+". בדוק את תגובת השרת.";
                runOnUiThread(new Runnable(){ public void run(){ status.setText(result); }});
            } catch(final Exception e) {
                runOnUiThread(new Runnable(){ public void run(){ status.setText("שגיאת חיבור: "+e.getMessage()); }});
            } finally { if(c!=null)c.disconnect(); }
        }}).start();
    }}
