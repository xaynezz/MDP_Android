package com.example.mdp_android;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.example.mdp_android.R;

import java.util.ArrayList;

/**
 * Custom ArrayAdapter for displaying Bluetooth devices in a ListView.
 * This adapter binds Bluetooth device data to list items in a device list view.
 */
public class BluetoothDeviceListAdapter extends ArrayAdapter<BluetoothDevice> {

    private final LayoutInflater layoutInflater; // Inflater for creating views
    private final ArrayList<BluetoothDevice> bluetoothDevices; // List of Bluetooth devices to display
    private final int resourceViewId; // Resource ID for the layout of each item in the list

    /**
     * Constructor for the BluetoothDeviceListAdapter.
     *
     * @param context       The context of the calling activity or fragment.
     * @param resourceViewId The layout resource ID for a single list item.
     * @param devices       The list of Bluetooth devices to be displayed.
     */
    public BluetoothDeviceListAdapter(Context context, int resourceViewId, ArrayList<BluetoothDevice> devices) {
        super(context, resourceViewId, devices);
        this.bluetoothDevices = devices;
        this.layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.resourceViewId = resourceViewId;
    }

    /**
     * Populates the list view with the Bluetooth device information.
     *
     * @param position    The position of the item in the list.
     * @param convertView The old view to reuse, if possible.
     * @param parent      The parent view that this view will eventually be attached to.
     * @return The view for the list item.
     */
    @Override
    @SuppressLint("MissingPermission")
    public View getView(int position, View convertView, ViewGroup parent) {
        Log.d("BluetoothDeviceListAdapter", "Getting View");
        if (convertView == null) {
            convertView = layoutInflater.inflate(resourceViewId, null); // Inflate a new view if not recycling
        }

        BluetoothDevice device = bluetoothDevices.get(position); // Get the Bluetooth device at the specified position

        // Set the device name and address in the respective text views
        if (device != null) {
            TextView deviceNameTextView = convertView.findViewById(R.id.deviceName);
            TextView deviceAddressTextView = convertView.findViewById(R.id.deviceAddress);

            if (deviceNameTextView != null) {
                deviceNameTextView.setText(device.getName());
            }
            if (deviceAddressTextView != null) {
                deviceAddressTextView.setText(device.getAddress());
            }
        }

        return convertView;
    }
}
