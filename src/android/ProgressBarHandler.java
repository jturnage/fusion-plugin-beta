package com.fusionetics.plugins.bodymap;

import com.fusionetics.plugins.bodymap.ThisPlugin;
import com.fusionetics.plugins.bodymap.BodymapEventHandler;

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
    // private float averagePercentPerSec = 0f;
    private Handler timerHandler = new Handler();
    private int remainingProgress = 0;
    private int recentProgress = 0;
    private int remainingTimeInSeconds = 0;
    private final float currentSmoother = 0.5f;
    private final float previousSmoother = 0.6f;

    private Stack progressOver3 = new Stack();
    private double previousSmooth3 = 0.0;
    private float smoothingFactorFor3 = 1.0f; // .2;

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
        // averagePercentPerSec = 0f;
        remainingProgress = 0;
        remainingTimeInSeconds = 0;
        recentProgress = 0;
        previousSmooth3 = 0.0;
        progressOver3.clear();
    }

    public void End() {
        KillTimer();
    }

    public void UpdateProgress(int percent) {
        this.lastPercent = this.currentPercent;
        this.currentPercent = percent;
        remainingProgress = 100-this.currentPercent;
        recentProgress = this.currentPercent - this.lastPercent;

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

            calculateTimeremainingProgress();
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
                toDisplay += " " + Integer.toString(this.currentTime) + "s";
                if(this.currentTime > 4) {
                    toDisplay += " " + this.remainingTimeInSeconds < 120 ? Integer.toString(this.remainingTimeInSeconds) + "s remaining" : ">2min remaining";
                }
            }

            this.progressBarText.setText(toDisplay);
        } else {
            this.progressBarText.setText("Processing, please wait...");
        }
    }

    private int calculateTimeremainingProgress() {
        this.lastPercent = this.currentPercent;

        while(this.progressOver3.size() >= 3)
            this.progressOver3.pop();

        this.progressOver3.push(this.currentPercent);
        float avgOver3 = 0.0f;
        for(int i = 0; i < this.progressOver3.size(); i++) {
            avgOver3 += (int)this.progressOver3.get(i);
        }
        avgOver3 /= this.progressOver3.size();

        double currentSmooth3 = this.previousSmooth3 - (previousSmoother * this.previousSmooth3) + (avgOver3 * this.currentSmoother);
        this.previousSmooth3 = currentSmooth3;

        double remainingBasedOn3 = currentSmooth3 > 0 ? this.remainingProgress / currentSmooth3 : 0.0;

        this.remainingTimeInSeconds = (int)Math.ceil((remainingBasedOn3*smoothingFactorFor3)/(smoothingFactorFor3));
        return 1;
    }
}
