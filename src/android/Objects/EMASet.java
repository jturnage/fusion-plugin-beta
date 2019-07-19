package com.fusionetics.plugins.bodymap.Objects;
import com.fusionetics.plugins.bodymap.ThisPlugin;

import android.util.Log;
import java.util.Stack;

public class EMASet {

    private int size;
    private float smoothingFactor;
    private Stack progresses = new Stack();
    private double previousSmooth = 0.0;

    private static final float previousSmoothingFactor = 0.4f;
    private static final float currentSmoothingFactor = 0.5f;
    private static double previousSmoothedRemainingTime = 0.0;

    public EMASet(int _size, float _smoothingFactor) {
        size = _size;
        smoothingFactor = _smoothingFactor;
    }

    public static void Reset(EMASet[] emaSets) {
        for(int i = 0 ; i < emaSets.length ; i++) {
            EMASet thisEmaSet = emaSets[i];
            thisEmaSet.Reset();
        }
    }

    public static int calculateRemaining(EMASet[] emaSets, int currentPercent, int lastPercent) {
        double resultNumerator = 0.0;
        double resultDenominator = 0.0;
        for(int i = 0 ; i < emaSets.length ; i++) {
            EMASet thisEmaSet = emaSets[i];
            double result = thisEmaSet.calculateRemaining(currentPercent, lastPercent);
            double thisFactor = thisEmaSet.getFactor();
            resultNumerator += result * thisFactor;
            resultDenominator += thisFactor;
            Log.d(ThisPlugin.TAG, "EMASet.calculateRemaining - for:"+thisEmaSet.getSize()+" current:"+currentPercent+" last:"+lastPercent+" result:"+result+" factor:"+thisFactor);
        }

        double currentRemainingTime = resultNumerator / (resultDenominator > 0.0 ? resultDenominator : 0.1);
        double currentSmoothedRemainingTime = (previousSmoothedRemainingTime * previousSmoothingFactor) + (currentRemainingTime * currentSmoothingFactor); 
        previousSmoothedRemainingTime = currentSmoothedRemainingTime;

        int remainingTimeInSeconds = (int)Math.ceil(currentSmoothedRemainingTime);
        Log.d(ThisPlugin.TAG, "EMASet.calculateRemaining - numerator:"+resultNumerator + " denominator:"+resultDenominator+" result:"+currentRemainingTime+" ceil:"+remainingTimeInSeconds);
        return remainingTimeInSeconds;
    }

    private double calculateRemaining(int currentPercent, int lastPercent) {
        while(progresses.size() >= size)
            progresses.pop();

        progresses.push(currentPercent-lastPercent);

        double sum = 0.0f;
        for(int i = 0; i < progresses.size(); i++) {
            sum += (double)(int)(progresses.get(i));
        }

        double avg = (double)sum / (double)progresses.size();
        double remaining = (100-currentPercent)/avg;

        if(size == 3) Log.d(ThisPlugin.TAG, "EMASet.calculateRemaining - for:"+size+" current:"+currentPercent+" last:"+lastPercent+" sum:"+sum+" avg:"+avg+/*" smooth:"+currentSmooth+*/" remain:"+remaining);
        return remaining;
    }

    private void Reset() {
        progresses.clear();
        previousSmooth = 0.0;
    }

    private float getFactor() {
        if(smoothingFactor <= 0.0f) return .001f; // It shouldn't be, just a sanity check
        return smoothingFactor;
    }

    private int getSize() {
        return size;
    }

}

