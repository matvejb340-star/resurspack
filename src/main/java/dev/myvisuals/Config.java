package dev.myvisuals;

public final class Config {
	private Config() {}

	public static boolean trajectory = true;
	public static boolean bridge = false;
	public static boolean autoApple = false;
	public static boolean autoApplePlus = false;
	public static Weather weather = Weather.VANILLA;
	public static KillAnim killAnim = KillAnim.OFF;
	public static boolean radar = false;
	public static boolean triggerBot = false;
	public static SkyMode skyMode = SkyMode.VANILLA;
	public static SwordSkin swordSkin = SwordSkin.VANILLA;
	public static BowSkin bowSkin = BowSkin.VANILLA;
	public static boolean voidFirework = true;
	public static TargetFx targetFx = TargetFx.OFF;

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
		targetFx = TargetFx.OFF;
		swordSkin = SwordSkin.VANILLA;
		bowSkin = BowSkin.VANILLA;
	}

	public enum SkyMode {
		VANILLA("Обычное"),
		AURORA_GREEN("Северное сияние (зелёное)"),
		AURORA_PURPLE("Северное сияние (фиолетовое)");

		public final String label;

		SkyMode(String label) {
			this.label = label;
		}

		public SkyMode next() {
			SkyMode[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}

	public enum BowSkin {
		VANILLA("Обычный лук"),
		RPG("РПГ");

		public final String label;

		BowSkin(String label) {
			this.label = label;
		}

		public BowSkin next() {
			BowSkin[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}

	public enum SwordSkin {
		VANILLA("Обычный меч"),
		M9("Нож M9"),
		M9_GOLD("Нож M9 золотой");

		public final String label;

		SwordSkin(String label) {
			this.label = label;
		}

		public SwordSkin next() {
			SwordSkin[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
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

	public enum TargetFx {
		OFF("Выключено"),
		CROSS("Круг с крестом"),
		WAVE("Волна"),
		BUBBLES("Пузырьки");

		public final String label;

		TargetFx(String label) {
			this.label = label;
		}

		public TargetFx next() {
			TargetFx[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}

	public enum KillAnim {
		OFF("Выключено"),
		KNEEL("На колено"),
		TOILET("Туалет с ракетой"),
		SOUL("Душа");

		public final String label;

		KillAnim(String label) {
			this.label = label;
		}

		public KillAnim next() {
			KillAnim[] all = values();
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
