package com.subhub.app.penance;

import android.content.Context;

/** Records one rate-limited Hardcore tamper event while protection is actually armed. */
public final class TamperTributeReporter {
    private TamperTributeReporter() {}

    public static int record(Context context) {
        if (context == null) return 0;
        long now = System.currentTimeMillis();
        PenanceManager manager = new PenanceManager(context);
        int charged = manager.recordInfraction(PenanceInfraction.TAMPER_ATTEMPT, 1, now);
        PenanceChargeNotifier.show(context, manager,
                PenanceInfraction.TAMPER_ATTEMPT, charged, now);
        return charged;
    }
}
