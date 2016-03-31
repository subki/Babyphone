/*
	"android babyalarm" makes your android smartphone monitor sounds  
	(e.g. of your sleeping baby) and calls a given phone number	in case 
	the loudness exceeds a certain level.
	
    Copyright (C) 2010  der_hannes@users.sourceforge.net
	

 	This file is part of "android babyalarm".

    "android babyalarm" is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    "android babyalarm" is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

	You can find a copy of the GNU General Public License in the source code
	of "android babyalarm" at <http://babyalarm.git.sourceforge.net/git/
	gitweb.cgi?p=babyalarm/babyalarm;a=blob_plain;f=gpl.txt;hb=HEAD>.

    Or visit <http://www.gnu.org/licenses/>.
*/

package com.example.subki.babyphon;

import android.media.*;
import android.media.MediaRecorder.AudioSource;

public class MicrophoneInput {
	private static final int[] frequencyArray = {44100,22050,11025,8000};
	public int frequency = MicrophoneInput.frequencyArray[3]; 
	private static int channel = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	private static int encoding = AudioFormat.ENCODING_PCM_16BIT;
	private static int bufferSize;
	private short[] audioData;
	private int currentAmplitude;
	private AudioRecord recorder;
	
	public MicrophoneInput()
	{
		try
		{
			bufferSize = AudioRecord.getMinBufferSize(frequency, channel, encoding);
			recorder = new AudioRecord(AudioSource.MIC,	frequency, channel, encoding, bufferSize * 5);
		}
		catch (Exception e)
		{
			System.out.println("Error in startRecording(): " + e.toString());
			currentAmplitude = -1;
		}	
	}
	
	protected int getCurrentLoudness() {
			// already initialized? (e.g. RECORD.AUDIO not set)
			if (recorder.getState() != android.media.AudioRecord.STATE_INITIALIZED)
			{
				return -3;
			}
			if (recorder.getRecordingState() == android.media.AudioRecord.RECORDSTATE_STOPPED)
			{
				recorder.startRecording(); //check to see if the Recorder has stopped or is not recording, and make it record.
			}

			try
			{
				// read
				audioData = new short[1000];
				int noOfResults = recorder.read(audioData,0,1000); //read the PCM audio data into the audioData array 
		
				if (noOfResults < 1){
					return -4;
				}
			}
			catch(Exception e){
				System.out.println("Error with recorder.read() " + e.toString());
				e.printStackTrace();
				currentAmplitude = -5;
			}
			
			try
			{
				// TODO calculate "real" mean amplitude
				
				// calculate geometric mean
				float mean;
				int sum = 0;
				int numberOfElements = 0;
				
				for (short i=0; i<1000; i++)
				{
					if (audioData[i] != 0)
					{
						numberOfElements++;
						if (audioData[i] > 0)
							sum = sum + audioData[i];
						else
							sum = sum + (-1 * audioData[i]);
					}
				}
				
				mean = sum / numberOfElements;
				
				currentAmplitude = (int) mean;
			}
			catch (Exception e)
			{
				System.out.println("Error in calculating amplitude's mean: " + e.toString());
				e.printStackTrace();
				currentAmplitude = -6;
			}
	
			return currentAmplitude;
	}
	
	public void stop(){
		recorder.stop();
		recorder.release();
		recorder = null;
		int dummy=0;
	}
}
