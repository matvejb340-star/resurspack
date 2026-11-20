package dev.myvisuals;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Свой музыкальный плеер. Песни (mp3 или wav) лежат в папке «myvisuals_music» внутри папки игры.
 * Играет отдельным потоком, не мешая звукам игры. Умеет паузу, вперёд, назад,
 * автопереход к следующей песне и режим «только избранные».
 */
public final class MusicPlayer {
	private MusicPlayer() {}

	private record Decoded(byte[] pcm, AudioFormat format) {}

	private static final Object LOCK = new Object();
	private static final Set<String> FAVORITES = new HashSet<>();

	private static volatile List<Path> songs = List.of();
	private static boolean favoritesLoaded = false;

	public static volatile boolean favoritesOnly = false;
	public static volatile float volume = 0.8f;

	private static volatile Path current = null;
	private static volatile boolean paused = false;
	private static volatile boolean running = false;
	private static volatile int pending = 0; // 1 — следующая, -1 — предыдущая
	private static volatile Path requested = null;
	private static volatile double positionSec = 0;
	private static volatile double lengthSec = 0;
	private static volatile String error = null;

	// ------------------------------------------------------------ список песен

	public static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("myvisuals_music");
	}

	public static List<Path> songs() {
		return songs;
	}

	public static void refresh() {
		try {
			Files.createDirectories(dir());
			loadFavorites();
			try (Stream<Path> s = Files.list(dir())) {
				songs = s.filter(MusicPlayer::isSong)
						.sorted(Comparator.comparing((Path p) -> p.getFileName().toString().toLowerCase()))
						.toList();
			}
		} catch (IOException e) {
			songs = List.of();
			error = "Не удалось прочитать папку с музыкой";
		}
	}

	private static boolean isSong(Path p) {
		String n = p.getFileName().toString().toLowerCase();
		return Files.isRegularFile(p) && (n.endsWith(".mp3") || n.endsWith(".wav"));
	}

	public static String displayName(Path p) {
		String n = p.getFileName().toString();
		int dot = n.lastIndexOf('.');
		return dot > 0 ? n.substring(0, dot) : n;
	}

	// --------------------------------------------------------------- избранное

	private static Path favFile() {
		return dir().resolve("favorites.txt");
	}

	private static void loadFavorites() {
		if (favoritesLoaded) return;
		favoritesLoaded = true;
		try {
			if (Files.exists(favFile())) FAVORITES.addAll(Files.readAllLines(favFile()));
		} catch (IOException ignored) {
		}
	}

	public static boolean isFavorite(Path p) {
		return FAVORITES.contains(p.getFileName().toString());
	}

	public static void toggleFavorite(Path p) {
		String n = p.getFileName().toString();
		if (!FAVORITES.remove(n)) FAVORITES.add(n);
		try {
			Files.write(favFile(), new ArrayList<>(FAVORITES));
		} catch (IOException ignored) {
		}
	}

	private static List<Path> playlist() {
		List<Path> all = songs;
		if (!favoritesOnly) return all;
		List<Path> out = new ArrayList<>();
		for (Path p : all) {
			if (isFavorite(p)) out.add(p);
		}
		return out;
	}

	// --------------------------------------------------------------- управление

	public static Path current() {
		return current;
	}

	public static boolean isPlaying() {
		return running && !paused;
	}

	public static boolean isPaused() {
		return running && paused;
	}

	public static double positionSec() {
		return positionSec;
	}

	public static double lengthSec() {
		return lengthSec;
	}

	public static String error() {
		return error;
	}

	public static void play(Path p) {
		synchronized (LOCK) {
			requested = p;
			pending = 0;
			paused = false;
			startLocked();
		}
	}

	public static void next() {
		synchronized (LOCK) {
			requested = null;
			pending = 1;
			paused = false;
			startLocked();
		}
	}

	public static void prev() {
		synchronized (LOCK) {
			requested = null;
			pending = -1;
			paused = false;
			startLocked();
		}
	}

	public static void togglePause() {
		synchronized (LOCK) {
			if (running) {
				paused = !paused;
				return;
			}
			List<Path> pl = playlist();
			if (pl.isEmpty()) return;
			requested = (current != null && pl.contains(current)) ? current : pl.get(0);
			pending = 0;
			paused = false;
			startLocked();
		}
	}

	private static void startLocked() {
		if (running) return;
		running = true;
		Thread t = new Thread(MusicPlayer::run, "myvisuals-music");
		t.setDaemon(true);
		t.start();
	}

	/** Выбирает, что играть дальше. Вызывается под LOCK. null — играть больше нечего. */
	private static Path nextTrack() {
		if (requested != null) {
			Path t = requested;
			requested = null;
			pending = 0;
			return t;
		}
		List<Path> pl = playlist();
		if (pl.isEmpty()) return null;

		int mode = pending;
		pending = 0;
		int idx = current == null ? -1 : pl.indexOf(current);

		if (mode == 1) return pl.get((idx + 1) % pl.size());
		if (mode == -1) return pl.get(idx <= 0 ? pl.size() - 1 : idx - 1);

		// песня доиграла сама: идём дальше, на последней останавливаемся
		if (idx + 1 >= pl.size()) return null;
		return pl.get(idx + 1);
	}

	private static void run() {
		try {
			while (true) {
				Path track;
				synchronized (LOCK) {
					track = nextTrack();
					if (track == null) {
						running = false;
						paused = false;
						return;
					}
					current = track;
				}

				positionSec = 0;
				lengthSec = 0;

				Decoded d;
				try {
					d = decode(track);
				} catch (Throwable t) {
					error = "Не удалось открыть: " + track.getFileName();
					continue;
				}
				error = null;
				lengthSec = d.pcm().length / (double) (d.format().getFrameSize() * d.format().getFrameRate());

				playPcm(d);
			}
		} catch (Throwable t) {
			error = "Нет звука (" + t.getClass().getSimpleName() + ")";
			synchronized (LOCK) {
				running = false;
				paused = false;
			}
		}
	}

	private static void playPcm(Decoded d) throws Exception {
		AudioFormat fmt = d.format();
		byte[] pcm = d.pcm();
		int frame = fmt.getFrameSize();
		int chunk = frame * 2048;

		SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
		line.open(fmt, frame * Math.max(1024, (int) (fmt.getSampleRate() / 4)));
		line.start();
		boolean started = true;

		try {
			int pos = 0;
			while (pos < pcm.length) {
				if (pending != 0 || requested != null) {
					line.stop();
					line.flush();
					return;
				}
				if (paused) {
					if (started) {
						line.stop();
						started = false;
					}
					Thread.sleep(40);
					continue;
				}
				if (!started) {
					line.start();
					started = true;
				}

				int n = Math.min(chunk, pcm.length - pos);
				n -= n % frame;
				if (n <= 0) break;

				byte[] buf = new byte[n];
				System.arraycopy(pcm, pos, buf, 0, n);
				applyVolume(buf, n);
				line.write(buf, 0, n);

				pos += n;
				positionSec = pos / (double) (frame * fmt.getFrameRate());
			}
			if (pending == 0 && requested == null) line.drain();
		} finally {
			line.close();
		}
	}

	private static void applyVolume(byte[] buf, int n) {
		float v = volume;
		if (v >= 0.999f) return;
		for (int i = 0; i + 1 < n; i += 2) {
			short sample = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
			int s = (int) (sample * v);
			buf[i] = (byte) s;
			buf[i + 1] = (byte) (s >> 8);
		}
	}

	// ----------------------------------------------------------------- декодеры

	private static Decoded decode(Path p) throws Exception {
		String n = p.getFileName().toString().toLowerCase();
		if (n.endsWith(".mp3")) return decodeMp3(p);
		return decodeWav(p);
	}

	private static Decoded decodeMp3(Path p) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
		int rate = 44100;
		int channels = 2;

		try (InputStream in = new BufferedInputStream(Files.newInputStream(p))) {
			Bitstream bs = new Bitstream(in);
			Decoder dec = new Decoder();
			try {
				Header h;
				while ((h = bs.readFrame()) != null) {
					SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
					rate = dec.getOutputFrequency();
					channels = dec.getOutputChannels();
					short[] s = sb.getBuffer();
					int len = sb.getBufferLength();
					for (int i = 0; i < len; i++) {
						out.write(s[i] & 0xFF);
						out.write((s[i] >> 8) & 0xFF);
					}
					bs.closeFrame();
				}
			} catch (Exception e) {
				// битый хвост файла: если что-то уже декодировано, играем это
				if (out.size() == 0) throw e;
			}
		}

		if (out.size() == 0) throw new IOException("empty");
		AudioFormat fmt = new AudioFormat(rate, 16, channels, true, false);
		return new Decoded(out.toByteArray(), fmt);
	}

	private static Decoded decodeWav(Path p) throws Exception {
		try (AudioInputStream in = AudioSystem.getAudioInputStream(p.toFile())) {
			AudioFormat base = in.getFormat();
			AudioFormat target = new AudioFormat(
					AudioFormat.Encoding.PCM_SIGNED,
					base.getSampleRate(), 16, base.getChannels(),
					base.getChannels() * 2, base.getSampleRate(), false);
			try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, in)) {
				return new Decoded(pcm.readAllBytes(), target);
			}
		}
	}
}
