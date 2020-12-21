package com.kyunggi.songsynthesizer;

import android.app.*;
import android.os.*;
import android.speech.tts.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import be.tarsos.dsp.*;
import be.tarsos.dsp.io.*;
import be.tarsos.dsp.io.android.*;
import be.tarsos.dsp.pitch.*;
import be.tarsos.dsp.resample.*;
import ca.uol.aig.fftpack.*;
import com.kyunggi.songsynthesizer.WavFile.*;
import java.io.*;
import java.util.*;
import net.mabboud.android_tone_player.*;
import be.tarsos.dsp.writer.*;

public class MainActivity extends Activity implements View.OnClickListener, TextToSpeech.OnInitListener,RadioGroup.OnCheckedChangeListener
{
	private TextToSpeech tts;
	private String TAG="SongSynth";
	HashMap<String,String> mTTSMap = new HashMap<String,String>();
	//mTTSMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "abcde");

	ArrayList<Utterance> utterances=new ArrayList<Utterance>();
	private ComplexDoubleFFT transformer;//=new ComplexDoubleFFT(256);

	enum PHASE
	{
		INITIAL,
		FREQSETTING,
		DONE
	};

	PHASE phase=PHASE.INITIAL;
	int letterIndex;
	File projectDir;
	
	private void DoDone()
	{
		// TODO: Implement this method
		new Thread(new Runnable(){
				@Override
				public void run()
				{
					// TODO: Implement this method
					runOnUiThread(new Runnable(){
							@Override
							public void run()
							{
								// TODO: Implement this method
								StringBuilder sb=new StringBuilder();
								for(Utterance u:utterances){
									sb.append("ch:"+u.getCh()+" orifreq="+u.getOrifreq()+" freq="+u.getFreq()+"\n");
								}
								etStatus.setText(sb.toString());
								return ;
							}								
					});
					System.gc();
					for(Utterance u:utterances)
					{
						double targetFreq=u.getFreq();
						double oriFreq=u.getOrifreq();
						int id=u.getId();
						File file=new File(projectDir.getAbsolutePath() + "/" + id + ".wav");
						try
						{
							WavFile wavFile= WavFile.openWavFile(file);
							// Get the number of audio channels in the wav file
							int numChannels = wavFile.getNumChannels();
							long frames=wavFile.getNumFrames();
							long sampleRate=wavFile.getSampleRate();
							// Create a buffer of 100 frames
							wavFile.close();

							final int index=id;
							AudioDispatcher dispatcher=AudioDispatcherFactory.fromPipe(file.getAbsolutePath(),(int)wavFile.getSampleRate(),(int)wavFile.getNumFrames(),0);
						/*	PitchDetectionHandler pdh = new PitchDetectionHandler() {
								@Override
								public void handlePitch(PitchDetectionResult result,AudioEvent e) {
									final float pitchInHz = result.getPitch();
									synchronized(utterances)
									{
										utterances.get(index).setOrifreq(pitchInHz);
										Log.e(TAG,Character.toString(utters[index])+pitchInHz);
									}
								}
							};*/
						//	AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.DYNAMIC_WAVELET, wavFile.getSampleRate(),(int) wavFile.getNumFrames(), pdh);
							double rate = 1;
							if(oriFreq>0)
								rate=oriFreq/targetFreq;
							RateTransposer rateTransposer;
						//	AudioDispatcher dispatcher;
							WaveformSimilarityBasedOverlapAdd wsola;

							//dispatcher = AudioDispatcherFactory.fromPipe(mAudiopath, 44100, 5000, 2500);
							rateTransposer = new RateTransposer(rate);
							wsola = new WaveformSimilarityBasedOverlapAdd(WaveformSimilarityBasedOverlapAdd.Parameters.musicDefaults(rate, wavFile.getSampleRate()));
							WriterProcessor writer = new WriterProcessor((TarsosDSPAudioFormat) dispatcher.getFormat(),new RandomAccessFile(new File(projectDir.getAbsolutePath() + "/" + id + "_mod.wav"),"rw") );

							wsola.setDispatcher(dispatcher);
						//	dispatcher.addAudioProcessor(wsola);
							dispatcher.addAudioProcessor(rateTransposer);
							dispatcher.addAudioProcessor(new AndroidAudioPlayer(dispatcher.getFormat()));
							dispatcher.setZeroPadFirstBuffer(true);
							dispatcher.setZeroPadLastBuffer(true);
							dispatcher.addAudioProcessor(writer);
							u.setThread(new Thread(dispatcher,"Audio Dispatcher"));
							u.getThread().start();
						}
						catch (IOException e)
						{
							Log.e(TAG, "", e);
						}
						catch (WavFileException e)
						{
							Log.e(TAG, "", e);
						}
					}
					
					return;
				}			
			}
		).start();
		return;
	}

	private void StartSettingFrequencies()
	{
		// TODO: Implement this method
		phase=PHASE.FREQSETTING;
		letterIndex=0;
		llSetFreq.setVisibility(View.VISIBLE);
		btStartFreq.setEnabled(false);
		tvLetter.setText(new Character(lyric.charAt(letterIndex)).toString());
		tvProgress.setText(letterIndex+"/"+lyric.length());
		btNextFreq.setEnabled(true);
		//NextFrequency();
	}

	
	String lyric;
	String title;
	int finallength;

	Button btGo;
	EditText etLyric;
	EditText etTitle;
	EditText etStatus;
	TextView tvLetter;
	GridLayout glFreqs;
	Button btStartFreq;
	Button btNextFreq;
	TextView tvProgress;
	LinearLayout llSetFreq;
	RadioGroup rgNote;
	RadioGroup rgOctave;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		btGo = (Button) findViewById(R.id.btGo);
		etLyric = (EditText) findViewById(R.id.etLyric);
		etTitle = (EditText) findViewById(R.id.etTitle);
		etStatus = (EditText) findViewById(R.id.etStatus);
		tvLetter = (TextView) findViewById(R.id.tvLetter);
		glFreqs = (GridLayout) findViewById(R.id.glFreqs);
		btStartFreq = (Button) findViewById(R.id.btStartFreq);
		btNextFreq= (Button) findViewById(R.id.btNextFreq);
		tvProgress=(TextView) findViewById(R.id.tvProgress);
		llSetFreq=(LinearLayout) findViewById(R.id.llSetFreq);
		rgOctave=(RadioGroup) findViewById(R.id.rgOctave);
		rgNote=(RadioGroup) findViewById(R.id.rgNote);
		rgOctave.setOnCheckedChangeListener(this);
		/*new RadioGroup.OnCheckedChangeListener()
		 {
		 public void onCheckedChanged(RadioGroup group, int checkedId) {
		 // checkedId is the RadioButton selected
		 RadioButton rb=(RadioButton)findViewById(checkedId);
		 Toast.makeText(getApplicationContext(), rb.getText(), Toast.LENGTH_SHORT).show();
		 }
		 });*/
		rgNote.setOnCheckedChangeListener(this);
		/*new RadioGroup.OnCheckedChangeListener()
		 {
		 public void onCheckedChanged(RadioGroup group, int checkedId) {
		 // checkedId is the RadioButton selected
		 RadioButton rb=(RadioButton)findViewById(checkedId);
		 Toast.makeText(getApplicationContext(), rb.getText(), Toast.LENGTH_SHORT).show();
		 }
		 });*/
		llSetFreq.setVisibility(View.GONE);
		btGo.setOnClickListener(this);
		btStartFreq.setEnabled(false);
		btStartFreq.setOnClickListener(this);
		btNextFreq.setEnabled(false);
		btNextFreq.setOnClickListener(this);
		new AndroidFFMPEGLocator(this);
    }

	@Override
    protected void onDestroy()
	{
        super.onDestroy();
        try
		{
			tts.shutdown();  //speech 리소스 해제
		}
		catch (Exception e)
		{}
    }
	public class Utterance
	{
		double[] buffer;
		char ch;
		int id;
		double orifreq;
		double freq;
		Thread thread;
		public Utterance(double[] buffer, char ch, int id, double orifreq)
		{
			//this.buffer = buffer;
			this.ch = ch;
			this.id = id;
			this.orifreq = orifreq;
		}

		public void setThread(Thread thread)
		{
			this.thread = thread;
		}

		public Thread getThread()
		{
			return thread;
		}

		public void setBuffer(double[] buffer)
		{
			this.buffer = buffer;
		}

		public double[] getBuffer()
		{
			return buffer;
		}

		public void setCh(char ch)
		{
			this.ch = ch;
		}

		public char getCh()
		{
			return ch;
		}

		public void setId(int id)
		{
			this.id = id;
		}

		public int getId()
		{
			return id;
		}

		public void setOrifreq(double orifreq)
		{
			this.orifreq = orifreq;
		}

		public double getOrifreq()
		{
			return orifreq;
		}

		public void setFreq(double freq)
		{
			this.freq = freq;
		}

		public double getFreq()
		{
			return freq;
		}
	}
	private void Go()
	{
		// TODO: Implement this method
		btGo.setEnabled(false);
		lyric = etLyric.getText().toString();
		title = etTitle.getText().toString();
		try
		{
			tts.shutdown();
			tts = null;
		}
		catch (Exception e)
		{}
	    tts = new TextToSpeech(this, this);
	}
	@Override
	public void onClick(View p1)
	{
		// TODO: Implement this method
		int id=p1.getId();
		switch (id)
		{
			case R.id.btGo:
				Go();
				break;
			case R.id.btStartFreq:
				StartSettingFrequencies();
				break;
			case R.id.btNextFreq:
				NextFrequency();
				break;
		}
	}

	private void NextFrequency()
	{
		// TODO: Implement this metho
		if(phase==PHASE.DONE)
		{
			btNextFreq.setEnabled(false);
			DoDone();
			return;
		}
		if(phase!=PHASE.FREQSETTING)
			return;
		int octaveId=rgOctave.getCheckedRadioButtonId();
		int noteId=rgNote.getCheckedRadioButtonId();
		Utterance utter=utterances.get(letterIndex);
		utter.setFreq(IdsToFrequency(octaveId,noteId));
		tvLetter.setText(new Character(lyric.charAt(letterIndex)).toString());
		tvProgress.setText(letterIndex+"/"+lyric.length());
		++letterIndex;
		if(letterIndex>=lyric.length()) 
		{
			btNextFreq.setText("Done");
			phase=PHASE.DONE;
			return;
		}
	}
	/* 
	private void CombineWaveFile(String file1, String file2) {
		FileInputStream in1 = null, in2 = null;
		FileOutputStream out = null;
		long totalAudioLen = 0;
		long totalDataLen = totalAudioLen + 36;
		long longSampleRate = RECORDER_SAMPLERATE;
		int channels = 2;
		long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels / 8;

		byte[] data = new byte[bufferSize];

		try {
			in1 = new FileInputStream(file1);
			in2 = new FileInputStream(file2);

			out = new FileOutputStream(getFilename3());

			totalAudioLen = in1.getChannel().size() + in2.getChannel().size();
			totalDataLen = totalAudioLen + 36;

			WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
								longSampleRate, channels, byteRate);

			while (in1.read(data) != -1) {

				out.write(data);

			}
			while (in2.read(data) != -1) {

				out.write(data);
			}

			out.close();
			in1.close();
			in2.close();

			Toast.makeText(this, "Done!!", Toast.LENGTH_LONG).show();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
									 long totalDataLen, long longSampleRate, int channels, long byteRate)
	throws IOException {

		byte[] header = new byte[44];

		header[0] = 'R';
		header[1] = 'I';
		header[2] = 'F';
		header[3] = 'F';
		header[4] = (byte)(totalDataLen & 0xff);
		header[5] = (byte)((totalDataLen >> 8) & 0xff);
		header[6] = (byte)((totalDataLen >> 16) & 0xff);
		header[7] = (byte)((totalDataLen >> 24) & 0xff);
		header[8] = 'W';
		header[9] = 'A';
		header[10] = 'V';
		header[11] = 'E';
		header[12] = 'f';
		header[13] = 'm';
		header[14] = 't';
		header[15] = ' ';
		header[16] = 16;
		header[17] = 0;
		header[18] = 0;
		header[19] = 0;
		header[20] = 1;
		header[21] = 0;
		header[22] = (byte) channels;
		header[23] = 0;
		header[24] = (byte)(longSampleRate & 0xff);
		header[25] = (byte)((longSampleRate >> 8) & 0xff);
		header[26] = (byte)((longSampleRate >> 16) & 0xff);
		header[27] = (byte)((longSampleRate >> 24) & 0xff);
		header[28] = (byte)(byteRate & 0xff);
		header[29] = (byte)((byteRate >> 8) & 0xff);
		header[30] = (byte)((byteRate >> 16) & 0xff);
		header[31] = (byte)((byteRate >> 24) & 0xff);
		header[32] = (byte)(2 * 16 / 8);
		header[33] = 0;
		header[34] = RECORDER_BPP;
		header[35] = 0;
		header[36] = 'd';
		header[37] = 'a';
		header[38] = 't';
		header[39] = 'a';
		header[40] = (byte)(totalAudioLen & 0xff);
		header[41] = (byte)((totalAudioLen >> 8) & 0xff);
		header[42] = (byte)((totalAudioLen >> 16) & 0xff);
		header[43] = (byte)((totalAudioLen >> 24) & 0xff);

		out.write(header, 0, 44);
	}
	*/
	@Override
	public void onCheckedChanged(RadioGroup p1, int p2)
	{
		// TODO: Implement this method
		int octid=rgOctave.getCheckedRadioButtonId();
		int notid=rgNote.getCheckedRadioButtonId();
		if(octid<0)
		{
			octid=R.id.rbov0;
		}
		if(notid<0)
		{
			notid=R.id.rbNTC;
		}
		int freq=(int)IdsToFrequency(octid,notid);
		OneTimeBuzzer buzzer = new OneTimeBuzzer();
		buzzer.setDuration(2);

// volume values are from 0-100
		buzzer.setVolume(100);
		buzzer.setToneFreqInHz(freq);
		buzzer.play();
	}
	private void CreateUtters()
	{
		// TODO: Implement this method
		projectDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/SongSynth/" + title);
		if (projectDir.exists())
		{
			runOnUiThread(new Runnable(){

					@Override
					public void run()
					{
						// TODO: Implement this method
						Toast.makeText(MainActivity.this, "dup", 1).show();
						btGo.setEnabled(true);
					}
				});	

			return;
		}
		projectDir.mkdirs();
		final char [] utters=lyric.toCharArray();
		finallength = utters.length;
		for (int i=0;i < utters.length;++i)
		{
			File file=new File(projectDir.getAbsolutePath() + "/" + i + ".wav");
			mTTSMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "" + i);
			tts.synthesizeToFile(new String(new char[]{utters[i]}), mTTSMap, file.getAbsolutePath());
		}

		try
		{
			synchronized (this)
			{
				this.wait();
			}
		}
		catch (InterruptedException e)
		{}
		runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					// TODO: Implement this method
					etStatus.setText("Split, synth done");
				}
			});

		for (int i=0;i < utters.length;++i)
		{
			File file=new File(projectDir.getAbsolutePath() + "/" + i + ".wav");
			try
			{
				WavFile wavFile= WavFile.openWavFile(file);
				// Get the number of audio channels in the wav file
				int numChannels = wavFile.getNumChannels();
				long frames=wavFile.getNumFrames();
				long sampleRate=wavFile.getSampleRate();
				// Create a buffer of 100 frames
				wavFile.close();
				//	double[] buffer = new double[(int)(frames * numChannels)];

				int framesRead;

				final int index=i;
				AudioDispatcher dispatcher=AudioDispatcherFactory.fromPipe(file.getAbsolutePath(),(int)wavFile.getSampleRate(),(int)wavFile.getNumFrames(),0);
				PitchDetectionHandler pdh = new PitchDetectionHandler() {
					@Override
					public void handlePitch(PitchDetectionResult result,AudioEvent e) {
						final float pitchInHz = result.getPitch();
						synchronized(utterances)
						{
							utterances.get(index).setOrifreq(pitchInHz);
							Log.e(TAG,Character.toString(utters[index])+pitchInHz);
						}
					}
				};
				AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.DYNAMIC_WAVELET, wavFile.getSampleRate(),(int) wavFile.getNumFrames(), pdh);
				dispatcher.addAudioProcessor(p);
				Utterance u= new Utterance((double[])null,/*buffer,*/ utters[i], i, 0);
				utterances.add(u);
				u.setThread(new Thread(dispatcher,"Audio Dispatcher"));
				u.getThread().start();

				/*	do
				 {
				 // Read frames into buffer
				 framesRead = wavFile.readFrames(buffer, (int)frames);
				 // Loop through frames and look for minimum and maximum value
				 }
				 while (framesRead != 0);
				 /*	double[] tmpbuf=new double[buffer.length*2];
				 for(int k=0;k<buffㅇer.length;++k)
				 {
				 tmpbuf[2*k]=buffer[k];
				 tmpbuf[2*k+1]=(double) 0;
				 }

				 transformer = new ComplexDoubleFFT(buffer.length);
				 transformer.ft(tmpbuf);
				 double maxfreq=(double) 0;
				 //FIXME
				 for (int j=0;j < tmpbuf.length;++j)
				 {
				 if (tmpbuf[j] > maxfreq)
				 {
				 maxfreq = tmpbuf[j];
				 }		
				 }*/
				/*//Log.v(TAG,Arrays.toString(buffer));
				 Log.v(TAG, "" + utters[i]);
				 Log.v(TAG, "" + maxfreq);
				 try{
				 for(int k=0;k<tmpbuf.length/2;++k)
				 {
				 double real=tmpbuf[2*k];
				 double imag=tmpbuf[2*k+1];
				 tmpbuf[2*(k+4)]=real;
				 tmpbuf[2*(k+4)+1]=imag;
				 /*int index=k+1;
				 if(index>=utters.length)
				 {
				 break;
				 }
				 //buffer[k]=buffer[index];
				 }
				 }catch(ArrayIndexOutOfBoundsException e)
				 {

				 }

				 transformer.bt(tmpbuf);
				 for(int k=0;k<tmpbuf.length/2;++k)
				 {
				 double r=tmpbuf[2*k];
				 double img=tmpbuf[2*k+1];
				 buffer[k]=Math.sqrt(r*r+img*img);
				 }
				 // Create a wav file with the name specified as the first argument
				 WavFile wavFile2 = WavFile.newWavFile(new File(projectDir.getAbsolutePath() + "/" + i + "_mod.wav"), 1, frames, wavFile.getValidBits(), sampleRate);
				 //Write the buffer
				 wavFile2.writeFrames(buffer,(int) frames);
				 wavFile2.close();
				 */
			}
			catch (IOException e)
			{
				Log.e(TAG, "", e);
			}
			catch (WavFileException e)
			{
				Log.e(TAG, "", e);
			}
		}
		for(Utterance ut:utterances)
		{
			Thread th=ut.getThread();
			try
			{
				if (th != null)
					th.join();
			}
			catch (InterruptedException e)
			{}
		}
		runOnUiThread(new Runnable()
			{

				@Override
				public void run()
				{
					// TODO: Implement this method
					etStatus.setText("Load,Analysis done");
				}


			});
		runOnUiThread(new Runnable()
			{

				@Override
				public void run()
				{
					// TODO: Implement this method
					btStartFreq.setEnabled(true);
				}
			});
	}
	@Override
	public void onInit(int p1)
	{
		tts.setOnUtteranceProgressListener(new UtteranceProgressListener()
			{
				@Override
				public void onStart(final String p1)
				{
				}

				@Override
				public void onDone(final String p1)
				{
					int i=Integer.parseInt(p1);
					if (i == finallength - 1)
					{
						synchronized (MainActivity.this)
						{
							MainActivity.this.notifyAll();
						}
					}
					// TODO: Implement this method
					//Toast.makeText(MainActivity.this, "done" + p1, 1).show();
				}

				@Override
				public void onError(final String p1)
				{		
					runOnUiThread(new Runnable(){

							@Override
							public void run()
							{
								// TODO: Implement this method
								Toast.makeText(MainActivity.this, "error" + p1, 1).show();

							}});
					// TODO: Implement this method
					//Toast.makeText(MainActivity.this, "error" + p1, 1).show();
				}
			});

		Locale enUs = new Locale("korea");  //Locale("en_US");
        if (tts.isLanguageAvailable(enUs) == TextToSpeech.LANG_AVAILABLE)
        {
			tts.setLanguage(enUs);
		}
        else
		{
            tts.setLanguage(Locale.KOREA);
        }
        //myTTS.setLanguage(Locale.US);   // 언어 설정 , 단말기에 언어 없는 버전에선 안되는듯
        tts.setPitch((float) 0.1);  // 높낮이 설정 1이 보통, 6.0미만 버전에선 높낮이도 설정이 안됨
        tts.setSpeechRate(1); // 빠르기 설정 1이 보
   		new Thread(new Runnable(){
				@Override
				public void run()
				{
					// TODO: Implement this method
					CreateUtters();
				}			
			}).start();
	}
	private double IdsToFrequency(int octaveId, int noteId)
	{
		// TODO: Implement this method
		final double la=440.0;
		double freq=la;
		final double stair=Math.pow(2,((double)1/(double)12));
		double stairfactor=1.0;
		int numHeight=0;
		switch(octaveId)
		{
			case R.id.rbov0:
				break;
			case R.id.rbov1:
				freq*=2;
				break;
			case R.id.rbov2:
				freq*=4;
				break;
			case R.id.rbov3:
				freq*=8;
				break;
			case R.id.rbovn1:
				freq/=2;
				break;
			case R.id.rbovn2:
				freq/=4;
				break;
			case R.id.rbovn3:
				freq/=8;
				break;	
		}
		switch(noteId)
		{
			case R.id.rbNTA:
				numHeight=0;
				break;
			case R.id.rbNTAS:
				numHeight=1;
				break;
			case R.id.rbNTB:
				numHeight=2;
				break;
			case R.id.rbNTC:
				numHeight=-9;
				break;
			case R.id.rbNTCS:
				numHeight=-8;
				break;
			case R.id.rbNTD:
				numHeight=-7;
				break;
			case R.id.rbNTDS:
				numHeight=-6;
				break;
			case R.id.rbNTE:
				numHeight=-5;
				break;
			case R.id.rbNTF:
				numHeight=-4;
				break;
			case R.id.rbNTFS:
				numHeight=-3;
				break;
			case R.id.rbNTG:
				numHeight=-2;
				break;
			case R.id.rbNTGS:
				numHeight=-1;
				break;
		}
		stairfactor=Math.pow(stair,(double)numHeight);
		freq*=stairfactor;
		return freq;
	}
	/*
	 private static void startCli(String source,String target,double cents) throws  IOException{
	 PitchDetectionHandler pitchDetectionHandler = new PitchDetectionHandler() {
	 @Override
	 public void handlePitch(PitchDetectionResult pitchDetectionResult,
	 AudioEvent audioEvent) {
	 float pitch = pitchDetectionResult.getPitch();
	 }
	 };

	 PitchProcessor pitchProcessor = new PitchProcessor(FFT_YIN, SAMPLE_RATE,
	 BUFFER_SIZE, pitchDetectionHandler);

	 AudioDispatcher audioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE,
	 BUFFER_SIZE, OVERLAP);

	 audioDispatcher.addAudioProcessor(pitchProcessor);

	 audioDispatcher.run();

	 return ;
	 }
	 */
	




}
