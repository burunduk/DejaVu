package org.fitchfamily.android.dejavu;

/**
 * Created by tfitch on 10/5/17.
 */

/**
 * A single observation made of a RF emitter.
 *
 * Used to convey all the information we have collected in the foreground about
 * a RF emitter we have seen to the background thread that actually does the
 * heavy lifting.
 *
 * It contains an identifier for the RF emitter (type and id), the received signal
 * level and optionally a note about about the emitter.
 */

public class Observation implements Comparable<Observation> {
    private RfIdentification ident;
    private int asu;
    private String note;

    Observation(String id, RfEmitter.EmitterType t) {
        ident = new RfIdentification(id, t);
        note = "";
        asu = BackendService.MINIMUM_ASU;
    }

    public int compareTo(Observation o) {
        int rslt = o.asu - asu;
        if (rslt == 0)
            rslt = ident.compareTo(o.ident);
        return rslt;
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        return (toString().compareTo(o.toString()) == 0);
    }

    @Override
    public int hashCode() {
        int result = 1;

        if (ident != null)
            result = ident.hashCode();
        result = (result << 31) + asu;
        return result;
    }

    public RfIdentification getIdent() {
        return ident;
    }

    public void setAsu(int signal) {
        if (signal > BackendService.MAXIMUM_ASU)
            asu = BackendService.MAXIMUM_ASU;
        else if (signal < BackendService.MINIMUM_ASU)
            asu = BackendService.MINIMUM_ASU;
        else
            asu = signal;
    }

    public int getAsu() {
        return asu;
    }

    public void setNote(String n) {
        note = n;
    }

    public String getNote() {
        return note;
    }

    public String toString() {
        return ident.toString() + ", asu=" + asu + ", note='" + note + "'";
    }
}