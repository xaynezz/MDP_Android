package com.example.mdp_android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.mdp_android.BluetoothConnectionManager;
import com.example.mdp_android.MainActivity;
import com.example.mdp_android.R;

import java.nio.charset.Charset;


/**
 * A fragment representing the Bluetooth communication UI, allowing users to send and receive messages.
 *
 * This fragment displays the chat interface for Bluetooth communication, handles sending messages,
 * and listens for incoming messages via a BroadcastReceiver.
 */
public class BluetoothComms extends Fragment {

    /**
     * Tag used for logging debug messages.
     */
    private static final String TAG = "BluetoothCommsFragment";

    /**
     * SharedPreferences used for storing messages.
     *
     * SharedPreferences allows storing and retrieving messages that are sent, so that the data persists even
     * after the app is closed or the device is restarted.
     */
    private SharedPreferences sharedPreferences;

    /**
     * TextView for displaying received messages.
     *
     * This TextView displays messages received via Bluetooth in the chat interface.
     */
    private TextView receivedMessagesTextView;

    /**
     * EditText for typing messages to send.
     */
    private EditText messageInputEditText;

    /**
     * The MainActivity that this fragment is attached to.
     */
    private final MainActivity mainActivity;

    /**
     * Constructs a new instance of the BluetoothComms fragment.
     *
     * @param main The MainActivity that this fragment is attached to.
     */
    public BluetoothComms(MainActivity main) {
        this.mainActivity = main;
    }

    /**
     * Called upon fragment creation.
     *
     * @param savedInstanceState The saved instance state.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register a local broadcast receiver to listen for incoming messages
        // This listens for intents with the action "incomingMessage" and updates the UI when messages are received.
        LocalBroadcastManager.getInstance(this.requireContext())
                .registerReceiver(this.incomingMessageReceiver, new IntentFilter("incomingMessage"));
    }

    /**
     * Called when the fragment's UI is being created.
     *
     * This method inflates the fragment's layout and initializes the UI elements, including setting up
     * listeners for the send button.
     *
     * @param inflater           The layout inflater.
     * @param container          The view group container.
     * @param savedInstanceState The saved instance state.
     * @return The root view for the fragment's UI.
     */
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        // Inflate the fragment's UI layout
        View root = inflater.inflate(R.layout.activity_bluetooth_comms, container, false);

        // Initialize UI elements
        ImageButton sendButton = root.findViewById(R.id.chat_send_button);
        this.receivedMessagesTextView = root.findViewById(R.id.chat_message_display);
        this.receivedMessagesTextView.setMovementMethod(new ScrollingMovementMethod());
        this.messageInputEditText = root.findViewById(R.id.chat_message_entry);

        // Initialize SharedPreferences for storing sent messages
        this.sharedPreferences = this.requireActivity()
                .getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);

        // Set a click listener on the send button to handle sending messages
        sendButton.setOnClickListener(view -> {
            String messageToSend = this.messageInputEditText.getText().toString();

            // Store the message in SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("message", sharedPreferences
                    .getString("message", "") + '\n' + messageToSend);
            editor.apply();

            // Display the sent message in the TextView
            this.receivedMessagesTextView.append(messageToSend + "\n");
            this.messageInputEditText.setText("");

            // Send the message via Bluetooth if connected if there is an active Bluetooth connection
            if (BluetoothConnectionManager.BluetoothConnectionStatus) {
                byte[] bytes = messageToSend.getBytes(Charset.defaultCharset());
                BluetoothConnectionManager.write(bytes);
            }
        });

        return root;
    }

    /**
     * Gets the TextView for displaying received messages.
     *
     * @return The TextView for displaying received messages.
     */
    public TextView getReceivedMessagesTextView() {
        return this.receivedMessagesTextView;
    }

    /**
     * BroadcastReceiver to handle incoming messages via Bluetooth.
     *
     * When a message is received via Bluetooth, it is broadcast within the app using an intent,
     * and this receiver updates the UI by appending the received message to the TextView.
     */
    private final BroadcastReceiver incomingMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get the incoming message and display it
            String receivedMessage = intent.getStringExtra("receivedMessage");
            receivedMessagesTextView.append(receivedMessage + "\n");
        }
    };

    /**
     * Log for debug messages.
     *
     * @param debugMessage The message to log.
     */
    private void logDebugMessage(String debugMessage) {
        Log.d(TAG, debugMessage);
    }
}
