package com.phoenix.servicecall.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BootReceiver — triggered when device restarts.
 *
 * Phase 1: Receiver registered and skeleton in place.
 * Phase 4: Will re-schedule pending WorkManager reminder jobs
 *          by querying Firestore for all snoozed/pending tasks.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {

            Log.d(TAG, "Device booted — re-registering WorkManager jobs");

            // Phase 4: ReminderScheduler.rescheduleAll(context) will go here
            // It will query Firestore for all pending/snoozed tasks and
            // re-register a WorkManager job for each one.
        }
    }
}
