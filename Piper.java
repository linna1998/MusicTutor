package pied;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

//import javax.sound.midi.InvalidMidiDataException;
//import javax.sound.midi.MidiSystem;
//import javax.sound.midi.MidiUnavailableException;
//import javax.sound.midi.Sequence;
//import javax.sound.midi.Sequencer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.GainProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.jvm.AudioPlayer;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

public class Piper extends JFrame {

	/**
	 *
	 */
	private static final long serialVersionUID = -3830419374132803358L;
	private AudioDispatcher dispatcher;
	private GainProcessor gain;
	private AudioPlayer audioPlayer;
	private UpdatePlot myPlot;
	private float pitch = -1;
	private SilenceDetector silenceDetector;
	private InputPanel inputPanel;
	private Mixer mixer=null;
	private File midifile = (new File("test.midi"));

	public Piper() {
		this.setLayout(new BorderLayout());
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setTitle("Music Tutor: learning violin right now!");
		myPlot = new UpdatePlot();
		new Thread(myPlot).start();

		// Open the midi file.
		if (!midifile.exists()) {
			System.out.println("打开文件失败：源midi文件" + midifile + "不存在!");
		} else {
			System.out.println("打开文件成功！");
		}

		JPanel inputSubPanel = new JPanel(new BorderLayout());
		inputPanel = new InputPanel();
		inputPanel.addPropertyChangeListener("mixer", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent arg0) {
				mixer = (Mixer) arg0.getNewValue();
				startFile(null, mixer);
				startFile(midifile, mixer);
			}
		});
		inputPanel.addPropertyChangeListener("loopback", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent arg0) {
				if(mixer!=null) {
					startFile(null, mixer);
					startFile(midifile, mixer);
				}
			}
		});
		inputSubPanel.add(inputPanel, BorderLayout.NORTH);

		this.add(inputSubPanel, BorderLayout.NORTH);

		JPanel chartSubPanel = new JPanel(new BorderLayout());
		TimeSeriesCollection dataset = new TimeSeriesCollection(myPlot.ts1);
		JFreeChart chart1 = ChartFactory.createTimeSeriesChart("Loudness", "Time", "dB", dataset, false, true, false);
		XYPlot plot = chart1.getXYPlot();
		ValueAxis axis = plot.getDomainAxis();
		axis.setFixedAutoRange(6000.0);
		ChartPanel panel = new ChartPanel(chart1);
		panel.setPreferredSize(new java.awt.Dimension(600, 300));
		chartSubPanel.add(panel, BorderLayout.NORTH);

		dataset = new TimeSeriesCollection(myPlot.ts2);
		JFreeChart chart2 = ChartFactory.createTimeSeriesChart("Pitch", "Time", "Hertz", dataset, false, true, false);
		plot = chart2.getXYPlot();
		axis = plot.getDomainAxis();
		axis.setFixedAutoRange(6000.0);
		panel = new ChartPanel(chart2);
		panel.setPreferredSize(new java.awt.Dimension(600, 300));
		chartSubPanel.add(panel, BorderLayout.SOUTH);

		this.add(chartSubPanel, BorderLayout.SOUTH);
	}

	public static void main(String[] argv) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					try {
						UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
					} catch (Exception e) {
						// ignore failure to set default look en feel;
					}
					JFrame frame = new Piper();
					frame.pack();
					frame.setSize(600, 800);
					frame.setVisible(true);
				}
			});
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new Error(e);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
			throw new Error(e);
		}
	}

	private void startFile(final File inputFile, Mixer mixer) {

		if (dispatcher != null) {
			dispatcher.stop();
		}
		AudioFormat format;
		try {
			if (inputFile != null) {
				format = AudioSystem.getAudioFileFormat(inputFile).getFormat();
			} else {
				format = new AudioFormat(44100, 16, 1, true, true);
			}
			gain = new GainProcessor(1.0);
			silenceDetector = new SilenceDetector();
			PitchDetectionHandler handler = new PitchDetectionHandler() {
				@Override
				public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
					pitch = pitchDetectionResult.getPitch();
				}
			};
			audioPlayer = new AudioPlayer(format);

			DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
			TargetDataLine line;
			line = (TargetDataLine) mixer.getLine(dataLineInfo);
			line.open(format, 4096);
			line.start();
			final AudioInputStream stream = new AudioInputStream(line);
			JVMAudioInputStream audioStream = new JVMAudioInputStream(stream);
			// create a new dispatcher
			dispatcher = new AudioDispatcher(audioStream, 2048, 0);

			dispatcher.addAudioProcessor(gain);
			// TODO
			// PitchEstimationAlgorithm can be changed in
			// MPM FFT_YIN YIN
			dispatcher.addAudioProcessor(new PitchProcessor(PitchEstimationAlgorithm.MPM, 44100, 2048, handler));
			dispatcher.addAudioProcessor(silenceDetector);
			if(inputPanel.loopback)dispatcher.addAudioProcessor(audioPlayer);

			Thread t = new Thread(dispatcher);
			t.start();

		} catch (UnsupportedAudioFileException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (LineUnavailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		myPlot.ts1.clear();
		myPlot.ts2.clear();
	}


	public class UpdatePlot implements Runnable {
		public TimeSeries ts1 = new TimeSeries("data");
		public TimeSeries ts2 = new TimeSeries("data");

		@Override
		public void run() {
			while (true) {
				if (silenceDetector != null) {
					ts1.addOrUpdate(new Millisecond(), silenceDetector.currentSPL());
					ts2.addOrUpdate(new Millisecond(), pitch);
				}
				try {
					Thread.sleep(40);
				} catch (InterruptedException ex) {
					System.out.println(ex);
				}
			}
		}

	}

}
