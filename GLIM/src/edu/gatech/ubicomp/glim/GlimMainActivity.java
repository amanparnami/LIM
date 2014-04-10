package edu.gatech.ubicomp.glim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

import zephyr.android.HxMBT.BTClient;
import zephyr.android.HxMBT.ZephyrProtocol;

import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * TODO: 
 * (done 2/13/14) Reduce redundancy in variables used for Bluetooth connection.
 * (done 2/13/14) Merge the common parts of Bluetooth connection protocol for both sensors.
 * (done 2/13/14) Rename functions and variables to semantically appropriate names.
 * - Create separate activities for initiating pairing HRM and EDA sensor to Glass.
 * - Enable data transmission to server.
 * - Enable annotation. 
 * - Allow multiple individual connection attempts to HRM and EDA 
 * 	Look at	http://stackoverflow.com/questions/4715865/how-to-programmatically-tell-if-a-bluetooth-device-is-connected-android-2-2
 * (done 4/7/14) Show instructions about "double tap" on start
 * Look at http://stackoverflow.com/questions/10216937/how-do-i-create-a-help-overlay-like-you-see-in-a-few-android-apps-and-ics
 */

/**
 * GLIM's main activity.
 */
public class GlimMainActivity extends Activity {

	/** Bluetooth connection related variables */
	private BluetoothAdapter mBtAdapter = null;
	private BluetoothSocket mBtSocket;
	private BluetoothDevice mBtDevice, mEdaBtDevice, mHrmBtDevice;
	private String mEdaBtDeviceName, mHrmBtDeviceName;
	private static boolean waitingForBonding = true;

	/** Bluetooth communication variables for Zephyr API */
	private BTClient mZephyrBtClient;
	private ZephyrProtocol mZephyrProtocol;

	/** I/O related variables */
	private OutputStream mOutputStream;
	private InputStream mInputStream;
	private HRMListener mHrmListener;
	private byte[] mReadBuffer, mAnnotationMarker;
	private ArrayList<String> mEDABuffer;

	/** Variables to keep track of I/O */
	private int mReadBufferPos;
	private int mBufferSize = 0;
	private int mCounter = 0;
	
	/** Factor sued to convert from dp into actual number of pixels*/
	float scale;

	public final static double HR_PERCENTAGE_LEVELS = 10; 
//	public final static double IVAN_EDA_REST = ;
//	public final static double IVAN_EDA_REST = ;
	
	public final static float BASE_HR_VALUE = 80;
	public final static float BASE_EDA_VALUE = 1.5f;
	
	/** Minimum percentage by which either signal has to change to jump a level*/
	public final static float MIN_PERCENTAGE_LEVEL_SHIFT = 10; 
	
	/** UUIDs for connecting with other devices. */
	private static final UUID MY_UUID_FOR_ANDROID_DEVICES = UUID
			.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
	// Standard SerialPortServiceID
	private static final UUID MY_UUID_FOR_NON_ANDROID_DEVICES = UUID
			.fromString("00001101-0000-1000-8000-00805f9b34fb");

	/** MAC address for all components. */
	public static final String BT_MAC_ADD_FOR_EDA_SENSOR = "00:06:66:0A:50:04";//Ivan's Q Sensor "00:06:66:0A:50:04";//Aman's Q Sensor "00:06:66:0A:50:D5";
	public static final String BT_MAC_ADD_FOR_GLASS = "F8:8F:CA:24:BF:32";//Ivan's Glass"F8:8F:CA:24:BF:32"; //Aman's Glass"F8:8F:CA:24:82:48";
	public static final String WIFI_MAC_ADD_FOR_GLASS = "f8:8f:ca:24:82:47";
	public static final String BT_MAC_ADD_FOR_MIO_HRM = "C3:52:C1:79:7B:B5";
	public static final String BT_MAC_ADD_FOR_ZEPHYR_HRM = "00:07:80:6E:A7:F2";//Ivan's HRM "00:07:80:6E:A7:F2"; //Aman's HRM "00:07:80:6D:7F:25"; 
	public static final String BT_MAC_ADD_FOR_NEXUS = "40:B0:FA:0C:E3:BD";

	/** Tags used while making server requests. */
	public static final String[] DATA_HEADER_TAGS = { "time", "z", "y", "x",
		"battery", "temp", "eda", "event" };
	public static final String[] EDA_DATA_HEADER_TAGS = { "time", "eda" };
	public static final String[] EDA_ANNOTATION_HEADER_TAGS = { "time" };

	/** Miscellaneous constants. */
	public static final String TAG = null;
	public static final String PIN = "0000";
	public static final int MAX_BUFFER_SIZE = 32;
	public static final int EDA_SAMPLING_RATE = 32;
	public static final int MSG_WHAT_HEART_RATE = 0x100;
	public static final int MSG_WHAT_INSTANT_SPEED = 0x101;

	public HashMap<String, Float> mapHrEda;
	
	/** UI related variables. */
	private TextView mLabel;
	private EditText mTextbox;
	private ImageView mImageView;
	private Animation mPulseHeartAnim;
	private GestureDetector mGestureDetector;
	private EDAWaveformView mWaveformView;
	private FrameLayout mAbsoluteFrameLayout;
	private View mInstructionOverlay;
	private View mMainView;
	
	private AudioManager mAudioManager;
	LinearLayout llRestIndicator = null;
	LinearLayout llActiveIndicator = null;
	LinearLayout llHyperIndicator = null;
	LinearLayout llSignalIndicator = null;
	TextView hrValueTV = null;
	TextView instantSpeedTV = null;

	/** Data listener related variables. */
	volatile boolean stopWorker;
	boolean isConnected = false;
	Thread mEdaSensorListenerThread;
	final Handler mHrmMsgHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_WHAT_HEART_RATE:
				String heartRateText = msg.getData().getString("HeartRate");
				//System.out.println("Heart Rate Info is " + heartRateText);
				if (hrValueTV != null) {
					hrValueTV.setText(heartRateText);
					float currentHR = Float.parseFloat(heartRateText);
					mapHrEda.put("HR", currentHR);
					//updateHeartRateBand(currentHR);
					Log.d("Heart Rate",Float.toString(currentHR));
					updateBand();
				}
				break;
			case MSG_WHAT_INSTANT_SPEED:
				String InstantSpeedtext = msg.getData().getString(
						"InstantSpeed");
				
				if (instantSpeedTV != null) {
					instantSpeedTV.setText(InstantSpeedtext);
				}
				break;
			}
		}
	};

	public void updateHeartRateBand(float heartRateValue) {
		if (heartRateValue < 65.0) { // Rest condition
			
			llRestIndicator.setBackgroundResource(R.color.green_bright);
			llRestIndicator.getLayoutParams().width = (int)(280*scale +0.5f);
			llActiveIndicator.setBackgroundResource(R.color.yellow_dull);
			llActiveIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
			llHyperIndicator.setBackgroundResource(R.color.red_dull);
			llHyperIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
			MarginLayoutParams params=(MarginLayoutParams)hrValueTV.getLayoutParams();
			params.leftMargin=(int)(140*scale +0.5f);
			//here 100 means 100px,not 80% of the width of the parent view
			//you may need a calculation to convert the percentage to pixels. 
			hrValueTV.setTextColor(Color.WHITE);
			hrValueTV.setLayoutParams(params);
		} else if (heartRateValue >= 65.0 && heartRateValue < 70.0) { // Active
			// condition
			
			llRestIndicator.setBackgroundResource(R.color.green_dull);
			llRestIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
			llActiveIndicator.setBackgroundResource(R.color.yellow_bright);
			llActiveIndicator.getLayoutParams().width = (int)(280*scale +0.5f);
			llHyperIndicator.setBackgroundResource(R.color.red_dull);
			llHyperIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
			MarginLayoutParams params=(MarginLayoutParams)hrValueTV.getLayoutParams();
			params.leftMargin=(int)(210*scale +0.5f);
			//here 100 means 100px,not 80% of the width of the parent view
			//you may need a calculation to convert the percentage to pixels. 
			hrValueTV.setTextColor(Color.BLACK);
			hrValueTV.setLayoutParams(params);
		} else { // Hyper condition
			
			llRestIndicator.setBackgroundResource(R.color.green_dull);
			llRestIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
			llActiveIndicator.setBackgroundResource(R.color.yellow_dull);
			llActiveIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
			llHyperIndicator.setBackgroundResource(R.color.red_bright);
			llHyperIndicator.getLayoutParams().width = (int)(280*scale +0.5f);
			MarginLayoutParams params=(MarginLayoutParams)hrValueTV.getLayoutParams();
			params.leftMargin=(int)(280*scale +0.5f);
			//here 100 means 100px,not 80% of the width of the parent view
			//you may need a calculation to convert the percentage to pixels. 
			hrValueTV.setTextColor(Color.WHITE);
			hrValueTV.setLayoutParams(params);
		}
	}
	
	/**
	 * 
	 * @param heartRateValue
	 */
	public void updateBand() {
		int shiftValue = totalShift();
		MarginLayoutParams params=(MarginLayoutParams)hrValueTV.getLayoutParams();
		Log.d("Shift", Integer.toString(shiftValue));
		switch(shiftValue) {
		case 0:
		case 1:// Rest condition
//			llRestIndicator.setBackgroundResource(R.color.green_bright);
//			llRestIndicator.getLayoutParams().width = (int)(280*scale +0.5f);
//			llActiveIndicator.setBackgroundResource(R.color.yellow_dull);
//			llActiveIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
//			llHyperIndicator.setBackgroundResource(R.color.red_dull);
//			llHyperIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
			llSignalIndicator.setBackgroundResource(R.color.blue_bright);
			llSignalIndicator.getLayoutParams().width = (int)(180*scale +0.5f);
//			params.leftMargin=(int)(140*scale +0.5f);
//			hrValueTV.setLayoutParams(params);
			//here 100 means 100px,not 80% of the width of the parent view
			//you may need a calculation to convert the percentage to pixels. 
			hrValueTV.setTextColor(Color.WHITE);
			
			break;
		case 2:
		case 3:// Active condition
			llSignalIndicator.setBackgroundResource(R.color.green_bright);
			llSignalIndicator.getLayoutParams().width = (int)(220*scale +0.5f);
//			llRestIndicator.setBackgroundResource(R.color.green_dull);
//			llRestIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
//			llActiveIndicator.setBackgroundResource(R.color.yellow_bright);
//			llActiveIndicator.getLayoutParams().width = (int)(280*scale +0.5f);
//			llHyperIndicator.setBackgroundResource(R.color.red_dull);
//			llHyperIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
//			params.leftMargin=(int)(210*scale +0.5f);
//			hrValueTV.setLayoutParams(params);
			//here 100 means 100px,not 80% of the width of the parent view
			//you may need a calculation to convert the percentage to pixels. 
			hrValueTV.setTextColor(Color.WHITE);
			
			break;
		case 4:
		case 5: // Hyper Active condition
			llSignalIndicator.setBackgroundResource(R.color.yellow_bright);
			llSignalIndicator.getLayoutParams().width = (int)(260*scale +0.5f);
//			llRestIndicator.setBackgroundResource(R.color.green_dull);
//			llRestIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
//			llActiveIndicator.setBackgroundResource(R.color.yellow_dull);
//			llActiveIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
//			llHyperIndicator.setBackgroundResource(R.color.red_bright);
//			llHyperIndicator.getLayoutParams().width = (int)(280*scale +0.5f);
//			params.leftMargin=(int)(280*scale +0.5f);
//			hrValueTV.setLayoutParams(params);
			//here 100 means 100px,not 80% of the width of the parent view
			//you may need a calculation to convert the percentage to pixels. 
			hrValueTV.setTextColor(Color.WHITE);
			
			break;
		case 6:
		case 7:
			llSignalIndicator.setBackgroundResource(R.color.orange_bright);
			llSignalIndicator.getLayoutParams().width = (int)(300*scale +0.5f);
//			llRestIndicator.setBackgroundResource(R.color.green_dull);
//			llRestIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
//			llActiveIndicator.setBackgroundResource(R.color.yellow_dull);
//			llActiveIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
//			llHyperIndicator.setBackgroundResource(R.color.red_bright);
//			llHyperIndicator.getLayoutParams().width = (int)(280*scale +0.5f);
//			params.leftMargin=(int)(280*scale +0.5f);
//			hrValueTV.setLayoutParams(params);
			//here 100 means 100px,not 80% of the width of the parent view
			//you may need a calculation to convert the percentage to pixels. 
			hrValueTV.setTextColor(Color.WHITE);
			
			break;
		default: // Stay in hyper
			llSignalIndicator.setBackgroundResource(R.color.red_bright);
			llSignalIndicator.getLayoutParams().width = (int)(360*scale +0.5f);
//			llRestIndicator.setBackgroundResource(R.color.green_dull);
//			llRestIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
//			llActiveIndicator.setBackgroundResource(R.color.yellow_dull);
//			llActiveIndicator.getLayoutParams().width = (int)(70*scale +0.5f);
//			llHyperIndicator.setBackgroundResource(R.color.red_bright);
//			llHyperIndicator.getLayoutParams().width = (int)(280*scale +0.5f);
//			params.leftMargin=(int)(280*scale +0.5f);
//			hrValueTV.setLayoutParams(params);
			//here 100 means 100px,not 80% of the width of the parent view
			//you may need a calculation to convert the percentage to pixels. 
			hrValueTV.setTextColor(Color.WHITE);
			
			break;
		}
	}

	/**
	 * Calculating the total shift in band based on percentage increase in both EDA and HR values from base levels.
	 * Note that either one of them can compensate for the other. Also we are interest in 10% increase only.
	 * @return
	 */
	public int totalShift() {
		float edaValue = mapHrEda.get("EDA");
		float hrValue = mapHrEda.get("HR");
		
		//TODO Handle cases when the value is negative and remove Math.abs
		float percentageIncrEDA = Math.abs(((edaValue-BASE_EDA_VALUE)/BASE_EDA_VALUE)*100); 
		float percentageIncrHR = Math.abs(((hrValue-BASE_HR_VALUE)/BASE_HR_VALUE)*100);
		
		int shiftEDA = (int) (percentageIncrEDA/MIN_PERCENTAGE_LEVEL_SHIFT);
		int shiftHR = (int) (percentageIncrHR/MIN_PERCENTAGE_LEVEL_SHIFT);
		
		return shiftEDA+shiftHR;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mAbsoluteFrameLayout = new FrameLayout(this.getBaseContext());
		setContentView(mAbsoluteFrameLayout);
		//setContentView(R.layout.activity_main);

		mMainView = getLayoutInflater().inflate(R.layout.activity_main, mAbsoluteFrameLayout, false);
		mInstructionOverlay = getLayoutInflater().inflate(R.layout.instruction_view, mAbsoluteFrameLayout, false);
		mAbsoluteFrameLayout.addView(mMainView);
		mAbsoluteFrameLayout.addView(mInstructionOverlay);

		mEdaBtDeviceName = new String(); 
		mHrmBtDeviceName = new String();

		mLabel = (TextView) findViewById(R.id.labelEdaValue);

		mapHrEda = new HashMap<String, Float>();
		mapHrEda.put("EDA", BASE_EDA_VALUE);
		mapHrEda.put("HR", BASE_HR_VALUE);
		
		//mWaveformView = (EDAWaveformView) findViewById(R.id.waveformView);
		mBufferSize = 0;
		mEDABuffer = new ArrayList<String>();
		
		scale = this.getBaseContext().getResources().getDisplayMetrics().density;

		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		mGestureDetector = createGestureDetector(this);
		
		llRestIndicator = ((LinearLayout) findViewById(R.id.hrIndicatorRest));
		llActiveIndicator = ((LinearLayout) findViewById(R.id.hrIndicatorActive));
		llHyperIndicator = ((LinearLayout) findViewById(R.id.hrIndicatorHyper));
		llSignalIndicator = ((LinearLayout) findViewById(R.id.signalIndicator));
		hrValueTV = (TextView) findViewById(R.id.labelHrValue);
//		instantSpeedTV = (TextView) findViewById(R.id.InstantSpeed);
		// mImageView = (ImageView) findViewById(R.id.heartBeatImage);
		// mPulseHeartAnim = AnimationUtils.loadAnimation(this, R.anim.pulse);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();

	}

	private GestureDetector createGestureDetector(Context context) {
		GestureDetector gestureDetector = new GestureDetector(context);
		// Create a base listener for generic gestures
		gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
			@Override
			public boolean onGesture(Gesture gesture) {
				if (gesture == Gesture.TAP) {
					// do something on tap
					
					openOptionsMenu();
					return true;
				} else if (gesture == Gesture.TWO_TAP) {
					//FIXME Doesn't immediately removes the view.
					//TODO Add an animation to cover up for the delay in removing the text.
					mAbsoluteFrameLayout.removeView(mInstructionOverlay);
					mAudioManager.playSoundEffect(AudioManager.FX_KEY_CLICK);
					// do something on two finger tap
					if (!isConnected) {
						try {
							// Connect to Bluetooth devices
							connectBtDevices();

							isConnected = true;
						} catch (IOException ex) {
							//Set isConnected back to false so that we try again in next tap.
							isConnected = false;
						}
					} else {
						// Disconnect Bluetooth
						try {
							disconnectBtDevices();
							mAbsoluteFrameLayout.addView(mInstructionOverlay);
							isConnected = false;
						} catch (IOException ex) {
							isConnected = true;
						}

					}
					return true;
				} else if (gesture == Gesture.THREE_TAP) {
					// saveAnnotation();
				} else if (gesture == Gesture.SWIPE_RIGHT) {
					// do something on right (forward) swipe
					return true;
				} else if (gesture == Gesture.SWIPE_LEFT) {
					// do something on left (backwards) swipe
					return true;
				}
				return false;
			}
		});
		// gestureDetector.setFingerListener(new
		// GestureDetector.FingerListener() {
		// @Override
		// public void onFingerCountChanged(int previousCount, int currentCount)
		// {
		// // do something on finger count changes
		// }
		// });
		// gestureDetector.setScrollListener(new
		// GestureDetector.ScrollListener() {
		// @Override
		// public boolean onScroll(float displacement, float delta, float
		// velocity) {
		// // do something on scrolling
		// }
		// });
		return gestureDetector;
	}

	/*
	 * Send generic motion events to the gesture detector
	 */
	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (mGestureDetector != null) {
			return mGestureDetector.onMotionEvent(event);
		}
		return false;
	}


	/*	*//**
	 * Handle the tap event from the touchpad.
	 *//*
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		// Handle tap events.
		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_ENTER:
			// sendDataToServer(mEDABuffer);
			openOptionsMenu();
			return true;
		case KeyEvent.KEYCODE_TAB:
			// saveAnnotation();
			return true;
		default:
			return super.onKeyDown(keyCode, event);
		}
	}*/

	/**
	 * Create connection between Glass and other bluetooth devices.
	 */
	void connectBtDevices() throws IOException{
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();

		if (mBtAdapter != null) {
			// If bluetooth is not enabled then do so.
			if (!mBtAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBluetooth, 0);
			}

			// Retrieve a list of paired devices
			Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
			String edaDeviceMacId = null, hrmDeviceMacId = null;

			if(pairedDevices.size() > 0) {
				// Check if the specific devices are paired already 
				for(BluetoothDevice device : pairedDevices) {
					if(device.getName().contains("Affectiva")) {
						edaDeviceMacId = device.getAddress();
						System.out.println("Paired EDA device found "+ edaDeviceMacId);
						mEdaBtDevice = device;
						mEdaBtDeviceName = mEdaBtDevice.getName();
					}
					if(device.getName().contains("HXM")){
						hrmDeviceMacId = device.getAddress();
						System.out.println("Paired HRM device found "+hrmDeviceMacId);
						mHrmBtDevice = device;
						mHrmBtDeviceName = mHrmBtDevice.getName();
					}
				}
			}

			// If EDA sensor is not paired already then do so
			connectBtEdaDevice(edaDeviceMacId);

			// If HRM is not paired already then do so
			connectBtHrmDevice(hrmDeviceMacId);

			startListeningForData();
		}
	}

	/**
	 * Initiate connection with EDA sensor
	 * @param edaDeviceMacId
	 * @throws IOException
	 */
	void connectBtEdaDevice(String edaDeviceMacId) throws IOException {
		if(edaDeviceMacId == null) {
			System.out.println("EDA device");
			mEdaBtDevice = mBtAdapter.getRemoteDevice(BT_MAC_ADD_FOR_EDA_SENSOR);
			mEdaBtDeviceName = mEdaBtDevice.getName();
			pairDevice(mEdaBtDevice);
		} 
		try {

			mBtSocket = mEdaBtDevice
					.createRfcommSocketToServiceRecord(MY_UUID_FOR_NON_ANDROID_DEVICES);
			mBtSocket.connect();
		} catch (IOException e) {
			//e.printStackTrace();
			System.err.println("Could not connect to EDA Sensor.");
		}

		try {
			mOutputStream = mBtSocket.getOutputStream();
			mInputStream = mBtSocket.getInputStream();
		} catch (IOException e) {
			//e.printStackTrace();
			System.err.println("Could not open an insput stream.");
		}

	}

	/**
	 * Initiates connection with HRM.
	 * @param hrmDeviceMacId
	 * @throws IOException
	 */
	void connectBtHrmDevice(String hrmDeviceMacId) throws IOException {
		if(hrmDeviceMacId == null) {
			System.out.println("HRM device");
			mHrmBtDevice = mBtAdapter.getRemoteDevice(BT_MAC_ADD_FOR_ZEPHYR_HRM);
			mHrmBtDeviceName = mHrmBtDevice.getName();
			pairDevice(mHrmBtDevice);
		}	
		// Next line is needed before connect to avoid "service discovery failed" error
		mBtAdapter.cancelDiscovery();
		mZephyrBtClient = new BTClient(mBtAdapter, BT_MAC_ADD_FOR_ZEPHYR_HRM);
		mHrmListener = new HRMListener(mHrmMsgHandler);
		mZephyrBtClient.addConnectedEventListener(mHrmListener);
	}

	/**
	 * Begin listening for data from sensors.
	 */
	void startListeningForData() {
		System.out.println("Listening for data..");

		// Start listening for HRM data
		listenForHrmData();

		// Start listening for EDA data 
		listenForEdaData();
	}

	/**
	 * Disconnect bluetooth connection between Glass and other devices.
	 */
	void disconnectBtDevices() throws IOException{
		// Stop listening for EDA data
		try {
			disconnectBtEdaDevice();
		} catch (IOException e) {
			//e.printStackTrace();
			System.err.println("Error in closing connection to EDA Sensor.");
		}

		// Stop listening for HRM data
		try {
			disconnectBtHrmDevice();
		} catch (IOException e) {
			//e.printStackTrace();
			System.err.println("Error in closing connection to HRM.");
		}
	}


	/**
	 * Listens for HRM data.
	 */
	void listenForHrmData() {
		if(mZephyrBtClient.IsConnected()) {
			mZephyrBtClient.start();
		}
	}

	/**
	 * Listens for EDA data.
	 * TODO: Clean this function
	 */
	void listenForEdaData() {
		final Handler handler = new Handler();
		final byte delimiter = 10; // This is the ASCII code for a newline
		// character

		stopWorker = false;
		mReadBufferPos = 0;
		mReadBuffer = new byte[1024];
		mEdaSensorListenerThread = new Thread(new Runnable() {
			public void run() {
				while (!Thread.currentThread().isInterrupted() && !stopWorker) {
					try {
						int bytesAvailable = mInputStream.available();
						if (bytesAvailable > 0) {
							byte[] packetBytes = new byte[bytesAvailable];
							mInputStream.read(packetBytes);
							for (int i = 0; i < bytesAvailable; i++) {
								byte b = packetBytes[i];
								if (b == delimiter) {
									byte[] encodedBytes = new byte[mReadBufferPos];
									System.arraycopy(mReadBuffer, 0,
											encodedBytes, 0,
											encodedBytes.length);
									// To remove <CR> that is at the end of the
									// string
									final String data = (new String(
											encodedBytes, "US-ASCII"))
											.replaceAll("\\n", "").replaceAll(
													"\\r", "");

									// TODO: Handle arrayindexoutofboundsexception when annotation is done by pressing button on Q sensor
									final String edaSignal = data.split(",")[6];
									final Long timestamp = System
											.currentTimeMillis();
									final Float fEdaSignal = Float
											.parseFloat(edaSignal);
									mReadBufferPos = 0;

									if (mBufferSize < MAX_BUFFER_SIZE) {
										mEDABuffer.add(mBufferSize,
												timestamp + ","
														+ edaSignal);
										mBufferSize++;
									} else if (mBufferSize == MAX_BUFFER_SIZE) {
										// We make a copy of mEDABuffer
										// before starting a request to
										// server because
										// by the time the server
										// prepares it's request the
										// actual copy of mEDABuffer has
										// changed.
										// Thus we now use the new copy
										// we just created that
										// preserves all values from
										// original buffer.
										String[] tempBufferCopy = new String[MAX_BUFFER_SIZE];
										System.arraycopy(mEDABuffer.toArray(), 0, tempBufferCopy, 0, MAX_BUFFER_SIZE);
										//sendDataToServer(tempBufferCopy);
										mEDABuffer.clear();
										mEDABuffer.add(0, timestamp
												+ "," + edaSignal);
										mBufferSize = 1;
										Log.d("EDA", Float.toString(fEdaSignal));
									}

//									handler.post(new Runnable() {
//										public void run() {
//											mWaveformView
//											.updateEDADataSimple(fEdaSignal);
//											mLabel.setText(fEdaSignal
//													.toString());
//										}
//									});
									handler.post(new Runnable() {
									public void run() {
										mapHrEda.put("EDA", fEdaSignal);
										updateBand();
										mLabel.setText(fEdaSignal
												.toString());
									}
								});
								} else {
									mReadBuffer[mReadBufferPos++] = b;
								}
							}
						}
					} catch (IOException ex) {
						stopWorker = true;
					}
				}
			}
		});

		mEdaSensorListenerThread.start();
	}

	// void sendData() throws IOException
	// {
	// String msg = mTextbox.getText().toString();
	// msg += "\n";
	// mOutputStream.write(msg.getBytes());
	// mLabel.setText("Data Sent");
	// }

	void disconnectBtEdaDevice() throws IOException {
		stopWorker = true;
		// mOutputStream.close();
		mInputStream.close();
		mBtSocket.close();
		// mLabel.setText("Bluetooth Closed");
	}

	void disconnectBtHrmDevice() throws IOException {
		/*
		 * Stopping the animation
		 */
		// mImageView.clearAnimation();

		/*
		 * Close the communication with the device & throw an exception if
		 * failure
		 */
		mZephyrBtClient.Close();

		/* This disconnects listener from acting on received messages */
		mZephyrBtClient.removeConnectedEventListener(mHrmListener);

	}

	private static String convertArraytoCSV(String[] list) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		int index = 0;
		for (String s : list) {
			if (index != 0)
				sb.append("\n");
			sb.append(s);
			index++;
		}
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Save annotation for the current data
	 */
	private void saveAnnotation() {
		// TODO 1. Save the annotation along with the bounds for the data
		// 2. Communicate via bluetooth to the phone
		// Log.d("Hola", "tap");
		String url = "http://madeby.amanparnami.com/AnnotateEDAServer/annotateglasseda.php";
		new HttpAsyncTask().execute(url, null, "annotation");
	}

	/*
	 * Sends buffered data to server in JSON format
	 */
	private void sendDataToServer(String[] list) {
		// TODO
		// 1. get buffered data and make a copy (otherwise the data might change
		// while the server request is processed) - done
		// 2. convert data to JSON string
		// 3. establish a connection with the server
		// 4. send request

		String url = "http://madeby.amanparnami.com/AnnotateEDAServer/annotateglasseda.php";
		new HttpAsyncTask().execute(url, list, "data");
	}

	/**
	 * Generates a new thread to make POST request, else the UI thread freezes
	 * 
	 * @author localadmin
	 * 
	 */
	private class HttpAsyncTask extends AsyncTask<Object, Void, String> {
		@Override
		protected String doInBackground(Object... args) {

			return dataPOST((String) args[0], (String[]) args[1],
					(String) args[2]);

		}

		// onPostExecute displays the results of the AsyncTask.
		@Override
		protected void onPostExecute(String result) {
			// Toast.makeText(getBaseContext(), "Data Sent!",
			// Toast.LENGTH_LONG).show();
		}

		@Override
		protected void onPreExecute() {
		}

		@Override
		protected void onProgressUpdate(Void... values) {
		}
	}

	public boolean isConnected() {
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected())
			return true;
		else
			return false;
	}

	public static String dataPOST(String url, String[] list,
			String mode) {
		InputStream inputStream = null;
		String result = "";
		try {

			// 1. create HttpClient
			HttpClient httpclient = new DefaultHttpClient();

			// 2. make POST request to the given URL
			HttpPost httpPost = new HttpPost(url);

			String json = "";

			// 3. build jsonObject
			JSONObject jsonObject = new JSONObject();
			// jsonObject.accumulate("name", person.getName());
			// jsonObject.accumulate("country", person.getCountry());
			// jsonObject.accumulate("twitter", person.getTwitter());

			// jsonObject.accumulate("edasignal", convertArraytoJSON(list));
			if (mode == "data") {
				jsonObject.accumulate("edasignal", convertArraytoCSV(list));
			} else if (mode == "annotation") {
				// TODO Empirically derive the offset used below. Currently
				// 2000ms is half of the time taken to achieve MAX_BUFFER_SIZE
				// of 128 used in EDAWaveformView.java
				jsonObject.accumulate("annotation",
						System.currentTimeMillis() - 2000); // subtracted 2000ms
				// to adjust the
				// annotation to
				// match with the
				// signal at the
				// center of screen
			}

			// 4. convert JSONObject to JSON to String
			json = jsonObject.toString();
			// Log.d("JSON", json);

			// ** Alternative way to convert Person object to JSON string usin
			// Jackson Lib
			// ObjectMapper mapper = new ObjectMapper();
			// json = mapper.writeValueAsString(person);

			// 5. set json to StringEntity
			StringEntity se = new StringEntity(json);

			// 6. set httpPost Entity
			httpPost.setEntity(se);

			// 7. Set some headers to inform server about the type of the
			// content
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");

			// 8. Execute POST request to the given URL
			HttpResponse httpResponse = httpclient.execute(httpPost);

			// 9. receive response as inputStream
			inputStream = httpResponse.getEntity().getContent();

			// 10. convert inputstream to string
			if (inputStream != null)
				result = convertInputStreamToString(inputStream);
			else
				result = "Did not work!";

		} catch (Exception e) {
			Log.d("InputStream", e.getLocalizedMessage());
		}

		Log.d("POST result", result);
		// 11. return result
		return result;
	}

	private boolean validate() {
		// if(etName.getText().toString().trim().equals(""))
		// return false;
		// else if(etCountry.getText().toString().trim().equals(""))
		// return false;
		// else if(etTwitter.getText().toString().trim().equals(""))
		// return false;
		// else
		return true;
	}

	private static String convertInputStreamToString(InputStream inputStream)
			throws IOException {
		BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(inputStream));
		String line = "";
		String result = "";
		while ((line = bufferedReader.readLine()) != null)
			result += line;

		inputStream.close();
		return result;

	}

	/**************************** Code from HxM *******************************/

	private void pairDevice(BluetoothDevice device) {
		try {

			Log.d(TAG, "Start Pairing...");
			Method m = device.getClass().getMethod("createBond", (Class[])null);
			device.getClass().getMethod("setPairingConfirmation", boolean.class).invoke(device, true);
			device.getClass().getMethod("cancelPairingUserInput", boolean.class).invoke(device);
			m.invoke(device, (Object[])null);
			Log.d(TAG, "Pairing finished.");
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}




}
