package edu.gatech.ubicomp.glim;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetooth;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class BTPairingRequestReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d("BTIntent", intent.getAction());
		Bundle b = intent.getExtras();
		final String HRM_PIN = "1234";
		final String EDA_PIN = "0000";
		String pinString = new String();
		String deviceMacId = b.get("android.bluetooth.device.extra.DEVICE")
				.toString();
		if(deviceMacId == GlimMainActivity.BT_MAC_ADD_FOR_EDA_SENSOR) {
			pinString = EDA_PIN;
		} else {

			pinString = HRM_PIN;
		}
		Log.d("BTIntent", b.get("android.bluetooth.device.extra.DEVICE")
				.toString());
		Log.d("BTIntent",
				b.get("android.bluetooth.device.extra.PAIRING_VARIANT")
				.toString());

		IBluetooth ib =getIBluetooth();
		try {
			ib.setPin(deviceMacId,pinString.getBytes());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private IBluetooth getIBluetooth() {
		IBluetooth ibt = null;

		try {

			Class c2 = Class.forName("android.os.ServiceManager");

			Method m2 = c2.getDeclaredMethod("getService",String.class);
			IBinder b = (IBinder) m2.invoke(null, "bluetooth");

			Class c3 = Class.forName("android.bluetooth.IBluetooth");

			Class[] s2 = c3.getDeclaredClasses();

			Class c = s2[0];
			Method m = c.getDeclaredMethod("asInterface",IBinder.class);
			m.setAccessible(true);
			ibt = (IBluetooth) m.invoke(null, b);


		} catch (Exception e) {
			Log.e("flowlab", "Erroraco!!! " + e.getMessage());
		}

		return ibt;
	}
}
