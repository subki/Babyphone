package com.example.subki.babyphon;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;


public class main extends AppCompatActivity {

    // internal constants
    private static final String PREFERENCES_NAME = "babyphonPrefs";

    private static final int STATUS_NEVER_REACHED=-99; // trigger initial invalidate
    private static final int STATUS_ERROR= -4;
    private static final int STATUS_CALLING_DISABLED = -3;
    private static final int STATUS_DISABLED_IN_PATIENCE_PERIOD = -2;
    private static final int STATUS_UNSET=-1;
    private static final int STATUS_GREEN = 0;
    private static final int STATUS_ALARM = 1;

    // handler
    Handler globalUpdateHandler;
    Handler currentLoudnessUpdateHandler;

    // update frequency
    private static final short UPDATE_INTERVAL_MILLISECONDS = 500;

    // misc globals
    private Timer geteNewMicValueTimer;
    private TimerTask globalScreenUpdateTask;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;
    private Date gracePeriodStart;
    private static final short PATIENCE_PERIOD_TOTAL_SECONDS = 30;
    private float patiencePeriodSecondsLeft;
    private PowerManager powermanager;
    private PowerManager.WakeLock wakelock;
    private int lastStatus = STATUS_NEVER_REACHED;


    // screen elements
    private CheckBox screenDoCallStatus;
    private Button screenStatusIndicator;
    private TextView screenLoudnessCurrent;
    private Button screenLoudnessLimitDown;
    private TextView screenLoudnessLimit;
    private Button screenLoudnessLimitUp;
    private EditText screenPhoneNumber;
    private Button screenPhoneTestCall;
    private TextView screenLog;

    // screen elements' initial content
    private Boolean doCallStatusBoolean = false;
    private int loudnessCurrentInt = (int) 0;
    private int loudnessLimitInt = (int) 500;
    private Boolean alarmStatusBoolean = false;

    // mic input object
    private MicrophoneInput micInput;

    /** Called when the activity is initially (!) launched. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Power Manager

        // get telephone status (detect ended call)
        phoneStateListener = new CallEndedListener();
        telephonyManager = (TelephonyManager) this
                .getSystemService(TELEPHONY_SERVICE);

        // override whatever was stored wrt. doCallStatus
        SharedPreferences settings = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("doCallStatus", false);
        editor.commit();

        // baptize screen elements
        screenDoCallStatus = (CheckBox) findViewById(R.id.CheckBoxDoCall);
        screenStatusIndicator = (Button) findViewById(R.id.ButtonStatusIndicator);
        screenLoudnessCurrent = (TextView) findViewById(R.id.TextViewLoudnessCurrent);
        screenLoudnessLimitDown = (Button) findViewById(R.id.ButtonLoudnessLimitDown);
        screenLoudnessLimit = (TextView) findViewById(R.id.TextViewLoudnessLimit);
        screenLoudnessLimitUp = (Button) findViewById(R.id.ButtonLoudnessLimitUp);
        screenPhoneNumber = (EditText) findViewById(R.id.EditTextPhoneNumber);
        screenPhoneTestCall = (Button) findViewById(R.id.ButtonPhoneTestCall);
        screenLog = (TextView) findViewById(R.id.TextViewLog);

        // screen elements' initial content
        screenDoCallStatus.setChecked(doCallStatusBoolean);
        if (doCallStatusBoolean) screenDoCallStatus.setText("Calling activated");
        else screenDoCallStatus.setText("Do call on alarm?");
        screenStatusIndicator.setText("");
        screenLoudnessLimit.setText("500");

        //
        // Register button listeners
        //

        // handle button "Do Call"
        screenDoCallStatus.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // trigger activation status
                if (doCallStatusBoolean == true) {
                    doCallStatusBoolean = false;
                    screenDoCallStatus.setText("Do call on alarm?");
                    log("Calling deactivated.");
                } else {
                    doCallStatusBoolean = true;
                    screenDoCallStatus.setText("Calling activated.");
                    log("Calling activated.");
                    // wait
                    gracePeriodStart = new Date();
                }
            }
        });

        // handle button limit down
        screenLoudnessLimitDown.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // trigger activation status
                loudnessLimitInt = loudnessLimitInt - 50;
                if (loudnessLimitInt < 0)
                    loudnessLimitInt = 0;

                log("Alarm limit reduced to " + loudnessLimitInt);
                screenLoudnessLimit.setText(String.valueOf(loudnessLimitInt));
            }
        });

        // handle button "limit up"
        screenLoudnessLimitUp.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // trigger activation status
                loudnessLimitInt = loudnessLimitInt + 50;
                log("Alarm limit raised to " + loudnessLimitInt);
                screenLoudnessLimit.setText(String.valueOf(loudnessLimitInt));
            }
        });

        // handle "call now" button
        screenPhoneTestCall.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // place test call
                if (screenPhoneNumber.getText() != null) {
                    try {
                        performCall(screenPhoneNumber.getText().toString());
                        log("Test call initiated: " + screenPhoneNumber.getText().toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                        log("Test call failed: " + e.toString());
                    }
                }
            }
        });
    }

    private void savePrefs()
    {
        // save data using SharedPreferences
        // (cf. android-sdk-linux_86/docs/guide/topics/data/data-storage.html#pref)
        //
        // We need an Editor object to make preference changes.
        // All objects are from android.context.Context
        SharedPreferences settings = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("phoneNumber", screenPhoneNumber.getText().toString());
        editor.putInt("loudnessLimit", Integer.valueOf(screenLoudnessLimit.getText().toString()).intValue());
        editor.putString("log",screenLog.getText().toString());
        editor.putBoolean("doCallStatus", doCallStatusBoolean);
        // Commit the edits!
        editor.commit();
    }

    private void restorePrefs()
    {
        // Restore preferences
        SharedPreferences settings = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        screenPhoneNumber.setText(settings.getString("phoneNumber", "unset"));
        loudnessLimitInt = settings.getInt("loudnessLimit", 500);
        screenLog.setText(settings.getString("log",""));
        alarmStatusBoolean = settings.getBoolean("alarmStatus", false);
        lastStatus = STATUS_UNSET;
        doCallStatusBoolean = settings.getBoolean("doCallStatus", false);
        screenDoCallStatus.setChecked(doCallStatusBoolean);

        //cut down log
        String[] logArray = screenLog.getText().toString().split("\\n");
        String logString = "";
        if (logArray.length > 20)
        {
            for (int i = 0; i < 20; i++)
            {
                logString += logArray[i] + "\n";
            }
        }
        screenLog.setText(logString);
    }

    // perform after app comes to foreground (may be on initial startup)
    @Override
    public void onResume() {
        super.onResume();

        // load settings
        restorePrefs();

        // don't sleep too deep
        // SCREEN_DIM_WAKE_LOCK: "Wake lock that ensures that the screen is on (but may be dimmed);
        //                        the keyboard backlight will be allowed to go off."
        powermanager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakelock = powermanager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        try
        {
            wakelock.acquire();
            log("Preventing screen off (may dim).");
        }
        catch(Exception e)
        {
            log("Could not prevent screen sleep. Error: " + e.toString());
        }

        // (re-)set grace period
        gracePeriodStart = new Date();

        // initiate mic access
        micInput = new MicrophoneInput();

        // timer for screen updates
        geteNewMicValueTimer = new Timer();
        globalScreenUpdateTask = new TimerTask() {
            public void run() {
                globalScreenUpdate();
            }
        };

        loudnessCurrentInt = 0;

        // start global screen update "thread"
        geteNewMicValueTimer.scheduleAtFixedRate(globalScreenUpdateTask, 0,
                UPDATE_INTERVAL_MILLISECONDS);

        // handle screen update ticks
        globalUpdateHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {

                // get recent loudness
                try{
                    loudnessCurrentInt = micInput.getCurrentLoudness();
                } catch (Exception e){
                    System.out.print(e.toString());
                    loudnessCurrentInt = -10;
                }
                // set loudness text
                screenLoudnessCurrent.setText(String.valueOf(loudnessCurrentInt));

                // alarm triggered?
                if (loudnessCurrentInt > loudnessLimitInt)
                    alarmStatusBoolean = true;
                else
                    alarmStatusBoolean = false;

                // determine current status (lowest reason first

                int currentStatus = STATUS_GREEN; // assume OK first
                if (alarmStatusBoolean) currentStatus = STATUS_ALARM;
                // update patience period value
                if (inPatiencePeriod()) currentStatus = STATUS_DISABLED_IN_PATIENCE_PERIOD;
                if (!doCallStatusBoolean) currentStatus = STATUS_CALLING_DISABLED;
                if (loudnessCurrentInt < 0) currentStatus = STATUS_ERROR;

                // update status field on status change or when in patience period
                if (
                        (currentStatus != lastStatus)
                                || 	(currentStatus == STATUS_DISABLED_IN_PATIENCE_PERIOD)
                        )
                {
                    // update lastStatus
                    lastStatus = currentStatus;

                    // update status field
                    int newColor = Color.GREEN;
                    String newText = "";
                    switch (currentStatus)
                    {
                        case STATUS_GREEN:
                            newColor = Color.GREEN;
                            newText = "Monitoring. Everything's silent.";
                            break;
                        case STATUS_ALARM:
                            newColor = Color.RED;
                            newText = "Alarm! (Loudness limit exceeded)";
                            break;
                        case STATUS_DISABLED_IN_PATIENCE_PERIOD:
                            newColor = Color.YELLOW;
                            newText = "Calling is suspended for "
                                    + ((int) PATIENCE_PERIOD_TOTAL_SECONDS - (int) patiencePeriodSecondsLeft)
                                    + " more seconds.";
                            break;
                        case STATUS_CALLING_DISABLED:
                            newColor = Color.YELLOW;
                            newText = "Calling not activated.";
                            break;
                        case STATUS_ERROR:
                            newColor = Color.RED;
                            newText = "Error. App malfunctioning. Please restart.";

                    }
                    screenStatusIndicator.setBackgroundColor(newColor);
                    screenStatusIndicator.setText(newText);
                }

                // handle alarm
                if (alarmStatusBoolean)
                {
                    if (inPatiencePeriod())
                        log("Alarm ignored (" + loudnessCurrentInt + ")");
                    else if (!doCallStatusBoolean)
                        log("Alarm ignored (" + loudnessCurrentInt + ")");
                    else
                    {
                        log("Alarm triggered (" + loudnessCurrentInt + ")");
                        // call
                        log("Calling  " + screenPhoneNumber.getText().toString());
                        savePrefs();
                        performCall(screenPhoneNumber.getText().toString());
                    }
                }

                log("Alarm saat ini (" + loudnessCurrentInt + ")");
                // TODO: check for (valid) phone number
                // && (screenPhoneNumber.getText().toString().matches("[[0-9] ]*"));
            }
        };
    }

    // if other app comes to foreground
    @Override
    public void onPause() {
        super.onPause();

        savePrefs();

        // disable mic
        micInput.stop();

        // stop wake lock
        try
        {
            wakelock.release();
        }
        catch(Exception e)
        {
            log("Could not stop wake lock. Error: " + e.toString());
        }
    }

    private void globalScreenUpdate() {
        // call screen update handler
        globalUpdateHandler.sendEmptyMessage(1);
    }

    public void performCall(String phoneNumber) {
        // listen to call's begin and end
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);

        // Start the call
        try {
            globalScreenUpdateTask.cancel();
            geteNewMicValueTimer.purge();
            geteNewMicValueTimer.cancel();
            loudnessCurrentInt = 0;
            //SystemClock.sleep(1000);
            Intent myCall = new Intent(Intent.ACTION_CALL);
            myCall.setData(Uri.parse("tel:" + phoneNumber));
            startActivity(myCall);
        } catch (Exception e) {
            e.printStackTrace();
            log("Error in performCall(): " + e.toString());
        }
    }

    private void log(String message) {
        java.util.Date now = new java.util.Date();
        screenLog.setText(now.toLocaleString() + " " + message + "\n" + screenLog.getText().toString());
    }

    // after an alarm was triggered, a "patience period" is started
    // where no alarm can be re-triggered until after that period
    private boolean inPatiencePeriod() {
        patiencePeriodSecondsLeft = (new Date().getTime() - gracePeriodStart.getTime()) / 1000;
        if (patiencePeriodSecondsLeft < PATIENCE_PERIOD_TOTAL_SECONDS)
            return true;
        else
            return false;
    }

    class CallEndedListener extends PhoneStateListener {
        boolean called = false;

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);

            // run only after call is started
            if (state == TelephonyManager.CALL_STATE_OFFHOOK)
                called = true;

            // Call has ended -- now bring the activity back to front
            if (called && state == TelephonyManager.CALL_STATE_IDLE) {
                called = false;
                telephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
                startActivity(new Intent(main.this, main.class));
            }
        }
    }
}
