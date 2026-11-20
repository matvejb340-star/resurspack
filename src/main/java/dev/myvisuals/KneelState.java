package dev.myvisuals;

/** Позволяет прикрепить к состоянию отрисовки игрока число «насколько он стоит на колене». */
public interface KneelState {
	float myvisuals$getKneel();

	void myvisuals$setKneel(float value);
}
