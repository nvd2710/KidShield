package com.yousafdev.KidShield.Activities;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yousafdev.KidShield.R;
import com.yousafdev.KidShield.Utils.SessionManager;

public class ChildDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_dashboard);

        Button logoutButton = findViewById(R.id.button_child_logout);
        logoutButton.setOnClickListener(v -> confirmLogout());
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log out?")
                .setMessage("Logging out will stop protection on this device. Continue?")
                .setPositiveButton("Log Out", (dialog, which) -> SessionManager.logout(this, true))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
