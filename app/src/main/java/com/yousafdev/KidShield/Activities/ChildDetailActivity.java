package com.yousafdev.KidShield.Activities;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.yousafdev.KidShield.Adapters.CallLogAdapter;
import com.yousafdev.KidShield.Adapters.SmsLogAdapter;
import com.yousafdev.KidShield.Models.CallLogEntry;
import com.yousafdev.KidShield.Models.SmsLogEntry;
import com.yousafdev.KidShield.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChildDetailActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String ALERTS_CHANNEL_ID = "KidShieldAlertsChannel";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 101;

    private MapView mapView;
    private GoogleMap googleMap;
    private String childUid;

    private RecyclerView callLogRecyclerView, smsLogRecyclerView;
    private CallLogAdapter callLogAdapter;
    private SmsLogAdapter smsLogAdapter;
    private List<CallLogEntry> callLogList;
    private List<SmsLogEntry> smsLogList;
    private ProgressBar progressBar;

    private DatabaseReference childDataRef;
    private DatabaseReference locationRef, callLogsRef, smsLogsRef;
    private ValueEventListener locationListener, callLogsListener, smsLogsListener;
    private Query blockedEventsQuery;
    private ChildEventListener blockedEventsListener;

    private int alertNotificationId = 2000;
    private long screenOpenedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_detail);

        screenOpenedAt = System.currentTimeMillis();

        childUid = getIntent().getStringExtra("CHILD_UID");
        String childEmail = getIntent().getStringExtra("CHILD_EMAIL");

        TextView title = findViewById(R.id.textView_child_detail_title);
        title.setText(childEmail);
        progressBar = findViewById(R.id.progressBar_details);

        // MapView Setup
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // App Blocker Button Setup
        Button manageAppsButton = findViewById(R.id.button_manage_apps);
        manageAppsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppBlockerActivity.class);
            intent.putExtra("CHILD_UID", childUid);
            startActivity(intent);
        });

        // Remote Lock Button Setup
        Button lockButton = findViewById(R.id.button_lock_device);
        lockButton.setOnClickListener(v -> sendLockCommand());

        createAlertsChannel();
        requestNotificationPermissionIfNeeded();

        // RecyclerViews Setup
        setupRecyclerViews();

        if (childUid != null) {
            listenForDataChanges();
        } else {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Child ID not found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupRecyclerViews() {
        callLogRecyclerView = findViewById(R.id.recyclerView_call_logs);
        callLogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        callLogList = new ArrayList<>();
        callLogAdapter = new CallLogAdapter(callLogList);
        callLogRecyclerView.setAdapter(callLogAdapter);

        smsLogRecyclerView = findViewById(R.id.recyclerView_sms_logs);
        smsLogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        smsLogList = new ArrayList<>();
        smsLogAdapter = new SmsLogAdapter(smsLogList);
        smsLogRecyclerView.setAdapter(smsLogAdapter);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
    }

    private void sendLockCommand() {
        if (childUid == null) {
            return;
        }
        FirebaseDatabase.getInstance().getReference("users").child(childUid)
                .child("commands").child("lock").setValue(ServerValue.TIMESTAMP)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Lock command sent to device.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to send lock command: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void listenForDataChanges() {
        childDataRef = FirebaseDatabase.getInstance().getReference("users").child(childUid).child("data");

        locationRef = childDataRef.child("location");
        locationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && googleMap != null) {
                    Double lat = snapshot.child("latitude").getValue(Double.class);
                    Double lon = snapshot.child("longitude").getValue(Double.class);
                    if (lat != null && lon != null) {
                        LatLng childLocation = new LatLng(lat, lon);
                        googleMap.clear();
                        googleMap.addMarker(new MarkerOptions().position(childLocation).title("Last known location"));
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(childLocation, 15f));
                    }
                }
                progressBar.setVisibility(View.GONE);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ChildDetailActivity.this, "Failed to load location: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        locationRef.addValueEventListener(locationListener);

        callLogsRef = childDataRef.child("call_logs");
        callLogsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                callLogList.clear();
                for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                    CallLogEntry entry = logSnapshot.getValue(CallLogEntry.class);
                    if (entry != null) {
                        callLogList.add(entry);
                    }
                }
                Collections.reverse(callLogList);
                callLogAdapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        callLogsRef.addValueEventListener(callLogsListener);

        smsLogsRef = childDataRef.child("sms_logs");
        smsLogsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                smsLogList.clear();
                for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                    SmsLogEntry entry = logSnapshot.getValue(SmsLogEntry.class);
                    if (entry != null) {
                        smsLogList.add(entry);
                    }
                }
                Collections.reverse(smsLogList);
                smsLogAdapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        smsLogsRef.addValueEventListener(smsLogsListener);

        listenForBlockedAttempts();
    }

    /** Notifies the parent when the child opens a blocked app while this screen is open. */
    private void listenForBlockedAttempts() {
        blockedEventsQuery = childDataRef.child("blocked_events").limitToLast(20);
        blockedEventsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                String packageName = snapshot.child("packageName").getValue(String.class);
                // Only notify for attempts that happened after this screen was opened, so existing
                // history does not spam notifications on load.
                if (timestamp != null && timestamp > screenOpenedAt && packageName != null) {
                    showBlockedAttemptNotification(packageName);
                }
            }
            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        blockedEventsQuery.addChildEventListener(blockedEventsListener);
    }

    private void showBlockedAttemptNotification(String packageName) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pending)
                .setContentTitle("Blocked app opened")
                .setContentText("Your child tried to open a blocked app: " + packageName)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        manager.notify(alertNotificationId++, builder.build());
    }

    private void createAlertsChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ALERTS_CHANNEL_ID, "KidShield Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts about the child's activity, such as blocked-app attempts.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onStop() { super.onStop(); mapView.onStop(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationRef != null && locationListener != null) locationRef.removeEventListener(locationListener);
        if (callLogsRef != null && callLogsListener != null) callLogsRef.removeEventListener(callLogsListener);
        if (smsLogsRef != null && smsLogsListener != null) smsLogsRef.removeEventListener(smsLogsListener);
        if (blockedEventsQuery != null && blockedEventsListener != null) blockedEventsQuery.removeEventListener(blockedEventsListener);
        mapView.onDestroy();
    }

    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}
