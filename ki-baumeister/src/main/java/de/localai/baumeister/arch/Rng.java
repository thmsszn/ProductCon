package de.localai.baumeister.arch;

import java.util.List;

/** Deterministischer Zufall (SplitMix64): gleicher Entwurf + gleicher Seed = gleiches Gebäude. */
public final class Rng {
    private long state;

    public Rng(long seed) {
        this.state = seed ^ 0x9E3779B97F4A7C15L;
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public int nextInt(int bound) {
        return (int) Math.floorMod(nextLong(), (long) bound);
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public boolean chance(double p) {
        return nextDouble() < p;
    }

    public <T> T pick(List<T> list) {
        return list.get(nextInt(list.size()));
    }
}
