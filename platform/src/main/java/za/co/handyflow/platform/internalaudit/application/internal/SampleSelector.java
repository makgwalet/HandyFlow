package za.co.handyflow.platform.internalaudit.application.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Chooses which items of an ordered population go into a sample. The same population order, size, method and seed always give the
 * same sample, which is what lets a reviewer re-draw and check it. Works on positions, so it does not care what the items are.
 */
public final class SampleSelector {
    private SampleSelector() {}

    public record Draw(List<Integer> positions, Integer start) {}

    public static Draw select(int populationSize, int sampleSize, String method, long seed) {
        if (sampleSize < 1 || sampleSize > populationSize) {
            throw new IllegalArgumentException("Sample size must be between 1 and the population size (" + populationSize + ")");
        }
        Random rnd = new Random(seed);
        switch (method == null ? "RANDOM" : method) {
            case "RANDOM": {
                List<Integer> all = new ArrayList<>(populationSize);
                for (int i = 0; i < populationSize; i++) all.add(i);
                Collections.shuffle(all, rnd);
                List<Integer> picked = new ArrayList<>(all.subList(0, sampleSize));
                Collections.sort(picked);
                return new Draw(picked, null);
            }
            case "SYSTEMATIC": {
                int interval = populationSize / sampleSize;      // at least 1
                int start = rnd.nextInt(interval);               // random start inside the first interval
                List<Integer> picked = new ArrayList<>(sampleSize);
                for (int i = 0; i < sampleSize; i++) picked.add(start + i * interval);
                return new Draw(picked, start);
            }
            default:
                throw new IllegalArgumentException("Selection method " + method + " is not supported");
        }
    }
}
