package cl.fernando.nubedj;

public final class CrossfaderMath {
    private CrossfaderMath() {}

    public static float[] equalPower(float position01) {
        float x = Math.max(0f, Math.min(1f, position01));
        double theta = x * Math.PI / 2.0;
        return new float[] {(float) Math.cos(theta), (float) Math.sin(theta)};
    }
}
