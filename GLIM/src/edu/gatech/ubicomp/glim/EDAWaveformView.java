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
  
  private static final int MAX_BUFFER_SIZE = 128; //keeping in mind the 640pixel wide screen
  
  // To make quieter sounds still show up well on the display, we use
  // +/- 8192 as the amplitude that reaches the top/bottom of the view
  // instead of +/- 32767. Any samples that have magnitude higher than this
  // limit will simply be clipped during drawing.
  private static final float MAX_AMPLITUDE_TO_DRAW = 10.0f;//8192.0f;
  private static final float MAX_EDA_VALUE = 11.0f;

  // The queue that will hold historical audio data.
  private LinkedList<int[]> mAudioData;
  private Vector<Float> mEDAData;
  private Paint mPaint;
  
  /*
   * The two variables will go off limit soon as the data keeps coming in.
   */
  private float runningAverage;
  private float runningDistanceSquareSum;
  private int runningCount;
  private float runningDeviation;
  private float lastValue = 0.0f;

  public EDAWaveformView(Context context) {
    this(context, null, 0);
  }

  public EDAWaveformView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EDAWaveformView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    mAudioData = new LinkedList<int[]>();
    mEDAData = new Vector<Float>();

    mPaint = new Paint();
    mPaint.setStyle(Paint.Style.STROKE);
    mPaint.setColor(Color.WHITE);
    mPaint.setStrokeWidth(0);
    mPaint.setAntiAlias(true);
  }

  /**
   * Updates the waveform view with a new "frame" of samples and renders it.
   * The new frame gets added to the front of the rendering queue, pushing the
   * previous frames back, causing them to be faded out visually.
   * 
   * @param buffer the most recent buffer of audio samples.
   */
  public synchronized void updateAudioData(int[] buffer) {
    int[] newBuffer;

    // We want to keep a small amount of history in the view to provide a nice
    // fading effect. We use a linked list that we treat as a queue for this.
    if (mAudioData.size() == HISTORY_SIZE) {
      newBuffer = mAudioData.removeFirst();
      System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
    } else {
      newBuffer = buffer.clone();
    }

    mAudioData.addLast(newBuffer);

    // Update the display.
    Canvas canvas = getHolder().lockCanvas();
    if (canvas != null) {
      drawWaveform(canvas);
      getHolder().unlockCanvasAndPost(canvas);
    }
  }
  
  public synchronized void updateEDADataSimple(Float newValue) {
	  //Put the new value in the queue
	  if(mEDAData.size() < MAX_BUFFER_SIZE) { //to check if buffer has enough data to draw
	    	mEDAData.add(newValue);
	    } else {
	    	mEDAData.remove(0);
	    	mEDAData.add(newValue);
	    }
	  lastValue = newValue;
    // Update the display.
    if(mEDAData.size() == MAX_BUFFER_SIZE){
    Canvas canvas = getHolder().lockCanvas();
    if (canvas != null) {
      drawEDASignal(canvas);
      getHolder().unlockCanvasAndPost(canvas);
    }
}
  }
  
  public synchronized void updateEDAData(Float newValue) {
	//to prevent the running average from dropping too low if the person took off the sensor mid experiment 
	  if(newValue != 0) {  
		  runningDistanceSquareSum += Math.pow(newValue - runningAverage,2); 
		  runningAverage = (float)(runningAverage*runningCount + newValue)/(runningCount+1);
		  
		  runningCount++; 
		  runningDeviation = (float)Math.sqrt(runningDistanceSquareSum/runningCount);
		  
//		  if(mEDAData.size() < MAX_BUFFER_SIZE) { //to check if buffer has enough data to draw
//		    	mEDAData.add((int) ((newValue - runningAverage)/runningDeviation));
//		    } else {
//		    	mEDAData.remove(0);
//		    	mEDAData.add((int) ((newValue - runningAverage)/runningDeviation));
//		    }
		  
		  //Calculate slope for the new value
		  if(mEDAData.size() < MAX_BUFFER_SIZE) { //to check if buffer has enough data to draw
		    	mEDAData.add((float)(newValue - lastValue)*1000);
		    } else {
		    	mEDAData.remove(0);
		    	mEDAData.add((float)(newValue - lastValue)*1000);
		    }
		  lastValue = newValue;
	  }
    
//	    if (mAudioData.size() == HISTORY_SIZE) {
//	      newBuffer = mAudioData.removeFirst();
//	      System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
//	    } else {
//	      newBuffer = buffer.clone();
//	    }
//
//	    mAudioData.addLast(newBuffer);

	    // Update the display.
	    if(mEDAData.size() == MAX_BUFFER_SIZE){
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
    float centerY = height / 2;
    float xAxisPos = (3* height) / 4;

    // We draw the history from oldest to newest so that the older audio
    // data is further back and darker than the most recent data.
    int colorDelta = 255 / (HISTORY_SIZE + 1);
    int brightness = colorDelta;

    
      //mPaint.setColor(Color.argb(brightness, 128, 255, 192));
    mPaint.setARGB(120, 255, 0, 0);	
    	
      float lastX = -1;
      float lastY = -1;

      // For efficiency, we don't draw all of the samples in the buffer,
      // but only the ones that align with pixel boundaries.
      for (int index = 0; index < MAX_BUFFER_SIZE; index++) {
        //int index = (int) ((x / width) * MAX_BUFFER_SIZE);
    	  int x = index*5 ;
        Float sample = mEDAData.elementAt(index);
        float y = xAxisPos - (sample / MAX_EDA_VALUE) * xAxisPos;
        if(y < 0.0f) { //Clipping of the wave when it goes beyond the prescribed limits
        	y = 0.1f;
        }
    	  
        if (lastX != -1) {
          //canvas.drawLine(lastX, lastY, x, y, mPaint);
//        	mPaint.setARGB(120, 0, 120, 0);
//        	canvas.drawLine(0, runningAverage + centerY, width-1, runningAverage + centerY, mPaint); //running average
        	mPaint.setARGB(255, 0, 255, 0);
        	canvas.drawLine(lastX, lastY,x,y, mPaint); //EDA Signal 
        	mPaint.setARGB(255, 120, 120, 120);
        	canvas.drawLine(0,xAxisPos, width-1,xAxisPos, mPaint); //xAxis
        }
        
        lastX = x;
        lastY = y;
     

      //brightness += colorDelta;
    }
  }

  /**
   * Repaints the view's surface.
   * 
   * @param canvas the {@link Canvas} object on which to draw.
   */
  private void drawWaveform(Canvas canvas) {
    // Clear the screen each time because SurfaceView won't do this for us.
    canvas.drawColor(Color.BLACK);

    float width = getWidth();
    float height = getHeight();
    float centerY = height / 2;

    // We draw the history from oldest to newest so that the older audio
    // data is further back and darker than the most recent data.
    int colorDelta = 255 / (HISTORY_SIZE + 1);
    int brightness = colorDelta;

    for (int[] buffer : mAudioData) {
      //mPaint.setColor(Color.argb(brightness, 128, 255, 192));
    mPaint.setARGB(120, 255, 0, 0);	
    	
      float lastX = -1;
      float lastY = -1;

      // For efficiency, we don't draw all of the samples in the buffer,
      // but only the ones that align with pixel boundaries.
      for (int x = 0; x < width; x=x+5) {
        int index = (int) ((x / width) * buffer.length);
        int sample = buffer[index];
//        float y = (sample / MAX_AMPLITUDE_TO_DRAW) * centerY + centerY;

    	  float y = (sample) + centerY;
    	  
        if (lastX != -1) {
          //canvas.drawLine(lastX, lastY, x, y, mPaint);
        	canvas.drawCircle(x, y, 5, mPaint);
        	
        }
        
        lastX = x;
        lastY = y;
      }

      brightness += colorDelta;
    }
  }
}
