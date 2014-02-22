/*
 * Copyright (C) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package edu.gatech.ubicomp.glim;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Vector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera.PreviewCallback;
import android.util.AttributeSet;
import android.view.SurfaceView;

/**
 * A view that displays audio data on the screen as a waveform.
 */
public class EDAWaveformView extends SurfaceView {

	// The number of buffer frames to keep around (for a nice fade-out
	// visualization.
	private static final int HISTORY_SIZE = 1;

	private static final int MAX_BUFFER_SIZE = 640; //keeping in mind the 640pixel wide screen

	// To make quieter sounds still show up well on the display, we use
	// +/- 8192 as the amplitude that reaches the top/bottom of the view
	// instead of +/- 32767. Any samples that have magnitude higher than this
	// limit will simply be clipped during drawing.
	private static final float MAX_AMPLITUDE_TO_DRAW = 10.0f;//8192.0f;
	private static final float MAX_EDA_VALUE = 11.0f;

	// The queue that will hold historical audio data.
	private LinkedList<int[]> mAudioData;
	private ArrayList<Float> mRawEdaData, mTonicEdaData, mPhasicEdaData;
	private Paint mPaint;
	private int mCounter = 0;
	private final float ALPHA = 0.02f;

	/*
	 * The two variables will go off limit soon as the data keeps coming in.
	 */
	private float runningAverage;
	private float runningDistanceSquareSum;
	private int runningCount;
	private float runningDeviation;
	private float lastRawEdaValue = 0.0f;
	private float lastTonicEdaValue = 0.0f;
	private float lastPhasicEdaValue = 0.0f;

	public EDAWaveformView(Context context) {
		this(context, null, 0);
	}

	public EDAWaveformView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EDAWaveformView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		mAudioData = new LinkedList<int[]>();
		mRawEdaData = new ArrayList<Float>();
		mTonicEdaData = new ArrayList<Float>();
		mPhasicEdaData = new ArrayList<Float>();

		mPaint = new Paint();
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setColor(Color.WHITE);
		mPaint.setStrokeWidth(0);
		mPaint.setAntiAlias(true);

		//FIXME Following code is supposed to draw the axis line as soon as EDAWaveformView is instantiated.
		//    Canvas canvas = getHolder().lockCanvas();
		//    if (canvas != null) {
		//    	mPaint.setARGB(128, 120, 120, 120);
		//    	int xAxisPos = (3* getHeight()) / 4;
		//    	canvas.drawLine(0,xAxisPos, getWidth()-1,xAxisPos, mPaint); //xAxis
		//    	getHolder().unlockCanvasAndPost(canvas);
		//    }
	}

	public synchronized void updateEDADataSimple(Float rawEdaValue) {
		//Put the new value in the queue
		float tonicEdaValue = 0.0f;
		float phasicEdaValue = 0.0f;

		if(mCounter == 0) {
			tonicEdaValue = rawEdaValue;
			phasicEdaValue = rawEdaValue - tonicEdaValue; 
		} else if (mCounter < MAX_BUFFER_SIZE) {
			tonicEdaValue = ALPHA*lastRawEdaValue + (1 - ALPHA)*lastTonicEdaValue;
			phasicEdaValue = rawEdaValue - tonicEdaValue;
		} else if (mCounter >= MAX_BUFFER_SIZE) {
			tonicEdaValue = ALPHA*lastRawEdaValue + (1 - ALPHA)*lastTonicEdaValue;
			phasicEdaValue = rawEdaValue - tonicEdaValue;
			mRawEdaData.remove(0);
			mTonicEdaData.remove(0);
			mPhasicEdaData.remove(0);
		}

		mRawEdaData.add(rawEdaValue);
		mTonicEdaData.add(tonicEdaValue);
		mPhasicEdaData.add(phasicEdaValue);
		lastRawEdaValue = rawEdaValue;
		lastTonicEdaValue = tonicEdaValue;
		lastPhasicEdaValue = phasicEdaValue;
		mCounter++;

		// Update the display.
		//if(mRawEdaData.size() == MAX_BUFFER_SIZE){
			Canvas canvas = getHolder().lockCanvas();
			if (canvas != null) {
				drawEDASignal(canvas);
				getHolder().unlockCanvasAndPost(canvas);
			}
		//}
	}

	public synchronized void updateEDAData(Float newValue) {
		//to prevent the running average from dropping too low if the person took off the sensor mid experiment 
		if(newValue != 0) {  
			runningDistanceSquareSum += Math.pow(newValue - runningAverage,2); 
			runningAverage = (float)(runningAverage*runningCount + newValue)/(runningCount+1);

			runningCount++; 
			runningDeviation = (float)Math.sqrt(runningDistanceSquareSum/runningCount);

			//		  if(mRawEdaData.size() < MAX_BUFFER_SIZE) { //to check if buffer has enough data to draw
			//		    	mRawEdaData.add((int) ((newValue - runningAverage)/runningDeviation));
			//		    } else {
			//		    	mRawEdaData.remove(0);
			//		    	mRawEdaData.add((int) ((newValue - runningAverage)/runningDeviation));
			//		    }

			//Calculate slope for the new value
			if(mRawEdaData.size() < MAX_BUFFER_SIZE) { //to check if buffer has enough data to draw
				mRawEdaData.add((float)(newValue - lastRawEdaValue)*1000);
			} else {
				mRawEdaData.remove(0);
				mRawEdaData.add((float)(newValue - lastRawEdaValue)*1000);
			}
			lastRawEdaValue = newValue;
		}

		// Update the display.
		if(mRawEdaData.size() == MAX_BUFFER_SIZE){
			Canvas canvas = getHolder().lockCanvas();
			if (canvas != null) {
				drawEDASignal(canvas);
				getHolder().unlockCanvasAndPost(canvas);
			}
		}
	}


	/**
	 * Repaints the view's surface.
	 * 
	 * @param canvas the {@link Canvas} object on which to draw.
	 */
	private void drawEDASignal(Canvas canvas) {
		// Clear the screen each time because SurfaceView won't do this for us.
		canvas.drawColor(Color.BLACK);

		float width = getWidth();
		float height = getHeight();
		int numPixelsPerPoint = (int)width/MAX_BUFFER_SIZE;
		float centerY = height / 2;
		float xAxisPos = (3* height) / 4;

		// We draw the history from oldest to newest so that the older audio
		// data is further back and darker than the most recent data.
		int colorDelta = 255 / (HISTORY_SIZE + 1);
		int brightness = colorDelta;


		//mPaint.setColor(Color.argb(brightness, 128, 255, 192));
		mPaint.setARGB(120, 255, 0, 0);	

		mPaint.setARGB(255, 80, 80, 80);
		canvas.drawLine(0,xAxisPos, width-1,xAxisPos, mPaint); //xAxis
		
		
		float lastX = -1;
		float lastRawEdaY = -1;
		float lastTonicEdaY = -1;
		float lastPhasicEdaY = -1;

		// For efficiency, we don't draw all of the samples in the buffer,
		// but only the ones that align with pixel boundaries.
		for (int index = 0; index < mRawEdaData.size(); index++) {
			//int index = (int) ((x / width) * MAX_BUFFER_SIZE);
			int x = index*numPixelsPerPoint ;
			Float rawEdaSample = mRawEdaData.get(index);
			Float tonicEdaSample = mTonicEdaData.get(index);
			Float phasicEdaSample = mPhasicEdaData.get(index);
			float rawEdaY = xAxisPos - (rawEdaSample / MAX_EDA_VALUE) * xAxisPos;
			if(rawEdaY < 0.0f) { //Clipping of the wave when it goes beyond the prescribed limits
				rawEdaY = 0.1f;
			}

			float tonicEdaY = xAxisPos - (tonicEdaSample / MAX_EDA_VALUE) * xAxisPos;
			if(tonicEdaY < 0.0f) { //Clipping of the wave when it goes beyond the prescribed limits
				tonicEdaY = 0.1f;
			}
			
			float phasicEdaY = xAxisPos - (phasicEdaSample / MAX_EDA_VALUE) * xAxisPos;
			if(phasicEdaY < 0.0f) { //Clipping of the wave when it goes beyond the prescribed limits
				phasicEdaY = 0.1f;
			}
			
			if (lastX != -1) {
				//canvas.drawLine(lastX, lastY, x, y, mPaint);
				//        	mPaint.setARGB(120, 0, 120, 0);
				//        	canvas.drawLine(0, runningAverage + centerY, width-1, runningAverage + centerY, mPaint); //running average
				/*mPaint.setColor(Color.YELLOW);
				canvas.drawLine(lastX, lastRawEdaY,x,rawEdaY, mPaint); //Raw EDA Signal 
*/				mPaint.setColor(Color.GREEN); 
				canvas.drawLine(lastX, lastTonicEdaY,x,tonicEdaY, mPaint); //Tonic EDA Signal 
				mPaint.setColor(Color.MAGENTA); 
				canvas.drawLine(lastX, lastPhasicEdaY,x,phasicEdaY, mPaint); //Phasic EDA Signal 
			}

			lastX = x;
			lastRawEdaY = rawEdaY;
			lastTonicEdaY = tonicEdaY;
			lastPhasicEdaY = phasicEdaY;


			//brightness += colorDelta;
		}
	}
}
