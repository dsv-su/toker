package se.su.dsv.json;

public class Tuple<A, B> {
    private final A fst;
    private final B snd;

    public static <A, B> Tuple<A, B> kv(A fst, B snd) {
        return new Tuple<>(fst, snd);
    }

    private Tuple(final A fst, final B snd) {
        this.fst = fst;
        this.snd = snd;
    }

    public A getFst() {
        return fst;
    }

    public B getSnd() {
        return snd;
    }
}
