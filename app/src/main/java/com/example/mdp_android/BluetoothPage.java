package com.example.mdp_android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.mdp_android.MainActivity;
import com.example.mdp_android.R;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/**
 * BluetoothPage manages Bluetooth device scanning, pairing, and connection in a popup activity.
 * The class also listens for various Bluetooth-related broadcasts and handles reconnection attempts if a connection is lost.
 */
public class BluetoothPage extends AppCompatActivity {
    private static final String TAG = "BluetoothPage"; // Debugging tag for logging
    private String connectionStatus; // Stores the current connection status
    BluetoothAdapter bluetoothAdapter; // Bluetooth adapter for managing Bluetooth functionality
    public ArrayList<BluetoothDevice> newBluetoothDevices; // List of newly discovered Bluetooth devices
    public ArrayList<BluetoothDevice> pairedBluetoothDevices; // List of paired Bluetooth devices
    public BluetoothDeviceListAdapter newDeviceListAdapter; // Adapter for displaying new devices in a ListView
    public BluetoothDeviceListAdapter pairedDeviceListAdapter; // Adapter for displaying paired devices in a ListView
    TextView connectionStatusTextView; // TextView to display connection status
    ListView otherDevicesListView; // ListView for displaying new devices
    ListView pairedDevicesListView; // ListView for displaying paired devices
    Button connectButton; // Button to initiate connection to a selected device
    ProgressDialog progressDialog; // Dialog for showing progress during connection attempts

    SharedPreferences sharedPreferences; // SharedPreferences for persisting connection status
    SharedPreferences.Editor sharedPreferencesEditor; // Editor for modifying SharedPreferences

    BluetoothConnectionManager bluetoothConnectionManager; // Manages Bluetooth connection services
    private static final UUID appUUID = UUID.fromString("00001101-0000-1000-8000-00805F9C34FA"); // Unique UUID for the app's Bluetooth service
    public static BluetoothDevice selectedBluetoothDevice; // The selected Bluetooth device to connect to

    boolean retryConnection = false; // Flag to indicate if reconnection attempts are needed
    Handler reconnectionHandler = new Handler(); // Handler for scheduling reconnection attempts


    // Runnable that attempts to reconnect to the Bluetooth device after a connection is lost
    Runnable reconnectionRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (!BluetoothConnectionManager.BluetoothConnectionStatus) {
                    startBluetoothConnection(selectedBluetoothDevice, appUUID);
                    Toast.makeText(BluetoothPage.this, "Reconnection Success", Toast.LENGTH_SHORT).show();
                }
                reconnectionHandler.removeCallbacks(reconnectionRunnable);
                retryConnection = false;
            } catch (Exception e) {
                Toast.makeText(BluetoothPage.this, "Failed to reconnect, trying in 5 seconds", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_page);

        // Set the window dimensions
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Switch bluetoothSwitch = findViewById(R.id.bt_switch);

        // Set initial Bluetooth switch state
        if (bluetoothAdapter.isEnabled()) {
            bluetoothSwitch.setChecked(true);
            bluetoothSwitch.setText(R.string.bt_page_switch_on);
        }

        // Initialize UI elements and lists
        otherDevicesListView = findViewById(R.id.other_devices_list);
        pairedDevicesListView = findViewById(R.id.paired_devices_list);
        newBluetoothDevices = new ArrayList<>();
        pairedBluetoothDevices = new ArrayList<>();
        connectButton = findViewById(R.id.bt_connect_button);

        // Register broadcast receivers for Bluetooth events
        IntentFilter bondStateChangedFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(bondStateChangedReceiver, bondStateChangedFilter);

        IntentFilter connectionStatusFilter = new IntentFilter("ConnectionStatus");
        LocalBroadcastManager.getInstance(this).registerReceiver(connectionStatusReceiver, connectionStatusFilter);

        // Set click listeners for device selection
        otherDevicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            @SuppressLint("MissingPermission")
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                bluetoothAdapter.cancelDiscovery();
                pairedDevicesListView.setAdapter(pairedDeviceListAdapter);

                String deviceName = newBluetoothDevices.get(i).getName();
                String deviceAddress = newBluetoothDevices.get(i).getAddress();
                Log.d(TAG, "onItemClick: A device is selected.");
                Log.d(TAG, "onItemClick: DEVICE NAME: " + deviceName);
                Log.d(TAG, "onItemClick: DEVICE ADDRESS: " + deviceAddress);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    Log.d(TAG, "onItemClick: Initiating pairing with " + deviceName);
                    newBluetoothDevices.get(i).createBond();

                    bluetoothConnectionManager = new BluetoothConnectionManager(BluetoothPage.this);
                    selectedBluetoothDevice = newBluetoothDevices.get(i);
                }
            }
        });

        pairedDevicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            @SuppressLint("MissingPermission")
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                bluetoothAdapter.cancelDiscovery();
                otherDevicesListView.setAdapter(newDeviceListAdapter);

                String deviceName = pairedBluetoothDevices.get(i).getName();
                String deviceAddress = pairedBluetoothDevices.get(i).getAddress();
                Log.d(TAG, "onItemClick: A device is selected.");
                Log.d(TAG, "onItemClick: DEVICE NAME: " + deviceName);
                Log.d(TAG, "onItemClick: DEVICE ADDRESS: " + deviceAddress);

                bluetoothConnectionManager = new BluetoothConnectionManager(BluetoothPage.this);
                selectedBluetoothDevice = pairedBluetoothDevices.get(i);
            }
        });

        // Handle Bluetooth switch toggling
        bluetoothSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            @SuppressLint("MissingPermission")
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                Log.d(TAG, "onChecked: Switch button toggled. Enabling/Disabling Bluetooth");
                compoundButton.setText(isChecked ? "ON" : "OFF");

                if (bluetoothAdapter == null) {
                    Log.d(TAG, "enableDisableBT: Device does not support Bluetooth capabilities!");
                    Toast.makeText(BluetoothPage.this, "Device Does Not Support Bluetooth capabilities!", Toast.LENGTH_SHORT).show();
                    compoundButton.setChecked(false);
                } else {
                    if (!bluetoothAdapter.isEnabled()) {
                        Log.d(TAG, "enableDisableBT: enabling Bluetooth");
                        Log.d(TAG, "enableDisableBT: Making device discoverable for 600 seconds.");

                        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                        startActivity(discoverableIntent);

                        compoundButton.setChecked(true);

                        IntentFilter btStateChangedFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
                        registerReceiver(bluetoothStateChangedReceiver, btStateChangedFilter);

                        IntentFilter scanModeChangedFilter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
                        registerReceiver(scanModeChangedReceiver, scanModeChangedFilter);
                    }
                    if (bluetoothAdapter.isEnabled()) {
                        Log.d(TAG, "enableDisableBT: disabling Bluetooth");
                        bluetoothAdapter.disable();

                        IntentFilter btStateChangedFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
                        registerReceiver(bluetoothStateChangedReceiver, btStateChangedFilter);
                    }
                }
            }
        });

        // Set connect button click listener
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (selectedBluetoothDevice == null) {
                    Toast.makeText(BluetoothPage.this, "Please Select a Device before connecting.", Toast.LENGTH_SHORT).show();
                } else {
                    startConnection();
                }
            }
        });

// Handle back button click to save connection status
        LinearLayout backButtonArea = findViewById(R.id.left_side);  // This is now the entire clickable area
        connectionStatusTextView = findViewById(R.id.bt_page_connection_status);
        connectionStatus = "Disconnected";
        sharedPreferences = getApplicationContext().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);

        if (sharedPreferences.contains("connStatus"))
            connectionStatus = sharedPreferences.getString("connStatus", "");

        connectionStatusTextView.setText(connectionStatus);

        backButtonArea.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sharedPreferencesEditor = sharedPreferences.edit();
                sharedPreferencesEditor.putString("connStatus", connectionStatusTextView.getText().toString());
                sharedPreferencesEditor.commit();
//                String s = connectionStatusTextView.getText().toString();
                //status.setText(s);
                finish();
            }
        });

        // Set up the progress dialog for reconnection attempts
        progressDialog = new ProgressDialog(BluetoothPage.this);
        progressDialog.setMessage("Waiting for other device to reconnect...");
        progressDialog.setCancelable(false);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
    }

    /**
     * Initiates scanning for Bluetooth devices, discovering both paired and new devices.
     */
    @SuppressLint("MissingPermission")
    public void startScanning() {
        Log.d(TAG, "startScanning: Scanning for unpaired devices.");
        newBluetoothDevices.clear();
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled()) {
                Toast.makeText(BluetoothPage.this, "Please turn on Bluetooth first!", Toast.LENGTH_SHORT).show();
            }
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
                checkBluetoothPermissions();
                bluetoothAdapter.startDiscovery();
                IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(deviceDiscoveryReceiver, discoverDevicesIntent);
            } else if (!bluetoothAdapter.isDiscovering()) {
                checkBluetoothPermissions();
                bluetoothAdapter.startDiscovery();
                IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(deviceDiscoveryReceiver, discoverDevicesIntent);
            }
            pairedBluetoothDevices.clear();
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            Log.d(TAG, "startScanning: Number of paired devices found: " + pairedDevices.size());
            for (BluetoothDevice device : pairedDevices) {
                Log.d(TAG, "Paired Devices: " + device.getName() + " : " + device.getAddress());
                pairedBluetoothDevices.add(device);
                pairedDeviceListAdapter = new BluetoothDeviceListAdapter(this, R.layout.bluetooth_device_list_adapter, pairedBluetoothDevices);
                pairedDevicesListView.setAdapter(pairedDeviceListAdapter);
            }
        }
    }

    /**
     * Triggers the scanning process when the scan button is toggled.
     */
    public void toggleButtonScan(View view) {
        startScanning();
    }

    /**
     * Checks for necessary Bluetooth permissions.
     * If permissions are not granted, they will be requested.
     */
    private void checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            int permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
            if (permissionCheck != 0) {
                this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
            }
        } else {
            Log.d(TAG, "checkBluetoothPermissions: No need to check permissions. SDK version < LOLLIPOP.");
        }
    }

    // BroadcastReceiver for Bluetooth state changes
    private final BroadcastReceiver bluetoothStateChangedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "bluetoothStateChangedReceiver: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "bluetoothStateChangedReceiver: STATE TURNING OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "bluetoothStateChangedReceiver: STATE ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "bluetoothStateChangedReceiver: STATE TURNING ON");
                        break;
                }
            }
        }
    };

    // BroadcastReceiver for Bluetooth scan mode changes (e.g., discoverability)
    private final BroadcastReceiver scanModeChangedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
                final int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                switch (mode) {
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d(TAG, "scanModeChangedReceiver: Discoverability Enabled.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d(TAG, "scanModeChangedReceiver: Discoverability Disabled. Able to receive connections.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "scanModeChangedReceiver: Discoverability Disabled. Not able to receive connections.");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d(TAG, "scanModeChangedReceiver: Connecting...");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d(TAG, "scanModeChangedReceiver: Connected.");
                        break;
                }
            }
        }
    };

    // BroadcastReceiver for discovered devices during scanning
    private final BroadcastReceiver deviceDiscoveryReceiver = new BroadcastReceiver() {
        @Override
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive: ACTION FOUND.");

            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    newBluetoothDevices.add(device);
                    Log.d(TAG, "onReceive: " + device.getName() + " : " + device.getAddress());
                    newDeviceListAdapter = new BluetoothDeviceListAdapter(context, R.layout.bluetooth_device_list_adapter, newBluetoothDevices);
                    otherDevicesListView.setAdapter(newDeviceListAdapter);
                }
            }
        }
    };

    // BroadcastReceiver for Bluetooth bonding state changes (pairing)
    private final BroadcastReceiver bondStateChangedReceiver = new BroadcastReceiver() {
        @Override
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "BOND_BONDED.");
                    Toast.makeText(BluetoothPage.this, "Successfully paired with " + device.getName(), Toast.LENGTH_SHORT).show();
                    selectedBluetoothDevice = device;
                    startScanning();
                }
                if (device.getBondState() == BluetoothDevice.BOND_BONDING) {
                    Log.d(TAG, "BOND_BONDING.");
                }
                if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                    Log.d(TAG, "BOND_NONE.");
                }
            }
        }
    };

    // BroadcastReceiver for handling connection status updates
    private final BroadcastReceiver connectionStatusReceiver = new BroadcastReceiver() {
        @Override
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra("Device");
            String status = intent.getStringExtra("Status");
            sharedPreferences = getApplicationContext().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
            sharedPreferencesEditor = sharedPreferences.edit();

            if (status.equals("connected")) {
                try {
                    progressDialog.dismiss();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }

                Log.d(TAG, "connectionStatusReceiver: Device now connected to " + device.getName());
                Toast.makeText(BluetoothPage.this, "Device now connected to " + device.getName(), Toast.LENGTH_SHORT).show();
                sharedPreferencesEditor.putString("connStatus", "Connected to " + device.getName());
                connectionStatusTextView.setText("Connected to " + device.getName());
            } else if (status.equals("disconnected") && !retryConnection) {
                Log.d(TAG, "connectionStatusReceiver: Disconnected from " + device.getName());
                Toast.makeText(BluetoothPage.this, "Disconnected from " + device.getName(), Toast.LENGTH_SHORT).show();
                bluetoothConnectionManager = new BluetoothConnectionManager(BluetoothPage.this);

                sharedPreferencesEditor.putString("connStatus", "Disconnected");
                connectionStatusTextView.setText("Disconnected");
                sharedPreferencesEditor.commit();

                try {
                    progressDialog.show();
                } catch (Exception e) {
                    Log.d(TAG, "BluetoothPage: connectionStatusReceiver Dialog show failure");
                }
                retryConnection = true;
                reconnectionHandler.postDelayed(reconnectionRunnable, 5000);
            }
            sharedPreferencesEditor.commit();
        }
    };

    /**
     * Starts the Bluetooth connection process with the selected device.
     */
    public void startConnection() {
        startBluetoothConnection(selectedBluetoothDevice, appUUID);
    }

    /**
     * Initializes the Bluetooth connection using the provided device and UUID.
     *
     * @param device The Bluetooth device to connect to.
     * @param uuid   The UUID for the Bluetooth service.
     */
    public void startBluetoothConnection(BluetoothDevice device, UUID uuid) {
        Log.d(TAG, "startBluetoothConnection: Initializing RFCOM Bluetooth Connection");
        bluetoothConnectionManager.startOutgoingConnectionThread(device, uuid);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: called");
        super.onDestroy();
        try {
            unregisterReceiver(bluetoothStateChangedReceiver);
            unregisterReceiver(scanModeChangedReceiver);
            unregisterReceiver(deviceDiscoveryReceiver);
            unregisterReceiver(bondStateChangedReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionStatusReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: called");
        super.onPause();
        try {
            unregisterReceiver(bluetoothStateChangedReceiver);
            unregisterReceiver(scanModeChangedReceiver);
            unregisterReceiver(deviceDiscoveryReceiver);
            unregisterReceiver(bondStateChangedReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionStatusReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void finish() {
        Intent data = new Intent();
        data.putExtra("mBTDevice", selectedBluetoothDevice);
        data.putExtra("myUUID", appUUID);
        setResult(RESULT_OK, data);
        super.finish();
    }
}
