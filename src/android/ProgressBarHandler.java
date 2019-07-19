package com.fusionetics.plugins.bodymap;

import com.fusionetics.plugins.bodymap.ThisPlugin;
import com.fusionetics.plugins.bodymap.BodymapEventHandler;
import com.fusionetics.plugins.bodymap.Objects.EMASet;

import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.Math;
// import java.util.ArrayList;
import java.util.Stack;

public class ProgressBarHandler
{
    private Activity activity = null;
    private ProgressBar progressBar;
    private TextView progressBarText;
    private int lastPercent = 0;
    private int currentPercent = 0;
    private int currentTime = 0;
    private Handler timerHandler = new Handler();
    private int remainingProgress = 0;
    private int remainingTimeInSeconds = 0;

    private EMASet[] emaSets = {
        new EMASet(3, .5f), //.3f
        new EMASet(6, .3f), //.5f
        new EMASet(10, .2f) //.8f
    };

    ProgressBarHandler(Activity _activity, ProgressBar _progressBar, TextView _progressBarText) {
        this.activity = _activity;
        this.progressBar = _progressBar;
        this.progressBarText = _progressBarText;
    }

    public void Start() {
        NewTimer();
        lastPercent = 0;
        currentPercent = 0;
        currentTime = 0;
        remainingProgress = 0;
        remainingTimeInSeconds = 0;
        EMASet.Reset(emaSets);
    }

    public void End() {
        KillTimer();
    }

    public void UpdateProgress(int percent) {
        this.currentPercent = percent;

        UpdateProgressUI();
    }

    private void NewTimer() {
        timerHandler.postDelayed(timerCallback, 1000);
    }

    private void KillTimer() {
        timerHandler.removeCallbacks(timerCallback);
    }

    private Runnable timerCallback = new Runnable() {
        @Override
        public void run() {
            currentTime++;
            Log.d(ThisPlugin.TAG, "ProgressBarHandler - timerCallback.run: " + currentTime + "sec into it.");

            remainingTimeInSeconds = EMASet.calculateRemaining(emaSets, currentPercent, lastPercent);
            lastPercent = currentPercent;

            UpdateProgressUI();
            timerHandler.postDelayed(timerCallback, 1000);
        }
    };

    private void UpdateProgressUI() {
        Log.d(ThisPlugin.TAG, "ProgressBarHandler.UpdateProgressUI");
        this.progressBar.setProgress(this.currentPercent);

        if(this.currentPercent < 100) {
            String toDisplay = Integer.toString(this.currentPercent) + "%";
            if(this.currentTime > 2) {
                // toDisplay += " " + Integer.toString(this.currentTime) + "s";
                if(this.currentTime > 4) {
                    toDisplay += " (" + (this.remainingTimeInSeconds < 120 ? Integer.toString(this.remainingTimeInSeconds) + "s remaining" : ">2min remaining")+ ")";
                }
            }

            this.progressBarText.setText(toDisplay);
        } else {
            this.progressBarText.setText("Processing, please wait...");
        }
    }

}
