package com.example.mdp_android;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class BluetoothPage extends AppCompatActivity {

    private static final String TAG = "Bluetooth Debug";

    private String connStatus;
    BluetoothAdapter mBluetoothAdapter;
    public ArrayList<BluetoothDevice> mNewBTDevices = new ArrayList<>();
    public ArrayList<BluetoothDevice> mPairedBTDevices = new ArrayList<>();
    public BluetoothDeviceListAdapter mNewDeviceListAdapter;
    public BluetoothDeviceListAdapter mPairedDeviceListAdapter;
    public static TextView connStatusTextView;
    ListView lvNewDevices;
    ListView lvPairedDevices;
    Button connectBtn;
    ImageButton btnSearch;
    ProgressDialog myDialog;

    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
    };
    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
    };

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    BluetoothConnectionManager mBluetoothConnection;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static BluetoothDevice mBTDevice;

    boolean retryConnection = false;
    Handler reconnectionHandler = new Handler();

    Runnable reconnectionRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (!BluetoothConnectionManager.BluetoothConnectionStatus) {
                    showLog("Reconnecting...");
                    startBTConnection(mBTDevice, MY_UUID);
                    updateStatus("Reconnection Success");
                }
                reconnectionHandler.removeCallbacks(reconnectionRunnable);
                retryConnection = false;
            } catch (Exception e) {
                showLog("Reconnection failed");
                e.printStackTrace();
                updateStatus("Failed to reconnect, trying in 5 seconds");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_page);  // Adjust layout as necessary

        sharedPreferences = getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        lvNewDevices = findViewById(R.id.other_devices_list);
        lvPairedDevices = findViewById(R.id.paired_devices_list);
        mNewBTDevices = new ArrayList<>();
        mPairedBTDevices = new ArrayList<>();

        connectBtn = findViewById(R.id.bt_connect_button);
        btnSearch = findViewById(R.id.bt_search_button);

        // Get bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        Switch bluetoothSwitch = findViewById(R.id.bt_switch);
        if (mBluetoothAdapter.isEnabled()) {
            bluetoothSwitch.setChecked(true);
            bluetoothSwitch.setText("ON");
        }

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBroadcastReceiver4, filter);

        IntentFilter filter2 = new IntentFilter("ConnectionStatus");
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver5, filter2);

        lvNewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mBluetoothAdapter.cancelDiscovery();
                lvPairedDevices.setAdapter(mPairedDeviceListAdapter);

                String deviceName = mNewBTDevices.get(i).getName();
                String deviceAddress = mNewBTDevices.get(i).getAddress();
                Log.d(TAG, "onItemClick: A device is selected.");
                Log.d(TAG, "onItemClick: DEVICE NAME: " + deviceName);
                Log.d(TAG, "onItemClick: DEVICE ADDRESS: " + deviceAddress);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    Log.d(TAG, "onItemClick: Initiating pairing with " + deviceName);
                    mNewBTDevices.get(i).createBond();

                    mBluetoothConnection = new BluetoothConnectionManager(BluetoothPage.this);
                    mBTDevice = mNewBTDevices.get(i);
                }
            }
        });

        lvPairedDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mBluetoothAdapter.cancelDiscovery();
                lvNewDevices.setAdapter(mNewDeviceListAdapter);

                String deviceName = mPairedBTDevices.get(i).getName();
                String deviceAddress = mPairedBTDevices.get(i).getAddress();
                Log.d(TAG, "onItemClick: A device is selected.");
                Log.d(TAG, "onItemClick: DEVICE NAME: " + deviceName);
                Log.d(TAG, "onItemClick: DEVICE ADDRESS: " + deviceAddress);

                mBluetoothConnection = new BluetoothConnectionManager(BluetoothPage.this);
                mBTDevice = mPairedBTDevices.get(i);
            }
        });

        bluetoothSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                Log.d(TAG, "onChecked: Enabling/Disabling Bluetooth");
                if (isChecked) {
                    compoundButton.setText("ON");
                } else {
                    compoundButton.setText("OFF");
                }

                if (mBluetoothAdapter == null) {
                    Log.d(TAG, "enableDisableBT: Does not have Bluetooth capabilities");
                    updateStatus("Error: Bluetooth is not supported on this device");
                    compoundButton.setChecked(false);
                } else {
                    if (!mBluetoothAdapter.isEnabled()) {
                        Log.d(TAG, "enableDisableBT: Enabling Bluetooth");

                        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                        startActivity(discoverableIntent);

                        compoundButton.setChecked(true);

                        IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
                        registerReceiver(mBroadcastReceiver1, BTIntent);
                    }
                    if (mBluetoothAdapter.isEnabled()) {
                        Log.d(TAG, "enableDisableBT: Disabling Bluetooth");
                        mBluetoothAdapter.disable();

                        IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
                        registerReceiver(mBroadcastReceiver1, BTIntent);
                    }
                }
            }
        });

        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mBTDevice == null) {
                    updateStatus("Please Select a Device before connecting.");
                } else {
                    startConnection();
                }
            }
        });

        btnSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Log.d(TAG, "onClick: search button");
                toggleButtonScan(view);
            }
        });

        LinearLayout backBtn = findViewById(R.id.left_side);

        connStatusTextView = findViewById(R.id.bt_page_connection_status);
        connStatus = "Disconnected";
        sharedPreferences = getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
        if (sharedPreferences.contains("connStatus"))
            connStatus = sharedPreferences.getString("connStatus", "");

//        connStatusTextView.setText(connStatus);
        runOnUiThread(() -> connStatusTextView.setText(connStatus));

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor = sharedPreferences.edit();
                editor.putString("connStatus", connStatusTextView.getText().toString());
                editor.apply();
                finish();
            }
        });

        myDialog = new ProgressDialog(this);
        myDialog.setMessage("Waiting for other device to reconnect...");
        myDialog.setCancelable(false);
        myDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
//                dialog.dismiss();
                runOnUiThread(() -> {
                    if (myDialog.isShowing()) {
                        myDialog.dismiss();
                    }
                });

            }
        });
    }

    private void checkBTPermissions() {
        int permission1 = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permission2 = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN);
        if (permission1 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, 1);
        } else if (permission2 != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_LOCATION, 1);
        }
    }

    public void Scanning() {
        Log.d(TAG, "toggleButton: Scanning for unpaired devices.");
        checkBTPermissions();
        mNewBTDevices.clear();
        if (mBluetoothAdapter != null) {
            if (!mBluetoothAdapter.isEnabled()) {
                updateStatus("Please turn on Bluetooth first!");
            }
            if (mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
                mBluetoothAdapter.startDiscovery();
                IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
            } else if (!mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.startDiscovery();
                IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
            }
            mPairedBTDevices.clear();
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            Log.d(TAG, "toggleButton: Number of paired devices found: " + pairedDevices.size());
            for (BluetoothDevice d : pairedDevices) {
                Log.d(TAG, "Paired Devices: " + d.getName() + " : " + d.getAddress());
                mPairedBTDevices.add(d);
                mPairedDeviceListAdapter = new BluetoothDeviceListAdapter(this, R.layout.bluetooth_device_list_adapter, mPairedBTDevices);
                lvPairedDevices.setAdapter(mPairedDeviceListAdapter);
            }
        }
    }

    public void toggleButtonScan(View view) {
        Scanning();
    }

    private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(mBluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, mBluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "onReceive: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "mBroadcastReceiver1: onReceive: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "onReceive: STATE ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "mBroadcastReceiver1: onReceive: STATE ON");
                        break;
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver2 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
                final int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
                switch (mode) {
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Enabled.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Able to receive connections.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Not able to receive connections.");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d(TAG, "mBroadcastReceiver2: Connecting...");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d(TAG, "mBroadcastReceiver2: Connected.");
                        break;
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver3 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive: ACTION FOUND.");

            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    mNewBTDevices.add(device);
                    Log.d(TAG, "onReceive: " + device.getName() + " : " + device.getAddress());
                    mNewDeviceListAdapter = new BluetoothDeviceListAdapter(context, R.layout.bluetooth_device_list_adapter, mNewBTDevices);
                    lvNewDevices.setAdapter(mNewDeviceListAdapter);
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver4 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (mDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "BOND_BONDED.");
                    updateStatus("Successfully paired with " + mDevice.getName());
                    Scanning();
                }
                if (mDevice.getBondState() == BluetoothDevice.BOND_BONDING) {
                    Log.d(TAG, "BOND_BONDING.");
                }
                if (mDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                    Log.d(TAG, "BOND_NONE.");
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver5 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            BluetoothDevice mDevice = intent.getParcelableExtra("Device");
            String status = intent.getStringExtra("Status");

            if (status.equals("connected")) {
                try {
//                    myDialog.dismiss();
                    runOnUiThread(() -> {
                        if (myDialog.isShowing()) {
                            myDialog.dismiss();
                        }
                    });
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }

                Log.d(TAG, "mBroadcastReceiver5: Device now connected to " + mDevice.getName());
                runOnUiThread(() -> connStatusTextView.setText("Connected to " + mDevice.getName()));
            } else if (status.equals("disconnected") && !retryConnection) {
                Log.d(TAG, "mBroadcastReceiver5: Disconnected from " + mDevice.getName());
                runOnUiThread(() -> connStatusTextView.setText("Disconnected"));

                retryConnection = true;
                reconnectionHandler.postDelayed(reconnectionRunnable, 5000);
            }
        }
    };

    public void startConnection() {
        startBTConnection(mBTDevice, MY_UUID);
    }

    public void startBTConnection(BluetoothDevice device, UUID uuid) {
        Log.d(TAG, "startBTConnection: Initializing RFCOM Bluetooth Connection");
        mBluetoothConnection.startClientThread(device, uuid);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: called");
        super.onDestroy();
        try {
            unregisterReceiver(mBroadcastReceiver1);
            unregisterReceiver(mBroadcastReceiver2);
            unregisterReceiver(mBroadcastReceiver3);
            unregisterReceiver(mBroadcastReceiver4);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver5);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: called");
        super.onPause();
        try {
            unregisterReceiver(mBroadcastReceiver1);
            unregisterReceiver(mBroadcastReceiver2);
            unregisterReceiver(mBroadcastReceiver3);
            unregisterReceiver(mBroadcastReceiver4);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver5);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private void showLog(String message) {
        Log.d(TAG, message);
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> {
            Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();
        });
    }
}
