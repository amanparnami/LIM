package edu.gatech.ubicomp.glim;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetooth;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class BTPairingRequestReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d("BTIntent", intent.getAction());
		Bundle b = intent.getExtras();
		Log.d("BTINTENT", b.get("android.bluetooth.device.extra.PAIRING_VARIANT").toString());
//		Bundle b = intent.getExtras();
		final String HRM_PIN = "1234";
		final String EDA_PIN = "0000";
		String pinString = new String();
		BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		String deviceMacId = btDevice.getAddress();
		if(deviceMacId == GlimMainActivity.BT_MAC_ADD_FOR_EDA_SENSOR) {
			pinString = EDA_PIN;
		} else {

			pinString = HRM_PIN;
		}
		Log.d("BTIntent", deviceMacId);
		
		btDevice.setPin(pinString.getBytes());
	}
}
