package dev.quiqzy.client.render;

/**
 * Live-Preview-Kamera: Wenn die Settings eines Visuals-Moduls offen sind, kreist die Kamera um deinen
 * echten Charakter (so sieht man die echten Flügel/Effekte live und dreht die Ansicht mit der Maus).
 * AuroraGui füllt yaw/pitch aus der Mausposition, FreecamMixins.CameraMixin setzt die Kamera.
 */
public final class PreviewCam {
	public static volatile boolean active;
	public static volatile float yaw;
	public static volatile float pitch;
	public static volatile float dist = 2.4f;

	private PreviewCam() {
	}
}
