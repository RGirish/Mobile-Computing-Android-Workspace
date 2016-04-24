package girish.raman.locationpredicttry;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener, RecognitionListener {

    static final int MIN_DISTANCE = 100;
    final int REQUEST_FINE_LOCATION = 100;
    final int REQUEST_EXTERNAL_STORAGE = 101;
    final int REQUEST_RECORD_AUDIO = 102;
    SQLiteDatabase db;
    private float downX;
    private float downY;
    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    private String LOG_TAG = "VoiceRecognitionActivity";

    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        return message;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        } else {
            setupSpeechRecognition();
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
        } else {
            createDatabase();
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
        } else {
            init();
        }

        startService(new Intent(this, WhereIsHeListenerService.class));
        startService(new Intent(this, SmartAppOpenSensor.class));

        Intent intent = new Intent(this, LocationAnalyzeService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Calendar futureTime = Calendar.getInstance();
        futureTime.set(Calendar.MONTH, Calendar.getInstance().get(Calendar.MONTH) + 1);
        alarmManager.set(AlarmManager.RTC_WAKEUP, Calendar.getInstance().getTimeInMillis() + 4838400000L, pendingIntent);
    }

    private void setupSpeechRecognition() {
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
    }

    private void init() {
        Intent intent = new Intent(this, LocationLogService.class);
        startService(intent);
        startService(new Intent(this, ButtonPatternListenerService.class));
        speakApplicationHasOpened();
        findViewById(R.id.main).setOnTouchListener(this);
    }

    public void speakApplicationHasOpened() {
        startTTSService("The application has been opened!");
    }

    void startTTSService(String textToSpeak) {
        Intent intent = new Intent(this, TTSService.class);
        intent.putExtra("textToSpeak", textToSpeak);
        startService(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {

            case REQUEST_RECORD_AUDIO: {
                if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(MainActivity.this, "Record Audio Access needed!", Toast.LENGTH_SHORT).show();
                } else {
                    setupSpeechRecognition();
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
                    }
                }
            }

            case REQUEST_EXTERNAL_STORAGE: {
                if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(MainActivity.this, "External Storage Access needed!", Toast.LENGTH_SHORT).show();
                } else {
                    createDatabase();
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
                    }
                }
            }

            case REQUEST_FINE_LOCATION: {
                if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(MainActivity.this, "GPS Access Permission needed!", Toast.LENGTH_SHORT).show();
                } else {
                    init();
                }
            }
        }
    }

    private void createDatabase() {
        db = openOrCreateDatabase(Environment.getExternalStorageDirectory() + File.separator + "location.db", SQLiteDatabase.CREATE_IF_NECESSARY, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS locationLog(dayOfWeek TEXT, hour TEXT, minute TEXT, latitude TEXT, longitude TEXT, speed TEXT, address TEXT, day TEXT, month TEXT, accuracy TEXT);");
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                downX = event.getX();
                downY = event.getY();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                float upX = event.getX();
                float upY = event.getY();

                float deltaX = downX - upX;
                float deltaY = downY - upY;

                if (Math.abs(deltaX) > MIN_DISTANCE) {
                    if (deltaX < 0) {
                        this.onLeftToRightSwipe();
                        return true;
                    }
                    if (deltaX > 0) {
                        this.onRightToLeftSwipe();
                        return true;
                    }
                } else if (Math.abs(deltaY) > MIN_DISTANCE) {
                    if (deltaY < 0) {
                        this.onTopToBottomSwipe();
                        return true;
                    }
                    if (deltaY > 0) {
                        speech.cancel();
                        speech.startListening(recognizerIntent);
                        return true;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public void onLeftToRightSwipe() {
        speakOutTheTime();
    }

    public void onTopToBottomSwipe() {
        speakOutTheDate();
    }

    public void onRightToLeftSwipe() {
        speakOutTheDay();
    }

    public void speakOutTheTime() {
        Calendar calendar = Calendar.getInstance();
        String textToSpeak = calendar.get(Calendar.HOUR_OF_DAY) + " hours " + calendar.get(Calendar.MINUTE) + " minutes " + calendar.get(Calendar.SECOND) + " seconds ";
        startTTSService(textToSpeak);
    }

    public void speakOutTheDate() {
        Calendar calendar = Calendar.getInstance();
        String month = null;
        String textToSpeak;

        switch (calendar.get(Calendar.MONTH)) {
            case 0:
                month = "January";
                break;
            case 1:
                month = "February";
                break;
            case 2:
                month = "March";
                break;
            case 3:
                month = "April";
                break;
            case 4:
                month = "May";
                break;
            case 5:
                month = "June";
                break;
            case 6:
                month = "July";
                break;
            case 7:
                month = "August";
                break;
            case 8:
                month = "September";
                break;
            case 9:
                month = "October";
                break;
            case 10:
                month = "November";
                break;
            case 11:
                month = "December";
                break;

        }
        if (calendar.get(Calendar.DAY_OF_MONTH) == 1 || calendar.get(Calendar.DAY_OF_MONTH) == 21 || calendar.get(Calendar.DAY_OF_MONTH) == 31) {
            textToSpeak = calendar.get(Calendar.DAY_OF_MONTH) + "st of " + month + " " + Integer.toString(calendar.get(Calendar.YEAR)).substring(1);
        } else if (calendar.get(Calendar.DAY_OF_MONTH) == 2 || calendar.get(Calendar.DAY_OF_MONTH) == 22) {
            textToSpeak = calendar.get(Calendar.DAY_OF_MONTH) + "nd of " + month + " " + Integer.toString(calendar.get(Calendar.YEAR)).substring(1);
        } else if (calendar.get(Calendar.DAY_OF_MONTH) == 3 || calendar.get(Calendar.DAY_OF_MONTH) == 23) {
            textToSpeak = calendar.get(Calendar.DAY_OF_MONTH) + "rd of " + month + " " + Integer.toString(calendar.get(Calendar.YEAR)).substring(1);
        } else {
            textToSpeak = calendar.get(Calendar.DAY_OF_MONTH) + "th " + month + " " + Integer.toString(calendar.get(Calendar.YEAR)).substring(1);
        }
        startTTSService(textToSpeak);
    }

    public void speakOutTheDay() {
        Calendar calendar = Calendar.getInstance();
        String textToSpeak = null;
        switch (calendar.get(Calendar.DAY_OF_WEEK)) {
            case 2:
                textToSpeak = "Monday";
                break;
            case 3:
                textToSpeak = "Tuesday";
                break;
            case 4:
                textToSpeak = "Wednesday";
                break;
            case 5:
                textToSpeak = "Thursday";
                break;
            case 6:
                textToSpeak = "Friday";
                break;
            case 7:
                textToSpeak = "Saturday";
                break;
            case 1:
                textToSpeak = "Sunday";
                break;

        }
        startTTSService(textToSpeak);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (speech != null) {
            speech.destroy();
            Log.i(LOG_TAG, "destroy");
        }

    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i(LOG_TAG, "onBeginningOfSpeech");
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.i(LOG_TAG, "onBufferReceived: " + buffer);
    }

    @Override
    public void onEndOfSpeech() {
        Log.i(LOG_TAG, "onEndOfSpeech");
    }

    @Override
    public void onError(int errorCode) {
        String errorMessage = getErrorText(errorCode);
        Log.d(LOG_TAG, "FAILED " + errorMessage);
        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
        Log.i(LOG_TAG, "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0) {
        Log.i(LOG_TAG, "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        Log.i(LOG_TAG, "onReadyForSpeech");
    }

    @Override
    public void onResults(Bundle results) {
        Log.i(LOG_TAG, "onResults");
        speech.stopListening();
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null) {
            if (matches.contains("where is father")) {
                Intent i = new Intent(this, TTSService.class);
                i.putExtra("textToSpeak", getSharedPreferences("WhereIsHe", MODE_PRIVATE).getString("fathersAddress", "Location Unknown"));
                startService(i);
                Toast.makeText(MainActivity.this, "Where is father?", Toast.LENGTH_SHORT).show();
            }

            if (matches.contains("where is mother")) {
                Intent i = new Intent(this, TTSService.class);
                i.putExtra("textToSpeak", getSharedPreferences("WhereIsHe", MODE_PRIVATE).getString("mothersAddress", "Location Unknown"));
                startService(i);
                Toast.makeText(MainActivity.this, "Where is mother?", Toast.LENGTH_SHORT).show();
            }

            if (matches.contains("where am i")) {
                Intent i = new Intent(this, TTSService.class);
                i.putExtra("textToSpeak", whereAmI());
                startService(i);
                Toast.makeText(MainActivity.this, "Where am i?", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(MainActivity.this, "No matches found!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        Log.i(LOG_TAG, "onRmsChanged: " + rmsdB);
    }

    public String whereAmI() {
        Geocoder geocoder;
        List<Address> addresses = null;
        geocoder = new Geocoder(this, Locale.getDefault());

        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            List<String> providers = lm.getProviders(true);
            for (String provider : providers) {
                Location location = lm.getLastKnownLocation(provider);
                if (location == null) {
                    Log.e("null", provider);
                    continue;
                }
                double longitude = location.getLongitude();
                double latitude = location.getLatitude();

                addresses = geocoder.getFromLocation(latitude, longitude, 1);
                Address addr = addresses.get(0);
                String address = addr.getAddressLine(0);
                String city = addr.getLocality();
                String state = addr.getAdminArea();
                String country = addr.getCountryName();
                return address + " " + city + " " + state + " " + country;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "Location Unknown";
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            processIntent(intent);
        }
    }

    void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) rawMsgs[0];

        byte[] payload = msg.getRecords()[0].getPayload();
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
        int languageCodeLength = payload[0] & 0063;
        try {
            String stringToSpeak = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
            Intent intent2 = new Intent(this, TTSService.class);
            intent2.putExtra("textToSpeak", stringToSpeak);
            startService(intent2);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public void onclickwhereisfather(View view) {
        Toast.makeText(MainActivity.this, "One moment...", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, TTSService.class);
        i.putExtra("textToSpeak", getSharedPreferences("WhereIsHe", MODE_PRIVATE).getString("fathersAddress", "Location Unknown"));
        startService(i);
    }

    public void onclickwhereami(View view) {
        Toast.makeText(MainActivity.this, "One moment...", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, TTSService.class);
        i.putExtra("textToSpeak", whereAmI());
        startService(i);
    }
}