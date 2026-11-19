package dev.myvisuals;

public final class Config {
	private Config() {}

	public static boolean trajectory = true;
	public static boolean bridge = false;
	public static Weather weather = Weather.VANILLA;

	public enum Weather {
		VANILLA("Обычная"),
		CLEAR("Ясно"),
		RAIN("Дождь"),
		SNOW("Снег"),
		THUNDER("Гроза"),
		TOTEMS("Дождь из тотемов"),
		SWORDS("Дождь из мечей");

		public final String label;

		Weather(String label) {
			this.label = label;
		}

		public Weather next() {
			Weather[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}
}
