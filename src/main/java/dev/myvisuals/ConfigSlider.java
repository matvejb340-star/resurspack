package dev.myvisuals;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/** Простой ползунок с настоящим диапазоном значений [min; max]. */
public class ConfigSlider extends SliderWidget {
	private final String label;
	private final double min;
	private final double max;
	private final DoubleConsumer setter;

	public ConfigSlider(int x, int y, int width, int height, String label,
						double min, double max, double current, DoubleConsumer setter) {
		super(x, y, width, height, Text.empty(), (current - min) / (max - min));
		this.label = label;
		this.min = min;
		this.max = max;
		this.setter = setter;
		updateMessage();
	}

	private double real() {
		return min + (max - min) * this.value;
	}

	@Override
	protected void updateMessage() {
		setMessage(Text.literal(label + ": " + String.format(Locale.ROOT, "%.2f", real())));
	}

	@Override
	protected void applyValue() {
		setter.accept(real());
	}
}
