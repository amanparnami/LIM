package edu.gatech.ubicomp.glim;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
		try {
			BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceMacId);
			Method m = BluetoothDevice.class.getMethod("convertPinToBytes",
					new Class[] { String.class });
			byte[] pin = (byte[]) m.invoke(device, pinString);
			m = device.getClass().getMethod("setPin",
					new Class[] { pin.getClass() });
			Object result = m.invoke(device, pin);
			Log.d("BTTest", result.toString());
			
			Log.d("Bond state", "BOND_STATED = " + device.getBondState());
		} catch (SecurityException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (NoSuchMethodException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
