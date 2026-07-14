package com.yousafdev.KidShield.Utils;

import android.app.Activity;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.yousafdev.KidShield.Activities.LoginActivity;
import com.yousafdev.KidShield.Services.MonitoringService;

/** Central place for ending a session so parent and child logout behave consistently. */
public final class SessionManager {

    private SessionManager() {
    }

    /**
     * Signs the current user out and returns to the login screen with a cleared task stack.
     *
     * @param stopMonitoring on a child device, also stops {@link MonitoringService} and cancels
     *                       the periodic sync alarm so monitoring does not continue after logout.
     */
    public static void logout(Activity activity, boolean stopMonitoring) {
        if (stopMonitoring) {
            activity.stopService(new Intent(activity, MonitoringService.class));
            BootReceiver.cancelDataSync(activity);
        }
        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
