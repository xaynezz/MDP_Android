package com.example.mdp_android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

import com.example.mdp_android.MainActivity;
import com.example.mdp_android.R;




/**
 * This class manages Bluetooth connections, handling both server-side and client-side roles.
 * It creates separate threads that run in the background to manage Bluetooth connections,
 * ensuring that the UI remains responsive even during blocking operations.
 *
 * - Server-side (`AcceptConnectionThread`): Listens for incoming Bluetooth connection requests from other devices.
 *   It waits passively for a connection using a blocking `accept()` call, which pauses the thread until a connection is made.
 *   Once a connection is accepted, the `establishCommunication()` method is called to start the `BluetoothCommunicationThread`,
 *   which manages communication.
 *   e.g., RPI (client) connects to Android (server)
 *   Flow: Constructor -> `startAcceptConnectionThread()` -> `AcceptConnectionThread.run()` -> `establishCommunication()` -> `BluetoothCommunicationThread` -> Communication starts
 *
 * - Client-side (`OutgoingConnectionThread`): Initiates a connection to another Bluetooth device.
 *   It attempts to connect using a `BluetoothSocket`. Unlike `AcceptConnectionThread`, `OutgoingConnectionThread` does not block after `connect()` is called.
 *   The connection is established asynchronously. Once connected, the `establishCommunication()` method is called to start the `BluetoothCommunicationThread`,
 *   which manages communication.
 *   e.g., Android (client) connects to another Bluetooth device (server)
 *   Flow: User selects device -> `startOutgoingConnectionThread()` -> `OutgoingConnectionThread.run()` -> `establishCommunication()` -> `BluetoothCommunicationThread` -> Communication starts
 *   The `startOutgoingConnectionThread()` method is called from the BluetoothPage activity when the user selects a device to connect to.
 *
 * The `establishCommunication()` method is crucial in both server and client scenarios, as it starts the `BluetoothCommunicationThread`,
 * which handles the Bluetooth communication after the connection is established.
 */
public class BluetoothConnectionManager {

    // Debugging tag for logging
    private static final String TAG = "BluetoothConnectionManager";

    // Name of the application for identifying the Bluetooth service
    private static final String appName = "MDP_Group_21";

    // Unique UUID for this application (used for establishing Bluetooth connections)
    public static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9C34FA");

    // Bluetooth adapter used for accessing Bluetooth functionality
    private final BluetoothAdapter bluetoothAdapter;

    // Application context, used for accessing resources and services
    Context context;

    // Thread for listening for incoming connections (server-side)
    private AcceptConnectionThread acceptConnectionThread;

    // Thread for initiating outgoing connections (client-side)
    private OutgoingConnectionThread outgoingConnectionThread;

    // Thread for managing the connected Bluetooth socket
    private static BluetoothCommunicationThread bluetoothCommunicationThread;

    // Bluetooth device to connect to
    private BluetoothDevice connectedDevice;

    // UUID of the device to connect to
    private UUID deviceUUID;

    // Progress dialog shown during connection attempts
    ProgressDialog processDialog;

    // Intent used for broadcasting connection status updates
    Intent connectionStatus;

    // Flag indicating the current Bluetooth connection status
    public static boolean BluetoothConnectionStatus = false;



    /**
     * Constructor for the BluetoothConnectionManager class.
     * Initializes the Bluetooth adapter and starts the AcceptConnectionThread to listen for incoming connections.
     *
     * @param context The current state of the application (used to access resources and services)
     */
    public BluetoothConnectionManager(Context context) {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.context = context;
        this.startAcceptConnectionThread(); // Start listening for incoming connections
    }

    /**
     * AcceptConnectionThread (server-side) is responsible for listening for incoming Bluetooth connection requests.
     * When a connection is accepted, it creates a Bluetooth socket and passes it to the BluetoothCommunicationThread for communication.
     */
    private class AcceptConnectionThread extends Thread {
        // Server socket used to listen for incoming connections
        private final BluetoothServerSocket ServerSocket;

        public AcceptConnectionThread() {
            BluetoothServerSocket temp = null;

            // Check permissions before attempting to create a server socket
            checkPermissions();

            try {
                // Create a new server socket to listen for incoming connections
                temp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, myUUID);
            } catch (SecurityException se) {
                // Handle the case where permission is not granted
                Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT");
                se.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.ServerSocket = temp;
        }

        /**
         * The main method of the AcceptConnectionThread. Continuously listens for incoming connection requests.
         * Once a connection is accepted, it hands off the Bluetooth socket to the BluetoothCommunicationThread.
         */
        public void run() {
            BluetoothSocket bluetoothSocket = null;
            try {
                // Accept an incoming connection (blocking call)
                bluetoothSocket = ServerSocket.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // If a connection was accepted, pass the socket to the BluetoothCommunicationThread
            if (bluetoothSocket != null) {
                establishCommunication(bluetoothSocket, bluetoothSocket.getRemoteDevice());
            }
        }

        /**
         * Cancels the AcceptConnectionThread by closing the server socket.
         * This stops the thread from listening for further incoming connections.
         */
        public void cancel() {
            try {
                ServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * OutgoingConnectionThread (client-side) is responsible for initiating an outgoing Bluetooth connection to another device.
     * Once the connection is established, it creates a Bluetooth socket and passes it to the BluetoothCommunicationThread for communication.
     */
    private class OutgoingConnectionThread extends Thread {
        // Bluetooth socket representing the outgoing connection
        private BluetoothSocket bluetoothSocket;

        /**
         * Constructor for the OutgoingConnectionThread class.
         * Creates a new BluetoothSocket and attempts to connect to the remote device.
         *
         * @param device The remote Bluetooth device to connect to
         * @param u      The UUID of the remote device
         */
        public OutgoingConnectionThread(BluetoothDevice device, UUID u) {
            connectedDevice = device;
            deviceUUID = u;
        }

        /**
         * The main method of the OutgoingConnectionThread. Attempts to connect to the remote device.
         * If the connection is successful, it hands off the Bluetooth socket to the BluetoothCommunicationThread.
         */
        public void run() {
            BluetoothSocket temp = null;

            // Check permissions before attempting to create a server socket
            checkPermissions();

            try {
                // Create a Bluetooth socket to connect to the remote device
                temp = connectedDevice.createRfcommSocketToServiceRecord(deviceUUID);
            } catch(SecurityException se){
                // Handle the case where permission is not granted
                Log.e(TAG, "Permission denied: BLUETOOTH_CONNECT");
                se.printStackTrace();
                return; // Exit the method to avoid further processing without permission
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothSocket = temp;
            bluetoothAdapter.cancelDiscovery(); // Cancel Bluetooth discovery to speed up the connection process

            try {
                // Attempt to connect to the remote device (blocking call)
                bluetoothSocket.connect();
                // If connection is successful, pass the socket to the BluetoothCommunicationThread
                establishCommunication(bluetoothSocket, connectedDevice);
            } catch (IOException e) {
                try {
                    // If the connection fails, close the socket
                    bluetoothSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                // Show a toast message on the UI thread indicating that the connection failed
                try {
                    BluetoothPage mBluetoothPageActivity = (BluetoothPage) context;
                    mBluetoothPageActivity.runOnUiThread(() -> Toast.makeText(context,
                            "Failed to connect to device!", Toast.LENGTH_SHORT).show());
                } catch (Exception z) {
                    z.printStackTrace();
                }
            }

            // Dismiss the progress dialog if it was shown during the connection attempt
            try {
                processDialog.dismiss();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        /**
         * Cancels the OutgoingConnectionThread by closing the Bluetooth socket.
         * This stops the thread from attempting to connect to the remote device.
         */
        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * BluetoothCommunicationThread is responsible for managing the Bluetooth connection once it is established.
     * It handles reading from and writing to the connected Bluetooth socket.
     */
    private class BluetoothCommunicationThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inStream;  // Input stream for receiving data
        private final OutputStream outStream;  // Output stream for sending data
        private boolean stopThread = false;  // Flag to stop the thread

        /**
         * Constructor for the BluetoothCommunicationThread class.
         * Initializes the input and output streams of the Bluetooth socket for data transfer.
         *
         * @param socket The Bluetooth socket representing the connected device
         */
        public BluetoothCommunicationThread(BluetoothSocket socket) {
            Log.d(TAG, "BluetoothCommunicationThread: Starting.");

            // Broadcast the connection status as "connected"
            connectionStatus = new Intent("ConnectionStatus");
            connectionStatus.putExtra("Status", "connected");
            connectionStatus.putExtra("Device", connectedDevice);
            LocalBroadcastManager.getInstance(context).sendBroadcast(connectionStatus);

            // Update the Bluetooth connection status flag
            BluetoothConnectionStatus = true;

            // Update the UI on main page to reflect the connected status
            TextView status = MainActivity.getBluetoothStatus();
            status.setText(R.string.bt_connected);
            status.setTextColor(Color.GREEN);

            TextView device = MainActivity.getConnectedDevice();
            device.setText(connectedDevice.getName());

            this.bluetoothSocket = socket;

            // Initialize input and output streams for communication
            InputStream tempIn = null;
            OutputStream tempOut = null;
            try {
                tempIn = bluetoothSocket.getInputStream();
                tempOut = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            inStream = tempIn;
            outStream = tempOut;
        }

        /**
         * The main method of the BluetoothCommunicationThread. Continuously reads data from the input stream of the Bluetooth socket.
         * If a complete message is received, it broadcasts the message using an Intent.
         */
        public void run() {
            byte[] buffer = new byte[1024];  // Buffer for reading data from the input stream
            int bytes;  // Number of bytes read from the input stream
            StringBuilder messageBuffer = new StringBuilder();  // Buffer to accumulate incoming messages

            while (true) {
                try {
                    // Read data from the input stream
                    bytes = inStream.read(buffer);
                    String incomingMessage = new String(buffer, 0, bytes);
                    Log.d(TAG, "InputStream: " + incomingMessage);

                    messageBuffer.append(incomingMessage);  // Accumulate the incoming message

                    // Check if the message contains a delimiter (e.g., a newline character)
                    int delimiterIndex = messageBuffer.indexOf("\n");
                    if (delimiterIndex != -1) {
                        // Split the buffer contents into individual messages
                        // "incomingMessage" is the action of the broadcast
                        // "receivedMessage" is the key for the actual message data
                        String[] messages = messageBuffer.toString().split("\n");
                        for (String message : messages) {
                            // Broadcast each incoming message using an Intent
                            Intent incomingMessageIntent = new Intent("incomingMessage");
                            incomingMessageIntent.putExtra("receivedMessage", message);
                            LocalBroadcastManager.getInstance(context).sendBroadcast(incomingMessageIntent);
                        }

                        // Reset the message buffer after processing the messages
                        messageBuffer = new StringBuilder();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading input stream. " + e.getMessage());

                    // Broadcast the connection status as "disconnected" if an error occurs
                    connectionStatus = new Intent("ConnectionStatus");
                    connectionStatus.putExtra("Status", "disconnected");
                    TextView status = MainActivity.getBluetoothStatus();
                    status.setText(R.string.bt_disconnected);
                    status.setTextColor(Color.RED);
                    connectionStatus.putExtra("Device", connectedDevice);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(connectionStatus);

                    // Update the Bluetooth connection status flag
                    BluetoothConnectionStatus = false;

                    break;  // Exit the loop if an error occurs
                }
            }
        }

        /**
         * Writes data to the output stream of the Bluetooth socket.
         *
         * @param bytes The data to be sent
         */
        public void write(byte[] bytes) {
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "write: Writing to output stream: " + text);
            try {
                outStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Error writing to output stream. " + e.getMessage());
            }
        }

        /**
         * Cancels the BluetoothCommunicationThread by closing the Bluetooth socket.
         * This stops the thread and terminates the connection.
         */
        public void cancel() {
            Log.d(TAG, "cancel: Closing Client Socket");
            try {
                this.stopThread = true;
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel: Failed to close BluetoothCommunicationThread mSocket " + e.getMessage());
            }
        }
    }

    /**
     * Starts the AcceptConnectionThread to listen for incoming Bluetooth connection requests.
     * If a OutgoingConnectionThread is already running, it is stopped to avoid conflicts.
     */
    public synchronized void startAcceptConnectionThread() {
        // Cancel any ongoing outgoing connection attempts
        if (this.outgoingConnectionThread != null) {
            this.outgoingConnectionThread.cancel();
            this.outgoingConnectionThread = null;
        }

        // Start a new AcceptConnectionThread if one isn't already running
        if (this.acceptConnectionThread == null) {
            this.acceptConnectionThread = new AcceptConnectionThread();
            this.acceptConnectionThread.start();
        }
    }

    /**
     * Starts the OutgoingConnectionThread to initiate an outgoing Bluetooth connection to a remote device.
     * Displays a progress dialog while the connection attempt is in progress.
     *
     * @param device The remote Bluetooth device to connect to
     * @param uuid   The UUID of the remote device
     */
    public void startOutgoingConnectionThread(BluetoothDevice device, UUID uuid) {
        try {
            // Show a progress dialog during the connection attempt
            this.processDialog = ProgressDialog.show(this.context, "Attempting to connect", "Please wait...", true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Start the OutgoingConnectionThread to initiate the connection
        this.outgoingConnectionThread = new OutgoingConnectionThread(device, uuid);
        this.outgoingConnectionThread.start();
    }

    /**
     * Initializes and starts the BluetoothCommunicationThread to manage communication over the established Bluetooth connection.
     * Stops the AcceptConnectionThread since the connection has been successfully established.
     *
     * @param bluetoothSocket The Bluetooth socket representing the connected device
     * @param device  The connected Bluetooth device
     */
    private void establishCommunication(BluetoothSocket bluetoothSocket, BluetoothDevice device) {
        connectedDevice = device;

        // Stop the AcceptConnectionThread since a connection has been established
        if (acceptConnectionThread != null) {
            acceptConnectionThread.cancel();
            acceptConnectionThread = null;
        }

        // Start the BluetoothCommunicationThread to manage the connection
        bluetoothCommunicationThread = new BluetoothCommunicationThread(bluetoothSocket);
        bluetoothCommunicationThread.start();
    }

    /**
     * Sends data to the connected Bluetooth device by writing it to the BluetoothCommunicationThread's output stream.
     *
     * @param out The data to be sent
     */
    public static void write(byte[] out) {
        Log.d(TAG, "write: Write is called.");
        bluetoothCommunicationThread.write(out);
    }


    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                // Request permissions
                ((Activity) context).requestPermissions(new String[]{
                                android.Manifest.permission.BLUETOOTH_SCAN,
                                android.Manifest.permission.BLUETOOTH_CONNECT,
                                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION},
                        1001); // Request code can be any number
            }
        }
    }

}
