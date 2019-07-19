package com.fusionetics.plugins.bodymap.Objects;
import com.fusionetics.plugins.bodymap.ThisPlugin;

import java.util.Stack;

public class EMASet {

    private int size;
    private float smoothingFactor;
    private Stack progresses = new Stack();
    private double previousSmooth = 0.0;

    private static final float previousSmoothingFactor = 0.3f;
    private static final float currentSmoothingFactor = 0.4f;
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

    public static int calculateRemaining(EMASet[] emaSets, int currentPercent) {
        double resultNumerator = 0.0;
        double resultDenominator = 0.0;
        for(int i = 0 ; i < emaSets.length ; i++) {
            EMASet thisEmaSet = emaSets[i];
            double result = thisEmaSet.calculateRemaining(currentPercent);
            resultNumerator += result * thisEmaSet.getFactor();
            resultDenominator += (double)thisEmaSet.getFactor();
        }

        // int remainingTimeInSeconds = (int)Math.ceil(resultNumerator/(resultDenominator>0.0?resultDenominator:0.1));
        double currentRemainingTime = resultNumerator / (resultDenominator > 0.0 ? resultDenominator : 0.1);
        double currentSmoothedRemainingTime = previousSmoothedRemainingTime - (previousSmoothingFactor * previousSmoothedRemainingTime) + (currentRemainingTime * currentSmoothingFactor);
        previousSmoothedRemainingTime = currentSmoothedRemainingTime;

        int remainingTimeInSeconds = (int)Math.ceil(currentSmoothedRemainingTime);
        return remainingTimeInSeconds;
    }

    private double calculateRemaining(int currentPercent) {
        while(progresses.size() >= size)
            progresses.pop();

        progresses.push(currentPercent);

        float avg = 0.0f;
        for(int i = 0; i < progresses.size(); i++) {
            avg += (int)progresses.get(i);
        }
        avg /= progresses.size();
        // double currentSmooth = previousSmooth - (previousSmoothingFactor * previousSmooth) + (avg * currentSmoothingFactor);
        // previousSmooth = currentSmooth;

        // double remaining = currentSmooth > 0 ? (100-currentPercent /*remainingProgress*/) / currentSmooth : 0.0;
        double remaining = (100-currentPercent)/avg;
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

}

