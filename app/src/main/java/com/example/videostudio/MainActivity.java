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
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int PICK_MEDIA = 41;
    private final ArrayList<Uri> photos = new ArrayList<Uri>();
    private LinearLayout root;
    private ImageView imagePreview;
    private VideoView videoPreview;
    private TextView status;
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
        card.setBackground(shape(Color.WHITE, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(8), 0, dp(8)); root.addView(card, p);
        TextView h = label(heading, 19, Color.rgb(28, 48, 83), true);
        card.addView(h);
        return card;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(23, 42, 78));
        getWindow().setNavigationBarColor(Color.rgb(23, 42, 78));
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(22));
        root.setBackgroundColor(Color.rgb(239, 243, 250));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(16), dp(18), dp(16), dp(18));
        hero.setBackground(shape(Color.rgb(23, 42, 78), 20));
        root.addView(hero);
        TextView title = label("סטודיו וידאו", 27, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        hero.addView(title);
        TextView subtitle = label("יוצרים סרטונים בקלות בעזרת Gemini", 14, Color.rgb(220,230,250), false);
        subtitle.setGravity(Gravity.CENTER);
        hero.addView(subtitle);

        LinearLayout editor = section("🎞  עורך המדיה שלי");
        FrameLayout preview = new FrameLayout(this);
        preview.setBackground(shape(Color.rgb(16, 22, 34), 14));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(205));
        pp.setMargins(0, dp(5), 0, dp(8));
        editor.addView(preview, pp);
        imagePreview = new ImageView(this);
        imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.addView(imagePreview, new FrameLayout.LayoutParams(-1, -1));
        videoPreview = new VideoView(this);
        preview.addView(videoPreview, new FrameLayout.LayoutParams(-1, -1));
        TextView hint = label("התצוגה המקדימה תופיע כאן", 15, Color.WHITE, false);
        hint.setGravity(Gravity.CENTER);
        preview.addView(hint, new FrameLayout.LayoutParams(-1, -1));
        Button addPhoto = actionButton("＋  הוספת תמונות");
        addPhoto.setOnClickListener(v -> pick("image/*"));
        editor.addView(addPhoto);
        Button addVideo = actionButton("▶  פתיחת סרטון מהמכשיר");
        addVideo.setOnClickListener(v -> pick("video/*"));
        editor.addView(addVideo);
        Button next = actionButton("התמונה הבאה");
        next.setOnClickListener(v -> showNextPhoto());
        editor.addView(next);
        Button playLast = actionButton("▶  הפעלת סרטון AI אחרון");
        playLast.setOnClickListener(v -> playLastVideo());
        editor.addView(playLast);

        LinearLayout ai = section("✨  יצירת סרטון עם Gemini");
        TextView info = label("תאר במילים את הסרטון שתרצה. Gemini ייצור עבורך קליפ וידאו קצר.", 13, Color.DKGRAY, false);
        ai.addView(info);
        prompt = new EditText(this);
        prompt.setTextSize(15);
        prompt.setGravity(Gravity.TOP | Gravity.RIGHT);
        prompt.setMinLines(4);
        prompt.setPadding(dp(12), dp(10), dp(12), dp(10));
        prompt.setHint("לדוגמה: צילום קולנועי של מכונית ספורט נוסעת בכביש הררי בשקיעה...");
        prompt.setBackground(shape(Color.rgb(246,248,252), 12));
        ai.addView(prompt, new LinearLayout.LayoutParams(-1, dp(112)));

        TextView ratioLabel = label("פורמט הסרטון", 14, Color.rgb(45,55,75), true);
        ai.addView(ratioLabel);
        ratioSpinner = new Spinner(this);
        String[] ratios = new String[] {"רחב 16:9", "אנכי 9:16"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, ratios);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ratioSpinner.setAdapter(adapter);
        ai.addView(ratioSpinner, new LinearLayout.LayoutParams(-1, dp(48)));

        Button settings = actionButton("⚙  הגדרת מפתח Gemini");
        settings.setOnClickListener(v -> showSettings());
        ai.addView(settings);
        Button generate = actionButton("✨  צור לי סרטון");
        generate.setBackground(shape(Color.rgb(20, 145, 112), 14));
        generate.setOnClickListener(v -> generateVideo());
        ai.addView(generate);

        status = label("מוכן ליצירה", 14, Color.rgb(35, 105, 78), true);
        status.setGravity(Gravity.CENTER);
        status.setBackground(shape(Color.rgb(232, 247, 240), 12));
        ai.addView(status);
        TextView note = label("יצירת וידאו דורשת מפתח Gemini API וזמינות/חיוב מתאימים בחשבון Google. הסרטון נשמר בתיקיית Movies במכשיר.", 12, Color.DKGRAY, false);
        ai.addView(note);
    }

    private void pick(String type) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(type); i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, "בחירת קובץ"), PICK_MEDIA); }
        catch (Exception e) { setStatus("לא ניתן לפתוח את בורר הקבצים"); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_MEDIA && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String type = getContentResolver().getType(uri);
            if (type != null && type.startsWith("video/")) {
                imagePreview.setVisibility(View.GONE);
                videoPreview.setVisibility(View.VISIBLE);
                videoPreview.setVideoURI(uri);
                videoPreview.start();
                setStatus("הסרטון נטען ומנגן");
            } else {
                photos.add(uri); photoIndex = photos.size() - 1;
                videoPreview.setVisibility(View.GONE);
                imagePreview.setVisibility(View.VISIBLE);
                showPhoto(photoIndex);
            }
        }
    }

    private void showNextPhoto() {
        if (photos.size() == 0) { setStatus("תחילה הוסף תמונות"); return; }
        photoIndex = (photoIndex + 1) % photos.size();
        videoPreview.setVisibility(View.GONE);
        imagePreview.setVisibility(View.VISIBLE);
        showPhoto(photoIndex);
    }

    private void showPhoto(int index) {
        try {
            imagePreview.setImageURI(photos.get(index));
            setStatus("תמונה " + (index + 1) + " מתוך " + photos.size());
        } catch (Exception e) { setStatus("לא ניתן להציג את התמונה"); }
    }

    private void showSettings() {
        final EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setHint("הדבק כאן מפתח Gemini API");
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String saved = getPreferences(0).getString("gemini_key", "");
        keyInput.setText(saved);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(14), dp(8), dp(14), dp(4));
        box.addView(keyInput);
        new AlertDialog.Builder(this)
            .setTitle("חיבור ל-Gemini")
            .setMessage("המפתח נשמר במכשיר בלבד. אל תשתף אותו עם אחרים.")
            .setView(box)
            .setPositiveButton("שמירה", (d, w) -> {
                getPreferences(0).edit().putString("gemini_key", keyInput.getText().toString().trim()).apply();
                setStatus("מפתח Gemini נשמר");
            })
            .setNegativeButton("ביטול", null).show();
    }

    private void setStatus(final String message) {
        runOnUiThread(new Runnable() { public void run() { status.setText(message); }});
    }

    private void generateVideo() {
        final String text = prompt.getText().toString().trim();
        final String key = getPreferences(0).getString("gemini_key", "");
        if (text.length() == 0) { setStatus("כתוב תיאור לסרטון"); return; }
        if (key.length() == 0) { setStatus("תחילה הגדר מפתח Gemini"); return; }
        final String ratio = ratioSpinner.getSelectedItemPosition() == 1 ? "9:16" : "16:9";
        setStatus("Gemini יוצר את הסרטון... זה עשוי להימשך מספר דקות");
        new Thread(new Runnable() { public void run() {
            HttpURLConnection c = null;
            try {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/interactions");
                c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(45000);
                c.setReadTimeout(360000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                c.setRequestProperty("x-goog-api-key", key);
                JSONObject body = new JSONObject();
                body.put("model", "gemini-omni-1.1-flash");
                body.put("input", text);
                JSONObject format = new JSONObject();
                format.put("type", "video");
                format.put("aspect_ratio", ratio);
                format.put("resolution", "720p");
                body.put("response_format", format);
                OutputStream out = c.getOutputStream();
                out.write(body.toString().getBytes("UTF-8"));
                out.flush(); out.close();

                int code = c.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                String response = readAll(stream);
                if (code < 200 || code >= 300) {
                    JSONObject err = new JSONObject(response);
                    String msg = err.optJSONObject("error") != null ? err.getJSONObject("error").optString("message", response) : response;
                    throw new Exception("שגיאת Gemini (" + code + "): " + msg);
                }

                JSONObject result = new JSONObject(response);
                String videoData = findVideoData(result);
                if (videoData == null || videoData.length() == 0) {
                    String state = result.optString("status", "לא ידוע");
                    throw new Exception("הבקשה הסתיימה אך לא נמצא קובץ וידאו בתשובה. מצב: " + state);
                }
                byte[] bytes = Base64.decode(videoData, Base64.DEFAULT);
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("לא ניתן ליצור תיקיית Movies");
                File file = new File(dir, "GeminiVideo_" + System.currentTimeMillis() + ".mp4");
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(bytes); fos.flush(); fos.close();
                lastVideo = file;
                setStatus("הסרטון מוכן ונשמר: Movies/" + file.getName());
                runOnUiThread(new Runnable() { public void run() { playLastVideo(); }});
            } catch (final Exception e) {
                setStatus("לא הצלחנו ליצור סרטון: " + e.getMessage());
            } finally { if (c != null) c.disconnect(); }
        }}).start();
    }

    private String findVideoData(JSONObject response) throws Exception {
        JSONArray steps = response.optJSONArray("steps");
        if (steps == null) return null;
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step == null || !"model_output".equals(step.optString("type"))) continue;
            JSONArray content = step.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject item = content.optJSONObject(j);
                if (item != null && "video".equals(item.optString("type"))) {
                    String data = item.optString("data", "");
                    if (data.length() > 0) return data;
                }
            }
        }
        return null;
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
        input.close();
        return new String(out.toByteArray(), "UTF-8");
    }

    private void playLastVideo() {
        if (lastVideo == null || !lastVideo.exists()) {
            setStatus("עדיין לא נוצר סרטון AI במכשיר");
            return;
        }
        imagePreview.setVisibility(View.GONE);
        videoPreview.setVisibility(View.VISIBLE);
        videoPreview.setVideoURI(Uri.fromFile(lastVideo));
        videoPreview.setOnPreparedListener(mp -> mp.start());
        setStatus("מנגן את הסרטון שנוצר");
    }
}
