package com.example.mdp_android;

import static com.example.mdp_android.GridMap.IMAGE_BEARING;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private Context context;
    private GridMap gridMap;
    private TextView robotXCoordText, robotYCoordText, robotDirectionText;
    private TextView robotStatusText;
    private static TextView bluetoothStatus, bluetoothDevice;
    private ImageButton upBtn, downBtn, leftBtn, rightBtn;
    private ProgressDialog btDisconnectDialog;
    Map<String, Integer> directionMap = new HashMap<>();
    public boolean imgRecTimerFlag = false;
    public boolean fastestCarTimerFlag = false;
    private BluetoothComms btFragment;
    private MapConfigFragment mapTabFragment;
    private ChallengeFragment challengeFragment;

    /**
     * onCreate is called when the app runs
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down then this Bundle contains the data it most
     *                           recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // remember to always call the super method
        super.onCreate(savedInstanceState);

        // choose which layout to be displayed, in this case the activity_main layout
        this.setContentView(R.layout.activity_main);

        // SectionsPagerAdapter extends from FragmentPagerAdapter
        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(),
                FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);

        // adds different fragments that comes into view when clicked
        // CHAT is for sending and receiving BT message to and from STM
        // MAP CONFIG is for configuring the map layout
        // CHALLENGE provides quick access to execute the algo for img recognition & fastest path
        this.btFragment = new BluetoothComms();
        this.mapTabFragment = new MapConfigFragment(this);
        this.challengeFragment = new ChallengeFragment(this);
        this.gridMap = new GridMap(this);
        sectionsPagerAdapter.addFragment(this.btFragment, "CHAT");
        sectionsPagerAdapter.addFragment(this.mapTabFragment, "MAP CONFIG");
        sectionsPagerAdapter.addFragment(this.challengeFragment, "CHALLENGE");

        // TODO
        // dont know what this section does, best to not touch
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        viewPager.setOffscreenPageLimit(2);

        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);

        LocalBroadcastManager
                .getInstance(this)
                .registerReceiver(messageReceiver, new IntentFilter("incomingMessage"));

        // Set up sharedPreferences
        this.context = getApplicationContext();
        this.sharedPreferences();
        this.editor.putString("message", "");
        this.editor.putString("direction", "None");
        this.editor.putString("connStatus", "Disconnected");
        this.editor.commit();

        // Toolbar
        Button bluetoothButton = findViewById(R.id.bluetooth_button);
        bluetoothButton.setOnClickListener(v -> {
            Intent popup = new Intent(MainActivity.this, BluetoothPage.class);
            this.startActivity(popup);
        });

        // Bluetooth Status
        MainActivity.bluetoothStatus = findViewById(R.id.bluetooth_status);
        MainActivity.bluetoothDevice = findViewById(R.id.bluetooth_device);

        // Map
        this.gridMap = findViewById(R.id.map_view);
        this.robotXCoordText = findViewById(R.id.xAxisTextView);
        this.robotYCoordText = findViewById(R.id.yAxisTextView);
        this.robotDirectionText = findViewById(R.id.direction_text);

        // Controller to manually control robot movement
        this.upBtn = findViewById(R.id.up_button);
        this.downBtn = findViewById(R.id.down_button);
        this.leftBtn = findViewById(R.id.left_button);
        this.rightBtn = findViewById(R.id.right_button);

        // Robot Status
        this.robotStatusText = findViewById(R.id.status_text);

        // pops up when BT is disconnected
        this.btDisconnectDialog = new ProgressDialog(MainActivity.this);
        this.btDisconnectDialog.setMessage("Waiting for other device to reconnect...");
        this.btDisconnectDialog.setCancelable(false);
        this.btDisconnectDialog.setButton(
                DialogInterface.BUTTON_NEGATIVE,
                "Cancel",
                (dialog, which) -> dialog.dismiss()
        );

        directionMap.puit stat("North", 0);
        directionMap.put("East", 2);
        directionMap.put("South", 4);
        directionMap.put("West", 6);
    }

    /**
     * Getter function for getting {@link GridMap} object
     * Used in {@link ChallengeFragment} and {@link MapConfigFragment}
     * @return {@link GridMap}
     */
    public GridMap getGridMap() {
        return this.gridMap;
    }

    /**
     * Getter function for getting the status of the robot
     * Used in {@link ChallengeFragment}
     * @return "Auto Movement/ImageRecog Stopped" or "Week 9 Stopped"
     */
    public TextView getRobotStatusText() {
        return this.robotStatusText;
    }

    /**
     * Getter function for getting the manual control UP button
     * Used in {@link ChallengeFragment}
     * @return The manual control UP button
     */
    public ImageButton getUpBtn() {
        return this.upBtn;
    }

    /**
     * Getter function for getting the manual control DOWN button
     * Used in {@link ChallengeFragment}
     * @return The manual control DOWN button
     */
    public ImageButton getDownBtn() {
        return this.downBtn;
    }

    /**
     * Getter function for getting the manual control LEFT button
     * Used in {@link ChallengeFragment}
     * @return The manual control LEFT button
     */
    public ImageButton getLeftBtn() {
        return this.leftBtn;
    }

    /**
     * Getter function for getting the manual control RIGHT button
     * Used in {@link ChallengeFragment}
     * @return The manual control RIGHT button
     */
    public ImageButton getRightBtn() {
        return this.rightBtn;
    }

    /**
     * Getter function for getting the bluetooth status
     * Used in {@link BluetoothConnectionManager}
     * @return "Disconnected" or "Connected"
     */
    public static TextView getBluetoothStatus() {
        return bluetoothStatus;
    }

    /**
     * Getter function for getting the connecteed bluetooth device name
     * Used in {@link BluetoothConnectionManager}
     * @return DEVICE_NAME
     */
    public static TextView getConnectedDevice() {
        return bluetoothDevice;
    }

    public void sharedPreferences() {
        this.sharedPreferences = this.getSharedPreferences(this.context);
        this.editor = this.sharedPreferences.edit();
    }

    /**
     * Modular function for sending message over via BT to STM
     * @param message String message to be sent over via BT
     */
    public void sendMessage(String message) {
        this.editor = this.sharedPreferences.edit();
        message += "\n";
        if (BluetoothConnectionManager.BluetoothConnectionStatus) {
            byte[] bytes = message.getBytes(Charset.defaultCharset());
            // Send the size of bytes to be sent, followed by a newline character
            //String sizeMessage = String.valueOf(bytes.length) + "\n";
            //byte[] sizeBytes = sizeMessage.getBytes(Charset.defaultCharset());
            //BluetoothConnectionService.write(sizeBytes);
            BluetoothConnectionManager.write(bytes);
        }
        this.printMessage(message);
    }

    /**
     * Basically adds a new line after the message is sent, so that the next message will appear on new line
     * @param message Last message that was sent over via BT
     */
    public void printMessage(String message) {
        this.btFragment.getReceivedMessagesTextView().append(message);
    }

    /**
     * Resets the robot's direction when user configures it in Map Config fragment
     * @param direction The updated direction
     */
    public void refreshDirection(String direction) {
        this.gridMap.setRobotDirection(direction);
        this.robotDirectionText.setText(this.sharedPreferences.getString("direction", ""));
    }

    /**
     * Updates the coordinate display whenever robot moves
     */
    public void refreshCoordinate() {
        this.robotXCoordText.setText(String.valueOf(this.gridMap.getCurCoord()[0]));
        this.robotYCoordText.setText(String.valueOf(this.gridMap.getCurCoord()[1]));
        this.robotDirectionText.setText(this.sharedPreferences.getString("direction", ""));
    }

    /**
     * Debugging function to show TAG + message, where TAG is the java file the message was logged
     * @param message the message to be logged
     */
    private void showLog(String message) {
        Log.d(TAG, message);
    }

    /**
     * Get SharedPreference, which is a key-value pair that stores important information across activity
     * @param context The current state of the application
     * @return The SharedPreference object
     */
    private SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
    }

    /**
     * Handles BT connection
     */
    private final BroadcastReceiver mBroadcastReceiver5 = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice mDevice = intent.getParcelableExtra("Device");
            String status = intent.getStringExtra("Status");
            sharedPreferences();

            if (status.equals("connected")) {
                try {
                    btDisconnectDialog.dismiss();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                Toast.makeText(MainActivity.this, "Device now connected to "
                        + mDevice.getName(), Toast.LENGTH_SHORT).show();
                editor.putString("connStatus", "Connected to " + mDevice.getName());
            } else if (status.equals("disconnected")) {
                Toast.makeText(MainActivity.this, "Disconnected from "
                        + mDevice.getName(), Toast.LENGTH_SHORT).show();
                editor.putString("connStatus", "Disconnected");
                btDisconnectDialog.show();
            }
            editor.commit();
        }
    };

    /**
     * Handles message sent from RPI
     * Message format:
     * IMG-[obstacle id]-[image id] for image rec
     *  ex: IMG-3-7 for obstacle 3 === image id 7
     * UPDATE-[x-coord]-[y-coord]-<N>[Bearing] - for updating robot coordinates
     *   ex 1: UPDATE-4.5-6-0 for moving robot to [4,6] (no change in direction, so assume is F/B move)
     *   ex 2: UPDATE-6-6-45 for moving robot 45 degrees to the left, and final position is [6,6]
     *   ex 3: UPDATE-6-6-N45 for moving robot 45 degrees to the right, and final position is [6,6]
     * ENDED for signaling Android that task is completed
     */
    BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("receivedMessage");
            System.out.println("debug" + message);
            if (message.contains("IMG")) {
                String[] cmd = message.split("-");
                gridMap.updateImageID(cmd[1], cmd[2]);
            } else if (message.contains("UPDATE")) {
                String[] cmd = message.split("-");
                int xPos = (int) Float.parseFloat(cmd[1]);
                int yPos = (int) Float.parseFloat(cmd[2]);
                double bearing;
                if (cmd[3].contains("N")) {
                    bearing = (double) -1 * Float.parseFloat(cmd[3].substring(1));
                } else {
                    bearing = Float.parseFloat(cmd[3]);
                }
                gridMap.moveRobot(new int[]{xPos, yPos}, bearing);
            } else if (message.equals("ENDED")) {
                ToggleButton imgRecBtn = findViewById(R.id.task1_start);
                ToggleButton fastestCarBtn = findViewById(R.id.task2_start);

                if (imgRecBtn.isChecked()) {
                    imgRecTimerFlag = true;
                    imgRecBtn.setChecked(false);
                    robotStatusText.setText(R.string.image_rec_end);
                    ChallengeFragment.timerHandler.removeCallbacks(challengeFragment.imgRecTimer);
                } else if (fastestCarBtn.isChecked()) {
                    imgRecTimerFlag = true;
                    fastestCarBtn.setChecked(false);
                    robotStatusText.setText(R.string.fastest_car_end);
                    ChallengeFragment.timerHandler.removeCallbacks(challengeFragment.fastestCarTimer);
                }
            }
        }
    };

    /**
     * Creates obstacle RPI message in the following format:
     * {
     *     "cat": "obstacles",
     *     "value": {
     *         "obstacles": [{"x": 5, "y": 10, "id": 1, "d": 2}, {"x": 18, "y": 0, "id": 2, "d": 0}],
     *         "mode": "0"
     *     }
     * }
     */
    public String getObstacleRpiMessage() throws JSONException {
        JSONObject outerObject = new JSONObject();
        JSONObject valueObject = new JSONObject();
        JSONArray obstacleArray = new JSONArray();
        for (int i = 0; i < this.gridMap.getObstacleCoord().size(); i ++) {
            int[] currentObstacle = this.gridMap.getObstacleCoord().get(i);
            String imageBearing = IMAGE_BEARING[currentObstacle[1] - 1][currentObstacle[0] - 1];

            JSONObject obstacleObject = new JSONObject();
            obstacleObject.put("x", currentObstacle[0]);
            obstacleObject.put("y", currentObstacle[1]);
            obstacleObject.put("d", directionMap.get(imageBearing));
            obstacleObject.put("id", currentObstacle[2]);

            obstacleArray.put(obstacleObject);

        }

        valueObject.put("obstacles", obstacleArray);
        valueObject.put("mode", "0");

        outerObject.put("cat", "obstacles");
        outerObject.put("value", valueObject);

        return outerObject.toString();
    }

    /**
     * Creates start RPI message
     */
    public String getStartRpiMessage() {
        return "{\"cat\": \"control\", \"value\": \"start\"}";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                BluetoothDevice mBTDevice = data.getExtras().getParcelable("mBTDevice");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver5);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver5);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            IntentFilter filter2 = new IntentFilter("ConnectionStatus");
            LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver5, filter2);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        this.showLog("Entering onSaveInstanceState");
        super.onSaveInstanceState(outState);

        outState.putString(TAG, "onSaveInstanceState");
        this.showLog("Exiting onSaveInstanceState");
    }
}