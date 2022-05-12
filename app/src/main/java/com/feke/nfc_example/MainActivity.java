package com.feke.nfc_example;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class MainActivity extends AppCompatActivity {

    public static final String Error_detected = "No NFC Tag Detected";
    public static final String Write_Success = "Text written successfully";
    public static final String Write_Error = "Error during writing, Try Again!";

    NfcAdapter nfcAdapter;
    PendingIntent pendingIntent;
    IntentFilter writeTagFilters[];
    boolean writeMode;
    Tag myTage;
    Context context;
    TextView message;
    TextView nfcContext;
    Button writeBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        message = (TextView) findViewById(R.id.message);
        nfcContext = (TextView) findViewById(R.id.tvResult);
        writeBtn = findViewById(R.id.writeBtn);

        context = this;

        writeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if(myTage == null ){
                        Toast.makeText(context, Error_detected, Toast.LENGTH_SHORT).show();
                    }else {
                        write("PlainText| "+message.getText().toString(), myTage);
                        Toast.makeText(context, Write_Success, Toast.LENGTH_SHORT).show();
                    }
                }catch (IOException e) {
                    Toast.makeText(context, Write_Error, Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }catch (FormatException e){
                    Toast.makeText(context, Write_Success  , Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        });

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if(nfcAdapter == null) {
            Toast.makeText(context, "This device does not support nfc", Toast.LENGTH_SHORT).show();
            return;
        }

        readFromIntent(getIntent());
        pendingIntent = PendingIntent.getActivity(context,0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writeTagFilters = new IntentFilter[] { tagDetected };
    }

    private void readFromIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
        || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
        || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsags = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs = null;
            if (rawMsags != null) {
                msgs = new NdefMessage[rawMsags.length];
                for (int i=0; i<rawMsags.length; i++) {
                    msgs[i] = (NdefMessage) rawMsags[i];
                }
            }
            buildTagViews(msgs);
        }
    }

    private void buildTagViews(NdefMessage[] msgs){
        if (msgs == null || msgs.length == 0) return;

        String text = "";

        byte[] payload = msgs[0].getRecords()[0].getPayload();
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16"; // Get the text encoding
        int languageCodeLength = payload[0] & 0063; // Get the language code, e.g "en"
//        String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");

        try {
            // Get the text
            text = new String(payload, languageCodeLength +1, payload.length-languageCodeLength-1, textEncoding);
        }catch (UnsupportedEncodingException e) {
            Log.e("unsupportedException", e.toString());
        }

        nfcContext.setText("Nfc content: "+text);
    }

    private void write(String text, Tag tag) throws IOException, FormatException{
        NdefRecord[] records = {createRecord(text)};
        NdefMessage message = new NdefMessage(records);

        //Get an instance of Ndef for the tag.
        Ndef ndef = Ndef.get(tag);
        // Enable I/O
        ndef.connect();
        // Write message
        ndef.writeNdefMessage(message);
        //Close the connection
        ndef.close();
    }

    private NdefRecord createRecord(String  text) throws UnsupportedEncodingException {
        String lang      = "en";
        byte[] textBytes = text.getBytes();
        byte[] langBytes = lang.getBytes("US-ASCII");
        int langLength = langBytes.length;
        int textLength = textBytes.length;
        byte[] payload = new byte[1+langLength+textLength];

        // set status byte (see NDEF spec for actual bits)
        payload[0] = (byte) langLength;

        // copy langBytes and textBytes into payload
        System.arraycopy(langBytes, 0, payload,1, langLength);
        System.arraycopy(textBytes, 0, payload, 1+langLength, textLength);

        NdefRecord ndefRecord = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload);

        return  ndefRecord;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readFromIntent(intent);

        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            myTage = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        writeModeOff();
    }

    @Override
    protected void onResume() {
        super.onResume();
        writeModeOn();
    }

    //Enable write
    private void writeModeOn() {
        writeMode = true;
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, writeTagFilters, null);
    }

    //Disable write
    private void writeModeOff() {
        writeMode = false;
        nfcAdapter.disableForegroundDispatch(this);
    }
}