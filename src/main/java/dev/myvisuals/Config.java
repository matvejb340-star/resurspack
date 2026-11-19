package dev.myvisuals;

public final class Config {
	private Config() {}

	public static boolean trajectory = true;
	public static boolean bridge = false;
	public static boolean autoApple = false;
	public static boolean autoApplePlus = false;
	public static Weather weather = Weather.VANILLA;

	// --- настройки рук ---
	public static double mainScale = 1.0;
	public static double mainX = 0.0;
	public static double mainY = 0.0;
	public static double mainZ = 0.0;
	public static double offScale = 1.0;
	public static double offX = 0.0;
	public static double offY = 0.0;
	public static double offZ = 0.0;
	public static SwordAnim swordAnim = SwordAnim.VANILLA;

	public static void resetHands() {
		mainScale = 1.0;
		mainX = 0.0;
		mainY = 0.0;
		mainZ = 0.0;
		offScale = 1.0;
		offX = 0.0;
		offY = 0.0;
		offZ = 0.0;
		swordAnim = SwordAnim.VANILLA;
	}

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

	public enum SwordAnim {
		VANILLA("Обычная"),
		CHOP("Сверху вниз"),
		STAB("Укол вперёд"),
		SLASH("Удар на себя");

		public final String label;

		SwordAnim(String label) {
			this.label = label;
		}

		public SwordAnim next() {
			SwordAnim[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}
}
