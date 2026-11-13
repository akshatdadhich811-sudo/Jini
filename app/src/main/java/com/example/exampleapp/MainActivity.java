package com.example.exampleapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // यहाँ अपनी Gemini API key लिखें
    private static final String GEMINI_KEY = "YOUR_GEMINI_KEY";
    private static final String GEMINI_MODEL = "gemini-2.5-flash";

    private static final String SYSTEM_PROMPT =
            "तुम एक दोस्ताना और समझदार घरेलू असिस्टेंट हो। सरल हिंदी में जवाब दो, ज़्यादातर 2-4 वाक्य। " +
            "पढ़ाई में मदद करते समय step-by-step समझाओ। किसी की तैयारी के बारे में पूछा जाए तो व्यावहारिक योजना बताओ। " +
            "कोई उदास या परेशान लगे तो प्यार से सुनो, और उसे किसी अपने इंसान से बात करने के लिए भी कहो। " +
            "मेडिकल, कानूनी या पैसों के बड़े फ़ैसलों पर सामान्य जानकारी दो और किसी विशेषज्ञ से मिलने की सलाह दो।";

    private static final String[] ALL_PERMS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
    };

    private static final List<String> CALL_STOP_WORDS = Arrays.asList(
            "call", "karo", "kar", "ko", "do", "mujhe", "please", "कॉल", "करो", "कर", "को", "करें"
    );

    private final JSONArray history = new JSONArray();

    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private TextView statusText;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        Button talkButton = findViewById(R.id.talkButton);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean mic = result.get(Manifest.permission.RECORD_AUDIO);
                    if (mic != null && mic) {
                        startListening();
                    } else {
                        show("Mic permission chahiye।");
                    }
                });

        tts = new TextToSpeech(this, this);
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    handle(list.get(0).toLowerCase(Locale.ROOT));
                }
            }
            @Override public void onError(int error) { show("सुन नहीं पाया (error " + error + ")"); }
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        talkButton.setOnClickListener(v -> {
            List<String> missing = new ArrayList<>();
            for (String p : ALL_PERMS) {
                if (!hasPerm(p)) missing.add(p);
            }
            if (missing.isEmpty()) {
                startListening();
            } else {
                permissionLauncher.launch(missing.toArray(new String[0]));
            }
        });
    }

    private boolean hasPerm(String perm) {
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("hi", "IN"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US);
            }
        }
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        show("सुन रहा हूँ...");
        recognizer.startListening(intent);
    }

    private void handle(String cmd) {
        show("You: " + cmd);

        if (cmd.contains("receive") || cmd.contains("रिसीव")) {
            reply("Call receive करना इस ऐप से नहीं हो सकता। फ़ोन का हरा बटन दबाएँ।");
        } else if (cmd.contains("call") || cmd.contains("कॉल")) {
            callByName(cmd);
        } else if (cmd.contains("time") || cmd.contains("समय")) {
            String now = new SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(new Date());
            reply("अभी " + now + " हुआ है।");
        } else if (cmd.contains("date") || cmd.contains("तारीख")) {
            String today = new SimpleDateFormat("dd MMMM yyyy", new Locale("hi", "IN")).format(new Date());
            reply("आज की तारीख " + today + " है।");
        } else if (cmd.contains("video") || cmd.contains("gaana") || cmd.contains("play")
                || cmd.contains("चलाओ") || cmd.contains("लगाओ")) {
            try {
                String query = cmd.replace("video", "").replace("gaana", "").replace("play", "")
                        .replace("chalao", "").replace("lagao", "")
                        .replace("चलाओ", "").replace("लगाओ", "").trim();
                String url = "https://www.youtube.com/results?search_query="
                        + URLEncoder.encode(query, "UTF-8");
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                reply(query + " के लिए YouTube खोल दिया।");
            } catch (Exception e) {
                reply("YouTube नहीं खुल पाया।");
            }
        } else if (cmd.contains("chrome") || cmd.contains("क्रोम")) {
            Intent chrome = getPackageManager().getLaunchIntentForPackage("com.android.chrome");
            if (chrome != null) {
                startActivity(chrome);
                reply("Chrome खोल दिया।");
            } else {
                reply("Chrome इस फ़ोन में नहीं मिला।");
            }
        } else {
            show("सोच रहा हूँ...");
            new Thread(() -> reply(askGemini(cmd))).start();
        }
    }

    private void callByName(String cmd) {
        try {
            if (!hasPerm(Manifest.permission.READ_CONTACTS)) {
                reply("Contacts की permission दें, फिर दोबारा बोलें।");
                return;
            }
            String name = extractName(cmd);
            if (name.isEmpty()) {
                reply("किसको call करना है?");
                return;
            }
            String number = findNumber(name);
            if (number == null) {
                reply(name + " नाम का contact नहीं मिला।");
                return;
            }
            if (hasPerm(Manifest.permission.CALL_PHONE)) {
                startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number)));
                reply(name + " को call कर रहा हूँ।");
            } else {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
                reply(name + " का dialer खोल दिया। Call बटन दबाएँ।");
            }
        } catch (Exception e) {
            reply("Call नहीं हो पाया।");
        }
    }

    private String extractName(String cmd) {
        StringBuilder sb = new StringBuilder();
        for (String token : cmd.trim().split("\\s+")) {
            if (!CALL_STOP_WORDS.contains(token)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(token);
            }
        }
        return sb.toString().trim();
    }

    private String findNumber(String name) {
        Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                },
                null, null, null);
        if (c == null) return null;
        String found = null;
        String target = name.toLowerCase(Locale.ROOT);
        try {
            while (c.moveToNext()) {
                String displayName = c.getString(1);
                if (displayName != null
                        && displayName.toLowerCase(Locale.ROOT).contains(target)) {
                    found = c.getString(0);
                    break;
                }
            }
        } finally {
            c.close();
        }
        return found;
    }

    private String askGemini(String question) {
        try {
            JSONObject userMsg = new JSONObject()
                    .put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", question)));
            history.put(userMsg);
            while (history.length() > 10) {
                history.remove(0);
                history.remove(0);
            }

            JSONObject body = new JSONObject();
            body.put("systemInstruction", new JSONObject().put("parts",
                    new JSONArray().put(new JSONObject().put("text", SYSTEM_PROMPT))));
            body.put("contents", history);

            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                    + GEMINI_MODEL + ":generateContent?key=" + GEMINI_KEY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            String answer = new JSONObject(sb.toString())
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text").trim();

            history.put(new JSONObject()
                    .put("role", "model")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", answer))));
            return answer;
        } catch (Exception e) {
            if (history.length() > 0) history.remove(history.length() - 1);
            return "AI से connection नहीं हो पाया। API key और इंटरनेट चेक करो।";
        }
    }

    private void reply(String text) {
        show("Assistant: " + text);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply");
    }

    private void show(String msg) {
        runOnUiThread(() -> statusText.append("\n" + msg));
    }

    @Override
    protected void onDestroy() {
        recognizer.destroy();
        tts.shutdown();
        super.onDestroy();
    }
            }
